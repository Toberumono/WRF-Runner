package toberumono.wrf;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import toberumono.json.JSONBoolean;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.json.JSONString;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.scope.InvalidVariableAccessException;
import toberumono.wrf.scope.ModuleScopedMap;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedComponent;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.ComputedTiming;
import toberumono.wrf.timing.NamelistTiming;
import toberumono.wrf.timing.Timing;

import static toberumono.wrf.SimulationConstants.*;

/**
 * The primary management construct for this program.
 * 
 * @author Toberumono
 */
public class Simulation extends ScopedComponent<Scope> {
	private static final ExecutorService pool = Executors.newWorkStealingPool();
	
	private final Logger logger;
	private final JSONObject configuration;
	private final ScopedMap general, timing;
	private final Path working, resolver;
	private final Timing globalTiming;
	private final Map<String, Module> modules;
	private final Set<Module> disabledModules;
	private final ScopedMap source, active;
	private Integer doms;
	private final NamelistNumber interval_seconds;
	private Boolean serialModuleExecution;
	
	/**
	 * Constructs a new {@link Simulation}.
	 * 
	 * @param base
	 *            the time at which the {@link Simulation} was started
	 * @param resolver
	 *            the {@link Path} used to resolve relative paths
	 * @param configuration
	 *            a {@link JSONObject} holding the configuration for the {@link Simulation}
	 * @throws IOException
	 *             if an error occurs while constructing the working directory
	 */
	public Simulation(Calendar base, Path resolver, JSONObject configuration) throws IOException {
		super(ModuleScopedMap.buildFromJSON(configuration), null);
		getParameters().setParent(this); //We have to assign parent after calling super because of the "this" component
		this.resolver = resolver;
		this.configuration = configuration;
		this.general = (ScopedMap) getParameters().get("general");
		this.timing = (ScopedMap) getParameters().get("timing");
		logger = Logger.getLogger(SIMULATION_LOGGER_ROOT);
		logger.setLevel(Level.parse(general.get("logging-level").toString().toUpperCase()));
		source = new ScopedMap(this);
		active = new ScopedMap(this);
		disabledModules = new HashSet<>();
		modules = Collections.unmodifiableMap(parseModules((JSONObject) configuration.get("module"), (JSONObject) configuration.get("path")));
		globalTiming = ((Boolean) getGeneral().get("use-computed-times")) ? new ComputedTiming((ScopedMap) getTimingMap().get("global"), base, this)
				: new NamelistTiming(getModule("wrf").getNamelist().get("time_control"), this);
		working = constructWorkingDirectory(getResolver().resolve(getGeneral().get("working-directory").toString()), (Boolean) getGeneral().get("always-suffix"));
		for (String name : this.modules.keySet())
			active.put(name, ((JSONObject) configuration.get("path")).containsKey(name) ? getWorkingPath().resolve(((Path) source.get(name)).getFileName()) : getWorkingPath().resolve(name));
		ScopedMap timestep = this.modules.containsKey("grib") && !disabledModules.contains(modules.get("grib"))
				? ScopedMap.buildFromJSON((JSONObject) ((JSONObject) configuration.get("grib")).get("timestep")) : null;
		interval_seconds = timestep != null ? new NamelistNumber(calcIntervalSeconds(timestep)) : null;
		doms = null;
		serialModuleExecution = null;
	}
	
	/**
	 * @return the processed global {@link Timing} information
	 */
	@NamedScopeValue("timing")
	public Timing getTiming() {
		return globalTiming;
	}
	
	/**
	 * @return the raw global {@link Timing} information as a {@link ScopedMap}
	 */
	@NamedScopeValue("timing-map")
	public ScopedMap getTimingMap() {
		return timing;
	}
	
	/**
	 * @return the "general" subsection of the configuration file as a {@link ScopedMap}
	 */
	@NamedScopeValue("general")
	public ScopedMap getGeneral() {
		return general;
	}
	
	/**
	 * Retrieves the absolute {@link Path} to the given {@link Module Module's} source directory.<br>
	 * The source directory of a {@link Module} is the absolute form of the {@link Path} specified in the "path" section of the configuration file.
	 * 
	 * @param module
	 *            the name of the {@link Module}
	 * @return absolute {@link Path} to the named {@link Module Module's} source directory
	 * @see #getActivePath(String)
	 */
	public Path getSourcePath(String module) {
		return (Path) source.get(module);
	}
	
