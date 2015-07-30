package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Locale;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistInnerMap;
import toberumono.namelist.parser.NamelistType;
import toberumono.structures.tuples.Pair;
import toberumono.utils.files.TransferFileWalker;

/**
 * A collection of methods that perform operations using the start and/or end times of a simulation.
 * 
 * @author Toberumono
 */
public class TimeRange extends Pair<Calendar, Calendar> {
	
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
	 */
	public TimeRange(Namelist namelist, Calendar current, JSONObject timing) {
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
		super.setX(start);
		super.setY(end);
	}
	
	//To avoid at least some of the copy-paste
	private static void round(int field, Calendar cal, String diff) {
		if (diff.equals("next"))
			cal.add(field, 1);
		else if (diff.equals("previous"))
			cal.add(field, -1);
	}
	
	/**
	 * Constructs a {@link TimeRange} from a start and end {@link Calendar}
	 * 
	 * @param start
	 *            the start {@link Calendar}
	 * @param end
	 *            the end {@link Calendar}
	 */
	public TimeRange(Calendar start, Calendar end) {
		super(start, end);
	}
	
	/**
	 * @return the start {@link Calendar}
	 * @see #getX()
	 */
	public Calendar getStart() {
		return getX();
	}
	
	/**
	 * @return the end {@link Calendar}
	 * @see #getY()
	 */
	public Calendar getEnd() {
		return getY();
	}
	
	/**
	 * @return the WPS date-string corresponding to the start {@link Calendar}
	 * @see #makeWPSDateString(Calendar)
	 */
	public String getWPSStartDate() {
		return makeWPSDateString(getStart());
	}
	
	/**
	 * @return the WPS date-string corresponding to the end {@link Calendar}
	 * @see #makeWPSDateString(Calendar)
	 */
	public String getWPSEndDate() {
		return makeWPSDateString(getEnd());
	}
	
	/**
	 * Writes this {@link TimeRange} into a WPS {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param wps
	 *            a WPS {@link Namelist} file
	 * @param doms
	 *            the number of domains to be used
	 * @return the updated {@link Namelist} file (this is for easier chaining of commands - this method modifies the passed)
	 *         file
	 */
	public Namelist updateWPSNamelistTimeRange(Namelist wps, int doms) {
		Pair<NamelistType, Object> start = new Pair<>(NamelistType.String, getWPSStartDate());
		Pair<NamelistType, Object> end = new Pair<>(NamelistType.String, getWPSEndDate());
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
	 * Writes this {@link TimeRange} into a WRF {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param input
	 *            a WRF {@link Namelist} file
	 * @param doms
	 *            the number of domains to be used
	 * @return the updated {@link Namelist} file (this is for easier chaining of commands - this method modifies the passed
	 *         file)
	 */
	public Namelist updateWRFNamelistTimeRange(Namelist input, int doms) {
		NamelistInnerList syear = new NamelistInnerList(), smonth = new NamelistInnerList(), sday = new NamelistInnerList();
		NamelistInnerList shour = new NamelistInnerList(), sminute = new NamelistInnerList(), ssecond = new NamelistInnerList();
		NamelistInnerList eyear = new NamelistInnerList(), emonth = new NamelistInnerList(), eday = new NamelistInnerList();
		NamelistInnerList ehour = new NamelistInnerList(), eminute = new NamelistInnerList(), esecond = new NamelistInnerList();
		Calendar start = getStart(), end = getEnd();
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
	 * Creates the output folder for the current run and returns a path to it.
	 * 
	 * @param working
	 *            a {@link Path} to the root working directory, in which a timestamped folder will be created to hold the
	 *            linked WRF and WPS installations, grib files, and output files
	 * @param wrf
	 *            a {@link Path} to the original WRF installation root directory
	 * @param wps
	 *            a {@link Path} to the original WPS installation root directory
	 * @return a {@link Path} to the root of the timestamped working directory for this run.
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public WRFPaths makeWorkingFolder(final Path working, final Path wrf, final Path wps) throws IOException {
		Logger l = Logger.getLogger("WRFRunner");
		final Path root = Files.createDirectories(working.resolve(getWPSStartDate().replaceAll(":", "_"))); //Having colons in the path messes up WRF, so... Underscores.
		WRFPaths paths = new WRFPaths(root, root.resolve("WRFV3").normalize(), root.resolve("WPS").normalize(), root.resolve("grib").normalize(), root, true);
		
		Files.walkFileTree(wrf, new TransferFileWalker(paths.wrf, TransferFileWalker.SYMLINK, p -> {
			String test = p.getFileName().toString().toLowerCase();
			return !(test.startsWith("namelist") || test.startsWith("wrfout") || test.startsWith("wrfin") || test.startsWith("wrfrst") || extensionTest(test));
		} , p -> !p.getFileName().toString().equals("src"), null, l));
		
		Files.walkFileTree(wps, new TransferFileWalker(paths.wps, TransferFileWalker.SYMLINK, p -> {
			Path fname = wrf.relativize(p).getFileName();
			return !(fname.toString().startsWith("namelist") || extensionTest(fname.toString()));
		} , p -> !p.getFileName().toString().equals("src"), null, l));
		return paths;
	}
	
	/**
	 * Just tests for certain extensions.
	 * 
	 * @param fileName
	 *            the file name to test
	 * @return {@code true} if one of those extensions was found
	 */
	public boolean extensionTest(String fileName) {
		String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
		if (extension.equals("csh"))
			return false;
		return extension.charAt(0) == 'f' || extension.charAt(0) == 'c' || extension.equals("log");
	}
}
