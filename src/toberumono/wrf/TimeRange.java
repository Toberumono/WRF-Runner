package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Locale;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistInnerList;
import toberumono.namelist.parser.NamelistInnerMap;
import toberumono.namelist.parser.NamelistType;
import toberumono.structures.tuples.Pair;
import toberumono.utils.files.TransferFileWalker;

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
	 * @return a {@link Pair} of {@link Calendar Calendars} that hold the calculated start and end times of the simulation
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
	
	public TimeRange(Calendar start, Calendar end) {
		super(start, end);
	}
	
	public Calendar getStart() {
		return getX();
	}
	
	public Calendar getEnd() {
		return getY();
	}
	
	public String getWPSStartDate() {
		return makeWPSDate(getStart());
	}
	
	public String getWPSEndDate() {
		return makeWPSDate(getEnd());
	}
	
	/**
	 * Writes the given <tt>tr</tt> into a WPS {@link Namelist}.<br>
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
	 * Writes the given <tt>tr</tt> into a WRF {@link Namelist}.<br>
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
	 * @return a WPS {@link Namelist} file-style date string
	 */
	public static final String makeWPSDate(Calendar cal) {
		return String.format(Locale.US, "%d-%02d-%02d_%02d:%02d:%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH),
				cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
	}
	
	/**
	 * Creates the output folder for the current run and returns a path to it.
	 * 
	 * @param working
	 *            a {@link Path} to the working directory in configuration.json.
	 * @return a {@link Path} to the root of the working directory for this run.
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public Path makeWorkingFolder(final Path working, final Path wrf, final Path wps, final String bash) throws IOException, InterruptedException {
		final Path root = Files.createDirectories(working.resolve(getWPSStartDate()));
		final Path wrfO = Files.createDirectories(root.resolve("WRFV3"));
		final Path wpsO = Files.createDirectories(root.resolve("WPS"));
		final ProcessBuilder wrfPB = Runner.makePB(root.toFile());
		TransferFileWalker tfw = new TransferFileWalker(wrfO, (s, t) -> {
			try {
				Runner.runPB(wrfPB, bash, "-c", "ln -sf \"" + s.toString() + "\" \"" + t.toString() + "\"");
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			return t;
		}, p -> {
			String test = wrf.relativize(p).toString();
			if (test.contains("namelist") || !test.contains("run"))
				return p.equals(wrf);
			return true;
		}, null, null);
		Files.walkFileTree(wrf, tfw);
		tfw = new TransferFileWalker(wpsO, (s, t) -> {
			try {
				Runner.runPB(wrfPB, bash, "-c", "ln -sf \"" + s.toString() + "\" \"" + t.toString() + "\"");
			}
			catch (InterruptedException e) {
				e.printStackTrace();
			}
			return t;
		}, p -> {
			Path fname = wrf.relativize(p).getFileName();
			return fname == null || !fname.toString().contains("namelist");
		}, null, null);
		Files.walkFileTree(wps, tfw);
		return root;
	}
}
