package toberumono.wrf.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistString;
import toberumono.namelist.parser.NamelistValueList;
import toberumono.utils.files.RecursiveEraser;
import toberumono.wrf.Simulation;

import static toberumono.utils.general.ProcessBuilders.*;

/**
 * Contains the logic for running WPS.
 * 
 * @author Toberumono
 */
public class WPSLogic {
	
	/**
	 * Writes this {@link Simulation} into a WPS {@link Namelist}.<br>
	 * Note: this method <i>does</i> modify the passed {@link Namelist} without cloning it, but does not write anything to
	 * disk.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @return <tt>namelists</tt> (this is for easier chaining of commands - this method modifies the passed loaded
	 *         {@link Namelist} directly)
	 */
	public static Map<String, Namelist> updateNamelistTimeRange(Map<String, Namelist> namelists, Simulation sim) {
		Namelist wps = namelists.get("wps");
		NamelistString start = new NamelistString(sim.getWPSStartDate());
		NamelistString end = new NamelistString(sim.getWPSEndDate());
		NamelistValueList<NamelistString> s = new NamelistValueList<>(), e = new NamelistValueList<>();
		for (int i = 0; i < sim.doms; i++) {
			s.add(start);
			e.add(end);
		}
		wps.get("share").put("start_date", s);
		wps.get("share").put("end_date", e);
		if (sim.interval_seconds != null) {
			NamelistValueList<NamelistNumber> is = new NamelistValueList<>();
			is.add(sim.interval_seconds);
			wps.get("share").put("interval_seconds", is);
		}
		return namelists;
	}
	
	/**
	 * This method modifies the original {@link Namelist} object directly - it does not create a copy. The return value is
	 * the same object - it's just for convenience in chaining.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @return the WPS {@link Namelist}
	 */
	@SuppressWarnings("unchecked")
	public static Namelist updatePaths(Map<String, Namelist> namelists, Simulation sim) {
		namelists = updateNamelistTimeRange(namelists, sim);
		Namelist wps = namelists.get("wps");
		//Convert the geog_data_path to an absolute path so that WPS doesn't break trying to find a path relative to its original location
		NamelistValueList<NamelistString> geogList = (NamelistValueList<NamelistString>) wps.get("geogrid").get("geog_data_path");
		Path newPath = sim.getSourcePath("wps").resolve(geogList.get(0).value().toString());
		geogList.set(0, new NamelistString(newPath.toAbsolutePath().normalize().toString()));
		//Ensure that the geogrid output is staying in the WPS working directory
		NamelistValueList<NamelistString> geoOutList = (NamelistValueList<NamelistString>) wps.get("share").get("opt_output_from_geogrid_path");
		if (geoOutList != null)
			if (geoOutList.size() > 0)
				geoOutList.set(0, new NamelistString("./"));
			else
				geoOutList.add(new NamelistString("./"));
		//Ensure that the metgrid output is going into the WRF working directory
		NamelistValueList<NamelistString> metList = (NamelistValueList<NamelistString>) wps.get("metgrid").get("opt_output_from_metgrid_path");
		if (metList == null) {
			metList = new NamelistValueList<>();
			wps.get("metgrid").put("opt_output_from_metgrid_path", metList);
		}
		String path = sim.get("wrf").resolve("run").toString();
		if (!path.endsWith(System.getProperty("file.separator")))
			path += System.getProperty("file.separator");
		if (metList.size() == 0)
			metList.add(new NamelistString(path));
		else
			metList.set(0, new NamelistString(path));
		return wps;
	}
	
	/**
	 * This executes the commands needed to run a WPS process (ungrib and geogrid are run in parallel).
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the WPS directory or any of the programs within it could not be opened or executed
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public static void run(Map<String, Namelist> namelists, Simulation sim) throws IOException, InterruptedException {
		ProcessBuilder wpsPB = makePB(sim.get("wps").toFile());
		String path = sim.get("wget").toString();
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
	
	/**
	 * Cleans up the WPS and GRIB directories via a {@link RecursiveEraser}. Therefore, this should <i>only</i> not be called
	 * on the source installation.
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up
	 */
	public static void cleanUp(Map<String, Namelist> namelists, Simulation sim) throws IOException {
		RecursiveEraser re = new RecursiveEraser();
		Files.walkFileTree(sim.get("wps"), re);
		Files.walkFileTree(sim.get("wget"), re);
	}
}