	/**
	 * Retrieves the absolute {@link Path} to the given {@link Module Module's} active directory.<br>
	 * The active directory of a {@link Module} is the absolute {@link Path} to the root of the {@link Module Module's} directory in the timestamped
	 * {@link #getWorkingPath() working directory} for the {@link Simulation}
	 * 
	 * @param module
	 *            the name of the {@link Module}
	 * @return absolute {@link Path} to the named {@link Module Module's} active directory
	 * @see #getSourcePath(String)
	 */
	public Path getActivePath(String module) {
		return (Path) active.get(module);
	}
	
	/**
	 * @return the {@link Path} used to resolve relative {@link Path Paths} in the configuration file
	 */
	@NamedScopeValue(value = "resolver", asString = true)
	public Path getResolver() {
		return resolver;
	}
	
	/**
	 * @return an absolute {@link Path} to the timestamped working directory
	 */
	@NamedScopeValue(value = {"working-directory", "working-path"}, asString = true)
	public Path getWorkingPath() {
		return working;
	}
	
	/**
	 * @param name
	 *            the name of the {@link Module} to retrieve
	 * @return the {@link Module} corresponding to the given {@code name} or {@code null}
	 */
	public Module getModule(String name) {
		return modules.get(name);
	}
	
	/**
	 * @return the number of domains to use (pulled from the WRF {@link Module Module's} {@link Namelist} file
	 * @throws IOException
	 *             if an error occurs while loading the {@link Namelist} data
	 */
	@NamedScopeValue("doms")
	public Integer getDoms() throws IOException {
		if (doms == null)
			doms = ((Number) modules.get("wrf").getNamelist().get("domains").get("max_dom").get(0).value()).intValue();
		return doms;
	}
	
	/**
	 * @return the computed interval seconds value for {@link Namelist} files as a {@link NamelistNumber}
	 */
	public NamelistNumber getIntervalSeconds() {
		return interval_seconds;
	}
	
	/**
	 * @return the computed interval seconds value for {@link Namelist} files as a {@link Number}
	 */
	@NamedScopeValue("interval-seconds")
	public Number getIntervalSecondsValue() {
		return getIntervalSeconds().value();
	}
	
	/**
	 * @return {@code true} if {@link Module Modules} should be executed exclusively serially
	 */
	@NamedScopeValue({"serial-module-execution", "force-serial-module-execution"})
	public Boolean isSerialModuleExecution() {
		if (serialModuleExecution == null)
			serialModuleExecution = getGeneral().containsKey("force-serial-module-execution") ? ((Boolean) getGeneral().get("force-serial-module-execution")) : false;
		return serialModuleExecution;
	}
	
