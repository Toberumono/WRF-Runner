package toberumono.wrf.scope;

import java.util.Calendar;

import toberumono.wrf.SimulationConstants;

import static toberumono.wrf.SimulationConstants.*;

/**
 * A collection of helper functions that don't necessarily belong to a particular class.
 * 
 * @author Toberumono
 */
public class ScopeUtils {
	
	/**
	 * Makes a {@link ScopedMap} from a {@link Calendar}. The resultant {@link ScopedMap} will have the fields enumerated in
	 * {@link SimulationConstants#TIMING_FIELD_NAMES}.
	 * 
	 * @param base
	 *            the {@link Calendar} from which the {@link ScopedMap} is to be generated
	 * @param parent
	 *            the parent {@link Scope}
	 * @return a {@link ScopedMap} based on the {@link Calendar}
	 */
	public static ScopedMap makeScopeFromCalendar(Calendar base, Scope parent) {
		ScopedMap out = new ScopedMap(parent);
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++)
			out.put(TIMING_FIELD_NAMES.get(i), base.get(TIMING_FIELDS.get(i)));
		return out;
	}
}
