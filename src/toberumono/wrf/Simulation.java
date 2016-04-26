package toberumono.wrf;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import toberumono.json.JSONBoolean;
import toberumono.json.JSONNumber;
import toberumono.json.JSONObject;
import toberumono.json.JSONString;
import toberumono.json.JSONSystem;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistSection;
import toberumono.structures.tuples.Pair;
import toberumono.structures.tuples.Triple;
import toberumono.utils.files.BasicTransferActions;
import toberumono.utils.files.TransferFileWalker;
import toberumono.utils.general.MutedLogger;
import toberumono.utils.general.Numbers;

/**
 * An individual WRF simulation. This extends a {@link Pair} of {@link Calendar Calendars} because the start and end times
 * are the main differentiating factor between simulations.
 * 
 * @author Toberumono
 */
public class Simulation extends HashMap<String, Path> {
	private static final String[] timeCodes = {"days", "hours", "minutes", "seconds"};
	private static final String[] userStringMap = {"millisecond", "second", "minute", "hour", "day", "month", "year"};
	private static final int[] fields = {Calendar.MILLISECOND, Calendar.SECOND, Calendar.MINUTE, Calendar.HOUR_OF_DAY, Calendar.DAY_OF_MONTH, Calendar.MONTH, Calendar.YEAR};
	protected final Calendar increment, constant, start, end;
	private final Logger log;
	private final boolean create;
	protected final Map<String, Path> sourcePaths;
	/**
	 * The {@link JSONObject} holding the root of the configuration file.
	 */
	public final JSONObject configuration;
	/**
	 * The {@link JSONObject} holding the general subsection of the configuration file.
	 */
	public final JSONObject general;
	/**
	 * The {@link JSONObject} holding the features subsection of the configuration file.
	 */
	public final JSONObject features;
	/**
	 * The {@link JSONObject} holding the parallel subsection of the configuration file.
	 */
	public final JSONObject parallel;
	/**
	 * The {@link JSONObject} holding the timing subsection of the configuration file.
	 */
	public final JSONObject timing;
	private final JSONObject timestep;
	/**
	 * The {@link JSONObject} holding the grib subsection of the configuration file.
	 */
	public final JSONObject grib;
	private final Path configurationFile, working;
	protected final Map<String, Namelist> namelists;
	
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
	 * @param configurationFile
	 *            the {@link Path} to the configuration file to use
	 * @param output
	 *            a {@link Path} to the output directory
	 * @param steps
	 *            the steps to be performed
	 * @param create
	 *            if {@code true}, this will call {@link Files#createDirectories} for each {@link Path}
	 * @param log
	 *            the {@link Logger} to use in the {@link Simulation TimeRange's} operations
	 * @throws IOException
	 *             if the timestamped working directory cannot be created
	 */
	public Simulation(Path configurationFile, Path output, Map<String, Triple<Step, CleanupFunction, Pair<Path, NamelistUpdater>>> steps, boolean create, Logger log)
			throws IOException {
		constant = Calendar.getInstance();
		this.log = log == null ? MutedLogger.getMutedLogger() : log;
		
		this.configurationFile = configurationFile;
		sourcePaths = new HashMap<>();
		configuration = (JSONObject) JSONSystem.loadJSON(this.configurationFile);
		((JSONObject) configuration.get("paths")).forEach((n, p) -> sourcePaths.put(n, Paths.get(((String) p.value())).normalize().toAbsolutePath()));
		general = (JSONObject) configuration.get("general");
		features = (JSONObject) general.get("features");
		parallel = (JSONObject) general.get("parallel");
		timing = (JSONObject) configuration.get("timing");
		grib = (JSONObject) configuration.get("grib");
		timestep = ((Boolean) features.get("wget").value()) ? (JSONObject) grib.get("timestep") : null;
		fixConfigurationFile();
		
		namelists = new HashMap<>();
		for (String step : steps.keySet()) {
			if (sourcePaths.containsKey(step))
				namelists.put(step, steps.get(step).getZ() != null ? new Namelist(sourcePaths.get(step).resolve(steps.get(step).getZ().getX())) : null);
		}
		
		working = Paths.get(general.get("working-directory").value().toString());
		if (!Files.isDirectory(working))
			Files.createDirectories(working);
		if (working == null)
			throw new NullPointerException("The working path cannot be null.");
		if (configuration.isModified())
			JSONSystem.writeJSON(configuration, configurationFile);
		
		this.create = create;
		doms = ((Number) namelists.get("wrf").get("domains").get("max_dom").get(0).value()).intValue();
		interval_seconds = timestep != null ? new NamelistNumber(calcIntervalSeconds(timestep)) : null;
		increment = (Calendar) constant.clone();
		NamelistSection tc = namelists.get("wrf").get("time_control");
		JSONObject rounding = (JSONObject) timing.get("rounding");
		JSONObject duration = (JSONObject) timing.get("duration");
		initializeConstant(tc, rounding);
		start = (Calendar) constant.clone();
		//Update the start time with the offset
		JSONObject offset = (JSONObject) timing.get("offset");
		if (((Boolean) offset.get("enabled").value()).booleanValue()) {
			addJSONDiff(increment, offset);
			addJSONDiff(start, offset);
		}
		//If we aren't using computed times or there is no duration data, grab it from the WRF namelist file's "time_control" section
		if (!((Boolean) timing.get("use-computed-times").value()).booleanValue() || duration == null) {
			duration = generateDuration(tc);
			timing.put("duration", duration);
		}
		end = (Calendar) start.clone();
		addJSONDiff(end, duration); //Calculate the end time from the duration data in the configuration file
		root = Simulation.makeWorkingFolder(constant, working, (Boolean) general.get("always-suffix").value());
		this.output = root.resolve(output);
	}
	
	private void initializeConstant(NamelistSection tc, JSONObject rounding) {
		if (!((Boolean) timing.get("use-computed-times").value()).booleanValue()) {
			constant.set(constant.YEAR, ((Number) tc.get("start_year").get(0).value()).intValue());
			constant.set(constant.MONTH, ((Number) tc.get("start_month").get(0).value()).intValue() - 1);
			constant.set(constant.DAY_OF_MONTH, ((Number) tc.get("start_day").get(0).value()).intValue());
			constant.set(constant.HOUR_OF_DAY, ((Number) tc.get("start_hour").get(0).value()).intValue());
			constant.set(constant.MINUTE, ((Number) tc.get("start_minute").get(0).value()).intValue());
			constant.set(constant.SECOND, ((Number) tc.get("start_second").get(0).value()).intValue());
		}
		else {
			String magnitude = ((String) rounding.get("magnitude").value()).toLowerCase();
			String diff = ((String) rounding.get("diff").value()).toLowerCase();
			double fraction = (Double) rounding.get("fraction").value();
			boolean doRounding = ((Boolean) rounding.get("enabled").value()).booleanValue();
			int rp = 1;
			for (; rp < userStringMap.length && !userStringMap[rp].equals(magnitude); rp++);
			if (rp == userStringMap.length) {
				log.log(Level.WARNING, "The magnitude field in timing.rounding has an invalid value.  Skipping rounding.");
				doRounding = false;
			}
			if (fraction <= 0.0 || fraction > 1.0) {
				log.log(Level.WARNING, "The fraction field in timing.rounding is outside of its valid range of (0.0, 1.0].  Assuming 1.0.");
				fraction = 1.0;
			}
			if (doRounding) {
				//The logic here is that if we are rounding to something, then we want to set everything before it to 0.
				round(fields[rp], constant, diff); //First, we handle the diff on the field that the user is rounding on
				if (fraction < 1.0) { //If they want to keep some portion of the field before this, then fraction will be less than 1.0.
					--rp;
					int offset = 1 - constant.getActualMinimum(fields[rp]);
					constant.set(fields[rp], (int) Numbers.semifloor(constant.getActualMaximum(fields[rp]) + offset, fraction, constant.get(fields[rp])));
					increment.set(fields[rp], increment.getActualMinimum(fields[rp]));
				}
				//Note that this does not interfere with the partial rounding of the next smallest field because the decrementation needs to happen regardless.
				--rp;
				for (int min = 0; rp >= 0; --rp) {//Set all of the smaller fields to their minimum values (presumably 0)
					constant.set(fields[rp], min = constant.getActualMinimum(fields[rp]));
					increment.set(fields[rp], min);
				}
			}
		}
		
	}
	
	/**
	 * @return the {@link Path} to the current configuration file
	 */
	public Path getConfigurationPath() {
		return configurationFile;
	}
	
	/**
	 * @return the {@link Path} to the current working directory
	 */
	public Path getWorkingPath() {
		return working;
	}
	
	private void fixConfigurationFile() {
		JSONSystem.transferField("cleanup", new JSONBoolean(true), features, general);
		JSONSystem.transferField("keep-logs", new JSONBoolean(false), general);
		JSONSystem.transferField("always-suffix", new JSONBoolean(false), general);
		JSONSystem.transferField("max-kept-outputs", new JSONNumber<>(15), general);
		if (sourcePaths.containsKey("working")) {
			JSONString working = new JSONString(sourcePaths.remove("working").toString());
			if (!general.containsKey("working-directory"))
				general.put("working-directory", working);
			((JSONObject) configuration.get("paths")).remove("working");
		}
		JSONSystem.transferField("working-directory", new JSONString(configurationFile.toAbsolutePath().getParent().resolve("Working").normalize().toString()), general);
		JSONSystem.transferField("fraction", new JSONNumber<>(1.0), new JSONObject[]{(JSONObject) timing.get("rounding")});
		JSONSystem.transferField("wrap-timestep", new JSONBoolean(true), grib);
		JSONSystem.transferField("use-computed-times", ((JSONObject) timing.get("rounding")).get("enabled"), timing); //If use-computed-times hasn't been set, use rounding.enabled as its value.
	}
	
	private void addJSONDiff(Calendar cal, JSONObject diff) {
		cal.add(Calendar.DAY_OF_MONTH, ((Number) diff.get("days").value()).intValue());
		cal.add(Calendar.HOUR_OF_DAY, ((Number) diff.get("hours").value()).intValue());
		cal.add(Calendar.MINUTE, ((Number) diff.get("minutes").value()).intValue());
		cal.add(Calendar.SECOND, ((Number) diff.get("seconds").value()).intValue());
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
				duration.put(timeCode, tc.get("run_" + timeCode).get(0).value());
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
	 * @return the {@link Logger Log} being used by the {@link Simulation}
	 */
	public final Logger getLog() {
		return log;
	}
	
	/**
	 * Gets the source {@link Path} for the given module
	 * 
	 * @param module
	 *            the name of the module (e.g. wrf or wps)
	 * @return the source {@link Path} for the module or {@code null} if it cannot be found
	 */
	public Path getSourcePath(String module) {
		return sourcePaths.get(module);
	}
	
	/**
	 * @return the {@link Calendar} representing the {@link Simulation Simulation's} constant component of the timing for the
	 *         GRIB download URL pattern
	 */
	public Calendar getConstant() {
		return constant;
	}
	
	/**
	 * @return the {@link Calendar} representing the {@link Simulation Simulation's} incremented component of the timing for
	 *         the GRIB download URL pattern
	 */
	public Calendar getIncrement() {
		return increment;
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
