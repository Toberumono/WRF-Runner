package toberumono.wrf.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Map;
import java.util.logging.Level;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistSection;
import toberumono.namelist.parser.NamelistValueList;
import toberumono.utils.files.RecursiveEraser;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.Simulation;
import toberumono.wrf.WRFPaths;

import static toberumono.utils.general.ProcessBuilders.*;

/**
 * Contains the logic for running WPS.
 * 
 * @author Toberumono
 */
public class WRFLogic {
	private static final String[] timeCodes = {"days", "hours", "minutes", "seconds"};
	
	/**
	 * This executes the commands needed to run a real-data WRF installation.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the WRF run directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public static void run(Map<String, Namelist> namelists, Simulation sim) throws IOException, InterruptedException {
		Path run = sim.get("wrf").resolve("run");
		ProcessBuilder wrfPB = makePB(run.toFile());
		runPB(wrfPB, "./real.exe", "2>&1", "|", "tee", "./real.log");
		String[] wrfCommand = new String[0];
		//Calculate which command to use
		if ((Boolean) sim.parallel.get("is-dmpar").value()) {
			if ((Boolean) sim.parallel.get("boot-lam").value())
				wrfCommand = new String[]{"mpiexec", "-boot", "-np", sim.parallel.get("processors").value().toString(), "./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
			else
				wrfCommand = new String[]{"mpiexec", "-np", sim.parallel.get("processors").value().toString(), "./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
		}
		else
			wrfCommand = new String[]{"./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
		try {
			runPB(wrfPB, wrfCommand);
		}
		catch (Throwable t) {
			sim.getLog().log(Level.SEVERE, "WRF error", t);
		}
		//Move the wrfout files to the output directory
		Files.walkFileTree(run, new TransferFileWalker(sim.output, Files::move,
				p -> p.getFileName().toString().toLowerCase().startsWith("wrfout"), p -> true, null, null, false));
	}
	
	/**
	 * Writes this {@link Simulation} into a WRF {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @return the updated {@link Namelist} file (this is for easier chaining of commands - this method modifies the passed
	 *         file)
	 */
	@SuppressWarnings("unchecked")
	public static Namelist updatePaths(Map<String, Namelist> namelists, Simulation sim) {
		Namelist input = namelists.get("wrf");
		NamelistValueList<NamelistNumber> syear = new NamelistValueList<>(), smonth = new NamelistValueList<>(), sday = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> shour = new NamelistValueList<>(), sminute = new NamelistValueList<>(), ssecond = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> eyear = new NamelistValueList<>(), emonth = new NamelistValueList<>(), eday = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> ehour = new NamelistValueList<>(), eminute = new NamelistValueList<>(), esecond = new NamelistValueList<>();
		Calendar start = sim.getStart(), end = sim.getEnd();
		for (int i = 0; i < sim.doms; i++) {
			syear.add(new NamelistNumber(start.get(Calendar.YEAR)));
			smonth.add(new NamelistNumber(start.get(Calendar.MONTH) + 1)); //We have to add 1 to the month because Java's Calendar system starts the months at 0
			sday.add(new NamelistNumber(start.get(Calendar.DAY_OF_MONTH)));
			shour.add(new NamelistNumber(start.get(Calendar.HOUR_OF_DAY)));
			sminute.add(new NamelistNumber(start.get(Calendar.MINUTE)));
			ssecond.add(new NamelistNumber(start.get(Calendar.SECOND)));
			eyear.add(new NamelistNumber(end.get(Calendar.YEAR)));
			emonth.add(new NamelistNumber(end.get(Calendar.MONTH) + 1)); //We have to add 1 to the month because Java's Calendar system starts the months at 0
			eday.add(new NamelistNumber(end.get(Calendar.DAY_OF_MONTH)));
			ehour.add(new NamelistNumber(end.get(Calendar.HOUR_OF_DAY)));
			eminute.add(new NamelistNumber(end.get(Calendar.MINUTE)));
			esecond.add(new NamelistNumber(end.get(Calendar.SECOND)));
		}
		NamelistSection tc = input.get("time_control");
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
		for (String timeCode : timeCodes)
			if (tc.containsKey("run_" + timeCode))
				((NamelistValueList<NamelistNumber>) tc.get("run_" + timeCode)).set(0, new NamelistNumber((Number) ((JSONObject) sim.timing.get("duration")).get(timeCode).value()));
		return input;
	}
	
	/**
	 * Cleans up the WRF directory after moving the outputs to {@link WRFPaths#output} via a {@link TransferFileWalker} via a
	 * {@link RecursiveEraser}. Therefore, this should <i>only</i> not be called on the source installation.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up
	 */
	public static void cleanUp(Map<String, Namelist> namelists, Simulation sim) throws IOException {
		Files.walkFileTree(sim.get("wrf"), new RecursiveEraser());
	}
	
}
