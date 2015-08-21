package toberumono.wrf;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.json.JSONBoolean;
import toberumono.json.JSONObject;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistSection;
import toberumono.namelist.parser.NamelistString;
import toberumono.namelist.parser.NamelistValueList;
import toberumono.structures.SortingMethods;
import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;
import toberumono.structures.tuples.Triple;
import toberumono.utils.files.RecursiveEraser;
import toberumono.utils.files.TransferFileWalker;

import static toberumono.utils.general.ProcessBuilders.*;

/**
 * A "script" for automatically running WRF and WPS installations.<br>
 * This will (from the namelists and its own configuration file (a small one)) compute the appropriate start and end times of
 * a simulation based on the date and time at which it is run, download the relevant data (in this case NAM data) for that
 * time range, run WPS, run WRF, and then clean up after itself. (Note that the cleanup is fairly easy because it creates a
 * working directory in which it runs WRF, and then just leaves the output there)
 * 
 * @author Toberumono
 */
public class WRFRunner {
	private static final String[] timeCodes = {"days", "hours", "minutes", "seconds"};
	
	protected JSONObject configuration, general, features, parallel, timing, grib;
	protected Path configurationPath, workingPath;
	protected final Logger log;
	protected final Map<String, Path> paths;
	protected final Map<String, Namelist> namelists;
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
		WRFRunner runner = new WRFRunner(Paths.get(args.length > 0 ? args[0] : "configuration.json"));
		runner.runWRF();
	}
	
	/**
	 * Constructs a {@link WRFRunner} from the given configuration file
	 * 
	 * @param configurationFile
	 *            a {@link Path} to the configuration file to be used
	 * @throws IOException
	 *             if the configuration file cannot be read from disk
	 */
	public WRFRunner(Path configurationFile) throws IOException {
		log = Logger.getLogger("WRFRunner");
		log.setLevel(Level.INFO);
		paths = new HashMap<>();
		refreshConfiguration(configurationFile);
		namelists = new HashMap<>();
		steps = new HashMap<>();
		executionOrder = new ArrayList<>();
		loadDefaultFeatures();
	}
	
	/**
	 * @return the {@link Path} to the current configuration file
	 */
	public Path getConfigurationPath() {
		return configurationPath;
	}
	
	/**
	 * @return the the {@link Logger} used by the {@link WRFRunner}
	 */
	public final Logger getLog() {
		return log;
	}
	
	/**
	 * This serves as a hook for loading the default set of steps.<br>
	 * Extending classes are encouraged to overwrite this.
	 */
	protected void loadDefaultFeatures() {
		addStep("wget", this::runWGet, (n, p) -> {} , null);
		addStep("wps", this::runWPS, this::cleanUpWPS,
				new Pair<>(Paths.get("namelist.wps"), (namelists, wpaths, sim) -> writeWPSPaths(updateWPSNamelistTimeRange(namelists, sim), wpaths, paths.get("wps"))));
		addStep("wrf", this::runWRF, this::cleanUpWRF,
				new Pair<>(Paths.get("run", "namelist.input"), (namelists, wpaths, sim) -> updateWRFNamelistTimeRange(namelists, sim)));
	}
	
	/**
	 * Reloads the data from the current configuration file.
	 * 
	 * @throws IOException
	 *             if the configuration file cannot be read from disk
	 * @see #getConfigurationPath()
	 */
	public void refreshConfiguration() throws IOException {
		refreshConfiguration(configurationPath);
	}
	
	/**
	 * Load the configuration data from the given JSON file and stores it for subsequent calls to
	 * {@link #refreshConfiguration()}.
	 * 
	 * @param configurationFile
	 *            a {@link Path} to the configuration file (provided initially as a command-line argument)
	 * @throws IOException
	 *             if the configuration file cannot be read from disk
	 */
	public void refreshConfiguration(Path configurationFile) throws IOException {
		this.configurationPath = configurationFile;
		configuration = (JSONObject) JSONSystem.loadJSON(this.configurationPath);
		((JSONObject) configuration.get("paths")).forEach((n, p) -> paths.put(n, Paths.get(((String) p.value())).normalize().toAbsolutePath()));
		workingPath = paths.remove("working");
		general = (JSONObject) configuration.get("general");
		features = (JSONObject) general.get("features");
		parallel = (JSONObject) general.get("parallel");
		timing = (JSONObject) configuration.get("timing");
		grib = (JSONObject) configuration.get("grib");
	}
	
	/**
	 * Executes the steps needed to run wget, WPS, and then WRF. This method automatically calculates the appropriate start
	 * and end times of the simulation from the configuration and Namelist files, and downloads the boundary data
	 * accordingly.
	 * 
	 * @throws IOException
	 *             if the Namelist files could not be read
	 * @throws InterruptedException
	 *             if one of the processes gets interrupted
	 */
	public void runWRF() throws IOException, InterruptedException {
		if (!general.containsKey("keep-logs"))
			general.put("keep-logs", new JSONBoolean(false));
		if (!general.containsKey("always-suffix"))
			general.put("always-suffix", new JSONBoolean(false));
			
		Logger simLogger = log.getLogger("WRFRunner.Simulation");
		simLogger.setLevel(Level.WARNING);
		for (String step : steps.keySet()) {
			if (paths.containsKey(step))
				namelists.put(step, steps.get(step).getZ() != null ? new Namelist(paths.get(step).resolve(steps.get(step).getZ().getX())) : null);
		}
		JSONObject timestep = ((Boolean) features.get("wget").value()) ? (JSONObject) grib.get("timestep") : null;
		Simulation sim = new Simulation(namelists, Calendar.getInstance(), timestep, timing, simLogger);
		if (configuration.isModified())
			JSONSystem.writeJSON(configuration, configurationPath);
		Path root = sim.makeWorkingFolder(workingPath, (Boolean) general.get("always-suffix").value());
		WRFPaths wpaths = new WRFPaths(root, root, true, log.getLogger("WRFRunner.WRFPaths"));
		
		for (String step : executionOrder) {
			if (paths.containsKey(step)) {
				wpaths.put(step, wpaths.root.resolve(paths.get(step).getFileName()));
				sim.linkWorkingDirectory(paths.get(step), wpaths.get(step));
				wpaths.put(step, root.resolve(paths.get(step).getFileName()));
			}
			else
				wpaths.put(step, root.resolve(step));
			Pair<Path, NamelistUpdater> pair = steps.get(step).getZ();
			if (pair != null)
				pair.getY().update(namelists, wpaths, sim).write(wpaths.get(step).resolve(steps.get(step).getZ().getX()));
		}
		
		for (String s : executionOrder) {
			Triple<Step, CleanupFunction, Pair<Path, NamelistUpdater>> step = steps.get(s);
			if (!features.containsKey(s) || ((Boolean) features.get(s).value()))
				step.getX().run(namelists, wpaths, sim);
			if (((Boolean) general.get("keep-logs").value()))
				Files.walkFileTree(wpaths.get(s), new TransferFileWalker(wpaths.output, Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
			if (((Boolean) features.get("cleanup").value()))
				step.getY().cleanUp(namelists, wpaths);
		}
		
		int maxOutputs = ((Number) general.get("max-kept-outputs").value()).intValue();
		if (maxOutputs < 1)
			return;
		SortedList<Path> sl = new SortedList<>(SortingMethods.PATH_MODIFIED_TIME_ASCENDING);
		Files.newDirectoryStream(workingPath).forEach(p -> { //We only want to get directories for this.
			if (Files.isDirectory(p))
				sl.add(p);
		});
		while (sl.size() > maxOutputs)
			Files.walkFileTree(sl.remove(0), new RecursiveEraser());
	}
	
	/**
	 * Writes this {@link Simulation} into a WPS {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @return <tt>namelists</tt> (this is for easier chaining of commands - this method modifies the passed loaded
	 *         {@link Namelist} directly)
	 */
	public Map<String, Namelist> updateWPSNamelistTimeRange(Map<String, Namelist> namelists, Simulation sim) {
		Namelist wps = namelists.get("wps");
		NamelistString start = new NamelistString(sim.getWPSStartDate());
		NamelistString end = new NamelistString(sim.getWPSEndDate());
		NamelistValueList<NamelistString> s = new NamelistValueList<>(), e = new NamelistValueList<>();
		for (int i = 0; i < sim.doms; i++) {
			s.add(start);
			e.add(end);
		}
		wps.get("share").put("start_date", s);
		wps.get("share").put("end_date", e);
		if (sim.interval_seconds != null) {
			NamelistValueList<NamelistNumber> is = new NamelistValueList<>();
			is.add(sim.interval_seconds);
			wps.get("share").put("interval_seconds", is);
		}
		return namelists;
	}
	
	/**
	 * Writes this {@link Simulation} into a WRF {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @return the updated {@link Namelist} file (this is for easier chaining of commands - this method modifies the passed
	 *         file)
	 */
	@SuppressWarnings("unchecked")
	public Namelist updateWRFNamelistTimeRange(Map<String, Namelist> namelists, Simulation sim) {
		Namelist input = namelists.get("wrf");
		NamelistValueList<NamelistNumber> syear = new NamelistValueList<>(), smonth = new NamelistValueList<>(), sday = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> shour = new NamelistValueList<>(), sminute = new NamelistValueList<>(), ssecond = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> eyear = new NamelistValueList<>(), emonth = new NamelistValueList<>(), eday = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> ehour = new NamelistValueList<>(), eminute = new NamelistValueList<>(), esecond = new NamelistValueList<>();
		Calendar start = sim.getStart(), end = sim.getEnd();
		for (int i = 0; i < sim.doms; i++) {
			syear.add(new NamelistNumber(start.get(Calendar.YEAR)));
			smonth.add(new NamelistNumber(start.get(Calendar.MONTH) + 1)); //We have to add 1 to the month because Java's Calendar system starts the months at 0
			sday.add(new NamelistNumber(start.get(Calendar.DAY_OF_MONTH)));
			shour.add(new NamelistNumber(start.get(Calendar.HOUR_OF_DAY)));
			sminute.add(new NamelistNumber(start.get(Calendar.MINUTE)));
			ssecond.add(new NamelistNumber(start.get(Calendar.SECOND)));
			eyear.add(new NamelistNumber(end.get(Calendar.YEAR)));
			emonth.add(new NamelistNumber(end.get(Calendar.MONTH) + 1)); //We have to add 1 to the month because Java's Calendar system starts the months at 0
			eday.add(new NamelistNumber(end.get(Calendar.DAY_OF_MONTH)));
			ehour.add(new NamelistNumber(end.get(Calendar.HOUR_OF_DAY)));
			eminute.add(new NamelistNumber(end.get(Calendar.MINUTE)));
			esecond.add(new NamelistNumber(end.get(Calendar.SECOND)));
		}
		NamelistSection tc = input.get("time_control");
		tc.put("start_year", syear);
		tc.put("start_month", smonth);
		tc.put("start_day", sday);
		tc.put("start_hour", shour);
		tc.put("start_minute", sminute);
		tc.put("start_second", ssecond);
		tc.put("end_year", eyear);
		tc.put("end_month", emonth);
		tc.put("end_day", eday);
		tc.put("end_hour", ehour);
		tc.put("end_minute", eminute);
		tc.put("end_second", esecond);
		for (String timeCode : timeCodes)
			if (tc.containsKey("run_" + timeCode))
				((NamelistValueList<NamelistNumber>) tc.get("run_" + timeCode)).set(0, new NamelistNumber((Number) ((JSONObject) timing.get("duration")).get(timeCode).value()));
		return input;
	}
	
	/**
	 * Updates the interval_seconds value if it should be updated.
	 * 
	 * @param sim
	 *            the current {@link Simulation}
	 * @param section
	 *            the {@link NamelistSection} that contains the interval_seconds key
	 */
	public static void updateIntervalSeconds(Simulation sim, NamelistSection section) {
		if (sim.interval_seconds != null) {
			NamelistValueList<NamelistNumber> is = new NamelistValueList<>();
			is.add(sim.interval_seconds);
			section.put("interval_seconds", is);
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
	
	/**
	 * This method modifies the original {@link Namelist} object directly - it does not create a copy. The return value is
	 * the same object - it's just for convenience in chaining.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param wpsPath
	 *            the <i>original</i> WPS path
	 * @return the WPS {@link Namelist}
	 */
	@SuppressWarnings("unchecked")
	public static Namelist writeWPSPaths(Map<String, Namelist> namelists, WRFPaths paths, Path wpsPath) {
		Namelist wps = namelists.get("wps");
		//Convert the geog_data_path to an absolute path so that WPS doesn't break trying to find a path relative to its original location
		NamelistValueList<NamelistString> geogList = (NamelistValueList<NamelistString>) wps.get("geogrid").get("geog_data_path");
		Path newPath = wpsPath.resolve(geogList.get(0).value().toString());
		geogList.set(0, new NamelistString(newPath.toAbsolutePath().normalize().toString()));
		//Ensure that the geogrid output is staying in the WPS working directory
		NamelistValueList<NamelistString> geoOutList = (NamelistValueList<NamelistString>) wps.get("share").get("opt_output_from_geogrid_path");
		if (geoOutList != null)
			if (geoOutList.size() > 0)
				geoOutList.set(0, new NamelistString("./"));
			else
				geoOutList.add(new NamelistString("./"));
		//Ensure that the metgrid output is going into the WRF working directory
		NamelistValueList<NamelistString> metList = (NamelistValueList<NamelistString>) wps.get("metgrid").get("opt_output_from_metgrid_path");
		if (metList == null) {
			metList = new NamelistValueList<>();
			wps.get("metgrid").put("opt_output_from_metgrid_path", metList);
		}
		String path = paths.get("wrf").resolve("run").toString();
		if (!path.endsWith(System.getProperty("file.separator")))
			path += System.getProperty("file.separator");
		if (metList.size() == 0)
			metList.add(new NamelistString(path));
		else
			metList.set(0, new NamelistString(path));
		return wps;
	}
	
	/**
	 * This downloads the necessary grib data for running the current simulation.<br>
	 * Override this method to change how grib data is downloaded (e.g. change the source, timing offset)
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWGet(Map<String, Namelist> namelists, WRFPaths paths, Simulation sim) throws IOException, InterruptedException {
		int[] offsets = new int[4], steps = new int[4], limits = new int[4];
		String url = (String) grib.get("url").value();
		Calendar start = sim.getStart(), end = sim.getEnd();
		
		JSONObject timestep = (JSONObject) grib.get("timestep");
		steps[0] = ((Number) timestep.get("days").value()).intValue();
		steps[1] = ((Number) timestep.get("hours").value()).intValue();
		steps[2] = ((Number) timestep.get("minutes").value()).intValue();
		steps[3] = ((Number) timestep.get("seconds").value()).intValue();
		
		JSONObject duration = (JSONObject) timing.get("duration");
		limits[0] = ((Number) duration.get("days").value()).intValue();
		limits[1] = ((Number) duration.get("hours").value()).intValue();
		limits[2] = ((Number) duration.get("minutes").value()).intValue();
		limits[3] = ((Number) duration.get("seconds").value()).intValue();
		Calendar test = (Calendar) start.clone();
		for (; !test.after(end); incrementOffsets(offsets, steps, test))
			downloadGribFile(parseIncrementedURL(url, start, 0, 0, offsets[0], offsets[1], offsets[2], offsets[3]), paths);
	}
	
	private static final void incrementOffsets(int[] offsets, int[] steps, Calendar test) {
		for (int i = 0; i < offsets.length; i++)
			offsets[i] += steps[i];
		test.add(Calendar.DAY_OF_MONTH, steps[0]);
		test.add(Calendar.HOUR_OF_DAY, steps[1]);
		test.add(Calendar.MINUTE, steps[2]);
		test.add(Calendar.SECOND, steps[3]);
	}
	
	/**
	 * Takes a URL with both the normal Java date/time markers (see
	 * <a href="http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#dt">Date/Time syntax</a>) and offset
	 * markers. The offset markers are <i>almost identical</i> in syntax to the standard Java date/time markers with, but
	 * they use 'i' instead of 't'.<br>
	 * Differences:
	 * <ul>
	 * <li>%ii --&gt; minutes without padding</li>
	 * <li>%is --&gt; seconds without padding</li>
	 * </ul>
	 * 
	 * @param url
	 *            the base form of the URL <i>prior</i> to <i>any</i> calls to {@link String#format(String, Object...)}
	 * @param start
	 *            the {@link Calendar} containing the start time
	 * @param years
	 *            the year offset from <tt>start</tt>
	 * @param months
	 *            the month offset from <tt>start</tt>
	 * @param days
	 *            the day offset from <tt>start</tt>
	 * @param hours
	 *            the hour offset from <tt>start</tt>
	 * @param minutes
	 *            the minute offset from <tt>start</tt>
	 * @param seconds
	 *            the second offset from <tt>start</tt>
	 * @return a {@link String} representation of a {@link URL} pointing to the generated location
	 */
	public String parseIncrementedURL(String url, Calendar start, int years, int months, int days, int hours, int minutes, int seconds) {
		url = url.trim().replaceAll("%([\\Q-#+ 0,(\\E]*?[tT])", "%1\\$$1"); //Force the formatter to use the first argument for all of the default date/time markers
		url = url.replaceAll("%[iI]Y", "%2\\$04d").replaceAll("%[iI]y", "%2\\$d"); //Year
		url = url.replaceAll("%[iI]m", "%3\\$02d").replaceAll("%[iI]e", "%3\\$d"); //Month
		url = url.replaceAll("%[iI]D", "%4\\$02d").replaceAll("%[iI]d", "%4\\$d"); //Day
		url = url.replaceAll("%[iI]H", "%5\\$02d").replaceAll("%[iI]k", "%5\\$d"); //Hour
		url = url.replaceAll("%[iI]M", "%6\\$02d").replaceAll("%[iI]i", "%6\\$d"); //Minute
		url = url.replaceAll("%[iI]S", "%7\\$02d").replaceAll("%[iI]s", "%7\\$d"); //Second
		return String.format(url, start, years + start.get(Calendar.YEAR), months + start.get(Calendar.MONTH) + 1, days + start.get(Calendar.DAY_OF_MONTH),
				hours + start.get(Calendar.HOUR_OF_DAY), minutes + start.get(Calendar.MINUTE), seconds + start.get(Calendar.SECOND));
	}
	
	/**
	 * Transfers a file from the given {@link URL} and places it in the grib directory.<br>
	 * The filename used in the grib directory is the component of the url after the final '/' (
	 * {@code name = url.substring(url.lastIndexOf('/') + 1)}).
	 * 
	 * @param url
	 *            a {@link String} representation of the {@link URL} to transfer
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if the transfer fails
	 */
	public void downloadGribFile(String url, WRFPaths paths) throws IOException {
		String name = url.substring(url.lastIndexOf('/') + 1);
		Path dest = paths.get("wget").resolve(name);
		log.info("Transferring: " + url + " -> " + dest.toString());
		try (ReadableByteChannel rbc = Channels.newChannel(new URL(url).openStream()); FileOutputStream fos = new FileOutputStream(dest.toString());) {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			log.fine("Completed Transfer: " + url + " -> " + dest.toString());
		}
		catch (IOException e) {
			log.severe("Failed Transfer: " + url + " -> " + dest.toString());
			log.log(Level.FINE, e.getMessage(), e);
			throw e;
		}
	}
	
	/**
	 * This executes the commands needed to run a WPS process (ungrib and geogrid are run in parallel).
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the WPS directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWPS(Map<String, Namelist> namelists, WRFPaths paths, Simulation sim) throws IOException, InterruptedException {
		ProcessBuilder wpsPB = makePB(paths.get("wps").toFile());
		String path = paths.get("wget").toString();
		if (!path.endsWith(System.getProperty("file.separator"))) //link_grib.csh requires that the path end with a '/'
			path += System.getProperty("file.separator");
		runPB(wpsPB, "./link_grib.csh", path);
		//Run ungrib and geogrid in parallel
		wpsPB.command("./ungrib.exe", "2>&1", "|", "tee", "./ungrib.log");
		Process ungrib = wpsPB.start();
		runPB(wpsPB, "./geogrid.exe", "2>&1", "|", "tee", "./geogrid.log");
		ungrib.waitFor();
		runPB(wpsPB, "./metgrid.exe", "2>&1", "|", "tee", "./metgrid.log");
	}
	
	/**
	 * This executes the commands needed to run a real-data WRF installation.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the WRF run directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWRF(Map<String, Namelist> namelists, WRFPaths paths, Simulation sim) throws IOException, InterruptedException {
		Path run = paths.get("wrf").resolve("run");
		ProcessBuilder wrfPB = makePB(run.toFile());
		runPB(wrfPB, "./real.exe", "2>&1", "|", "tee", "./real.log");
		String[] wrfCommand = new String[0];
		//Calculate which command to use
		if ((Boolean) parallel.get("is-dmpar").value()) {
			if ((Boolean) parallel.get("boot-lam").value())
				wrfCommand = new String[]{"mpiexec", "-boot", "-np", parallel.get("processors").value().toString(), "./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
			else
				wrfCommand = new String[]{"mpiexec", "-np", parallel.get("processors").value().toString(), "./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
		}
		else
			wrfCommand = new String[]{"./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
		try {
			runPB(wrfPB, wrfCommand);
		}
		catch (Throwable t) {
			log.log(Level.SEVERE, "WRF error", t);
		}
		//Move the wrfout files to the output directory
		Files.walkFileTree(run,
				new TransferFileWalker(paths.output, Files::move, p -> p.getFileName().toString().toLowerCase().startsWith("wrfout"), p -> true, null, null, false));
	}
	
	/**
	 * Cleans up the WPS and GRIB directories via a {@link RecursiveEraser}. Therefore, this should <i>only</i> not be called
	 * on the source installation.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up
	 */
	public void cleanUpWPS(Map<String, Namelist> namelists, WRFPaths paths) throws IOException {
		RecursiveEraser re = new RecursiveEraser();
		Files.walkFileTree(paths.get("wps"), re);
		Files.walkFileTree(paths.get("wget"), re);
	}
	
	/**
	 * Cleans up the WRF directory after moving the outputs to {@link WRFPaths#output} via a {@link TransferFileWalker} via a
	 * {@link RecursiveEraser}. Therefore, this should <i>only</i> not be called on the source installation.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up
	 */
	public void cleanUpWRF(Map<String, Namelist> namelists, WRFPaths paths) throws IOException {
		Files.walkFileTree(paths.get("wrf"), new RecursiveEraser());
	}
}
