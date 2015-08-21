package toberumono.wrf;

import java.util.Map;

import toberumono.namelist.parser.Namelist;

/**
 * A function that writes a {@link Namelist} to a file.
 * 
 * @author Toberumono
 */
@FunctionalInterface
public interface NamelistUpdater {
	
	/**
	 * Updates a {@link Namelist} with values specific to the current {@link Simulation}
	 * 
	 * @param namelists
	 *            a {@link Map} connecting the name of each WRF module to its loaded {@link Namelist}
	 * @param sim
	 *            the current {@link Simulation}
	 * @return the {@link Namelist} that was updated
	 */
	public Namelist update(Map<String, Namelist> namelists, Simulation sim);
}
