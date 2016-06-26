package toberumono.wrf.components.parallel;

import java.util.logging.Logger;

import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.clear.Clear;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

/**
 * Root interface for custom types that handle parallelization information.
 * 
 * @author Toberumono
 */
public interface Parallel extends Scope {
	/**
	 * The name that {@link Logger Loggers} in instances of {@link Clear} should be created from.
	 */
	public static final String LOGGER_NAME = SIMULATION_LOGGER_ROOT + ".Parallel";
	
	/**
	 * @return {@code true} iff the command should be run in parallel
	 */
	@NamedScopeValue("is-parallel")
	public Boolean isParallel();
	
	/**
	 * @return the number of processors to use
	 */
	@NamedScopeValue("num-processors")
	public Integer getNumProcessors();
	
	/**
	 * Generates a shell command based on the information in the {@link Parallel} instance that can be passed to a {@link ProcessBuilder}.
	 * 
	 * @param executablePath
	 *            the path to the executable file (can be relative)
	 * @param logPath
	 *            the path to the log file (can be relative, doesn't need to exist)
	 * @return a shell command based on the information in the {@link Parallel} instance that can be passed to a {@link ProcessBuilder}
	 */
	public String[] makeCommand(String executablePath, String logPath);
	
	/**
	 * Generates a shell command for serial processes that can be passed to a {@link ProcessBuilder}.
	 * 
	 * @param executablePath
	 *            the path to the executable file (can be relative)
	 * @param logPath
	 *            the path to the log file (can be relative, doesn't need to exist)
	 * @return a shell command for serial processes that can be passed to a {@link ProcessBuilder}
	 */
	public static String[] makeSerialCommand(String executablePath, String logPath) {
		return new String[]{executablePath, "2>&1", "|", "tee", logPath};
	}
}
