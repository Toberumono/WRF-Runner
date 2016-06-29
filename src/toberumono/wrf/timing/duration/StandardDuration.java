package toberumono.wrf.timing.duration;

import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

/**
 * The default implementation of {@link Duration}.
 * 
 * @author Toberumono
 */
public class StandardDuration extends AbstractDuration {
	private int[] duration;
	
	/**
	 * Initializes a new instance of {@link StandardDuration} with a {@link Logger} derived from {@link Duration#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public StandardDuration(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		for (int i = 0; i < duration.length; i++)
			base.add(TIMING_FIELD_IDS.get(i), duration[i]);
		return base;
	}
	
	@Override
	protected void compute() {
		duration = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < duration.length; i++)
			if (getParameters().containsKey(TIMING_FIELD_NAMES.get(i))) //TODO implement inheritance via checking for String values equal to "inherit"
				duration[i] = ((Number) getParameters().get(TIMING_FIELD_NAMES.get(i))).intValue();
	}
}
