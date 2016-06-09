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
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import toberumono.wrf.Module;
import toberumono.wrf.Simulation;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.ScopedConfiguration;
import toberumono.wrf.timing.Timing;

/**
 * Contains the logic for running getting the grib files.
 * 
 * @author Toberumono
 */
public class GRIBModule extends Module {
	private static final ExecutorService pool = Executors.newWorkStealingPool(12);
	private static final int[] calendarOffsetFields = {Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND};
	private String url;
	private Timing incremented;
	private ScopedConfiguration timestep;
	private Boolean wrap;
	private final Lock lock;
	
	/**
	 * Initializes a new {@link GRIBModule} with the given {@code parameters} for the given {@link Simulation}
	 * 
	 * @param parameters
	 *            the {@link GRIBModule GRIBModule's} parameters
	 * @param sim
	 *            the {@link Simulation} for which the {@link GRIBModule} is being initialized
	 */
	public GRIBModule(ScopedConfiguration parameters, Simulation sim) {
		super(parameters, sim);
		url = null;
		incremented = null;
		timestep = null;
		wrap = null;
		ScopedConfiguration grib = (ScopedConfiguration) parameters.get("configuration");
		url = (String) grib.get("url");
		timestep = (ScopedConfiguration) grib.get("timestep");
		wrap = (Boolean) timestep.get("wrap");
		lock = new ReentrantLock();
	}
	
	@Override
	protected Timing parseTiming(ScopedConfiguration timing) {
		return super.parseTiming((ScopedConfiguration) timing.get("constant"));
	}
	
	/**
	 * @return the {@link Timing} data for the incremented component of the download URL
	 */
	public Timing getIncrementedTiming() {
		if (incremented != null)
			return incremented;
		try {
			lock.lock();
			if (incremented == null)
				incremented = WRFRunnerComponentFactory.generateComponent(Timing.class, (ScopedConfiguration) ((ScopedConfiguration) getParameters().get("timing")).get("incremented"), getTiming());
		}
		finally {
			lock.unlock();
		}
		return incremented;
	}
	
	@Override
	public void updateNamelist() throws IOException, InterruptedException {/* This module has no Namelist */}
	
	@Override
	public void execute() throws IOException, InterruptedException {
		int[] offsets = new int[4], steps = new int[4];
		List<Future<Boolean>> downloads = new ArrayList<>();
		Calendar constant = getTiming().getStart(), test = (Calendar) getSim().getGlobalTiming().getStart().clone(), end = getSim().getGlobalTiming().getEnd(),
				increment = (Calendar) getIncrementedTiming().getStart().clone();
		
		steps[0] = ((Number) timestep.get("days")).intValue();
		steps[1] = ((Number) timestep.get("hours")).intValue();
		steps[2] = ((Number) timestep.get("minutes")).intValue();
		steps[3] = ((Number) timestep.get("seconds")).intValue();
		
		for (; !test.after(end); incrementOffsets(offsets, steps, test, increment))
			downloads.add(downloadGribFile(parseIncrementedURL(url, constant, increment, wrap, 0, 0, offsets[0], offsets[1], offsets[2], offsets[3]), getSim()));
		
		IOException exc = null;
		boolean failed = false;
		for (Future<Boolean> download : downloads) {
			try {
				download.get();
			}
			catch (ExecutionException e) {
				failed = true;
				if (e.getCause() instanceof IOException) //We want to throw an IOException if it occured
					exc = (IOException) e.getCause();
			}
		}
		if (failed) {
			if (exc != null)
				throw exc;
			throw new IOException("Failed to download the necessary GRIB files.");
		}
	}
	
	/**
	 * Takes a URL with both the normal Java date/time markers (see
	 * <a href="http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#dt">Date/Time syntax</a>) and offset
	 * markers. The offset markers are <i>almost identical</i> in syntax to the standard Java date/time markers, but they use
	 * 'i' instead of 't'.<br>
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
	 *            whether the increment {@link Calendar} or the offsets should be used (if false, the time fields will not
	 *            wrap)
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
		return pool.submit(() -> {
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
	public void cleanUp() throws IOException, InterruptedException {/* This module doesn't perform any cleanup */}
}
