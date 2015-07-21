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

import toberumono.additional.structures.tuples.Pair;
import toberumono.json.JSONObject;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistInnerMap;
import toberumono.namelist.parser.NamelistParser;
import toberumono.namelist.parser.NamelistType;

public class Runner {
	public JSONObject settings, general, paths, commands, features;
	public String settingsFileName;
	public Path root;
	
	public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
		Runner runner = new Runner(Paths.get(Runner.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent(), args[0]);
		runner.runWRF();
	}
	
	public Runner(Path root, String settingsFileName) throws IOException {
		this.root = root;
		refreshSettings(settingsFileName);
	}
	
	/**
	 * Reloads the data from the settings.json file.
	 * 
	 * @throws IOException
	 *             if an IO error occurs
	 */
	public void refreshSettings() throws IOException {
		refreshSettings(settingsFileName);
	}
	
	public void refreshSettings(String settingsFileName) throws IOException {
		this.settingsFileName = settingsFileName;
		settings = (JSONObject) JSONSystem.loadJSON(root.resolve(settingsFileName));
		paths = (JSONObject) settings.get("paths");
		commands = (JSONObject) settings.get("commands");
		general = (JSONObject) settings.get("general");
		features = (JSONObject) general.get("features");
	}
	
	public void runWRF() throws IOException, URISyntaxException, InterruptedException {
		Path wrfPath = Paths.get(((String) paths.get("wrf").value())).toAbsolutePath();
		Path wpsPath = Paths.get(((String) paths.get("wps").value())).toAbsolutePath();
		
		Path wrfNamelistPath = wrfPath.resolve("namelist.input");
		Namelist input = NamelistParser.parseNamelist(wrfNamelistPath);
		Path wpsNamelistPath = wpsPath.resolve("namelist.wps");
		Namelist wps = NamelistParser.parseNamelist(wpsNamelistPath);
		int doms = ((Number) input.get("domains").get("max_dom").get(0).getY()).intValue();
		
		Pair<Calendar, Calendar> timeRange = getTimeRange(input, Calendar.getInstance(), (JSONObject) settings.get("timing"));
		updateWRFNamelistTimeRange(input, wrfNamelistPath, doms, timeRange);
		NamelistParser.writeNamelist(updateWPSNamelistTimeRange(wps, doms, timeRange), wpsNamelistPath);
		
		if (((Boolean) features.get("wget").value()))
			runWGet(timeRange, input);
		
		if (((Boolean) features.get("wps").value()))
			runWPS(wps, wpsPath, doms, timeRange);
		
		if (((Boolean) features.get("wrf").value()))
			runWRF(input, wrfPath, doms, timeRange);
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
		Path workingDir = Paths.get((String) paths.get("working").value()).toAbsolutePath();
		Files.createDirectories(workingDir);
		NamelistInnerMap tc = input.get("time_control");
		int hoursDuration = ((Number) tc.get("run_days").get(0).getY()).intValue() + ((Number) tc.get("run_hours").get(0).getY()).intValue() + ((Number) tc.get("start_hour").get(0).getY()).intValue();
		if (((Number) tc.get("run_minutes").get(0).getY()).intValue() != 0 || ((Number) tc.get("run_seconds").get(0).getY()).intValue() != 0 || hoursDuration % 3 != 0)
			throw new RuntimeException("Run length must be such that when it is converted to hours, it is divisible by 3.");
		
		Path gribPath = Paths.get((String) paths.get("grib_data").value()).toAbsolutePath().normalize();
		Files.createDirectories(gribPath);
		ProcessBuilder wgetPB = makePB(gribPath.toFile());
		Calendar start = timeRange.getX();
		String url = "http://www.ftp.ncep.noaa.gov/data/nccf/com/nam/prod/nam.";
		url += String.format(Locale.US, "%d%02d%02d", start.get(Calendar.YEAR), start.get(Calendar.MONTH) + 1, start.get(Calendar.DAY_OF_MONTH)) + "/nam.t00z.awip3d";
		String suffix = ".tm00.grib2";
		for (int i = ((Number) tc.get("start_hour").get(0).getY()).intValue(); i <= hoursDuration; i += 3) {
			String[] command = {(String) commands.get("wget").value(), "--no-check-certificate", "-N", url + String.format(Locale.US, "%02d", i) + suffix};
			System.out.println(runPB(wgetPB, command));
		}
	}
	
