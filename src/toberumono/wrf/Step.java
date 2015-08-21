package toberumono.wrf;

import java.io.IOException;
import java.util.Map;

import toberumono.namelist.parser.Namelist;

/**
 * Represents an individual step in a simulation.
 * 
 * @author Toberumono
 */
@FunctionalInterface
public interface Step {
	
	/**
	 * Perform the steps that constitute executing the {@link Step}
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @throws IOException
	 *             if the directory or any of the programs in it could not be opened
	 * @throws InterruptedException
	 *             if one of the processes is interrupted
	 */
	public void run(Map<String, Namelist> namelists, Simulation sim) throws IOException, InterruptedException;
}
