package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistInnerMap;
import toberumono.namelist.parser.NamelistType;
import toberumono.structures.tuples.Pair;
import toberumono.utils.files.BasicTransferActions;
import toberumono.utils.files.TransferFileWalker;

/**
 * An individual WRF simulation. This extends a {@link Pair} of {@link Calendar Calendars} because the start and end times
 * are the main differentiating factor between simulations.
 * 
 * @author Toberumono
 */
public class Simulation extends Pair<Calendar, Calendar> {
	protected final Logger log;
	
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
	 * @param log
	 *            the {@link Logger} to use in the {@link Simulation TimeRange's} operations
	 */
	public Simulation(Namelist namelist, Calendar current, JSONObject timing, Logger log) {
		this.log = log;
		Calendar start = (Calendar) current.clone();
		Calendar end = (Calendar) start.clone();
		NamelistInnerMap tc = namelist.get("time_control");
		JSONObject rounding = (JSONObject) timing.get("rounding");
		JSONObject duration = (JSONObject) timing.get("duration");
		if (timing.get("use-computed-times") == null) //If use-computed-times hasn't been set, this is an older installation, so we can copy the value from the old flag.
			timing.put("use-computed-times", rounding.get("enabled"));
		if (!((Boolean) timing.get("use-computed-times").value()).booleanValue()) {
			start.set(start.YEAR, ((Number) tc.get("start_year").get(0).getY()).intValue());
			start.set(start.MONTH, ((Number) tc.get("start_month").get(0).getY()).intValue() - 1);
			start.set(start.DAY_OF_MONTH, ((Number) tc.get("start_day").get(0).getY()).intValue());
			start.set(start.HOUR_OF_DAY, ((Number) tc.get("start_hour").get(0).getY()).intValue());
			start.set(start.MINUTE, ((Number) tc.get("start_minute").get(0).getY()).intValue());
			start.set(start.SECOND, ((Number) tc.get("start_second").get(0).getY()).intValue());
			generateDuration(tc);
			duration.clearModified();
		}
		else {
			String magnitude = ((String) rounding.get("magnitude").value()).toLowerCase();
			String diff = ((String) rounding.get("diff").value()).toLowerCase();
			rounding: if (((Boolean) rounding.get("enabled").value()).booleanValue()) {
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
			
			if (duration == null) { //If there is no duration data, grab it from the WRF namelist file's "time_control" section
				duration = generateDuration(tc);
				timing.put("duration", duration);
			}
		}
		end = (Calendar) start.clone();
		//Calculate the end time from the duration data in the configuration file
		//Coincidentally, this is the same process needed for generating the timing data from the namelist file alone
		end.add(Calendar.DAY_OF_MONTH, ((Number) duration.get("days").value()).intValue());
		end.add(Calendar.HOUR_OF_DAY, ((Number) duration.get("hours").value()).intValue());
		end.add(Calendar.MINUTE, ((Number) duration.get("minutes").value()).intValue());
		end.add(Calendar.SECOND, ((Number) duration.get("seconds").value()).intValue());
		super.setX(start);
		super.setY(end);
	}
	
	/**
	 * Generates the timing--&gt;duration subsection of the configuration file from the "time_control" section of the WRF
	 * namelist file.
	 * 
	 * @param tc
	 *            the "time_control" section of the WRF namelist file
	 * @return a {@link JSONObject} representing the timing--&gt;duration subsection of the configuration file
	 */
	private final JSONObject generateDuration(NamelistInnerMap tc) {
		log.log(Level.WARNING, "configuration did not contain timing->duration.  Using and writing default values.");
		JSONObject duration = new JSONObject();
		duration.put("days", (Number) tc.get("run_days").get(0).getY());
		duration.put("hours", (Number) tc.get("run_hours").get(0).getY());
		duration.put("minutes", (Number) tc.get("run_minutes").get(0).getY());
		duration.put("seconds", (Number) tc.get("run_seconds").get(0).getY());
		duration.clearModified();
		return duration;
	}
	
	//To avoid at least some of the copy-paste
	private static void round(int field, Calendar cal, String diff) {
		if (diff.equals("next"))
			cal.add(field, 1);
		else if (diff.equals("previous"))
			cal.add(field, -1);
	}
	
	/**
	 * Constructs a {@link Simulation} from a start and end {@link Calendar}
	 * 
	 * @param start
	 *            the start {@link Calendar}
	 * @param end
	 *            the end {@link Calendar}
	 * @param log
	 *            the {@link Logger} to use in the {@link Simulation TimeRange's} operations
	 */
	public Simulation(Calendar start, Calendar end, Logger log) {
		super(start, end);
		this.log = log;
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
	 * Writes this {@link Simulation} into a WPS {@link Namelist}.<br>
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
	 * Writes this {@link Simulation} into a WRF {@link Namelist}.<br>
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
		final Path root = Files.createDirectories(working.resolve(getWPSStartDate().replaceAll(":", "_")).normalize()); //Having colons in the path messes up WRF, so... Underscores.
		WRFPaths paths = new WRFPaths(root, root.resolve("WRFV3"), root.resolve("WPS"), root.resolve("grib"), root, true);
		linkWorkingDirectory(wrf, paths.wrf);
		linkWorkingDirectory(wps, paths.wps);
		return paths;
	}
	
	/**
	 * Performs the operation used to link the working directories back to the source installation.<br>
	 * This uses {@link BasicTransferActions#SYMLINK}.
	 * 
	 * @param source
	 *            a {@link Path} to the source installation
	 * @param target
	 *            a {@link Path} to the target working directory
	 * @throws IOException
	 *             if an error occured while creating the links.
	 */
	public void linkWorkingDirectory(Path source, Path target) throws IOException {
		//We don't need anything from the src directories, so we exclude them.
		Files.walkFileTree(source, new TransferFileWalker(target, BasicTransferActions.SYMLINK,
				p -> !filenameTest(p.getFileName().toString()), p -> !p.getFileName().toString().equals("src"), null, log));
	}
	
	/**
	 * Tests the filename for patterns that indicate that the file should be excluded from the linking operation.<br>
	 * Basically, we want to minimize the number of links we're creating.
	 * 
	 * @param filename
	 *            the filename to test
	 * @return {@code true} if the name matches one of the patterns
	 */
	public boolean filenameTest(String filename) {
		filename = filename.toLowerCase();
		String extension = filename.substring(filename.lastIndexOf('.') + 1);
		if (extension.equals("csh"))
			return false;
		if (filename.startsWith("wrf") && filename.indexOf('.') == -1) //This eliminates all wrfbdy, wrfin, wrfout, wrfrst files.
			return true;
		if (filename.startsWith("rsl.out") || filename.startsWith("rsl.error"))
			return true;
		return filename.startsWith("namelist") || filename.startsWith("readme") || extension.charAt(0) == 'f' || extension.charAt(0) == 'c' || extension.equals("log");
	}
}
