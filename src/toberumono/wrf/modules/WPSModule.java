package toberumono.wrf.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistString;
import toberumono.namelist.parser.NamelistValueList;
import toberumono.utils.files.RecursiveEraser;
import toberumono.wrf.Module;
import toberumono.wrf.Simulation2;

import static toberumono.utils.general.ProcessBuilders.*;

/**
 * Contains the logic for running WPS.
 * 
 * @author Toberumono
 */
public class WPSModule extends Module {
	
	public WPSModule(JSONObject parameters, Simulation2 sim) throws IOException {
		super(parameters, sim);
	}
	
	@Override
	public void updateNamelist() throws IOException, InterruptedException {
		Namelist wps = getNamelist();
		NamelistString start = new NamelistString(Simulation2.makeWPSDateString(getTiming().getStart()));
		NamelistString end = new NamelistString(Simulation2.makeWPSDateString(getTiming().getEnd()));
		NamelistValueList<NamelistString> s = new NamelistValueList<>(), e = new NamelistValueList<>();
		for (int i = 0; i < getSim().getDoms(); i++) {
			s.add(start);
			e.add(end);
		}
		wps.get("share").put("start_date", s);
		wps.get("share").put("end_date", e);
		if (getSim().getIntervalSeconds() != null) {
			NamelistValueList<NamelistNumber> is = new NamelistValueList<>();
			is.add(getSim().getIntervalSeconds());
			wps.get("share").put("interval_seconds", is);
		}
		//Convert the geog_data_path to an absolute path so that WPS doesn't break trying to find a path relative to its original location
		@SuppressWarnings("unchecked") NamelistValueList<NamelistString> geogList = (NamelistValueList<NamelistString>) wps.get("geogrid").get("geog_data_path");
		Path newPath = getSim().getSourcePath("wps").resolve(geogList.get(0).value().toString());
		geogList.set(0, new NamelistString(newPath.toAbsolutePath().normalize().toString()));
		//Ensure that the geogrid output is staying in the WPS working directory
		@SuppressWarnings("unchecked") NamelistValueList<NamelistString> geoOutList = (NamelistValueList<NamelistString>) wps.get("share").get("opt_output_from_geogrid_path");
		if (geoOutList != null)
			if (geoOutList.size() > 0)
				geoOutList.set(0, new NamelistString("./"));
			else
				geoOutList.add(new NamelistString("./"));
		//Ensure that the metgrid output is going into the WRF working directory
		@SuppressWarnings("unchecked") NamelistValueList<NamelistString> metList = (NamelistValueList<NamelistString>) wps.get("metgrid").get("opt_output_from_metgrid_path");
		if (metList == null) {
			metList = new NamelistValueList<>();
			wps.get("metgrid").put("opt_output_from_metgrid_path", metList);
		}
		String path = getSim().getActivePath("wrf").resolve("run").toString();
		if (!path.endsWith(System.getProperty("file.separator")))
			path += System.getProperty("file.separator");
		if (metList.size() == 0)
			metList.add(new NamelistString(path));
		else
			metList.set(0, new NamelistString(path));
	}
	
	@Override
	public void execute() throws IOException, InterruptedException {
		ProcessBuilder wpsPB = makePB(getSim().getActivePath(getName()).toFile());
		String path = getSim().getActivePath("grib").toString();
		if (!path.endsWith(System.getProperty("file.separator"))) //link_grib.csh requires that the path end with a '/'
			path += System.getProperty("file.separator");
		runPB(wpsPB, "./link_grib.csh", path);
		//Run ungrib and geogrid in parallel
		wpsPB.command("./ungrib.exe", "2>&1", "|", "tee", "./ungrib.log");
		Process ungrib = wpsPB.start();
		runPB(wpsPB, "./geogrid.exe", "2>&1", "|", "tee", "./geogrid.log");
		ungrib.waitFor();
		runPB(wpsPB, "./metgrid.exe", "2>&1", "|", "tee", "./metgrid.log");
	}
	
	@Override
	public void cleanUp() throws IOException, InterruptedException {
		RecursiveEraser re = new RecursiveEraser();
		Files.walkFileTree(getSim().getActivePath(getName()), re);
		Files.walkFileTree(getSim().getActivePath("grib"), re);
	}
}
