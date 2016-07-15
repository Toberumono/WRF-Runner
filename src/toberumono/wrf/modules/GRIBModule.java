package toberumono.wrf.modules;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import toberumono.wrf.Module;
import toberumono.wrf.Simulation;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.ModuleScopedMap;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.Timing;

import static java.util.Calendar.*;
import static toberumono.wrf.SimulationConstants.*;

/**
 * Contains the logic for getting the necessary GRIB files.
 * 
 * @author Toberumono
 */
public class GRIBModule extends Module {
	private static final long[] TIMING_FACTORS = {1, 1000, 60, 60, 24, 30, 365};
	static {
		for (int i = 1; i < TIMING_FACTORS.length; i++) //This makes it so that each factor is how much the value in the corresponding timing field would have to be multiplied by to convert it to milliseconds
			TIMING_FACTORS[i] *= TIMING_FACTORS[i - 1];
	}
	private String url;
	private volatile Timing incremented;
	private volatile ScopedMap timestep, intermediate;
	private Boolean wrap, useIncrementDuration;
	private Integer maxConcurrentDownloads;
	private volatile ExecutorService pool;
	
	/**
	 * Initializes a new {@link GRIBModule} with the given {@code parameters} for the given {@link Simulation}
	 * 
	 * @param parameters
	 *            the {@link GRIBModule GRIBModule's} parameters
	 * @param sim
	 *            the {@link Simulation} for which the {@link GRIBModule} is being initialized
	 */
	public GRIBModule(ModuleScopedMap parameters, Simulation sim) {
		super(parameters, sim);
		url = null;
		incremented = null;
		timestep = null;
		intermediate = null;
		wrap = null;
		useIncrementDuration = null;
		maxConcurrentDownloads = null;
		pool = null;
	}
	
	/**
	 * @return a {@link ScopedMap} that points to the constant and incremented timing data
	 */
	@NamedScopeValue("timing")
	public ScopedMap intermediateScope() {
		if (intermediate != null)
			return intermediate;
		synchronized (this) {
			if (intermediate == null) {
				intermediate = new ScopedMap(this);
				intermediate.put("constant", getTiming());
				intermediate.put("incremented", getIncrementedTiming());
			}
		}
		return intermediate;
	}
	
	@Override
	protected Timing parseTiming(ScopedMap timing) {
		return super.parseTiming(timing.containsKey("constant") ? (ScopedMap) timing.get("constant") : timing);
	}
	
	@Override
	@NamedScopeValue("constant")
	public Timing getTiming() {
		return super.getTiming();
	}
	
	/**
	 * @return the {@link Timing} data for the incremented component of the download URL
	 */
	@NamedScopeValue("incremented")
	public Timing getIncrementedTiming() {
		if (incremented != null)
			return incremented;
		synchronized (this) {
			if (incremented == null)
				incremented = WRFRunnerComponentFactory.generateComponent(Timing.class, ((ScopedMap) getParameters().get("timing")).containsKey("incremented")
						? (ScopedMap) ((ScopedMap) getParameters().get("timing")).get("incremented") : ((ScopedMap) getParameters().get("timing")), getTiming());
		}
		return incremented;
	}
	
	/**
	 * @return the maximum number of concurrent downloads allowed. Defaults to 8 (because 8 downloads at a 3-hour timestep is enough to download the
	 *         GRIB data for a 24-hour {@link Simulation})
	 */
	@NamedScopeValue("max-concurrent-downloads")
	public Integer getMaxConcurrentDownloads() {
		if (maxConcurrentDownloads != null)
			return maxConcurrentDownloads;
		synchronized (this) {
			if (maxConcurrentDownloads == null) {
				Object mcd = ((ScopedMap) getParameters().get("configuration")).get("max-concurrent-downloads");
				if (mcd instanceof Number) {
					maxConcurrentDownloads = ((Number) mcd).intValue();
					if (maxConcurrentDownloads < 1) //If max-concurrent-downloads is less than 1, it is treated as disabling the limit
						maxConcurrentDownloads = Integer.MAX_VALUE;
				}
				else if (mcd instanceof Boolean) {
					if (!(Boolean) mcd)
						maxConcurrentDownloads = Integer.MAX_VALUE;
					else
						throw new IllegalArgumentException("If max-concurrent-downloads is a Boolean, its value must be false.");
				}
				else {
					maxConcurrentDownloads = 8;
				}
			}
		}
		return maxConcurrentDownloads;
	}
	
