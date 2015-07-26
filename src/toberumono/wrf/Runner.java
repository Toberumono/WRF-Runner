package toberumono.wrf;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.Locale;

import toberumono.json.JSONObject;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistInnerMap;
import toberumono.namelist.parser.NamelistParser;
import toberumono.namelist.parser.NamelistType;
import toberumono.structures.tuples.Pair;

public class Runner {
	protected JSONObject configuration, general, paths, commands, features, parallel;
	protected Path root, configurationFile;
	
	public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
		Runner runner = new Runner(Paths.get(Runner.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent(), Paths.get(args[0]));
		runner.runWRF();
	}
	
	public Runner(Path root, Path configurationFile) throws IOException {
		this.root = root;
		configurationFile = root.resolve(configurationFile);
		refreshConfiguration(configurationFile);
	}
	
	/**
	 * @return the {@link Path} to the current configuration file
	 */
	public Path getConfigurationFile() {
		return configurationFile;
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
	 *             if the file cannot be read from disk
	 */
	public void refreshConfiguration(Path configurationFile) throws IOException {
		this.configurationFile = configurationFile;
		configuration = (JSONObject) JSONSystem.loadJSON(root.resolve(configurationFile));
		paths = (JSONObject) configuration.get("paths");
		commands = (JSONObject) configuration.get("commands");
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
		
		Path wrfNamelistPath = wrfPath.resolve("namelist.input");
		Namelist input = NamelistParser.parseNamelist(wrfNamelistPath);
		Path wpsNamelistPath = wpsPath.resolve("namelist.wps");
		Namelist wps = NamelistParser.parseNamelist(wpsNamelistPath);
		int doms = ((Number) input.get("domains").get("max_dom").get(0).getY()).intValue();
		
		Pair<Calendar, Calendar> timeRange = getTimeRange(input, Calendar.getInstance(), (JSONObject) configuration.get("timing"));
		NamelistParser.writeNamelist(updateWRFNamelistTimeRange(input, doms, timeRange), wrfNamelistPath);
		NamelistParser.writeNamelist(updateWPSNamelistTimeRange(wps, doms, timeRange), wpsNamelistPath);
		
		if (((Boolean) features.get("wget").value()))
			runWGet(timeRange, input);
		
		if (((Boolean) features.get("wps").value()))
			runWPS(wps, wpsPath, timeRange);
		
		if (((Boolean) features.get("wrf").value()))
			runWRF(input, wrfPath, timeRange);
	}
	
	/**
	 * This downloads the necessary grib data for running the current simulation.<br>
	 * Override this method to change how grib data is downloaded (e.g. change the source, timing offset)
	 * 
	 * @param timeRange
	 *            a {@link Pair} of {@link Calendar} objects that represent the start and end times of the simulation
	 * @param input
	 *            the namelist.input file from the WRF directory
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWGet(Pair<Calendar, Calendar> timeRange, Namelist input) throws IOException, InterruptedException {
		NamelistInnerMap tc = input.get("time_control");
		//We construct the duration in hours here and add the start hour so that we only need to do the addition for the offset once instead of max_dom times.
		int hoursDuration = ((Number) tc.get("run_days").get(0).getY()).intValue() + ((Number) tc.get("run_hours").get(0).getY()).intValue() + ((Number) tc.get("start_hour").get(0).getY()).intValue();
		if (((Number) tc.get("run_minutes").get(0).getY()).intValue() != 0 || ((Number) tc.get("run_seconds").get(0).getY()).intValue() != 0 || hoursDuration % 3 != 0)
			throw new RuntimeException("Run length must be such that when it is converted to hours, it is divisible by 3.");
		
		//Create the grib directory if it doesn't already exist and initialize a ProcessBuilder to use that directory 
		Path gribPath = Paths.get((String) paths.get("grib_data").value()).toAbsolutePath().normalize();
		Files.createDirectories(gribPath);
		ProcessBuilder wgetPB = makePB(gribPath.toFile());
		Calendar start = timeRange.getX();
		//Construct the prefix of the url
		String url = "http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.";
		url += String.format(Locale.US, "%d%02d%02d", start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH)) + "/nam.t00z.awip3d";
		String suffix = ".tm00.grib2";
		//Download each datafile
		for (int i = ((Number) tc.get("start_hour").get(0).getY()).intValue(); i <= hoursDuration; i += 3) {
			String[] command = {(String) commands.get("wget").value(), "--no-check-certificate", "-N", url + String.format(Locale.US, "%02d", i) + suffix};
			System.out.println(runPB(wgetPB, command));
		}
	}
	
	/**
	 * This executes the commands needed to run a WPS process in sequence (ungrib and geogrid are run in parallel).
	 * 
	 * @param wps
	 *            the WPS {@link Namelist} file
	 * @param wpsPath
	 *            the {@link Path} to the root of the WPS installation
	 * @param timeRange
	 *            a {@link Pair} of {@link Calendar Calendars} that hold the calculated start and end times of the simulation
	 * @throws IOException
	 *             if the WPS directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWPS(Namelist wps, Path wpsPath, Pair<Calendar, Calendar> timeRange) throws IOException, InterruptedException {
		ProcessBuilder wpsPB = makePB(wpsPath.toFile());
		Path gribPath = Paths.get((String) paths.get("grib_data").value()).toAbsolutePath().normalize();
		runPB(wpsPB, "./link_grib.csh", gribPath.toString() + "/");
		//Run ungrib and geogrid in parallel
		wpsPB.command("./ungrib.exe", "2>&1", "|", "tee", "./ungrib.log");
		Process ungrib = wpsPB.start();
		runPB(wpsPB, "./geogrid.exe", "2>&1", "|", "tee", "./geogrid.log");
		ungrib.waitFor();
		runPB(wpsPB, "./metgrid.exe", "2>&1", "|", "tee", "./metgrid.log");
		if (((Boolean) features.get("cleanup").value())) {
			runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f GRIBFILE*");
			runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f " + ((String) wps.get("ungrib").get("prefix").get(0).getY()) + "*");
			if (wps.get("share").get("opt_output_from_geogrid_path") != null)
				runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f "
						+ Paths.get(((String) wps.get("share").get("opt_output_from_geogrid_path").get(0).getY()), "geo_").toString() + "*");
			else
				runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f geo_*");
			runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -r -f \"" + gribPath.toString() + "\"");
		}
	}
	
	/**
	 * This executes the commands needed to run a real-data WRF installation.
	 * 
	 * @param input
	 *            the WRF {@link Namelist} file
	 * @param wrfPath
	 *            the path to the <i>run</i> directory of the WRF installation
	 * @param timeRange
	 *            a {@link Pair} of {@link Calendar Calendars} that hold the calculated start and end times of the simulation
	 * @throws IOException
	 *             if the WRF run directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void runWRF(Namelist input, Path wrfPath, Pair<Calendar, Calendar> timeRange) throws IOException, InterruptedException {
		ProcessBuilder wrfPB = makePB(wrfPath.toFile());
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
			runPB(wrfPB, wrfCommand);
			if (((Boolean) features.get("cleanup").value()))
				runPB(wrfPB, (String) commands.get("bash").value(), "-c", "rm -f met_em*");
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
	
	/**
	 * Calculates the appropriate start and end times for the simulation from the configuration data and WRF {@link Namelist}
	 * file.
	 * 
	 * @param namelist
	 *            the WRF {@link Namelist} file
	 * @param current
	 *            a {@link Calendar} object with the current date/time data
	 * @param timing
	 *            a {@link JSONObject} holding the timing data in from the configuration file
	 * @return a {@link Pair} of {@link Calendar Calendars} that hold the calculated start and end times of the simulation
	 */
	public static Pair<Calendar, Calendar> getTimeRange(Namelist namelist, Calendar current, JSONObject timing) {
		Calendar start = (Calendar) current.clone();
		NamelistInnerMap tc = namelist.get("time_control");
		JSONObject rounding = (JSONObject) timing.get("rounding");
		String magnitude = ((String) rounding.get("magnitude").value()).toLowerCase();
		String diff = ((String) rounding.get("diff").value()).toLowerCase();
		if (!((Boolean) rounding.get("enabled").value()).booleanValue()) {
			start.set(start.YEAR, ((Number) tc.get("start_year").get(0).getY()).intValue());
			start.set(start.MONTH, ((Number) tc.get("start_month").get(0).getY()).intValue() - 1);
			start.set(start.DAY_OF_MONTH, ((Number) tc.get("start_day").get(0).getY()).intValue());
			start.set(start.HOUR_OF_DAY, ((Number) tc.get("start_hour").get(0).getY()).intValue());
			start.set(start.MINUTE, ((Number) tc.get("start_minute").get(0).getY()).intValue());
			start.set(start.SECOND, ((Number) tc.get("start_second").get(0).getY()).intValue());
		}
		else
			rounding: {
				//The logic here is that if we are rounding to something, then we want to set everything before it to 0.
				if (magnitude.equals("second")) {
					round(Calendar.SECOND, start, diff);
					break rounding;
				}
				start.set(Calendar.SECOND, 0);
				if (magnitude.equals("minute")) {
					round(Calendar.MINUTE, start, diff);
					break rounding;
				}
				start.set(Calendar.MINUTE, 0);
				if (magnitude.equals("hour")) {
					round(Calendar.HOUR_OF_DAY, start, diff);
					break rounding;
				}
				start.set(Calendar.HOUR_OF_DAY, 0);
				//Yes, I know these last three are kind of ridiculous, but, you never know.
				if (magnitude.equals("day")) {
					round(Calendar.DAY_OF_MONTH, start, diff);
					break rounding;
				}
				start.set(Calendar.DAY_OF_MONTH, 1);
				if (magnitude.equals("month")) {
					round(Calendar.YEAR, start, diff);
					break rounding;
				}
				start.set(Calendar.MONTH, 0);
				if (magnitude.equals("year")) {
					round(Calendar.YEAR, start, diff);
					break rounding;
				}
				start.set(Calendar.YEAR, 0);
			}
		
		//Update the start time with the offset
		JSONObject offset = (JSONObject) timing.get("offset");
		if (((Boolean) offset.get("enabled").value()).booleanValue()) {
			start.add(Calendar.DAY_OF_MONTH, ((Number) offset.get("days").value()).intValue());
			start.add(Calendar.HOUR_OF_DAY, ((Number) offset.get("hours").value()).intValue());
			start.add(Calendar.MINUTE, ((Number) offset.get("minutes").value()).intValue());
			start.add(Calendar.SECOND, ((Number) offset.get("seconds").value()).intValue());
		}
		
		//Calculate the end time from the duration data in the Namelist file
		Calendar end = (Calendar) start.clone();
		end.add(Calendar.SECOND, ((Number) tc.get("run_seconds").get(0).getY()).intValue());
		end.add(Calendar.MINUTE, ((Number) tc.get("run_minutes").get(0).getY()).intValue());
		end.add(Calendar.HOUR_OF_DAY, ((Number) tc.get("run_hours").get(0).getY()).intValue());
		end.add(Calendar.DAY_OF_MONTH, ((Number) tc.get("run_days").get(0).getY()).intValue());
		return new Pair<>(start, end);
	}
	
	//To avoid at least some of the copy-paste
	private static void round(int field, Calendar cal, String diff) {
		if (diff.equals("next"))
			cal.add(field, 1);
		else if (diff.equals("previous"))
			cal.add(field, -1);
	}
	
	/**
	 * Writes the given <tt>timeRange</tt> into a WPS {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param wps
	 *            a WPS {@link Namelist} file
	 * @param doms
	 *            the number of domains to be used
	 * @param timeRange
	 *            a {@link Pair} of {@link Calendar Calendars} that hold the calculated start and end times of the simulation
	 * @return the updated {@link Namelist} file (this is for easier chaining of commands - this method modifies the passed)
	 *         file
	 */
	public static Namelist updateWPSNamelistTimeRange(Namelist wps, int doms, Pair<Calendar, Calendar> timeRange) {
		Pair<NamelistType, Object> start = new Pair<>(NamelistType.String, makeWPSDate(timeRange.getX()));
		Pair<NamelistType, Object> end = new Pair<>(NamelistType.String, makeWPSDate(timeRange.getY()));
		NamelistInnerList s = new NamelistInnerList(), e = new NamelistInnerList();
		for (int i = 0; i < doms; i++) {
			s.add(start);
			e.add(end);
		}
		wps.get("share").put("start_date", s);
		wps.get("share").put("end_date", e);
		return wps;
	}
	
	/**
	 * Converts the date in the given {@link Calendar} to a WPS {@link Namelist} file date string
	 * 
	 * @param cal
	 *            a {@link Calendar}
	 * @return a WPS {@link Namelist} file-style date string
	 */
	private static String makeWPSDate(Calendar cal) {
		return String.format(Locale.US, "%d-%02d-%02d_%02d:%02d:%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	
	/**
	 * Writes the given <tt>timeRange</tt> into a WRF {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param input
	 *            a WRF {@link Namelist} file
	 * @param doms
	 *            the number of domains to be used
	 * @param timeRange
	 *            a {@link Pair} of {@link Calendar Calendars} that hold the calculated start and end times of the simulation
	 * @return the updated {@link Namelist} file (this is for easier chaining of commands - this method modifies the passed
	 *         file)
	 */
	public static Namelist updateWRFNamelistTimeRange(Namelist input, int doms, Pair<Calendar, Calendar> timeRange) {
		NamelistInnerList syear = new NamelistInnerList(), smonth = new NamelistInnerList(), sday = new NamelistInnerList();
		NamelistInnerList shour = new NamelistInnerList(), sminute = new NamelistInnerList(), ssecond = new NamelistInnerList();
		NamelistInnerList eyear = new NamelistInnerList(), emonth = new NamelistInnerList(), eday = new NamelistInnerList();
		NamelistInnerList ehour = new NamelistInnerList(), eminute = new NamelistInnerList(), esecond = new NamelistInnerList();
		Calendar start = timeRange.getX(), end = timeRange.getY();
		for (int i = 0; i < doms; i++) {
			syear.add(new Pair<>(NamelistType.Number, start.get(Calendar.YEAR)));
			smonth.add(new Pair<>(NamelistType.Number, start.get(Calendar.MONTH) + 1)); //We have to add 1 to the month because Java's Calendar system starts the months at 0
			sday.add(new Pair<>(NamelistType.Number, start.get(Calendar.DAY_OF_MONTH)));
			shour.add(new Pair<>(NamelistType.Number, start.get(Calendar.HOUR_OF_DAY)));
			sminute.add(new Pair<>(NamelistType.Number, start.get(Calendar.MINUTE)));
			ssecond.add(new Pair<>(NamelistType.Number, start.get(Calendar.SECOND)));
			eyear.add(new Pair<>(NamelistType.Number, end.get(Calendar.YEAR)));
			emonth.add(new Pair<>(NamelistType.Number, end.get(Calendar.MONTH) + 1)); //We have to add 1 to the month because Java's Calendar system starts the months at 0
			eday.add(new Pair<>(NamelistType.Number, end.get(Calendar.DAY_OF_MONTH)));
			ehour.add(new Pair<>(NamelistType.Number, end.get(Calendar.HOUR_OF_DAY)));
			eminute.add(new Pair<>(NamelistType.Number, end.get(Calendar.MINUTE)));
			esecond.add(new Pair<>(NamelistType.Number, end.get(Calendar.SECOND)));
		}
		NamelistInnerMap tc = input.get("time_control");
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
		return input;
	}
}