	/**
	 * Constructs the {@link Simulation Simulation's} timestamped working directory based on the {@link Path} specified in the "working-directory"
	 * field of "general".
	 * 
	 * @param workingRoot
	 *            the {@link Path} to the root (non-timestamped) working directory
	 * @param always_suffix
	 *            if {@code true} a '+1' will be appended to unique timestamped names (this is to provide consistency with the notation for when
	 *            subsequent {@link Simulation Simulations} request folders with the same timestamp).
	 * @return the {@link Path} to the constructed working directory
	 * @throws IOException
	 *             if an I/O error occured
	 */
	public Path constructWorkingDirectory(Path workingRoot, boolean always_suffix) throws IOException {
		Path active = Files.createDirectories(workingRoot).resolve("active"), root;
		try (FileChannel channel = FileChannel.open(active, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = channel.lock()) {
			String name = makeWPSDateString(getTiming().getStart()).replaceAll(":", "_"); //Having colons in the path messes up WRF, so... Underscores.
			try (Stream<Path> children = Files.list(workingRoot)) {
				int count = children.filter(p -> p.getFileName().toString().startsWith(name)).toArray().length;
				root = Files.createDirectories(workingRoot.resolve(always_suffix || count > 0 ? name + "+" + (count + 1) : name));
			}
		}
		return root;
	}
	
	/**
	 * Links the {@link Simulation Simulation's} {@link Module Modules} into their respective active directories as per the logic in
	 * {@link Module#linkToWorkingDirectory()}.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs while linking the {@link Module Modules}
	 */
	public void linkModules() throws IOException {
		for (Module module : modules.values())
			module.linkToWorkingDirectory();
	}
	
	/**
	 * Updates the {@link Simulation Simulation's} {@link Module Modules'} {@link Namelist} files and writes to the result to each {@link Module
	 * Module's} active directory as per the logic in {@link Module#updateNamelist()} and {@link Module#writeNamelist()}.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public void updateNamelists() throws IOException {
		for (Module module : modules.values()) {
			module.updateNamelist();
			module.writeNamelist();
		}
	}
	
	private Map<String, Module> parseModules(JSONObject modules, JSONObject paths) {
		Map<String, Module> out = new LinkedHashMap<>();
		for (String name : modules.keySet()) {
			try {
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
		ScopedMap description = ScopedMap.buildFromJSON((JSONObject) modules.get(name), this); //We use this so that computed fields can be accessed here
		JSONObject parameters = condenseSubsections(name::equals, configuration, "configuration", Integer.MAX_VALUE);
		if (!parameters.containsKey(TIMING_FIELD_NAME))
			parameters.put(TIMING_FIELD_NAME, makeGenericInheriter());
		parameters.put("name", new JSONString(name));
		ModuleScopedMap moduleParameters = ModuleScopedMap.buildFromJSON(parameters);
		@SuppressWarnings("unchecked") Class<? extends Module> clazz = (Class<? extends Module>) Class.forName(description.get("class").toString());
		Constructor<? extends Module> constructor = clazz.getConstructor(ModuleScopedMap.class, Simulation.class);
		Module m = constructor.newInstance(moduleParameters, this);
		if (description.containsKey("execute") && !((Boolean) description.get("execute")))
			disabledModules.add(m);
		return m;
	}
	
	/**
	 * Executes the {@link Module Modules} loaded in the {@link Simulation}
	 * 
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws InterruptedException
	 *             if any of the {@link Module} processes are interrupted
	 */
	public void executeModules() throws IOException, InterruptedException {
		List<Module> remaining = modules.values().stream().filter(mod -> !disabledModules.contains(mod)).collect(Collectors.toList());
		Set<Module> completed = new HashSet<>();
		while (remaining.size() > 0) {
			List<Module> runnable = new ArrayList<>();
			for (Iterator<Module> iter = remaining.iterator(); iter.hasNext();) {
				Module current = iter.next();
				if (completed.containsAll(current.getDependencies())) {
					runnable.add(current);
					iter.remove();
				}
			}
			if (runnable.size() == 0)
				break;
			if (isSerialModuleExecution()) {
				for (Module module : runnable)
					completed.add(executeModule(module));
			}
			else {
				List<Future<Module>> running = runnable.stream().map(module -> pool.submit(() -> executeModule(module))).collect(Collectors.toList());
				for (Future<Module> future : running) {
					try {
						completed.add(future.get());
					}
					catch (ExecutionException e) {
						if (e.getCause() instanceof IOException)
							throw (IOException) e.getCause();
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	/**
	 * Executes a single {@link Module} and handles keep-logs and cleanup.
	 * 
	 * @param module
	 *            the {@link Module} to execute
	 * @return the {@link Module} passed to {@code Module} (for compatibility with {@link Callable})
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws InterruptedException
	 *             if the process is interrupted
	 */
	protected Module executeModule(Module module) throws IOException, InterruptedException {
		module.execute();
		if ((Boolean) general.get("keep-logs"))
			Files.walkFileTree(getActivePath(module.getName()),
					new TransferFileWalker(getWorkingPath(), Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
		if ((Boolean) general.get("cleanup"))
			module.cleanUp();
		return module;
	}
	
	private static int calcIntervalSeconds(ScopedMap timestep) {
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
	
	/**
	 * @return a {@link JSONObject} that just has the field, "inherit" set to {@code true}
	 */
	public static final JSONObject makeGenericInheriter() {
		JSONObject out = new JSONObject();
		out.put("inherit", new JSONBoolean(true));
		return out;
	}
	
	@Override
	public boolean hasValueByName(String name) {
		if (getModule(name) != null)
			return true;
		switch (name) {
			case "sim":
			case "simulation":
			case "source-paths":
			case "active-paths":
				return true;
			default:
				return super.hasValueByName(name);
		}
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		Object out = getModule(name);
		if (out != null)
			return out;
		switch (name) {
			case "sim":
			case "simulation":
				return this;
			case "source-paths":
				return source;
			case "active-paths":
				return active;
			default:
				return super.getValueByName(name);
		}
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
	
	/**
	 * Creates a new {@link Simulation}.
	 * 
	 * @param resolver
	 *            the {@link Path} used to resolve relative paths
	 * @param configuration
	 *            a {@link JSONObject} holding the configuration for the {@link Simulation}
	 * @return the new {@link Simulation}
	 * @throws IOException
	 *             if an error occurs while constructing the working directory
	 */
	public static Simulation initSimulation(JSONObject configuration, Path resolver) throws IOException {
		return new Simulation(Calendar.getInstance(), resolver, configuration);
	}
}
