package toberumono.wrf;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistInnerMap;
import toberumono.namelist.parser.NamelistParser;
import toberumono.namelist.parser.NamelistType;
import toberumono.structures.SortingMethods;
import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;
import toberumono.utils.files.RecursiveEraser;
import toberumono.utils.files.TransferFileWalker;

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
	protected JSONObject configuration, general, paths, features, parallel;
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
	 * @throws URISyntaxException
	 *             if a {@link Path} is invalid
	 * @throws InterruptedException
	 *             if a process gets interrupted
	 */
	public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
		WRFRunner runner = new WRFRunner(Paths.get(args[0]));
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
		refreshConfiguration(configurationFile);
	}
	
	/**
	 * @return the {@link Path} to the current configuration file
	 */
	public Path getConfigurationFile() {
		return configurationFile;
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
	 * @see #getConfigurationFile()
	 */
	public void refreshConfiguration() throws IOException {
		refreshConfiguration(configurationFile);
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
		this.configurationFile = configurationFile;
		configuration = (JSONObject) JSONSystem.loadJSON(this.configurationFile);
		paths = (JSONObject) configuration.get("paths");
		general = (JSONObject) configuration.get("general");
		features = (JSONObject) general.get("features");
		parallel = (JSONObject) general.get("parallel");
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
		Path wrfPath = Paths.get(((String) paths.get("wrf").value())).toAbsolutePath();
		Path wpsPath = Paths.get(((String) paths.get("wps").value())).toAbsolutePath();
		Path workingPath = Paths.get(((String) this.paths.get("working").value())).normalize().toAbsolutePath();
		
		Path wrfNamelistPath = wrfPath.resolve("run").resolve("namelist.input");
		Namelist input = NamelistParser.parseNamelist(wrfNamelistPath);
		Path wpsNamelistPath = wpsPath.resolve("namelist.wps");
		Namelist wps = NamelistParser.parseNamelist(wpsNamelistPath);
		int doms = ((Number) input.get("domains").get("max_dom").get(0).getY()).intValue();
		
		Simulation sim = new Simulation(input, Calendar.getInstance(), (JSONObject) configuration.get("timing"), log.getLogger("WRFRunner.Simulation"));
		WRFPaths paths = sim.makeWorkingFolder(workingPath, wrfPath, wpsPath);
		
		NamelistParser.writeNamelist(sim.updateWRFNamelistTimeRange(input, doms), paths.wrf.resolve("run").resolve("namelist.input"));
		NamelistParser.writeNamelist(writeWPSPaths(sim.updateWPSNamelistTimeRange(wps, doms), wpsPath, paths.wrf), paths.wps.resolve("namelist.wps"));
		
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
			
		int maxOutputs = ((Number) general.get("max-outputs").value()).intValue();
		if (maxOutputs < 1)
			return;
		SortedList<Path> sl = new SortedList<>(SortingMethods.PATH_MODIFIED_TIME_ASCENDING);
		Files.newDirectoryStream(workingPath).forEach(sl::add);
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
	public static Namelist writeWPSPaths(Namelist wps, Path wpsPath, Path wrfPath) {
		//Convert the geog_data_path to an absolute path so that WPS doesn't break trying to find a path relative to its original location
		NamelistInnerList geogList = wps.get("geogrid").get("geog_data_path");
		Path newPath = wpsPath.resolve(geogList.get(0).getY().toString());
		geogList.set(0, new Pair<>(NamelistType.String, newPath.toAbsolutePath().normalize().toString()));
		//Ensure that the geogrid output is staying in the WPS working directory
		NamelistInnerList geoOutList = wps.get("share").get("opt_output_from_geogrid_path");
		if (geoOutList != null)
			geoOutList.set(0, new Pair<>(NamelistType.String, "./"));
		//Ensure that the metgrid output is going into the WRF working directory
		NamelistInnerList metList = wps.get("metgrid").get("opt_output_from_metgrid_path");
		if (metList == null) {
			metList = new NamelistInnerList();
			wps.get("metgrid").put("opt_output_from_metgrid_path", metList);
		}
		String path = wrfPath.resolve("run").toString();
		if (!path.endsWith(System.getProperty("file.separator")))
			path += System.getProperty("file.separator");
		metList.set(0, new Pair<>(NamelistType.String, path));
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
		NamelistInnerMap tc = input.get("time_control");
		//We construct the duration in hours here and add the start hour so that we only need to do the addition for the offset once instead of max_dom times.
		int hoursDuration = ((Number) tc.get("run_days").get(0).getY()).intValue() + ((Number) tc.get("run_hours").get(0).getY()).intValue() + ((Number) tc.get("start_hour").get(0).getY()).intValue();
		if (((Number) tc.get("run_minutes").get(0).getY()).intValue() != 0 || ((Number) tc.get("run_seconds").get(0).getY()).intValue() != 0 || hoursDuration % 3 != 0)
			throw new RuntimeException("Run length must be such that when it is converted to hours, it is divisible by 3.");
			
		//Create the grib directory if it doesn't already exist and initialize a ProcessBuilder to use that directory 
		Files.createDirectories(paths.grib);
		Calendar start = sim.getX();
		//Construct the prefix of the url
		String url = "http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.";
		url += String.format(Locale.US, "%d%02d%02d", start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH));
		String suffix = ".tm00.grib2";
		//Download each datafile
		for (int i = ((Number) tc.get("start_hour").get(0).getY()).intValue(); i <= hoursDuration; i += 3) {
			String name = "nam.t00z.awip3d" + String.format(Locale.US, "%02d", i) + suffix;
			downloadGribFile(new URL(url + "/" + name), paths);
		}
	}
	
	/**
	 * Transfers a file from the given {@link URL} and places it in the grib directory ({@link WRFPaths#grib}).
	 * 
	 * @param url
	 *            the {@link URL} to transfer
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if the transfer fails
	 */
	public void downloadGribFile(URL url, WRFPaths paths) throws IOException {
		String name = url.toString();
		name = name.substring(name.lastIndexOf('/') + 1);
		Path dest = paths.grib.resolve(name);
		log.info("Started Transfer: " + url + " -> " + dest.toString());
		try (ReadableByteChannel rbc = Channels.newChannel(url.openStream()); FileOutputStream fos = new FileOutputStream(dest.toString());) {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			log.info("Completed Transfer: " + url + " -> " + dest.toString());
		}
		catch (Throwable t) {
			log.severe("Failed Transfer: " + url + " -> " + dest.toString());
			log.log(Level.FINE, t.getMessage(), t);
			throw t;
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
		Files.walkFileTree(paths.wrf.resolve("run"), new TransferFileWalker(paths.output, Files::move, p -> p.getFileName().toString().toLowerCase().startsWith("wrfout"), p -> true, null, null));
		if (((Boolean) features.get("cleanup").value()))
			Files.walkFileTree(paths.wrf, new RecursiveEraser());
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
	 * Starts a {@link Process} using the given {@link ProcessBuilder} and command and waits for its completion.
	 * 
	 * @param pb
	 *            the {@link ProcessBuilder} with which to execute the command
	 * @param command
	 *            the command to execute
	 * @return the exit value of the {@link Process} that started
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws InterruptedException
	 *             if the {@link Process} was interrupted
	 */
	public static int runPB(ProcessBuilder pb, String... command) throws IOException, InterruptedException {
		pb.command(command);
		Process p = pb.start();
		return p.waitFor();
	}
	
	/**
	 * Performs the common steps for setting up the {@link ProcessBuilder ProcessBuilders} used in this system. (Basically
	 * just avoids some copy and paste)
	 * 
	 * @param directory
	 *            the working directory for the {@link ProcessBuilder}
	 * @return a {@link ProcessBuilder} with {@link Redirect#INHERIT} redirections and the given working directory
	 */
	public static ProcessBuilder makePB(File directory) {
		ProcessBuilder pb = new ProcessBuilder();
		pb.redirectError(Redirect.INHERIT);
		pb.redirectOutput(Redirect.INHERIT);
		pb.directory(directory);
		return pb;
	}
}
