package toberumono.wrf;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import toberumono.json.JSONArray;
import toberumono.json.JSONBoolean;
import toberumono.json.JSONData;
import toberumono.json.JSONNumber;
import toberumono.json.JSONObject;
import toberumono.json.JSONString;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.structures.SortingMethods;
import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.versioning.VersionNumber;
import toberumono.utils.files.RecursiveEraser;
import toberumono.wrf.timing.DisabledTiming;
import toberumono.wrf.timing.ComputedTiming;
import toberumono.wrf.timing.Timing;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.clear.DisabledClear;
import toberumono.wrf.timing.clear.ListClear;
import toberumono.wrf.timing.clear.StandardClear;
import toberumono.wrf.timing.duration.DisabledDuration;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.duration.ListDuration;
import toberumono.wrf.timing.duration.StandardDuration;
import toberumono.wrf.timing.offset.DisabledOffset;
import toberumono.wrf.timing.offset.ListOffset;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.offset.StandardOffset;
import toberumono.wrf.timing.round.BucketRound;
import toberumono.wrf.timing.round.DisabledRound;
import toberumono.wrf.timing.round.FractionalRound;
import toberumono.wrf.timing.round.FunctionRound;
import toberumono.wrf.timing.round.ListRound;
import toberumono.wrf.timing.round.Round;

import static toberumono.wrf.SimulationConstants.*;

/**
 * A "script" for automatically running WRF and WPS installations.<br>
 * This will (from the Namelists and its own configuration file (a small one)) compute the appropriate start and end times of a simulation based on
 * the date and time at which it is run, download the relevant data (in this case NAM data) for that time range, run WPS, run WRF, and then clean up
 * after itself. (Note that the cleanup is fairly easy because it creates a working directory in which it runs WRF, and then just leaves the output
 * there)
 * 
 * @author Toberumono
 */
public class WRFRunner {
	protected final Logger log;
	private static final String[] DEFAULT_MODULE_NAMES = {"grib", "wps", "wrf"};
	private static final String[] DEFAULT_MODULE_CLASSES = {"toberumono.wrf.modules.GRIBModule", "toberumono.wrf.modules.WPSModule", "toberumono.wrf.modules.WRFModule"};
	private static final String[] DEFAULT_MODULE_NAMELISTS = {null, "namelist.wps", "run/namelist.input"};
	private static final String[][] DEFAULT_MODULE_DEPENDENCIES = {{}, {"grib"}, {"wps"}};
	
	/**
	 * All that is needed to run this "script".
	 * 
	 * @param args
	 *            the arguments to the script. This must have a length of 1, and contain a valid path to a configuration file.
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws InterruptedException
	 *             if a process gets interrupted
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		initFactories();
		boolean cacheUpdates = false;
		Path configurationPath = Paths.get("configuration.json");
		for (String arg : args) {
			switch (arg) {
				case "--cache-updates-only":
					cacheUpdates = true;
					break;
				default:
					configurationPath = Paths.get(arg);
			}
		}
		boolean canRun = true;
		WRFRunner runner = new WRFRunner();
		JSONObject configuration = (JSONObject) JSONSystem.loadJSON(configurationPath);
		if (!configuration.containsKey("version") || getVersionNumber(configuration).compareTo(new VersionNumber("4.0.0")) < 0)
			canRun = !runner.checkForPotentialFormulaStrings(configuration);
		if (!canRun) {
			runner.log.severe("Cannot run the simulation - the configuration file has potentially invalid Strings");
			Runtime.getRuntime().exit(1);
		}
		else {
			configuration = runner.upgradeConfiguration(configuration);
			if (!cacheUpdates && configuration.isModified()) {
				runner.getLog().info("Updating the configuration file located at: " + configurationPath);
				JSONSystem.writeJSON(configuration, configurationPath);
				runner.getLog().info("Updates completed.");
			}
			Simulation sim = runner.createSimulation(configuration, configurationPath);
			runner.runSimulation(sim);
		}
	}
	
	/**
	 * Initializes the {@link WRFRunnerComponentFactory WRFRunnerComponentFactories} for {@link Offset}, {@link Round}, {@link Duration},
	 * {@link Clear}, and {@link Timing}.
	 */
	public static void initFactories() {
		WRFRunnerComponentFactory<Offset> offsetFactory = WRFRunnerComponentFactory.getFactory(Offset.class, "standard", DisabledOffset::getDisabledOffsetInstance);
		offsetFactory.addComponentConstructor("standard", StandardOffset::new);
		offsetFactory.addComponentConstructor("list", ListOffset::new);
		WRFRunnerComponentFactory<Round> roundFactory = WRFRunnerComponentFactory.getFactory(Round.class, "bucket", DisabledRound::getDisabledRoundInstance);
		roundFactory.addComponentConstructor("bucket", BucketRound::new);
		roundFactory.addComponentConstructor("fractional", FractionalRound::new);
		roundFactory.addComponentConstructor("function", FunctionRound::new);
		roundFactory.addComponentConstructor("list", ListRound::new);
		WRFRunnerComponentFactory<Duration> durationFactory = WRFRunnerComponentFactory.getFactory(Duration.class, "standard", DisabledDuration::getDisabledDurationInstance);
		durationFactory.addComponentConstructor("standard", StandardDuration::new);
		durationFactory.addComponentConstructor("list", ListDuration::new);
		WRFRunnerComponentFactory<Clear> clearFactory = WRFRunnerComponentFactory.getFactory(Clear.class, "standard", DisabledClear::getDisabledClearInstance);
		clearFactory.addComponentConstructor("standard", StandardClear::new);
		clearFactory.addComponentConstructor("list", ListClear::new);
		WRFRunnerComponentFactory<Timing> timingFactory = WRFRunnerComponentFactory.getFactory(Timing.class, "computed", DisabledTiming::getDisabledTimingInstance);
		timingFactory.addComponentConstructor("computed", ComputedTiming::new);
	}
	