	private ExecutorService getPool() {
		if (pool != null)
			return pool;
		synchronized (this) {
			if (pool == null)
				pool = Executors.newWorkStealingPool(getMaxConcurrentDownloads());
		}
		return pool;
	}
	
	@Override
	public void updateNamelist() throws IOException {/* This module has no Namelist */}
	
	/**
	 * @return {@code true} iff the incremented timing values should be wrapped via the {@link Calendar Calendar's} wrapping policy
	 */
	@NamedScopeValue({"should-wrap", "wrap"})
	public boolean shouldWrap() {
		if (wrap == null) //First time is so that we can avoid unnecessary synchronization
			synchronized (this) {
				if (wrap == null)
					wrap = evaluateToType(getTimestep().get("wrap"), "timestep.wrap", Boolean.class);
			}
		return wrap;
	}
	
	/**
	 * @return {@code true} iff the reference end time for GRIB downloads should be the end time computed by {@link #getIncrementedTiming()} rather
	 *         than the global end time
	 */
	@NamedScopeValue("use-increment-duration")
	public boolean useIncrementDuration() {
		if (useIncrementDuration == null) //First time is so that we can avoid unnecessary synchronization
			synchronized (this) {
				if (useIncrementDuration == null)
					useIncrementDuration = evaluateToType(((ScopedMap) getParameters().get("configuration")).get("use-increment-duration"), "use-increment-duration", Boolean.class);
			}
		return useIncrementDuration;
	}
	
	/**
	 * @return a {@link ScopedMap} containing the information that determines the timestep between GRIB files
	 */
	@NamedScopeValue("timestep")
	public ScopedMap getTimestep() {
		if (timestep == null) //First time is so that we can avoid unnecessary synchronization
			synchronized (this) {
				if (timestep == null)
					timestep = evaluateToType(((ScopedMap) getParameters().get("configuration")).get("timestep"), "timestep", ScopedMap.class);
			}
		return timestep;
	}
	
	/**
	 * @return a {@link String} containing the base URL to use for GRIB file downloads
	 */
	@NamedScopeValue("url")
	public String getURL() {
		if (url == null) //First time is so that we can avoid unnecessary synchronization
			synchronized (this) {
				if (url == null)
					url = evaluateToType(((ScopedMap) getParameters().get("configuration")).get("url"), "url", String.class);
			}
		return url;
	}
	
