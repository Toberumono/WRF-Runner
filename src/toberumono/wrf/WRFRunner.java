package toberumono.wrf;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.namelist.parser.Namelist;
import toberumono.structures.SortingMethods;
import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;
import toberumono.structures.tuples.Triple;
import toberumono.utils.files.RecursiveEraser;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.modules.WPSLogic;
import toberumono.wrf.modules.WRFLogic;
import toberumono.wrf.modules.WgetLogic;

/**
 * A "script" for automatically running WRF and WPS installations.<br>
 * This will (from the Namelists and its own configuration file (a small one)) compute the appropriate start and end times of
 * a simulation based on the date and time at which it is run, download the relevant data (in this case NAM data) for that
 * time range, run WPS, run WRF, and then clean up after itself. (Note that the cleanup is fairly easy because it creates a
 * working directory in which it runs WRF, and then just leaves the output there)
 * 
 * @author Toberumono
 */
public class WRFRunner {
	
	protected Path configurationFile;
	protected final Logger log;
	protected final Map<String, Triple<Step, CleanupFunction, Pair<Path, NamelistUpdater>>> steps;
	protected final List<String> executionOrder;
	
	/**
	 * All that is needed to run this "script".
	 * 
	 * @param args
	 *            the arguments to the script. This must have a length of 1, and contain a valid path to a configuration
	 *            file.
	 * @throws IOException
	 *             if an I/O error occurs
	 * @throws InterruptedException
	 *             if a process gets interrupted
	 */
	public static void main(String[] args) throws IOException, InterruptedException {
		WRFRunner runner = new WRFRunner();
		Simulation sim = runner.createSimulation(Paths.get(args.length > 0 ? args[0] : "configuration.json"));
		runner.runSimulation(sim);
	}
	
	/**
	 * Constructs a {@link WRFRunner} from the given configuration file
	 * 
	 * @throws IOException
	 *             if the configuration file cannot be read from disk
	 */
	public WRFRunner() throws IOException {
		log = Logger.getLogger("WRFRunner");
		log.setLevel(Level.INFO);
		steps = new HashMap<>();
		executionOrder = new ArrayList<>();
		loadDefaultFeatures();
	}
	
	/**
	 * @return the the {@link Logger} used by the {@link WRFRunner}
	 */
	public final Logger getLog() {
		return log;
	}
	
	/**
	 * @return the steps that a {@link Simulation} should perform
	 */
	public final Map<String, Triple<Step, CleanupFunction, Pair<Path, NamelistUpdater>>> getSimulationSteps() {
		return steps;
	}
	
	/**
	 * This serves as a hook for loading the default set of steps.<br>
	 * Extending classes are encouraged to overwrite this.
	 */
	protected void loadDefaultFeatures() {
		addStep("wget", WgetLogic::runWGet, (n, p) -> {} , null);
		addStep("wps", WPSLogic::run, WPSLogic::cleanUp, new Pair<>(Paths.get("namelist.wps"), WPSLogic::updatePaths));
		addStep("wrf", WRFLogic::run, WRFLogic::cleanUp, new Pair<>(Paths.get("run", "namelist.input"), WRFLogic::updatePaths));
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
		Logger simLogger = log.getLogger("WRFRunner.Simulation");
		simLogger.setLevel(Level.WARNING);
		return new Simulation(configurationFile, Paths.get("./"), steps, true, simLogger);
	}
	
	/**
	 * Executes the steps needed to run wget, WPS, and then WRF. This method automatically calculates the appropriate start
	 * and end times of the simulation from the configuration and {@link Namelist} files, and downloads the boundary data
	 * accordingly.
	 * 
	 * @param sim
	 *            the {@link Simulation} to run
	 * @throws IOException
	 *             if the {@link Namelist} files could not be read
	 * @throws InterruptedException
	 *             if one of the processes gets interrupted
	 */
	public void runSimulation(Simulation sim) throws IOException, InterruptedException {
		for (String step : steps.keySet())
			if (sim.sourcePaths.containsKey(step))
				sim.put(step, sim.root.resolve(sim.sourcePaths.get(step).getFileName()));
				
		for (String step : executionOrder) {
			if (sim.sourcePaths.containsKey(step)) {
				sim.put(step, sim.root.resolve(sim.sourcePaths.get(step).getFileName()));
				sim.linkWorkingDirectory(sim.sourcePaths.get(step), sim.get(step));
			}
			else
				sim.put(step, sim.root.resolve(step));
			Pair<Path, NamelistUpdater> pair = steps.get(step).getZ();
			if (pair != null)
				pair.getY().update(sim.namelists, sim).write(sim.get(step).resolve(steps.get(step).getZ().getX()));
		}
		
		for (String s : executionOrder) {
			Triple<Step, CleanupFunction, Pair<Path, NamelistUpdater>> step = steps.get(s);
			if (!sim.features.containsKey(s) || ((Boolean) sim.features.get(s).value()))
				step.getX().run(sim.namelists, sim);
			if (((Boolean) sim.general.get("keep-logs").value()))
				Files.walkFileTree(sim.get(s), new TransferFileWalker(sim.output, Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
			if (((Boolean) sim.general.get("cleanup").value()))
				step.getY().cleanUp(sim.namelists, sim);
		}
		
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(sim.getWorkingPath())) {
			int maxOutputs = ((Number) sim.general.get("max-kept-outputs").value()).intValue();
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
	 * Adds a step to the simulation. Steps are executed in either the order added or the order specified by the most recent
	 * call to {@link #setOrder(String...)}. This function automatically adds the step to the end of the execution order.
	 * 
	 * @param name
	 *            the name of the {@link Step}
	 * @param step
	 *            the {@link Step}
	 * @param cleanup
	 *            the {@link CleanupFunction} for the {@link Step}
	 * @param namelistHandler
	 *            a {@link Pair} that contains a {@link Path} to the {@link Namelist Namelist's} location relative to its
	 *            {@link Step Step's} installation directory and a function that updates the {@link Namelist} with values
	 *            specific to the current {@link Simulation}
	 */
	public void addStep(String name, Step step, CleanupFunction cleanup, Pair<Path, NamelistUpdater> namelistHandler) {
		steps.put(name, new Triple<>(step, cleanup, namelistHandler));
		executionOrder.add(name);
	}
	
	/**
	 * Sets the order in which the {@link Step Steps} should be executed.
	 * 
	 * @param steps
	 *            the names of the {@link Step Steps} to execute, in order of execution.
	 */
	public void setOrder(String... steps) {
		executionOrder.clear();
		for (String step : steps)
			executionOrder.add(step);
	}
}
