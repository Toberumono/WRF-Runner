package toberumono.wrf.modules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistNumber;
import toberumono.namelist.parser.NamelistString;
import toberumono.namelist.parser.NamelistValueList;
import toberumono.utils.files.RecursiveEraser;
import toberumono.wrf.Module;
import toberumono.wrf.Simulation;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.components.parallel.Parallel;
import toberumono.wrf.scope.ModuleScopedMap;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.utils.general.ProcessBuilders.*;

/**
 * Contains the logic for running WPS.
 * 
 * @author Toberumono
 */
public class WPSModule extends Module {
	private volatile Parallel parallel;
	
	/**
	 * Initializes a new {@link WPSModule} with the given {@code parameters} for the given {@link Simulation}
	 * 
	 * @param parameters
	 *            the {@link WPSModule WPSModule's} parameters
	 * @param sim
	 *            the {@link Simulation} for which the {@link WPSModule} is being initialized
	 */
	public WPSModule(ModuleScopedMap parameters, Simulation sim) {
		super(parameters, sim);
		parallel = null;
	}
	
	/**
	 * @return the {@link ScopedMap} containing information controlling how the WRF step is parallelized (if it is parallelized at all)
	 */
	@NamedScopeValue("parallel")
	public Parallel getParallel() {
		if (parallel != null)
			return parallel;
		synchronized (this) {
			if (parallel == null)
				parallel = getParameters().get("configuration") instanceof ScopedMap && ((ScopedMap) getParameters().get("configuration")).get("parallel") instanceof ScopedMap
						? WRFRunnerComponentFactory.generateComponent(Parallel.class, (ScopedMap) ((ScopedMap) getParameters().get("configuration")).get("parallel"), this)
						: WRFRunnerComponentFactory.getDisabledComponentInstance(Parallel.class, null, this);
		}
		return parallel;
	}
	
	@Override
	public void updateNamelist() throws IOException {
		Namelist wps = getNamelist();
		NamelistString start = new NamelistString(Simulation.makeWPSDateString(getTiming().getStart()));
		NamelistString end = new NamelistString(Simulation.makeWPSDateString(getTiming().getEnd()));
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
		wpsPB.command(Parallel.makeSerialCommand("./ungrib.exe", "./ungrib.log"));
		Process ungrib = wpsPB.start();
		runPB(wpsPB, getParallel().makeCommand("./geogrid.exe", "./geogrid.log"));
		ungrib.waitFor();
		runPB(wpsPB, getParallel().makeCommand("./metgrid.exe", "./metgrid.log"));
	}
	
	@Override
	public void cleanUp() throws IOException {
		RecursiveEraser re = new RecursiveEraser();
		Files.walkFileTree(getSim().getActivePath(getName()), re);
		Files.walkFileTree(getSim().getActivePath("grib"), re);
	}
}
