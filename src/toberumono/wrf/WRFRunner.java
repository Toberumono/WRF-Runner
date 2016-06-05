package toberumono.wrf;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.structures.SortingMethods;
import toberumono.structures.collections.lists.SortedList;
import toberumono.utils.files.RecursiveEraser;
import toberumono.wrf.timing.DisabledTiming;
import toberumono.wrf.timing.JSONTiming;
import toberumono.wrf.timing.Timing;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.clear.DisabledClear;
import toberumono.wrf.timing.clear.StandardClear;
import toberumono.wrf.timing.duration.DisabledDuration;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.duration.StandardDuration;
import toberumono.wrf.timing.offset.DisabledOffset;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.offset.StandardOffset;
import toberumono.wrf.timing.rounding.BucketRounding;
import toberumono.wrf.timing.rounding.DisabledRounding;
import toberumono.wrf.timing.rounding.FractionalRounding;
import toberumono.wrf.timing.rounding.Rounding;

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
		initFactories();
		WRFRunner runner = new WRFRunner();
		Simulation sim = runner.createSimulation(Paths.get(args.length > 0 ? args[0] : "configuration.json"));
		runner.runSimulation(sim);
	}
	
	public static void initFactories() {
		WRFRunnerComponentFactory<Offset> offsetFactory = WRFRunnerComponentFactory.getFactory(Offset.class, "standard", DisabledOffset::getDisabledOffsetInstance);
		offsetFactory.addComponentConstructor("standard", StandardOffset::new);
		offsetFactory.addComponentConstructor("disabled", (p, s) -> offsetFactory.getDisabledComponentInstance());
		WRFRunnerComponentFactory<Rounding> roundingFactory = WRFRunnerComponentFactory.getFactory(Rounding.class, "bucket", DisabledRounding::getDisabledRoundingInstance);
		roundingFactory.addComponentConstructor("bucket", BucketRounding::new);
		roundingFactory.addComponentConstructor("fractional", FractionalRounding::new);
		roundingFactory.addComponentConstructor("disabled", (p, s) -> roundingFactory.getDisabledComponentInstance());
		WRFRunnerComponentFactory<Duration> durationFactory = WRFRunnerComponentFactory.getFactory(Duration.class, "standard", DisabledDuration::getDisabledDurationInstance);
		durationFactory.addComponentConstructor("standard", StandardDuration::new);
		durationFactory.addComponentConstructor("disabled", (p, s) -> durationFactory.getDisabledComponentInstance());
		WRFRunnerComponentFactory<Clear> clearFactory = WRFRunnerComponentFactory.getFactory(Clear.class, "standard", DisabledClear::getDisabledClearInstance);
		clearFactory.addComponentConstructor("standard", StandardClear::new);
		clearFactory.addComponentConstructor("disabled", (p, s) -> clearFactory.getDisabledComponentInstance());
		WRFRunnerComponentFactory<Timing> timingFactory = WRFRunnerComponentFactory.getFactory(Timing.class, "json", DisabledTiming::getDisabledTimingInstance);
		timingFactory.addComponentConstructor("json", JSONTiming::new);
		timingFactory.addComponentConstructor("disabled", (p, s) -> timingFactory.getDisabledComponentInstance());
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
	}
	
	public WRFRunner(JSONObject config) {
		log = Logger.getLogger("WRFRunner");
		log.setLevel(Level.INFO);
		JSONObject general = (JSONObject) config.get("general"), paths = (JSONObject) config.get("paths"), modules = (JSONObject) config.get("modules");
		
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
		Logger simLogger = log.getLogger("WRFRunner.Simulation");
		simLogger.setLevel(Level.WARNING);
		return Simulation.initSimulation(configurationFile, true);
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
		sim.linkModules();
		sim.updateNamelists();
		sim.executeModules();
		cleanUpOldSimulations(sim);
	}
	
	private void cleanUpOldSimulations(Simulation sim) {
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(sim.getWorkingPath())) {
			int maxOutputs = ((Number) sim.getGeneral().get("max-kept-outputs").value()).intValue();
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
}
