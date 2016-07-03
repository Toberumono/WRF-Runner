package toberumono.wrf.modules;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;

import toberumono.wrf.Module;
import toberumono.wrf.Simulation;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.ModuleScopedMap;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.Timing;

/**
 * Contains the logic for getting the necessary GRIB files.
 * 
 * @author Toberumono
 */
public class GRIBModule extends Module {
	private static final int[] calendarOffsetFields = {Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND};
	private String url;
	private Timing incremented;
	private ScopedMap timestep, intermediate;
	private Boolean wrap;
	private Integer maxConcurrentDownloads;
	private ExecutorService pool;
	
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
		int[] offsets = new int[4], steps = new int[4];
		List<Future<Boolean>> downloads = new ArrayList<>();
		Calendar constant = getTiming().getStart(), test = (Calendar) getSim().getTiming().getStart().clone(), end = getSim().getTiming().getEnd(),
				increment = (Calendar) getIncrementedTiming().getStart().clone();
		for (int i = 0; i < offsets.length; i++)
			offsets[i] = increment.get(calendarOffsetFields[i]);
		
		steps[0] = ((Number) timestep.get("days")).intValue();
		steps[1] = ((Number) timestep.get("hours")).intValue();
		steps[2] = ((Number) timestep.get("minutes")).intValue();
		steps[3] = ((Number) timestep.get("seconds")).intValue();
		
		if (getMaxConcurrentDownloads() < 1)
			throw new IllegalArgumentException("max-concurrent-downloads must be greater than 0.");
		if (test.after(end))
			return;
		
		IOException exc = null;
		boolean failed = false;
		do {
			for (; downloads.size() < getMaxConcurrentDownloads() && !test.after(end); incrementOffsets(offsets, steps, test, increment))
				downloads.add(downloadGribFile(parseIncrementedURL(url, constant, increment, shouldWrap(), 0, 0, offsets[0], offsets[1], offsets[2], offsets[3]), getSim()));
			Future<Boolean> download = downloads.remove(0);
			try {
				download.get();
			}
			catch (ExecutionException e) {
				failed = true;
				if (e.getCause() instanceof IOException) //We want to throw an IOException if it occured
					exc = (IOException) e.getCause();
			}
		} while (downloads.size() > 0);
		if (failed) {
			if (exc != null)
				throw exc;
			throw new IOException("Failed to download the necessary GRIB files.");
		}
	}
	
	/**
	 * Takes a URL with both the normal Java date/time markers (see
	 * <a href="http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#dt">Date/Time syntax</a>) and offset markers. The offset markers are
	 * <i>almost identical</i> in syntax to the standard Java date/time markers, but they use 'i' instead of 't'.<br>
	 * Differences:
	 * <ul>
	 * <li>%ii --&gt; minutes without padding</li>
	 * <li>%is --&gt; seconds without padding</li>
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
	public static String parseIncrementedURL(String url, Calendar start, Calendar increment, boolean wrapTimestep, int years, int months, int days, int hours, int minutes, int seconds) {
		url = url.trim().replaceAll("%([\\Q-#+ 0,(\\E]*?[tT])", "%1\\$$1"); //Force the formatter to use the first argument for all of the default date/time markers
		url = url.replaceAll("%[iI]Y", "%2\\$04d").replaceAll("%[iI]y", "%2\\$d"); //Year
		url = url.replaceAll("%[iI]m", "%3\\$02d").replaceAll("%[iI]e", "%3\\$d"); //Month
		url = url.replaceAll("%[iI]D", "%4\\$02d").replaceAll("%[iI]d", "%4\\$d"); //Day
		url = url.replaceAll("%[iI]H", "%5\\$02d").replaceAll("%[iI]k", "%5\\$d"); //Hour
		url = url.replaceAll("%[iI]M", "%6\\$02d").replaceAll("%[iI]i", "%6\\$d"); //Minute
		url = url.replaceAll("%[iI]S", "%7\\$02d").replaceAll("%[iI]s", "%7\\$d"); //Second
		if (!wrapTimestep)
			return String.format(url, start, years, months + 1, days, hours, minutes, seconds);
		return String.format(url, start, increment.get(Calendar.YEAR), increment.get(Calendar.MONTH) + 1, increment.get(Calendar.DAY_OF_MONTH),
				increment.get(Calendar.HOUR_OF_DAY), increment.get(Calendar.MINUTE), increment.get(Calendar.SECOND));
	}
	
	private static final void incrementOffsets(int[] offsets, int[] steps, Calendar test, Calendar base) {
		for (int i = 0; i < offsets.length; i++) {
			offsets[i] += steps[i];
			test.add(calendarOffsetFields[i], steps[i]);
			base.add(calendarOffsetFields[i], steps[i]);
		}
	}
	
	/**
	 * Transfers a file from the given {@link URL} and places it in the grib directory.<br>
	 * The filename used in the grib directory is the component of the url after the final '/' (
	 * {@code name = url.substring(url.lastIndexOf('/') + 1)}).
	 * 
	 * @param url
	 *            a {@link String} representation of the {@link URL} to transfer
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the transfer fails
	 */
	private Future<Boolean> downloadGribFile(String url, Simulation sim) {
		return getPool().submit(() -> {
			String name = url.substring(url.lastIndexOf('/') + 1);
			Path dest = sim.getActivePath(getName()).resolve(name);
			logger.info("Transferring: " + url + " -> " + dest.toString());
			try (ReadableByteChannel rbc = Channels.newChannel(new URL(url).openStream()); FileOutputStream fos = new FileOutputStream(dest.toString());) {
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				logger.fine("Completed Transfer: " + url + " -> " + dest.toString());
				return true; //This makes it Callable
			}
			catch (IOException e) {
				logger.severe("Failed Transfer: " + url + " -> " + dest.toString());
				logger.log(Level.FINE, e.getMessage(), e);
				throw e;
			}
		});
	}
	
	@Override
	public void cleanUp() throws IOException {/* This module doesn't perform any cleanup */}
}