	public int runWPS(Namelist wps, Path wpsPath, int doms, Pair<Calendar, Calendar> timeRange) throws IOException, InterruptedException {
		ProcessBuilder wpsPB = makePB(wpsPath.toFile());
		Path gribPath = Paths.get((String) paths.get("grib_data").value()).toAbsolutePath().normalize();
		int wpsExitValue = runPB(wpsPB, "./link_grib.csh", gribPath.toString() + "/");
		//Run ungrib and geogrid in parallel
		wpsPB.command("./ungrib.exe", "2>&1", "|", "tee", "./ungrib.log");
		Process ungrib = wpsPB.start();
		wpsExitValue = runPB(wpsPB, "./geogrid.exe", "2>&1", "|", "tee", "./geogrid.log");
		wpsExitValue = ungrib.waitFor();
		wpsExitValue = runPB(wpsPB, "./metgrid.exe", "2>&1", "|", "tee", "./metgrid.log");
		if (((Boolean) features.get("cleanup").value())) {
			wpsExitValue = runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f GRIBFILE*");
			wpsExitValue = runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f " + ((String) wps.get("ungrib").get("prefix").get(0).getY()) + "*");
			if (wps.get("share").get("opt_output_from_geogrid_path") != null)
				wpsExitValue = runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f "
						+ Paths.get(((String) wps.get("share").get("opt_output_from_geogrid_path").get(0).getY()), "geo_").toString() + "*");
			else
				wpsExitValue = runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -f geo_*");
			wpsExitValue = runPB(wpsPB, (String) commands.get("bash").value(), "-c", "rm -r -f \"" + gribPath.toString() + "\"");
		}
		return wpsExitValue;
	}
	
	public int runWRF(Namelist input, Path wrfPath, int doms, Pair<Calendar, Calendar> timeRange) throws IOException, InterruptedException {
		ProcessBuilder wrfPB = makePB(wrfPath.toFile());
		wrfPB.redirectOutput(wrfPath.resolve("real.log").toFile());
		int wrfExitValue = runPB(wrfPB, "./real.exe", "2>&1");
		wrfPB.redirectOutput(Redirect.INHERIT);
		wrfPB.redirectErrorStream(true);
		wrfPB.redirectOutput(wrfPath.resolve("wrf.log").toFile());
		String[] wrfCommand = {"mpiexec", "-boot", "-np", general.get("processors").value().toString(), "./wrf.exe", "2>&1"};
		if (((Boolean) general.get("wait-for-WRF").value())) {
			wrfExitValue = runPB(wrfPB, wrfCommand);
			if (((Boolean) features.get("cleanup").value()))
				wrfExitValue = runPB(wrfPB, (String) commands.get("bash").value(), "-c", "rm -f met_em*");
		}
		else {
			wrfPB.command(wrfCommand);
			wrfPB.start();
		}
		return wrfExitValue;
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
		JSONObject offset = (JSONObject) timing.get("offset");
		if (((Boolean) offset.get("enabled").value()).booleanValue()) {
			start.add(Calendar.DAY_OF_MONTH, ((Number) offset.get("days").value()).intValue());
			start.add(Calendar.HOUR_OF_DAY, ((Number) offset.get("hours").value()).intValue());
			start.add(Calendar.MINUTE, ((Number) offset.get("minutes").value()).intValue());
			start.add(Calendar.SECOND, ((Number) offset.get("seconds").value()).intValue());
		}
		
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
	
	public static Namelist updateWPSNamelistTimeRange(Namelist wps, int doms, Pair<Calendar, Calendar> timeRange) throws IOException {
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
	
	private static String makeWPSDate(Calendar cal) {
		return String.format(Locale.US, "%d-%02d-%02d_%02d:%02d:%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	
	public static void updateWRFNamelistTimeRange(Namelist input, Path namelistPath, int doms, Pair<Calendar, Calendar> timeRange) throws IOException {
		NamelistInnerList syear = new NamelistInnerList(), smonth = new NamelistInnerList(), sday = new NamelistInnerList();
		NamelistInnerList shour = new NamelistInnerList(), sminute = new NamelistInnerList(), ssecond = new NamelistInnerList();
		NamelistInnerList eyear = new NamelistInnerList(), emonth = new NamelistInnerList(), eday = new NamelistInnerList();
		NamelistInnerList ehour = new NamelistInnerList(), eminute = new NamelistInnerList(), esecond = new NamelistInnerList();
		Calendar start = timeRange.getX(), end = timeRange.getY();
		for (int i = 0; i < doms; i++) {
			syear.add(new Pair<>(NamelistType.Number, start.get(Calendar.YEAR)));
			smonth.add(new Pair<>(NamelistType.Number, start.get(Calendar.MONTH) + 1));
			sday.add(new Pair<>(NamelistType.Number, start.get(Calendar.DAY_OF_MONTH)));
			shour.add(new Pair<>(NamelistType.Number, start.get(Calendar.HOUR_OF_DAY)));
			sminute.add(new Pair<>(NamelistType.Number, start.get(Calendar.MINUTE)));
			ssecond.add(new Pair<>(NamelistType.Number, start.get(Calendar.SECOND)));
			eyear.add(new Pair<>(NamelistType.Number, end.get(Calendar.YEAR)));
			emonth.add(new Pair<>(NamelistType.Number, end.get(Calendar.MONTH) + 1));
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
		NamelistParser.writeNamelist(input, namelistPath);
	}
}