	@Override
	public void execute() throws IOException, InterruptedException {
		if (getMaxConcurrentDownloads() < 1)
			throw new IllegalArgumentException("max-concurrent-downloads must be greater than 0.");
		
		int[] offsets = new int[TIMING_FIELD_IDS.size()], steps = new int[TIMING_FIELD_IDS.size()];
		Calendar constant = getTiming().getStart(), increment = (Calendar) getIncrementedTiming().getStart().clone();
		Calendar end = useIncrementDuration() ? getIncrementedTiming().getEnd() : getSim().getTiming().getEnd();
		if (increment.after(end)) {
			getLogger().info("increment (" + increment.toString() + ") starts after the Simulation's end time (" + end.toString() + "). No GRIB files will be downloaded.");
			return;
		}
		
		long stepLength = 0l;
		for (int i = 0; i < TIMING_FIELD_IDS.size(); i++) {
			offsets[i] = increment.get(TIMING_FIELD_IDS.get(i));
			steps[i] = getTimestep().containsKey(TIMING_FIELD_NAMES.get(i)) ? evaluateToNumber(getTimestep().get(TIMING_FIELD_NAMES.get(i)), "timestep." + TIMING_FIELD_NAMES.get(i)).intValue() : 0;
			stepLength += steps[i] * TIMING_FACTORS[i];
		}
		if (stepLength <= 0)
			throw new IllegalArgumentException("The net step length must be greater than 0.");
		
		CompletionService<Boolean> cpool = new ExecutorCompletionService<>(getPool());
		final String preprocessedURL = preprocessIncrementedURL(getURL(), constant);
		Set<Future<Boolean>> active = new HashSet<>();
		//Initializing wasBefore to true allows us to avoid a do-while loop and support simulation start times that are offset from the increment start time
		for (boolean wasBefore = true; wasBefore || active.size() > 0;) {
			for (; active.size() < getMaxConcurrentDownloads() && wasBefore; wasBefore = increment.before(end), incrementOffsets(offsets, steps, increment))
				active.add(cpool.submit(downloadGribFile(generateIncrementedURL(preprocessedURL, increment, offsets))));
			if (active.size() > 0) {
				Future<Boolean> future = cpool.take();
				try {
					active.remove(future);
					future.get();
					while (active.size() > 0 && (future = cpool.poll(1, TimeUnit.SECONDS)) != null) {
						active.remove(future);
						future.get();
					}
				}
				catch (InterruptedException | ExecutionException e) {
					for (Future<Boolean> cancelling : active) //Cancel all current downloads
						cancelling.cancel(true);
					throw e.getCause() instanceof IOException ? (IOException) e.getCause() : new IOException("Failed to download the necessary GRIB files.", e.getCause());
				}
			}
		}
	}
	
	private final void incrementOffsets(int[] offsets, int[] steps, Calendar increment) {
		for (int i = 0; i < offsets.length; i++) {
			offsets[i] += steps[i];
			increment.add(TIMING_FIELD_IDS.get(i), steps[i]);
		}
	}
	
	private String preprocessIncrementedURL(String url, Calendar constant) {
		//Stores all mid-String escaped % signs and forces the formatter to use the first argument for all of the default date/time markers
		String out = String.format(url.trim().replaceAll("(%%)(.)", "$1~$2").replaceAll("%[iI].", "%$0").replaceAll("%([\\Q-#+ 0,(\\E]*?[tT])", "%1\\$$1"), constant);
		out = out.replaceAll("%[iI]L", "%1\\$03d").replaceAll("%[iI]q", "%1\\$d"); //Millisecond
		out = out.replaceAll("%[iI]S", "%2\\$02d").replaceAll("%[iI]s", "%2\\$d"); //Second
		out = out.replaceAll("%[iI]M", "%3\\$02d").replaceAll("%[iI]i", "%3\\$d"); //Minute
		out = out.replaceAll("%[iI]H", "%4\\$02d").replaceAll("%[iI]k", "%4\\$d"); //Hour
		out = out.replaceAll("%[iI]D", "%5\\$02d").replaceAll("%[iI]d", "%5\\$d"); //Day
		out = out.replaceAll("%[iI]m", "%6\\$02d").replaceAll("%[iI]e", "%6\\$d"); //Month
		out = out.replaceAll("%[iI]Y", "%7\\$04d").replaceAll("%[iI]y", "%7\\$d"); //Year
		out = out.replaceAll("(%)~(.)", "%$1$2");
		return out.charAt(out.length() - 1) == '%' ? out + "%" : out; //Restores any terminating % signs
	}
	
	private String generateIncrementedURL(String url, Calendar increment, int[] offsets) {
		if (shouldWrap())
			return String.format(url, increment.get(MILLISECOND), increment.get(SECOND), increment.get(MINUTE), increment.get(HOUR_OF_DAY), increment.get(Calendar.DAY_OF_MONTH),
					increment.get(MONTH) + 1, increment.get(YEAR));
		return String.format(url, offsets[0], offsets[1], offsets[2], offsets[3], offsets[4], offsets[5] + 1, offsets[6]);
	}
	
