package toberumono.wrf;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.json.JSONBoolean;
import toberumono.json.JSONObject;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistString;
import toberumono.structures.SortingMethods;
import toberumono.structures.collections.lists.SortedList;
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
	protected JSONObject configuration, general, paths, features, parallel, timing, grib;
	protected Path configurationPath;
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
		refreshConfiguration(configurationFile);
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
		paths = (JSONObject) configuration.get("paths");
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
			general.put("keep-logs", new JSONBoolean(true));
		if (!parallel.containsKey("use-suffix"))
			parallel.put("use-suffix", new JSONBoolean(false));
		Path wrfPath = Paths.get(((String) paths.get("wrf").value())).toAbsolutePath();
		Path wpsPath = Paths.get(((String) paths.get("wps").value())).toAbsolutePath();
		Path workingPath = Paths.get(((String) paths.get("working").value())).normalize().toAbsolutePath();
		
		Path wrfNamelistPath = wrfPath.resolve("run").resolve("namelist.input");
		Namelist input = new Namelist(wrfNamelistPath);
		Path wpsNamelistPath = wpsPath.resolve("namelist.wps");
		Namelist wps = new Namelist(wpsNamelistPath);
		int doms = ((Number) input.get("domains").get("max_dom").get(0).getY()).intValue();
		
		Logger simLogger = log.getLogger("WRFRunner.Simulation");
		simLogger.setLevel(Level.WARNING);
		Simulation sim = new Simulation(input, Calendar.getInstance(), timing, simLogger);
		if (configuration.isModified())
			JSONSystem.writeJSON(configuration, configurationPath);
		WRFPaths paths = sim.makeWorkingFolder(workingPath, wrfPath, wpsPath, (Boolean) parallel.get("always-suffix").value());
		
		JSONObject timestep = null;
		if (((Boolean) features.get("wget").value()))
			timestep = (JSONObject) grib.get("timestep");
		sim.updateWRFNamelistTimeRange(input, timestep, doms).write(paths.wrf.resolve("run").resolve("namelist.input"));
		writeWPSPaths(sim.updateWPSNamelistTimeRange(wps, timestep, doms), wpsPath, paths.wrf).write(paths.wps.resolve("namelist.wps"));
		
		if (((Boolean) features.get("wget").value()))
			runWGet(sim, paths, input);
			
		if (((Boolean) features.get("wps").value()))
			runWPS(wps, paths, sim);
		else
			wpsCleanup(paths);
			
		if (((Boolean) features.get("wrf").value()))
			runWRF(input, paths, sim);
		else
			wrfCleanup(paths);
			
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
	 * This method modifies the original {@link Namelist} object directly - it does not create a copy. The return value is
	 * the same object - it's just for convenience in chaining.
	 * 
	 * @param wps
	 *            the WPS {@link Namelist}
	 * @param wpsPath
	 *            the <i>original</i> WPS path
	 * @param wrfPath
	 *            the <i>linked</i> WRF path (Working/WRFV3)
	 * @return the WPS {@link Namelist}
	 */
	@SuppressWarnings("unchecked")
	public static Namelist writeWPSPaths(Namelist wps, Path wpsPath, Path wrfPath) {
		//Convert the geog_data_path to an absolute path so that WPS doesn't break trying to find a path relative to its original location
		NamelistInnerList<NamelistString> geogList = (NamelistInnerList<NamelistString>) wps.get("geogrid").get("geog_data_path");
		Path newPath = wpsPath.resolve(geogList.get(0).getY().toString());
		geogList.set(0, new NamelistString(newPath.toAbsolutePath().normalize().toString()));
		//Ensure that the geogrid output is staying in the WPS working directory
		NamelistInnerList<NamelistString> geoOutList = (NamelistInnerList<NamelistString>) wps.get("share").get("opt_output_from_geogrid_path");
		if (geoOutList != null)
			geoOutList.set(0, new NamelistString("./"));
		//Ensure that the metgrid output is going into the WRF working directory
		NamelistInnerList<NamelistString> metList = (NamelistInnerList<NamelistString>) wps.get("metgrid").get("opt_output_from_metgrid_path");
		if (metList == null) {
			metList = new NamelistInnerList<>();
			wps.get("metgrid").put("opt_output_from_metgrid_path", metList);
		}
		String path = wrfPath.resolve("run").toString();
		if (!path.endsWith(System.getProperty("file.separator")))
			path += System.getProperty("file.separator");
		metList.set(0, new NamelistString(path));
		return wps;
	}
	
	/**
	 * This downloads the necessary grib data for running the current simulation.<br>
	 * Override this method to change how grib data is downloaded (e.g. change the source, timing offset)
	 * 
	 * @param sim
	 *            the current {@link Simulation}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param input
	 *            the namelist.input file from the WRF directory
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWGet(Simulation sim, WRFPaths paths, Namelist input) throws IOException, InterruptedException {
		int[] offsets = new int[4], steps = new int[4], limits = new int[4];
		String url = (String) grib.get("url").value();
		Files.createDirectories(paths.grib);
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
	 * Transfers a file from the given {@link URL} and places it in the grib directory ({@link WRFPaths#grib}).<br>
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
		Path dest = paths.grib.resolve(name);
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
	 * This executes the commands needed to run a WPS process in sequence (ungrib and geogrid are run in parallel).
	 * 
	 * @param wps
	 *            the WPS {@link Namelist} file
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the WPS directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWPS(Namelist wps, WRFPaths paths, Simulation sim) throws IOException, InterruptedException {
		ProcessBuilder wpsPB = makePB(paths.wps.toFile());
		runPB(wpsPB, "./link_grib.csh", paths.grib.toString() + System.getProperty("file.separator"));
		//Run ungrib and geogrid in parallel
		wpsPB.command("./ungrib.exe", "2>&1", "|", "tee", "./ungrib.log");
		Process ungrib = wpsPB.start();
		runPB(wpsPB, "./geogrid.exe", "2>&1", "|", "tee", "./geogrid.log");
		ungrib.waitFor();
		runPB(wpsPB, "./metgrid.exe", "2>&1", "|", "tee", "./metgrid.log");
		wpsCleanup(paths);
	}
	
	/**
	 * This executes the commands needed to run a real-data WRF installation.
	 * 
	 * @param input
	 *            the WRF {@link Namelist} file
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the WRF run directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWRF(Namelist input, WRFPaths paths, Simulation sim) throws IOException, InterruptedException {
		ProcessBuilder wrfPB = makePB(paths.wrf.resolve("run").toFile());
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
		//If we are waiting for WRF, we can also clean up after it.
		if (((Boolean) general.get("wait-for-WRF").value())) {
			try {
				runPB(wrfPB, wrfCommand);
			}
			catch (Throwable t) {
				log.log(Level.SEVERE, "WRF error", t);
			}
			wrfCleanup(paths);
		}
		else {
			wrfPB.command(wrfCommand);
			wrfPB.start();
		}
	}
	
	/**
	 * Cleans up the {@link WRFPaths#wps} and {@link WRFPaths#grib} via a {@link RecursiveEraser}. Therefore, this should
	 * <i>only</i> not be called on the source installation.
	 * 
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up
	 */
	public void wpsCleanup(WRFPaths paths) throws IOException {
		if (!((Boolean) features.get("cleanup").value()))
			return;
		if (((Boolean) general.get("keep-logs").value()))
			Files.walkFileTree(paths.wps, new TransferFileWalker(paths.output, Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
		RecursiveEraser re = new RecursiveEraser();
		Files.walkFileTree(paths.wps, re);
		Files.walkFileTree(paths.grib, re);
	}
	
	/**
	 * Cleans up the {@link WRFPaths#wrf} after moving the outputs to {@link WRFPaths#output} via a
	 * {@link TransferFileWalker} via a {@link RecursiveEraser}. Therefore, this should <i>only</i> not be called on the
	 * source installation.
	 * 
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up
	 */
	public void wrfCleanup(WRFPaths paths) throws IOException {
		Files.walkFileTree(paths.wrf.resolve("run"),
				new TransferFileWalker(paths.output, Files::move, p -> p.getFileName().toString().toLowerCase().startsWith("wrfout"), p -> true, null, null, false));
		if (!((Boolean) features.get("cleanup").value()))
			return;
		Files.walkFileTree(paths.wrf, new RecursiveEraser());
		if (((Boolean) general.get("keep-logs").value()))
			Files.walkFileTree(paths.wps, new TransferFileWalker(paths.output, Files::move, p -> p.getFileName().toString().toLowerCase().endsWith(".log"), p -> true, null, null, true));
	}
}
