package toberumono.wrf;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import toberumono.json.JSONArray;
import toberumono.json.JSONBoolean;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.json.JSONString;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedConfiguration;
import toberumono.wrf.timing.JSONTiming;
import toberumono.wrf.timing.NamelistTiming;
import toberumono.wrf.timing.Timing;

public class Simulation extends AbstractScope<Scope> {
	
	private final Logger logger;
	private final JSONObject configuration;
	private final ScopedConfiguration general, timing, parallel;
	private final Path working, resolver;
	private final Timing globalTiming;
	private final Map<String, Module> modules;
	private final Set<Module> disabledModules;
	private final Map<String, Path> source, active;
	private Integer doms;
	private final NamelistNumber interval_seconds;
	
	public Simulation(Calendar base, Path resolver, JSONObject configuration, JSONObject general, JSONObject modules, JSONObject paths, JSONObject timing) throws IOException {
		super(null);
		this.resolver = resolver;
		this.configuration = configuration;
		this.general = ScopedConfiguration.buildFromJSON(general, this);
		this.timing = ScopedConfiguration.buildFromJSON(timing, this);
		logger = Logger.getLogger("WRFRunner.Simulation");
		logger.setLevel(Level.parse(((String) general.get("logging-level").value()).toUpperCase()));
		parallel = (ScopedConfiguration) getGeneral().get("parallel");
		source = new HashMap<>();
		active = new HashMap<>();
		disabledModules = new HashSet<>();
		this.modules = Collections.unmodifiableMap(parseModules(modules, paths));
		globalTiming = ((Boolean) getTiming().get("use-computed-times")) ? new JSONTiming((ScopedConfiguration) this.timing.get("global"), base)
				: new NamelistTiming(this.modules.get("wrf").getNamelist().get("time_control"));
		working = constructWorkingDirectory(getResolver().getFileSystem().getPath(getGeneral().get("working-directory").toString()), (Boolean) general.get("always-suffix").value());
		for (JSONData<?> mod : (JSONArray) modules.get("execution-order")) {
			String name = (String) mod.value();
			active.put(name, paths.containsKey(name) ? getWorkingPath().resolve(source.get(name).getFileName()) : getWorkingPath().resolve(name));
		}
		ScopedConfiguration timestep = this.modules.containsKey("grib") && !disabledModules.contains(modules.get("grib"))
						? ScopedConfiguration.buildFromJSON((JSONObject) ((JSONObject) configuration.get("grib")).get("timestep")) : null;
		interval_seconds = timestep != null ? new NamelistNumber(calcIntervalSeconds(timestep)) : null;
		doms = null;
	}
	
	public Timing getGlobalTiming() {
		return globalTiming;
	}
	
	public ScopedConfiguration getTiming() {
		return timing;
	}
	
	public ScopedConfiguration getGeneral() {
		return general;
	}
	
	public ScopedConfiguration getParallel() {
		return parallel;
	}
	
	public Path getSourcePath(String module) {
		return source.get(module);
	}
	
	public Path getActivePath(String module) {
		return active.get(module);
	}
	
	public Path getResolver() {
		return resolver;
	}
	
	public Path getWorkingPath() {
		return working;
	}
	
	public Integer getDoms() throws IOException {
		if (doms == null)
			doms = ((Number) modules.get("wrf").getNamelist().get("domains").get("max_dom").get(0).value()).intValue();
		return doms;
	}
	
	public NamelistNumber getIntervalSeconds() {
		return interval_seconds;
	}
	
