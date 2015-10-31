package toberumono.wrf.modules;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Map;
import java.util.logging.Level;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.wrf.Simulation;

/**
 * Contains the logic for running wget.
 * 
 * @author Toberumono
 */
public class WgetLogic {
	private static final int[] calendarOffsetFields = {Calendar.DAY_OF_MONTH, Calendar.HOUR_OF_DAY, Calendar.MINUTE, Calendar.SECOND};
	
	/**
	 * This downloads the necessary grib data for running the current simulation.<br>
	 * Override this method to change how grib data is downloaded (e.g. change the source, timing offset)
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if an IO error occurs
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public static void runWGet(Map<String, Namelist> namelists, Simulation sim) throws IOException, InterruptedException {
		int[] offsets = new int[4], steps = new int[4], limits = new int[4];
		String url = (String) sim.grib.get("url").value();
		Calendar constant = sim.getConstant(), end = sim.getEnd(), test = (Calendar) sim.getStart().clone(), increment = sim.getIncrement();
		
		JSONObject timestep = (JSONObject) sim.grib.get("timestep");
		steps[0] = ((Number) timestep.get("days").value()).intValue();
		steps[1] = ((Number) timestep.get("hours").value()).intValue();
		steps[2] = ((Number) timestep.get("minutes").value()).intValue();
		steps[3] = ((Number) timestep.get("seconds").value()).intValue();
		
		JSONObject duration = (JSONObject) sim.timing.get("duration");
		limits[0] = ((Number) duration.get("days").value()).intValue();
		limits[1] = ((Number) duration.get("hours").value()).intValue();
		limits[2] = ((Number) duration.get("minutes").value()).intValue();
		limits[3] = ((Number) duration.get("seconds").value()).intValue();
		for (; !test.after(end); incrementOffsets(offsets, steps, test, increment))
			downloadGribFile(parseIncrementedURL(url, constant, increment, 0, 0, offsets[0], offsets[1], offsets[2], offsets[3]), sim);
	}
	
	/**
	 * Takes a URL with both the normal Java date/time markers (see
	 * <a href="http://docs.oracle.com/javase/8/docs/api/java/util/Formatter.html#dt">Date/Time syntax</a>) and offset
	 * markers. The offset markers are <i>almost identical</i> in syntax to the standard Java date/time markers with, but
	 * they use 'i' instead of 't'.<br>
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
	public static String parseIncrementedURL(String url, Calendar start, Calendar increment, int years, int months, int days, int hours, int minutes, int seconds) {
		url = url.trim().replaceAll("%([\\Q-#+ 0,(\\E]*?[tT])", "%1\\$$1"); //Force the formatter to use the first argument for all of the default date/time markers
		url = url.replaceAll("%[iI]Y", "%2\\$04d").replaceAll("%[iI]y", "%2\\$d"); //Year
		url = url.replaceAll("%[iI]m", "%3\\$02d").replaceAll("%[iI]e", "%3\\$d"); //Month
		url = url.replaceAll("%[iI]D", "%4\\$02d").replaceAll("%[iI]d", "%4\\$d"); //Day
		url = url.replaceAll("%[iI]H", "%5\\$02d").replaceAll("%[iI]k", "%5\\$d"); //Hour
		url = url.replaceAll("%[iI]M", "%6\\$02d").replaceAll("%[iI]i", "%6\\$d"); //Minute
		url = url.replaceAll("%[iI]S", "%7\\$02d").replaceAll("%[iI]s", "%7\\$d"); //Second
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
	public static void downloadGribFile(String url, Simulation sim) throws IOException {
		String name = url.substring(url.lastIndexOf('/') + 1);
		Path dest = sim.get("wget").resolve(name);
		sim.getLog().info("Transferring: " + url + " -> " + dest.toString());
		try (ReadableByteChannel rbc = Channels.newChannel(new URL(url).openStream()); FileOutputStream fos = new FileOutputStream(dest.toString());) {
			fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
			sim.getLog().fine("Completed Transfer: " + url + " -> " + dest.toString());
		}
		catch (IOException e) {
			sim.getLog().severe("Failed Transfer: " + url + " -> " + dest.toString());
			sim.getLog().log(Level.FINE, e.getMessage(), e);
			throw e;
		}
	}
}