	/**
	 * Takes a URL with both the normal Java date/time markers (see
	 * <a href="http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#dt">Date/Time syntax</a>) and offset markers. The offset markers are
	 * <i>almost identical</i> in syntax to the standard Java date/time markers, but they use 'i' instead of 't'.<br>
	 * Differences:
	 * <ul>
	 * <li>%ii --&gt; minutes without padding</li>
	 * <li>%is --&gt; seconds without padding</li>
	 * <li>%iq --&gt; milliseconds without padding</li>
	 * </ul>
	 * 
	 * @param url
	 *            the base form of the URL <i>prior</i> to <i>any</i> calls to {@link String#format(String, Object...)}
	 * @param start
	 *            the {@link Calendar} containing the start time - this is constant across URLs
	 * @param increment
	 *            the {@link Calendar} containing the incremented time - this changes across URLs
	 * @param wrapTimestep
	 *            whether the increment {@link Calendar} or the offsets should be used (if false, the time fields will not wrap)
	 * @param offsets
	 *            the offsets from <tt>start</tt>
	 * @return a {@link String} representation of a {@link URL} pointing to the generated location
	 */
	@Deprecated
	public static String parseIncrementedURL(String url, Calendar start, Calendar increment, boolean wrapTimestep, int[] offsets) {
		url = url.trim().replaceAll("%([\\Q-#+ 0,(\\E]*?[tT])", "%1\\$$1"); //Force the formatter to use the first argument for all of the default date/time markers
		url = url.replaceAll("%[iI]L", "%2\\$03d").replaceAll("%[iI]q", "%2\\$d"); //Millisecond
		url = url.replaceAll("%[iI]S", "%3\\$02d").replaceAll("%[iI]s", "%3\\$d"); //Second
		url = url.replaceAll("%[iI]M", "%4\\$02d").replaceAll("%[iI]i", "%4\\$d"); //Minute
		url = url.replaceAll("%[iI]H", "%5\\$02d").replaceAll("%[iI]k", "%5\\$d"); //Hour
		url = url.replaceAll("%[iI]D", "%6\\$02d").replaceAll("%[iI]d", "%6\\$d"); //Day
		url = url.replaceAll("%[iI]m", "%7\\$02d").replaceAll("%[iI]e", "%7\\$d"); //Month
		url = url.replaceAll("%[iI]Y", "%8\\$04d").replaceAll("%[iI]y", "%8\\$d"); //Year
		if (!wrapTimestep)
			return String.format(url, start, offsets[0], offsets[1], offsets[2], offsets[3], offsets[4], offsets[5] + 1, offsets[6]);
		return String.format(url, start, increment.get(MILLISECOND), increment.get(SECOND), increment.get(MINUTE), increment.get(HOUR_OF_DAY),
				increment.get(Calendar.DAY_OF_MONTH), increment.get(MONTH) + 1, increment.get(YEAR));
	}
	
	/**
	 * Transfers a file from the given {@link URL} and places it in the grib directory.<br>
	 * The filename used in the grib directory is the component of the url after the final '/' (
	 * {@code name = url.substring(url.lastIndexOf('/') + 1)}).
	 * 
	 * @param url
	 *            a {@link String} representation of the {@link URL} to transfer
	 * @throws IOException
	 *             if the transfer fails
	 */
	private Callable<Boolean> downloadGribFile(String url) {
		return () -> {
			Path dest = getSim().getActivePath(getName()).resolve(url.substring(url.lastIndexOf('/') + 1));
			getLogger().info("Transferring: " + url + " -> " + dest.toString());
			try (ReadableByteChannel rbc = Channels.newChannel(new URL(url).openStream()); FileOutputStream fos = new FileOutputStream(dest.toString());) {
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				getLogger().fine("Completed Transfer: " + url + " -> " + dest.toString());
				return true; //This makes it Callable
			}
			catch (IOException e) {
				getLogger().severe("Failed Transfer: " + url + " -> " + dest.toString());
				getLogger().log(Level.FINE, e.getMessage(), e);
				throw e;
			}
		};
	}
	
	@Override
	public void cleanUp() throws IOException {/* This module doesn't perform any cleanup */}
}