	public Path constructWorkingDirectory(Path workingRoot, boolean always_suffix) throws IOException {
		Path active = Files.createDirectories(workingRoot).resolve("active"), root;
		try (FileChannel channel = FileChannel.open(active, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
			String name = makeWPSDateString(getGlobalTiming().getStart()).replaceAll(":", "_"); //Having colons in the path messes up WRF, so... Underscores.
			try (Stream<Path> children = Files.list(workingRoot)) {
				int count = children.filter(p -> p.getFileName().toString().startsWith(name)).toArray().length;
				root = Files.createDirectories(workingRoot.resolve(always_suffix || count > 0 ? name + "+" + (count + 1) : name));
			}
		}
		return root;
	}
	
	public void linkModules() throws IOException {
		for (Module module : modules.values())
			module.linkToWorkingDirectory();
	}
	
	public void updateNamelists() throws IOException, InterruptedException {
		for (Module module : modules.values()) {
			module.updateNamelist();
			module.writeNamelist();
		}
	}
	
	public void executeModules() throws IOException, InterruptedException {
		for (Module module : modules.values()) {
			if (disabledModules.contains(module))
				continue;
			module.execute();
			if ((Boolean) general.get("keep-logs"))
				Files.walkFileTree(getActivePath(module.getName()),
						new TransferFileWalker(getWorkingPath(), Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
			if ((Boolean) general.get("cleanup"))
				module.cleanUp();
		}
	}
	
	private Map<String, Module> parseModules(JSONObject modules, JSONObject paths) {
		Map<String, Module> out = new LinkedHashMap<>();
		for (JSONData<?> mod : (JSONArray) modules.get("execution-order")) {
			try {
				String name = mod.value().toString();
				if (paths.containsKey(name))
					source.put(name, getResolver().resolve(paths.get(name).value().toString()));
				out.put(name, loadModule(name, modules));
			}
			catch (ClassNotFoundException | NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				// TODO Deal with failures when loading modules
				e.printStackTrace();
			}
		}
		return out;
	}
	
	private Module loadModule(String name, JSONObject modules)
			throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException, ClassNotFoundException {
		JSONObject description = (JSONObject) modules.get(name);
		JSONObject parameters = condenseSubsections(name::equals, configuration, "configuration", Integer.MAX_VALUE);
		parameters.put("name", new JSONString(name));
		@SuppressWarnings("unchecked") Class<? extends Module> clazz = (Class<? extends Module>) Class.forName(description.get("class").value().toString());
		Constructor<? extends Module> constructor = clazz.getConstructor(ScopedConfiguration.class, Simulation.class);
		Module m = constructor.newInstance(ScopedConfiguration.buildFromJSON(parameters), this);
		if (description.containsKey("execute") && !((Boolean) description.get("execute").value()))
			disabledModules.add(m);
		return m;
	}
	
	private static int calcIntervalSeconds(ScopedConfiguration timestep) {
		int out = ((Number) timestep.get("seconds")).intValue();
		out += ((Number) timestep.get("minutes")).intValue() * 60;
		out += ((Number) timestep.get("hours")).intValue() * 60 * 60;
		out += ((Number) timestep.get("days")).intValue() * 24 * 60 * 60;
		return out;
	}
	
	/**
	 * Converts the date in the given {@link Calendar} to a WPS {@link Namelist} file date string
	 * 
	 * @param cal
	 *            a {@link Calendar}
	 * @return a date string usable in a WPS {@link Namelist} file
	 */
	public static final String makeWPSDateString(Calendar cal) {
		return String.format(Locale.US, "%d-%02d-%02d_%02d:%02d:%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	
	private static JSONObject condenseSubsections(Predicate<String> lookingFor, JSONObject root, String rootName, int maxDepth) {
		JSONObject out = condenseSubsections(new JSONObject(), lookingFor, root, rootName, maxDepth - 1);
		out.clearModified();
		return out;
	}
	
	private static JSONObject condenseSubsections(JSONObject condensed, Predicate<String> lookingFor, JSONObject container, String containerName, int remainingDepth) {
		for (Entry<String, JSONData<?>> e : container.entrySet()) {
			if (lookingFor.test(e.getKey()))
				condensed.put(containerName, e.getValue());
			else if (remainingDepth > 0 && e.getValue() instanceof JSONObject)
				condenseSubsections(condensed, lookingFor, (JSONObject) e.getValue(), e.getKey(), remainingDepth - 1);
		}
		return condensed;
	}
	
	public static Simulation initSimulation(Path configurationPath, boolean updateFile) throws IOException {
		Calendar base = Calendar.getInstance();
		JSONObject configuration = (JSONObject) JSONSystem.loadJSON(configurationPath);
		//Extract configuration file sections
		JSONObject general = (JSONObject) configuration.get("general");
		JSONObject module = (JSONObject) configuration.get("module");
		JSONObject path = (JSONObject) configuration.get("path");
		JSONObject timing = (JSONObject) configuration.get("timing");
		
		if (updateFile) {
			JSONSystem.transferField("use-computed-times", new JSONBoolean(true), timing);
			JSONSystem.transferField("logging-level", new JSONString("INFO"), general);
		}
		
		return new Simulation(base, configurationPath.toAbsolutePath().normalize().getParent(), configuration, general, module, path, timing);
	}
}
