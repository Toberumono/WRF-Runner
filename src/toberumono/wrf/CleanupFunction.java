package toberumono.wrf;

import java.io.IOException;
import java.util.Map;

import toberumono.namelist.parser.Namelist;

/**
 * Represents an individual cleanup function in a simulation.
 * 
 * @author Toberumono
 */
@FunctionalInterface
public interface CleanupFunction {

	/**
	 * Perform the steps that constitute executing the {@link Step}
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param paths
	 *            the paths to the working directories in use by this {@link WRFRunner}
	 * @throws IOException
	 *             if the directory or any of the programs in it could not be opened
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void cleanUp(Map<String, Namelist> namelists, WRFPaths paths) throws IOException, InterruptedException;
}
