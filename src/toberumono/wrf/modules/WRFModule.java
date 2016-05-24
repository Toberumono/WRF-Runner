package toberumono.wrf.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.logging.Level;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistSection;
import toberumono.namelist.parser.NamelistValueList;
import toberumono.utils.files.RecursiveEraser;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.Module;
import toberumono.wrf.Simulation2;

import static toberumono.utils.general.ProcessBuilders.*;

/**
 * Contains the logic for running WPS.
 * 
 * @author Toberumono
 */
public class WRFModule extends Module {
	private static final String[] timeCodes = {"days", "hours", "minutes", "seconds"};
	
	public WRFModule(JSONObject parameters, Simulation2 sim) throws IOException {
		super(parameters, sim);
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public void updateNamelist() throws IOException, InterruptedException {
		NamelistValueList<NamelistNumber> syear = new NamelistValueList<>(), smonth = new NamelistValueList<>(), sday = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> shour = new NamelistValueList<>(), sminute = new NamelistValueList<>(), ssecond = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> eyear = new NamelistValueList<>(), emonth = new NamelistValueList<>(), eday = new NamelistValueList<>();
		NamelistValueList<NamelistNumber> ehour = new NamelistValueList<>(), eminute = new NamelistValueList<>(), esecond = new NamelistValueList<>();
		Calendar start = getTiming().getStart(), end = getTiming().getEnd();
		for (int i = 0; i < getSim().getDoms(); i++) {
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
		NamelistSection tc = getNamelist().get("time_control");
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
				((NamelistValueList<NamelistNumber>) tc.get("run_" + timeCode)).set(0, new NamelistNumber((Number) ((JSONObject) getSim().getTiming().get("duration")).get(timeCode).value()));
	}
	
	@Override
	public void execute() throws IOException, InterruptedException {
		Path run = getSim().getActivePath("wrf").resolve("run");
		ProcessBuilder wrfPB = makePB(run.toFile());
		runPB(wrfPB, "./real.exe", "2>&1", "|", "tee", "./real.log");
		String[] wrfCommand = new String[0];
		//Calculate which command to use
		if ((Boolean) getSim().getParallel().get("is-dmpar").value()) {
			if ((Boolean) getSim().getParallel().get("boot-lam").value())
				wrfCommand = new String[]{"mpiexec", "-boot", "-np", getSim().getParallel().get("processors").value().toString(), "./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
			else
				wrfCommand = new String[]{"mpiexec", "-np", getSim().getParallel().get("processors").value().toString(), "./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
		}
		else
			wrfCommand = new String[]{"./wrf.exe", "2>&1", "|", "tee", "./wrf.log"};
		try {
			runPB(wrfPB, wrfCommand);
		}
		catch (Throwable t) {
			logger.log(Level.SEVERE, "WRF error", t);
		}
		//Move the wrfout files to the output directory
		Files.walkFileTree(run, new TransferFileWalker(getSim().getWorkingPath(), Files::move, p -> p.getFileName().toString().toLowerCase().startsWith("wrfout"), p -> true, null, null, false));
	}
	
	@Override
	public void cleanUp() throws IOException, InterruptedException {
		Files.walkFileTree(getSim().getActivePath(getName()), new RecursiveEraser());
	}
	
}