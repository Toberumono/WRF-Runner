package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.namelist.parser.Namelist;
import toberumono.utils.files.BasicTransferActions;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.timing.Timing;

import static toberumono.wrf.SimulationConstants.*;

public abstract class Module {
	private final Timing timing;
	private final Path namelistPath;
	private final Namelist namelist;
	protected final Logger logger;
	private final String name;
	private final Simulation2 sim;
	
	public Module(JSONObject parameters, Simulation2 sim) throws IOException {
		this.sim = sim;
		name = (String) parameters.get("name").value();
		logger = Logger.getLogger("module::" + getName());
		JSONObject module = ((JSONObject) parameters.get("module"));
		namelistPath = module.containsKey(NAMELIST_FIELD_NAME) ? Paths.get((String) module.get(NAMELIST_FIELD_NAME).value()) : null; //namelistPath holds the relative location of the namelist file
		namelist = ingestNamelist();
		timing = parseTiming((JSONObject) parameters.get("timing"));
	}
	
	public Namelist ingestNamelist() throws IOException {
		if (namelistPath != null && getSim().getSourcePath(getName()) != null)
			return new Namelist(getSim().getSourcePath(getName()).resolve(namelistPath));
		return null;
	}
	
	public void writeNamelist() throws IOException {
		if (namelist != null && getSim().getActivePath(getName()) != null)
			namelist.write(getSim().getActivePath(getName()).resolve(namelistPath));
	}
	
	public abstract void updateNamelist() throws IOException, InterruptedException;
	
	public abstract void execute() throws IOException, InterruptedException;
	
	public abstract void cleanUp() throws IOException, InterruptedException;
	
	protected Timing parseTiming(JSONObject timing) {
		return new Timing(timing, getSim().getGlobalTiming());
	}
	
	public Timing getTiming() {
		return timing;
	}
	
	public String getName() {
		return name;
	}
	
	public Simulation2 getSim() {
		return sim;
	}
	
	public Namelist getNamelist() {
		return namelist;
	}
	
	/**
	 * Performs the operation used to link the working directories back to the source installation.<br>
	 * This uses {@link BasicTransferActions#SYMLINK}.
	 * 
	 * @throws IOException
	 *             if an error occured while creating the links.
	 */
	public void linkToWorkingDirectory() throws IOException {
		if (sim.getSourcePath(getName()) != null)
			//We don't need anything from the src directories, so we exclude them.
			Files.walkFileTree(sim.getSourcePath(getName()), new TransferFileWalker(sim.getActivePath(getName()), BasicTransferActions.SYMLINK,
					p -> !filenameTest(p.getFileName().toString()), p -> !p.getFileName().toString().equals("src"), null, logger, false));
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
}
