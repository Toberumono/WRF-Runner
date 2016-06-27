package toberumono.wrf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import toberumono.namelist.parser.Namelist;
import toberumono.utils.files.BasicTransferActions;
import toberumono.utils.files.TransferFileWalker;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.ModuleScopedMap;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.ScopedList;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.Timing;

import static toberumono.wrf.SimulationConstants.*;

/**
 * Root class for {@link Module Modules} that are used by the {@link Simulation}.
 * 
 * @author Toberumono
 */
public abstract class Module extends AbstractScope<Simulation> {
	protected final Logger logger;
	private final String name;
	private final ScopedMap parameters, module;
	private Timing timing;
	private Path namelistPath;
	private Namelist namelist;
	private ScopedList dependencies;
	
	/**
	 * Constructs a new {@link Module} defined by the given {@link ModuleScopedMap parameters} and initialized by the given {@link Simulation}.
	 * 
	 * @param parameters
	 *            the parameters that define the {@link Module}
	 * @param sim
	 *            the {@link Simulation} that initialized the {@link Module}
	 */
	public Module(ModuleScopedMap parameters, Simulation sim) {
		super(sim);
		this.parameters = parameters;
		this.parameters.setParent(this);
		namelistPath = null;
		namelist = null;
		timing = null;
		dependencies = null;
		name = (String) parameters.get("name");
		logger = Logger.getLogger(SIMULATION_LOGGER_ROOT + ".module." + getName());
		module = (ScopedMap) parameters.get("module");
	}
	
	/**
	 * Parses {@link Timing} information from the given {@link ScopedMap}.
	 * 
	 * @param timing
	 *            the {@link ScopedMap} containing the {@link Module Module's} {@link Timing} information
	 * @return the parsed {@link Timing} information
	 */
	protected Timing parseTiming(ScopedMap timing) {
		return WRFRunnerComponentFactory.generateComponent(Timing.class, timing, getSim().getTiming());
	}
	
	/**
	 * Reads the {@link Namelist} data for the {@link Module} from disk.<br>
	 * <b>Note:</b> The path to the file that the {@link Namelist} data is read from is defined by
	 * {@code getSim().getSourcePath(getName()).resolve(getNamelistPath())}
	 * 
	 * @return the data in the {@link Namelist} file as a {@link Namelist} object
	 * @throws IOException
	 *             if an I/O error occurs while reading from the {@link Namelist} file
	 */
	protected Namelist ingestNamelist() throws IOException {
		if (getNamelistPath() != null && getSim().getSourcePath(getName()) != null)
			return new Namelist(getSim().getSourcePath(getName()).resolve(getNamelistPath()));
		return null;
	}
	
	/**
	 * Writes the {@link Module Module's} {@link Namelist} data to disk.<br>
	 * <b>Note:</b> The path to the file that the {@link Namelist} data is written to is defined by
	 * {@code getSim().getActivePath(getName()).resolve(getNamelistPath())}
	 * 
	 * @throws IOException
	 *             if an I/O error occurs while writing to the {@link Namelist} file
	 */
	protected void writeNamelist() throws IOException {
		if (getNamelist() != null && getSim().getActivePath(getName()) != null)
			getNamelist().write(getSim().getActivePath(getName()).resolve(getNamelistPath()));
	}
	
	/**
	 * The logic to update the {@link Module Module's} {@link Namelist} file
	 * 
	 * @throws IOException
	 *             if an I/O error occurs while updating the {@link Namelist} file
	 * @throws InterruptedException
	 *             if the process is interrupted
	 */
	public abstract void updateNamelist() throws IOException, InterruptedException;
	
	/**
	 * The logic to execute the subprocesses that form the {@link Module Module's} step in the {@link Simulation}.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs while running the subprocesses
	 * @throws InterruptedException
	 *             if the process is interrupted
	 */
	public abstract void execute() throws IOException, InterruptedException;
	
	/**
	 * Cleans up the files that are no longer of use to the {@link Simulation}. This is run after {@link #execute()} returned.
	 * 
	 * @throws IOException
	 *             if an I/O error occurs while cleaning up files
	 * @throws InterruptedException
	 *             if the process is interrupted
	 */
	public abstract void cleanUp() throws IOException, InterruptedException;
	
	/**
	 * @return the {@link Path} to the {@link Module Module's} namelist file relative to the {@link Module Module's} root directory
	 */
	@NamedScopeValue(value="namelist-path", asString=true)
	public Path getNamelistPath() { //namelistPath holds the location of the namelist file relative to the module's root directory
		if (namelistPath == null)
			namelistPath = (module.containsKey(NAMELIST_FIELD_NAME) ? Paths.get((String) module.get(NAMELIST_FIELD_NAME)) : null);
		return namelistPath;
	}
	
	/**
	 * @return the {@link Module Module's} processed {@link Timing} information
	 */
	@NamedScopeValue("timing")
	public Timing getTiming() {
		if (timing != null) //First one is to avoid unnecessary use of synchronization
			return timing;
		synchronized (name) {
			if (timing != null)
				return timing;
			return timing = parseTiming((ScopedMap) parameters.get("timing"));
		}
	}
	
	/**
	 * @return a {@link ScopedList} wherein each value is a {@link Module} that must be run before this {@link Module} can be run
	 */
	@NamedScopeValue("dependencies")
	public ScopedList getDependencies() {
		if (dependencies != null) //First one is to avoid unnecessary use of synchronization
			return dependencies;
		synchronized (name) {
			if (dependencies != null)
				return dependencies;
			ScopedList dependencies = new ScopedList(this);
			if (module.containsKey("dependencies")) {
				Object deps = module.get("dependencies");
				if (deps instanceof String)
					dependencies.add(getSim().getModule((String) deps));
				else if (deps instanceof ScopedList)
					((ScopedList) deps).stream().map(o -> o instanceof String ? (String) o : o.toString()).map(getSim()::getModule).forEach(dependencies::add);
			}
			this.dependencies = dependencies;
		}
		return dependencies;
	}
	
	/**
	 * @return the {@link Module Module's} name
	 */
	@NamedScopeValue("name")
	public String getName() {
		return name;
	}
	
	/**
	 * @return the {@link Simulation} that initialized the {@link Module}
	 */
	@NamedScopeValue("sim")
	public Simulation getSim() {
		return getParent();
	}
	
	/**
	 * @return the {@link Module Module's} {@link Namelist} file
	 * @throws IOException
	 *             if an I/O error occurs while loading the {@link Namelist} file
	 */
	public Namelist getNamelist() throws IOException {
		if (namelist != null) //First one is to avoid unnecessary use of synchronization
			return namelist;
		synchronized (name) {
			if (namelist != null)
				return namelist;
			return namelist = ingestNamelist();
		}
	}
	
	/**
	 * @return the parameters that defined the {@link Module} as a {@link ScopedMap}
	 */
	@NamedScopeValue("parameters")
	public ScopedMap getParameters() {
		return parameters;
	}
	
	/**
	 * Performs the operation used to link the working directories back to the source installation.<br>
	 * This uses {@link BasicTransferActions#SYMLINK}.
	 * 
	 * @throws IOException
	 *             if an error occured while creating the links.
	 */
	public void linkToWorkingDirectory() throws IOException {
		Files.createDirectories(getSim().getActivePath(getName()));
		if (getSim().getSourcePath(getName()) != null)
			//We don't need anything from the src directories, so we exclude them.
			Files.walkFileTree(getSim().getSourcePath(getName()), new TransferFileWalker(getSim().getActivePath(getName()), BasicTransferActions.SYMLINK,
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