	/**
	 * Constructs a {@link WRFRunner} from the given configuration file
	 * 
	 * @throws IOException
	 *             if the configuration file cannot be read from disk
	 */
	public WRFRunner() throws IOException {
		log = Logger.getLogger("WRFRunner");
		Handler systemOut = new ConsoleHandler();
		systemOut.setLevel(Level.ALL);
		log.addHandler(systemOut);
		log.setUseParentHandlers(false);
	}
	
	/**
	 * @return the the {@link Logger} used by the {@link WRFRunner}
	 */
	public final Logger getLog() {
		return log;
	}
	
	/**
	 * Constructs a {@link Simulation} using the given configuration file.
	 * 
	 * @param configurationFile
	 *            a {@link Path} to the configuration file
	 * @return the {@link Simulation} defined by that configuration file
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public Simulation createSimulation(Path configurationFile) throws IOException {
		return Simulation.initSimulation(upgradeConfiguration((JSONObject) JSONSystem.loadJSON(configurationFile)), configurationFile.toAbsolutePath().normalize().getParent());
	}
	
	/**
	 * Constructs a {@link Simulation} using the given configuration file.
	 * 
	 * @param configuration
	 *            the configuration file loaded into a {@link JSONObject}
	 * @param configurationFile
	 *            a {@link Path} to the configuration file
	 * @return the {@link Simulation} defined by that configuration file
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public Simulation createSimulation(JSONObject configuration, Path configurationFile) throws IOException {
		return Simulation.initSimulation(upgradeConfiguration((JSONObject) JSONSystem.loadJSON(configurationFile)), configurationFile.toAbsolutePath().normalize().getParent());
	}
	
	/**
	 * Executes the steps needed to run wget, WPS, and then WRF. This method automatically calculates the appropriate start and end times of the
	 * simulation from the configuration and {@link Namelist} files, and downloads the boundary data accordingly.
	 * 
	 * @param sim
	 *            the {@link Simulation} to run
	 * @throws IOException
	 *             if the {@link Namelist} files could not be read
	 * @throws InterruptedException
	 *             if one of the processes gets interrupted
	 */
	public void runSimulation(Simulation sim) throws IOException, InterruptedException {
		sim.linkModules();
		sim.updateNamelists();
		sim.executeModules();
		cleanUpOldSimulations(sim);
	}
	
