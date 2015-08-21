package toberumono.wrf;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistSection;
import toberumono.structures.tuples.Pair;
import toberumono.utils.files.BasicTransferActions;
import toberumono.utils.files.TransferFileWalker;
import toberumono.utils.general.MutedLogger;

/**
 * An individual WRF simulation. This extends a {@link Pair} of {@link Calendar Calendars} because the start and end times
 * are the main differentiating factor between simulations.
 * 
 * @author Toberumono
 */
public class Simulation extends HashMap<String, Path> {
	private static final String[] timeCodes = {"days", "hours", "minutes", "seconds"};
	protected final Calendar start, end;
	protected final Logger log;
	protected final boolean create;
	
	/**
	 * The timestamped working directory
	 */
	public final Path root;
	/**
	 * The directory into which the output files are moved
	 */
	public final Path output;
	/**
	 * The number of domains in use for the simulation.
	 */
	public final int doms;
	/**
	 * The value to interval_seconds to in namelist files. If this is {@code null}, then the interval_seconds value should
	 * not be overwritten.
	 */
	public final NamelistNumber interval_seconds;
	
	/**
	 * Calculates the appropriate start and end times for the simulation from the configuration data and WRF {@link Namelist}
	 * file.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param current
	 *            a {@link Calendar} object with the current date/time data
	 * @param working
	 *            a {@link Path} to the root working directory.
	 * @param output
	 *            a {@link Path} to the output directory
	 * @param timestep
	 *            the grib--&gt;timestep subsection of the configuration file. If wget feature is not being used, this must
	 *            be {@code null}.
	 * @param timing
	 *            a {@link JSONObject} holding the timing data from the configuration file
	 * @param create
	 *            if {@code true}, this will call {@link Files#createDirectories} for each {@link Path}
	 * @param always_suffix
	 *            the value of the "use-suffix" field in the general--&gt;parallel subsection of the configuration file
	 * @param log
	 *            the {@link Logger} to use in the {@link Simulation TimeRange's} operations
	 * @throws IOException
	 *             if the timestamped working directory cannot be created
	 */
	public Simulation(Map<String, Namelist> namelists, Calendar current, Path working, Path output, JSONObject timestep, JSONObject timing, boolean create, boolean always_suffix, Logger log)
			throws IOException {
		if (working == null)
			throw new NullPointerException("The working path cannot be null.");
		if (log == null)
			log = MutedLogger.getMutedLogger();
		this.log = log;
		this.create = create;
		doms = ((Number) namelists.get("wrf").get("domains").get("max_dom").get(0).value()).intValue();
		interval_seconds = timestep != null ? new NamelistNumber(calcIntervalSeconds(timestep)) : null;
		start = (Calendar) current.clone();
		NamelistSection tc = namelists.get("wrf").get("time_control");
		JSONObject rounding = (JSONObject) timing.get("rounding");
		JSONObject duration = (JSONObject) timing.get("duration");
		if (timing.get("use-computed-times") == null) //If use-computed-times hasn't been set, this is an older installation, so we can copy the value from the old flag.
			timing.put("use-computed-times", rounding.get("enabled"));
		if (!((Boolean) timing.get("use-computed-times").value()).booleanValue()) {
			start.set(start.YEAR, ((Number) tc.get("start_year").get(0).value()).intValue());
			start.set(start.MONTH, ((Number) tc.get("start_month").get(0).value()).intValue() - 1);
			start.set(start.DAY_OF_MONTH, ((Number) tc.get("start_day").get(0).value()).intValue());
			start.set(start.HOUR_OF_DAY, ((Number) tc.get("start_hour").get(0).value()).intValue());
			start.set(start.MINUTE, ((Number) tc.get("start_minute").get(0).value()).intValue());
			start.set(start.SECOND, ((Number) tc.get("start_second").get(0).value()).intValue());
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
				//Yes, I know these last three are kind of ridiculous, but you never know.
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
		root = Simulation.makeWorkingFolder(start, working, always_suffix);
		this.output = root.resolve(output);
	}
	
	/**
	 * Generates the timing--&gt;duration subsection of the configuration file from the "time_control" section of the WRF
	 * namelist file.
	 * 
	 * @param tc
	 *            the "time_control" section of the WRF namelist file
	 * @return a {@link JSONObject} representing the timing--&gt;duration subsection of the configuration file
	 */
	private final JSONObject generateDuration(NamelistSection tc) {
		log.log(Level.WARNING, "configuration did not contain timing->duration.  Using and writing default values.");
		JSONObject duration = new JSONObject();
		for (String timeCode : timeCodes)
			if (tc.containsKey("run_" + timeCode))
				duration.put(timeCode, (Number) tc.get("run_" + timeCode).get(0).value());
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
	 * @return the {@link Calendar} representing the {@link Simulation Simulation's} start time
	 */
	public Calendar getStart() {
		return start;
	}
	
	/**
	 * @return the {@link Calendar} representing the {@link Simulation Simulation's} end time
	 */
	public Calendar getEnd() {
		return end;
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
	
	private int calcIntervalSeconds(JSONObject timestep) {
		int out = ((Number) timestep.get("seconds").value()).intValue();
		out += ((Number) timestep.get("minutes").value()).intValue() * 60;
		out += ((Number) timestep.get("hours").value()).intValue() * 60 * 60;
		out += ((Number) timestep.get("days").value()).intValue() * 24 * 60 * 60;
		return out;
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
	 * Creates the output folder for the current run and returns a path to it.<br>
	 * This appropriately handles parallel (both thread-wise and process-wise) calls.<br>
	 * If <tt>always_suffix</tt> is false and the timestamped folder already exists and there is an active simulation using
	 * it, this method will fail.
	 * 
	 * @param timestamp
	 *            a {@link Calendar} containing the date and time with which to timestamp the working directory.
	 * @param working
	 *            a {@link Path} to the root working directory, in which a timestamped folder will be created to hold the
	 *            linked WRF and WPS installations, grib files, and output files
	 * @param always_suffix
	 *            the value of the "use-suffix" field in the general--&gt;parallel subsection of the configuration file
	 * @return a {@link Path} to the root of the timestamped working directory for this run.
	 * @throws IOException
	 *             if an I/O error occurs
	 */
	public static Path makeWorkingFolder(Calendar timestamp, final Path working, boolean always_suffix) throws IOException {
		Path active = Files.createDirectories(working).resolve("active");
		try (FileChannel chan = FileChannel.open(active, StandardOpenOption.CREATE, StandardOpenOption.WRITE); FileLock lock = chan.lock();) {
			String rootName = makeWPSDateString(timestamp).replaceAll(":", "_"); //Having colons in the path messes up WRF, so... Underscores.
			StringBuilder name = new StringBuilder(rootName);
			try (Stream<Path> children = Files.list(working)) {
				int count = children.filter(p -> p.getFileName().toString().startsWith(rootName)).toArray().length;
				if (always_suffix || count > 0)
					name.append("+" + (count + 1));
			}
			Path root = Files.createDirectories(working.resolve(name.toString()).normalize());
			return root;
		}
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
				p -> !filenameTest(p.getFileName().toString()), p -> !p.getFileName().toString().equals("src"), null, log, false));
	}
	
	/**
	 * Tests the filename for patterns that indicate that the file should be excluded from the linking operation.<br>
	 * Basically, we want to minimize the number of links we're creating.
	 * 
	 * @param filename
	 *            the filename to test
	 * @return {@code true} if the name matches one of the patterns
	 */
	public static boolean filenameTest(String filename) {
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
	
	@Override
	public Path put(String key, Path value) {
		if (create)
			try {
				Files.createDirectories(value);
			}
			catch (IOException e) {
				log.log(Level.SEVERE, "Unable to create " + value.toString(), e);
			}
		return super.put(key, value);
	}
	
	@Override
	public void putAll(Map<? extends String, ? extends Path> m) {
		for (Map.Entry<? extends String, ? extends Path> e : m.entrySet())
			put(e.getKey(), e.getValue());
	}
}
