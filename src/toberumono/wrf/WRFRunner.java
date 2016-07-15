package toberumono.wrf;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Scanner;
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
import toberumono.structures.tuples.Pair;
import toberumono.utils.files.RecursiveEraser;
import toberumono.wrf.components.parallel.DisabledParallel;
import toberumono.wrf.components.parallel.Parallel;
import toberumono.wrf.components.parallel.StandardParallel;
import toberumono.wrf.timing.ComputedTiming;
import toberumono.wrf.timing.DisabledTiming;
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
import toberumono.wrf.upgrader.UpgradeWarning;
import toberumono.wrf.upgrader.configuration.ConfigurationUpgrader;
import toberumono.wrf.upgrader.configuration.FormulaUpgradeProblemHandler;

import static toberumono.wrf.SimulationConstants.TIMING_FIELD_NAMES;

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
	private static final String[] DEFAULT_MODULE_NAMES = {"grib", "wps", "wrf"};
	private static final String[] DEFAULT_MODULE_CLASSES = {"toberumono.wrf.modules.GRIBModule", "toberumono.wrf.modules.WPSModule", "toberumono.wrf.modules.WRFModule"};
	private static final String[] DEFAULT_MODULE_NAMELISTS = {null, "namelist.wps", "run/namelist.input"};
	private static final String[][] DEFAULT_MODULE_DEPENDENCIES = {{}, {"grib"}, {"wps"}};
	
	protected final Logger log;
	private final ConfigurationUpgrader upgrader;
	
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
		WRFRunnerCommandLineArguments arguments = new WRFRunnerCommandLineArguments(args);
		WRFRunner runner = new WRFRunner();
		runner.runSimulation(runner.createSimulation(arguments));
	}
	
	/**
	 * Initializes the {@link WRFRunnerComponentFactory WRFRunnerComponentFactories} for {@link Offset}, {@link Round}, {@link Duration},
	 * {@link Clear}, {@link Timing}, and {@link Parallel}.
	 */
	public static void initFactories() {
		WRFRunnerComponentFactory<Offset> offsetFactory = WRFRunnerComponentFactory.createFactory(Offset.class, "standard", DisabledOffset::new);
		offsetFactory.addComponentConstructor("standard", StandardOffset::new);
		offsetFactory.addComponentConstructor("list", ListOffset::new);
		WRFRunnerComponentFactory<Round> roundFactory = WRFRunnerComponentFactory.createFactory(Round.class, "bucket", DisabledRound::new);
		roundFactory.addComponentConstructor("bucket", BucketRound::new);
		roundFactory.addComponentConstructor("fractional", FractionalRound::new);
		roundFactory.addComponentConstructor("function", FunctionRound::new);
		roundFactory.addComponentConstructor("list", ListRound::new);
		WRFRunnerComponentFactory<Duration> durationFactory = WRFRunnerComponentFactory.createFactory(Duration.class, "standard", DisabledDuration::new);
		durationFactory.addComponentConstructor("standard", StandardDuration::new);
		durationFactory.addComponentConstructor("list", ListDuration::new);
		WRFRunnerComponentFactory<Clear> clearFactory = WRFRunnerComponentFactory.createFactory(Clear.class, "standard", DisabledClear::new);
		clearFactory.addComponentConstructor("standard", StandardClear::new);
		clearFactory.addComponentConstructor("list", ListClear::new);
		WRFRunnerComponentFactory<Timing> timingFactory = WRFRunnerComponentFactory.createFactory(Timing.class, "computed", DisabledTiming::new);
		timingFactory.addComponentConstructor("computed", ComputedTiming::new);
		WRFRunnerComponentFactory<Parallel> parallelFactory = WRFRunnerComponentFactory.createFactory(Parallel.class, "standard", DisabledParallel::new);
		parallelFactory.addComponentConstructor("standard", StandardParallel::new);
	}
	
	/**
	 * Constructs a {@link WRFRunner} from the given configuration file
	 * 
	 * @throws IOException
	 *             if the configuration file cannot be read from disk
	 */
	public WRFRunner() throws IOException {
		log = Logger.getLogger("WRFRunner");
		upgrader = new ConfigurationUpgrader();
		Handler systemOut = new ConsoleHandler();
		systemOut.setLevel(Level.ALL);
		log.addHandler(systemOut);
		log.setUseParentHandlers(false);
		initConfigurationUpgradeActions(getUpgrader());
		initConfigurationUpgradeProblemHandlers(getUpgrader());
	}
	
	/**
	 * @return the the {@link Logger} used by the {@link WRFRunner}
	 */
	public final Logger getLog() {
		return log;
	}
	
	/**
	 * @return the {@link ConfigurationUpgrader} used be the {@link WRFRunner}
	 */
	public ConfigurationUpgrader getUpgrader() {
		return upgrader;
	}
	
	/**
	 * Constructs a {@link Simulation} based on the given {@link WRFRunnerCommandLineArguments arguments}. This handles the logic required to upgrade
	 * the configuration file in order to create a {@link Simulation}.
	 * 
	 * @param args
	 *            the processed command line arguments
	 * @return a new {@link Simulation} based on the given arguments
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public Simulation createSimulation(WRFRunnerCommandLineArguments args) throws IOException {
		JSONObject configuration = (JSONObject) JSONSystem.loadJSON(args.getConfigurationPath());
		Pair<JSONObject, Collection<UpgradeWarning>> upgradeResult;
		if (args.isPerformInteractiveUpgrade())
			try (Scanner input = new Scanner(System.in)) {
				upgradeResult = getUpgrader().performUpgrade(configuration, () -> input.nextLine(), System.out::print);
			}
		else
			upgradeResult = getUpgrader().performUpgrade(configuration);
		
		if (upgradeResult.getY().size() > 0) {
			for (UpgradeWarning warning : upgradeResult.getY())
				getLog().log(Level.WARNING, warning.getText());
			if (!args.ignoreUpgradeProblems()) {
				getLog().severe("Cannot run the simulation because there were issues found that prevented an automatic configuration file upgrade." +
						"\nRerun the program with the '--interactive-upgrade' command-line argument to be walked through the potential problems.");
				System.exit(1);
			}
			else {
				getLog().warning("Ignoring potential upgrade problems found during the automatic configuration file upgrade." +
						"\nRun the program with the '--interactive-upgrade' command-line argument to be walked through the potential problems.");
			}
		}
		configuration = applyDefaults(depluralize(upgradeResult.getX(), false));
		if ((!args.ignoreUpgradeProblems() || upgradeResult.getY().size() == 0) && !args.cacheUpdates() && configuration.isModified()) {
			getLog().info("Updating the configuration file located at: " + args.getConfigurationPath());
			JSONSystem.writeJSON(configuration, args.getConfigurationPath());
			getLog().info("Updates completed.");
		}
		return createSimulation(configuration, args.getConfigurationPath());
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
		return createSimulation((JSONObject) JSONSystem.loadJSON(configurationFile), configurationFile);
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
		return Simulation.initSimulation(configuration, configurationFile.toAbsolutePath().normalize().getParent());
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
	
	protected void initConfigurationUpgradeActions(ConfigurationUpgrader upgrader) {
		upgrader.addUpgradeAction("1.3.0", root -> {
			if (root.containsKey("paths")) {
				JSONObject paths = (JSONObject) root.get("paths");
				if (paths.containsKey("wrf")) { //As of this version, we use the root of the WRF directory
					String wrf = (String) paths.get("wrf").value();
					if (wrf.endsWith("/run"))
						paths.put("wrf", wrf.substring(0, wrf.length() - 4)); //-4 because we want to get rid of the potential trailing '/'
				}
			}
		});
		//This is renamed to max-kept-outputs in the 1.5.5 section, so this step is just for posterity
		upgrader.addUpgradeAction("1.4.0", root -> JSONSystem.renameField((JSONObject) root.get("general"), new JSONNumber<>(15), "max-outputs"));
		
		upgrader.addUpgradeAction("1.5.0", root -> {
			if (root.containsKey("commands")) //As of this version, commands are automatically determined
				root.remove("commands");
		});
		
		upgrader.addUpgradeAction("1.5.5", root -> JSONSystem.renameField((JSONObject) root.get("general"), new JSONNumber<>(15), "max-outputs", "max-kept-outputs"));
		
		upgrader.addUpgradeAction("1.5.6", root -> {
			JSONObject timing = (JSONObject) root.get("timing");
			JSONSystem.transferField("use-computed-times", timing.get("rounding") instanceof JSONObject ? ((JSONObject) timing.get("rounding")).get("enabled") : new JSONBoolean(true), timing);
		});
		
		upgrader.addUpgradeAction("1.6.1", root -> JSONSystem.transferField("keep-logs", new JSONBoolean(false), (JSONObject) root.get("general")));
		
		upgrader.addUpgradeAction("1.7.0", root -> {
			JSONObject general = (JSONObject) root.get("general");
			if (!general.containsKey("always-suffix")) {
				if (general.get("parallel") instanceof JSONObject && ((JSONObject) general.get("parallel")).containsKey("always-suffix"))
					general.put("always-suffix", ((JSONObject) general.get("parallel")).get("always-suffix"));
				else if (root.get("wrf") instanceof JSONObject && ((JSONObject) root.get("wrf")).get("parallel") instanceof JSONObject &&
						((JSONObject) ((JSONObject) root.get("wrf")).get("parallel")).containsKey("always-suffix"))
					general.put("always-suffix", ((JSONObject) ((JSONObject) root.get("wrf")).get("parallel")).get("always-suffix"));
				else
					general.put("always-suffix", false);
			}
		});
		
		upgrader.addUpgradeAction("2.0.0", root -> ((JSONObject) root.get("general")).remove("wait-for-wrf"));
		
		upgrader.addUpgradeAction("2.1.5", root -> {
			JSONObject general = (JSONObject) root.get("general");
			if (root.containsKey("paths")) {
				JSONObject paths = (JSONObject) root.get("paths");
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
		
		upgrader.addUpgradeAction("2.1.9", root -> {
			JSONObject timing = (JSONObject) root.get("timing");
			if (timing.get("rounding") instanceof JSONObject && !((JSONObject) timing.get("rounding")).containsKey("fraction"))
				((JSONObject) timing.get("rounding")).put("fraction", 1.0);
		});
		
		upgrader.addUpgradeAction("3.1.0", root -> JSONSystem.transferField("wrap-timestep", new JSONBoolean(true), (JSONObject) root.get("grib")));
		
		upgrader.addUpgradeAction("4.0.0", root -> depluralize(root, false));
		upgrader.addUpgradeAction("4.0.0", root -> {
			JSONObject general = (JSONObject) root.get("general"), timing = (JSONObject) root.get("timing"), modules = (JSONObject) root.get("module");
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
				JSONObject rounding = timing.get("rounding") instanceof JSONObject ? (JSONObject) timing.remove("rounding") : new JSONObject();
				JSONObject global = new JSONObject();
				if (timing.containsKey("duration"))
					global.put("duration", timing.remove("duration")); //Duration has remained unchanged
				global.put("offset", timing.remove("offset")); //Offset has remained unchanged
				JSONObject clear = new JSONObject();
				
				if (rounding.containsKey("magnitude")) {
					String magnitude = (String) rounding.get("magnitude").value();
					if (!magnitude.endsWith("s")) {
						magnitude = magnitude + "s";
						rounding.put("magnitude", magnitude);
					}
					//Build the timing->global->clear subsection
					clear.put("keep", TIMING_FIELD_NAMES.get(TIMING_FIELD_NAMES.indexOf(magnitude) - 1));
				}
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
			if (((JSONObject) root.get("grib")).containsKey("wrap-timestep")) //If grib contains wrap-timestep, transfer it to grib->timestep
				JSONSystem.transferField("wrap-timestep", new JSONBoolean(true), (JSONObject) root.get("grib"), (JSONObject) ((JSONObject) root.get("grib")).get("timestep"));
			if (((JSONObject) ((JSONObject) root.get("grib")).get("timestep")).containsKey("wrap-timestep"))
				JSONSystem.renameField((JSONObject) ((JSONObject) root.get("grib")).get("timestep"), new JSONBoolean(true), "wrap-timestep", "wrap");
		});
		upgrader.addUpgradeAction("4.0.0", root -> {
			JSONObject modules = (JSONObject) root.get("module");
			if (modules.containsKey("execution-order")) {
				JSONArray eo = (JSONArray) modules.remove("execution-order");
				for (int i = 1; i < eo.size(); i++) {
					JSONObject module = (JSONObject) modules.get(eo.get(i).value());
					if (!module.containsKey("dependencies")) {
						JSONArray deps = new JSONArray();
						deps.add(eo.get(i - 1));
						module.put("dependencies", deps);
					}
				}
			}
		});
		
		upgrader.addUpgradeAction("5.0.0", root -> {
			//Rename all uses of type = json in Timing subsections to type = computed
			JSONObject timing = (JSONObject) root.get("timing"), temp;
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
			ConfigurationUpgrader.recursiveRenameField(timing, "rounding", "round"); //Rounding should only be renamed within timing
		});
		
		upgrader.addUpgradeAction("5.1.0", root -> {
			if (!((JSONObject) root.get("general")).containsKey("serial-module-execution"))
				((JSONObject) root.get("general")).put("serial-module-execution", false);
		});
		
		upgrader.addUpgradeAction("5.2.0", root -> {
			JSONObject general = (JSONObject) root.get("general");
			if (!root.containsKey("wrf"))
				root.put("wrf", new JSONObject());
			if (root.get("wrf") instanceof JSONObject && !((JSONObject) root.get("wrf")).containsKey("parallel")) {
				if (general.containsKey("parallel"))
					((JSONObject) root.get("wrf")).put("parallel", general.get("parallel"));
				else {
					JSONObject defaultParallel = new JSONObject();
					defaultParallel.put("is-dmpar", false);
					defaultParallel.put("boot-lam", false);
					defaultParallel.put("processors", 2);
					((JSONObject) root.get("wrf")).put("parallel", defaultParallel);
				}
			}
			if (general.containsKey("parallel"))
				general.remove("parallel");
		});
		upgrader.addUpgradeAction("5.3.2", root -> JSONSystem.transferField("use-computed-times", new JSONBoolean(true), (JSONObject) root.get("timing"), (JSONObject) root.get("general")));
		upgrader.addUpgradeAction("6.0.2", root -> JSONSystem.renameField((JSONObject) root.get("general"), new JSONBoolean(false), "serial-module-execution", "force-serial-module-execution"));
		upgrader.addUpgradeAction("6.1.0", root -> JSONSystem.renameField((JSONObject) root.get("grib"), new JSONNumber<>(8), "max-concurrent-downloads"));
		upgrader.addUpgradeAction("7.4.0", root -> JSONSystem.renameField((JSONObject) root.get("grib"), new JSONBoolean(false), "use-increment-duration"));
	}
	
	protected void initConfigurationUpgradeProblemHandlers(ConfigurationUpgrader upgrader) {
		upgrader.addUpgradeProblemHandler(new FormulaUpgradeProblemHandler());
	}
	
	/**
	 * Uses the "version" field in the given {@code configuration} file to upgrade the file to the latest version. This is a convenience method for
	 * {@code upgradeConfiguration(configuration, false)}.<br>
	 * <b>Note:</b> This method does <i>not</i> modify the passed {@link JSONObject}.
	 * 
	 * @param configuration
	 *            the configuration file to upgrade as a {@link JSONObject}
	 * @return an upgraded copy of {@code configuration}
	 */
	public JSONObject upgradeConfiguration(JSONObject configuration) {
		return upgradeConfiguration(configuration, false);
	}
	
	/**
	 * Uses the "version" field in the given {@code configuration} file to upgrade the file to the latest version.<br>
	 * <b>Note:</b> This method does <i>not</i> modify the passed {@link JSONObject}
	 * 
	 * @param configuration
	 *            the configuration file to upgrade as a {@link JSONObject}
	 * @param interactive
	 *            whether to perform an interactive upgrade (useful when there are issues preventing an automatic upgrade)
	 * @return an upgraded copy of {@code configuration}
	 */
	public JSONObject upgradeConfiguration(JSONObject configuration, boolean interactive) {
		Pair<JSONObject, Collection<UpgradeWarning>> upgradeResult;
		if (interactive) {
			try (Scanner input = new Scanner(System.in)) {
				upgradeResult = getUpgrader().performUpgrade(configuration, () -> input.nextLine(), System.out::print);
			}
		}
		else {
			upgradeResult = getUpgrader().performUpgrade(configuration);
		}
		return upgradeResult.getX();
	}
	
	private static final JSONObject buildModuleSubsection(Boolean enabled, String moduleClass, String namelist, String... dependencies) {
		JSONObject out = new JSONObject();
		out.put("enabled", enabled);
		out.put("class", moduleClass);
		if (namelist != null)
			out.put("namelist", namelist);
		if (dependencies.length > 0)
			out.put("dependencies", new JSONArray(Arrays.stream(dependencies).map(JSONString::new).collect(Collectors.toList())));
		return out;
	}
	
	/**
	 * Simple method to depluralize sections of the configuration file. Primarily a helper method for
	 * {@link #upgradeConfiguration(JSONObject, boolean)}.
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
	
	private static JSONObject applyDefaults(JSONObject configuration) {
		JSONObject general = (JSONObject) configuration.get("general");
		applyDefault(general, "cleanup", true);
		applyDefault(general, "keep-logs", false);
		applyDefault(general, "always-suffix", false);
		applyDefault(general, "max-kept-outputs", 15);
		applyDefault(general, "logging-level", "info");
		applyDefault(general, "force-serial-module-execution", false);
		applyDefault(general, "use-computed-times", true);
		return configuration;
	}
	
	private static void applyDefault(JSONObject container, String name, Object value) {
		if (!container.containsKey(name))
			container.put(name, value);
	}
}