	private void cleanUpOldSimulations(Simulation sim) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(sim.getWorkingPath())) {
			int maxOutputs = ((Number) sim.getGeneral().get("max-kept-outputs")).intValue();
			if (maxOutputs < 1)
				return;
			SortedList<Path> sl = new SortedList<>(SortingMethods.PATH_MODIFIED_TIME_ASCENDING);
			for (Path p : stream)
				if (Files.isDirectory(p))
					sl.add(p);
			while (sl.size() > maxOutputs)
				Files.walkFileTree(sl.remove(0), new RecursiveEraser());
		}
		catch (IOException e) {
			log.log(Level.SEVERE, "Unable to clean up old simulation data.", e);
		}
	}
	
	/**
	 * Uses the "version" field in the given {@code configuration} file to upgrade the file to the latest version.<br>
	 * <b>Note:</b> This method does <i>not</i> modify the passed {@link JSONObject}
	 * 
	 * @param configuration
	 *            the configuration file to upgrade as a {@link JSONObject}
	 * @return an upgraded copy of {@code configuration}
	 */
	public JSONObject upgradeConfiguration(JSONObject configuration) {
		JSONObject out = configuration.deepCopy();
		JSONObject general = (JSONObject) out.get("general"), timing = (JSONObject) out.get("timing");
		if (!out.containsKey("version"))
			out.put("version", "1.0.0");
		performConfigurationUpgradeAction(out, "1.3.0", () -> {
			if (out.containsKey("paths")) {
				JSONObject paths = (JSONObject) out.get("paths");
				if (paths.containsKey("wrf")) { //As of this version, we use the root of the WRF directory
					String wrf = (String) paths.get("wrf").value();
					if (wrf.endsWith("/run"))
						paths.put("wrf", wrf.substring(0, wrf.length() - 4)); //-4 because we want to get rid of the potential trailing '/'
				}
			}
		});
		
		performConfigurationUpgradeAction(out, "1.4.0", () -> {
			JSONSystem.renameField(general, new JSONNumber<>(15), "max-outputs"); //This is renamed to max-kept-outputs in the 1.5.5 section, so this step is just for posterity
		});
		
		performConfigurationUpgradeAction(out, "1.5.0", () -> {
			if (out.containsKey("commands")) //As of this version, commands are automatically determined
				out.remove("commands");
		});
		
		performConfigurationUpgradeAction(out, "1.5.5", () -> JSONSystem.renameField(general, new JSONNumber<>(15), "max-outputs", "max-kept-outputs"));
		
		performConfigurationUpgradeAction(out, "1.5.6",
				() -> JSONSystem.transferField("use-computed-times", timing.containsKey("rounding") ? ((JSONObject) timing.get("rounding")).get("enabled") : new JSONBoolean(true), timing));
		
		performConfigurationUpgradeAction(out, "1.6.1", () -> JSONSystem.transferField("keep-logs", new JSONBoolean(false), general));
		
		performConfigurationUpgradeAction(out, "1.7.0", () -> {
			if (!general.containsKey("always-suffix")) {
				if (general.get("parallel") instanceof JSONObject && ((JSONObject) general.get("parallel")).containsKey("always-suffix"))
					general.put("always-suffix", ((JSONObject) general.get("parallel")).get("always-suffix"));
				else if (out.get("wrf") instanceof JSONObject && ((JSONObject) out.get("wrf")).get("parallel") instanceof JSONObject &&
						((JSONObject) ((JSONObject) out.get("wrf")).get("parallel")).containsKey("always-suffix"))
					general.put("always-suffix", ((JSONObject) ((JSONObject) out.get("wrf")).get("parallel")).get("always-suffix"));
				else
					general.put("always-suffix", false);
			}
		});
		
		performConfigurationUpgradeAction(out, "2.0.0", () -> general.remove("wait-for-wrf"));
		
		performConfigurationUpgradeAction(out, "2.1.5", () -> {
			if (out.containsKey("paths")) {
				JSONObject paths = (JSONObject) out.get("paths");
				String working = null;
				if (paths.containsKey("working"))
					working = (String) paths.remove("working").value();
				if (paths.containsKey("working-directory")) //working-directory is the more recent name, so it has priority
					working = (String) paths.remove("working-directory").value();
				if (working != null)
					JSONSystem.transferField("working-directory", new JSONString(working), general); //We use this because it will also ensure that existing values aren't overwritten
			}
			if (general.containsKey("features"))
				JSONSystem.transferField("cleanup", new JSONBoolean(true), (JSONObject) general.get("features"), general);
		});
		
		performConfigurationUpgradeAction(out, "2.1.9", () -> {
			if (timing.get("rounding") instanceof JSONObject && !((JSONObject) timing.get("rounding")).containsKey("fraction"))
				((JSONObject) timing.get("rounding")).put("fraction", 1.0);
		});
		
		performConfigurationUpgradeAction(out, "3.1.0", () -> JSONSystem.transferField("wrap-timestep", new JSONBoolean(true), (JSONObject) out.get("grib")));
		
		depluralize(out, false);
		performConfigurationUpgradeAction(out, "4.0.0", () -> {
			JSONObject modules = (JSONObject) out.get("module");
			JSONSystem.transferField("logging-level", new JSONString("INFO"), general);
			//Build the module section
			if (general.containsKey("features")) {
				JSONObject features = (JSONObject) general.get("features");
				JSONSystem.renameField(features, new JSONBoolean(true), "wget", "grib");
				for (int i = 0; i < DEFAULT_MODULE_NAMES.length; i++) {
					if (!modules.containsKey(DEFAULT_MODULE_NAMES[i]))
						modules.put(DEFAULT_MODULE_NAMES[i], buildModuleSubsection(features.containsKey(DEFAULT_MODULE_NAMES[i]) ? (Boolean) features.get(DEFAULT_MODULE_NAMES[i]).value() : true,
								DEFAULT_MODULE_CLASSES[i], DEFAULT_MODULE_NAMELISTS[i], DEFAULT_MODULE_DEPENDENCIES[i]));
				}
			}
			else {
				for (int i = 0; i < DEFAULT_MODULE_NAMES.length; i++)
					if (!modules.containsKey(DEFAULT_MODULE_NAMES[i]))
						modules.put(DEFAULT_MODULE_NAMES[i], buildModuleSubsection(true, DEFAULT_MODULE_CLASSES[i], DEFAULT_MODULE_NAMELISTS[i], DEFAULT_MODULE_DEPENDENCIES[i]));
			}
			//Build the timing->global subsection
			if (!timing.containsKey("global")) {
				JSONObject rounding = (JSONObject) timing.remove("rounding");
				JSONObject global = new JSONObject();
				if (timing.containsKey("duration"))
					global.put("duration", timing.remove("duration")); //Duration has remained unchanged
				global.put("offset", timing.remove("offset")); //Offset has remained unchanged
				JSONObject clear = new JSONObject();
				
				String magnitude = (String) rounding.get("magnitude").value();
				if (!magnitude.endsWith("s")) {
					magnitude = magnitude + "s";
					rounding.put("magnitude", magnitude);
				}
				//Build the timing->global->clear subsection
				clear.put("keep", TIMING_FIELD_NAMES.get(TIMING_FIELD_NAMES.indexOf(magnitude) - 1));
				clear.put("enabled", rounding.get("enabled"));
				global.put("clear", clear);
				//Build the timing->global->rounding subsection
				rounding.put("type", "fractional");
				if (!rounding.containsKey("fraction")) {
					rounding.put("fraction", 1.0);
					rounding.put("enabled", false);
				}
				if (!rounding.containsKey("diff") || rounding.get("diff").equals("current"))
					rounding.put("diff", "none");
				global.put("rounding", rounding);
				timing.put("global", global);
			}
			if (((JSONObject) out.get("grib")).containsKey("wrap-timestep")) //If grib contains wrap-timestep, transfer it to grib->timestep
				JSONSystem.transferField("wrap-timestep", new JSONBoolean(true), (JSONObject) out.get("grib"), (JSONObject) ((JSONObject) out.get("grib")).get("timestep"));
			if (((JSONObject) ((JSONObject) out.get("grib")).get("timestep")).containsKey("wrap-timestep"))
				JSONSystem.renameField((JSONObject) ((JSONObject) out.get("grib")).get("timestep"), new JSONBoolean(true), "wrap-timestep", "wrap");
		});
		
		performConfigurationUpgradeAction(out, "5.0.0", () -> {
			//Rename all uses of type = json in Timing subsections to type = computed
			JSONObject temp;
			for (JSONData<?> val : timing.values()) { //All timing subsections except for grib are single-depth.  Therefore, this will take care of most of the appropriate renaming
				if (val instanceof JSONObject) {
					temp = (JSONObject) val;
					if (temp.containsKey("type") && temp.get("type").value().equals("json"))
						temp.put("type", "computed");
				}
			}
			if (timing.get("grib") instanceof JSONObject) {
				for (JSONData<?> val : ((JSONObject) timing.get("grib")).values()) { //This takes care of the remaining type renames.
					if (val instanceof JSONObject) {
						temp = (JSONObject) val;
						if (temp.containsKey("type") && temp.get("type").value().equals("json"))
							temp.put("type", "computed");
					}
				}
			}
			//Rename rounding to round
			recursiveRenameField(timing, "rounding", "round"); //Rounding should only be renamed within timing
		});
		
		performConfigurationUpgradeAction(out, "5.1.0", () -> {
			if (!general.containsKey("serial-module-execution"))
				general.put("serial-module-execution", false);
		});
		
		performConfigurationUpgradeAction(out, "5.2.0", () -> {
			if (!out.containsKey("wrf"))
				out.put("wrf", new JSONObject());
			if (out.get("wrf") instanceof JSONObject && !((JSONObject) out.get("wrf")).containsKey("parallel")) {
				if (general.containsKey("parallel"))
					((JSONObject) out.get("wrf")).put("parallel", general.get("parallel"));
				else {
					JSONObject defaultParallel = new JSONObject();
					defaultParallel.put("is-dmpar", true);
					defaultParallel.put("boot-lam", false);
					defaultParallel.put("processors", 2);
					((JSONObject) out.get("wrf")).put("parallel", defaultParallel);
				}
			}
			if (general.containsKey("parallel"))
				general.remove("parallel");
		});
		return out;
	}
	
	private boolean checkForPotentialFormulaStrings(JSONData<?> root) {
		return checkForPotentialFormulaStrings(root, "");
	}
	
	private boolean checkForPotentialFormulaStrings(JSONData<?> root, String path) {
		if (root instanceof JSONString) {
			if (((String) root.value()).charAt(0) == '=')
				log.log(Level.WARNING, "The value of " + path + "starts with an '" + ((String) root.value()).charAt(0) + "'. It will therefore be treated as a formula." +
						"\nIf this is not correct, add a '\\' to the start of the value before running the simulation." +
						"\nSee https://github.com/Toberumono/WRF-Runner/wiki/Configuration-Formula-System for more information.");
			return true;
		}
		else if (root instanceof JSONObject) {
			boolean potentialIssue = false;
			for (Entry<String, JSONData<?>> entry : ((JSONObject) root).entrySet())
				if (checkForPotentialFormulaStrings(entry.getValue(), (path.length() > 0 ? path + "->" : path) + entry.getKey()))
					potentialIssue = true;
			return potentialIssue;
		}
		else if (root instanceof JSONArray) {
			boolean potentialIssue = false;
			JSONArray arr = (JSONArray) root;
			for (int i = 0; i < arr.size(); i++)
				if (checkForPotentialFormulaStrings(arr.get(i), path + "[" + i + "]"))
					potentialIssue = true;
			return potentialIssue;
		}
		return false;
	}
	
	private static void recursiveRenameField(JSONData<?> root, String oldName, String newName) {
		if (root instanceof JSONArray)
			for (JSONData<?> e : (JSONArray) root)
				recursiveRenameField(e, oldName, newName);
		else if (root instanceof JSONObject) {
			JSONObject obj = (JSONObject) root;
			if (obj.containsKey(oldName))
				obj.put(newName, obj.remove(oldName));
			for (JSONData<?> v : obj.values())
				recursiveRenameField(v, oldName, newName);
		}
	}
	
	private static VersionNumber getVersionNumber(JSONObject configuration) {
		return new VersionNumber((String) configuration.get("version").value());
	}
	
	private static void performConfigurationUpgradeAction(JSONObject configuration, String newVersion, ConfigurationUpgradeAction action) {
		if (getVersionNumber(configuration).compareTo(new VersionNumber(newVersion)) < 0) {
			action.perform();
			configuration.put("version", newVersion);
		}
	}
	
	private static final JSONObject buildModuleSubsection(Boolean enabled, String moduleClass, String namelist, String... dependencies) {
		JSONObject out = new JSONObject();
		out.put("enabled", enabled);
		out.put("class", moduleClass);
		if (namelist != null)
			out.put("namelist", namelist);
		if (dependencies.length > 0)
			out.put("dependencies", new JSONArray(Arrays.stream(dependencies).map(s -> new JSONString(s)).collect(Collectors.toList())));
		return out;
	}
	
	/**
	 * Simple method to depluralize sections of the configuration file. Primarily a helper method for {@link #upgradeConfiguration(JSONObject)}.
	 * 
	 * @param configuration
	 *            a {@link JSONObject} containing the configuration information
	 * @param createCopy
	 *            whether a duplicate of {@code configuration} should be made before applying the changes (if {@code true}, this method will have no
	 *            side effects)
	 * @return a {@link JSONObject} with the relevant sections depluralized
	 */
	public JSONObject depluralize(JSONObject configuration, boolean createCopy) {
		JSONObject out = createCopy ? configuration.deepCopy() : configuration;
		JSONSystem.renameField(out, new JSONObject(), "paths", "path");
		JSONSystem.renameField(out, new JSONObject(), "modules", "module");
		return out;
	}
}
