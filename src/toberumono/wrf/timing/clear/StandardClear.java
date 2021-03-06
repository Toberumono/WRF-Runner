package toberumono.wrf.timing.clear;

import java.util.Arrays;
import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

/**
 * The default implementation of {@link Clear}.
 * 
 * @author Toberumono
 */
public class StandardClear extends AbstractClear {
	private final int[] values;
	private int keep;
	
	/**
	 * Initializes a new instance of {@link StandardClear} with a {@link Logger} derived from {@link Clear#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public StandardClear(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
		values = new int[TIMING_FIELD_NAMES.size()];
		Arrays.fill(values, -1);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		for (int i = 0; i < keep; i++)
			base.set(TIMING_FIELD_IDS.get(i), values[i] == -1 ? base.getActualMinimum(TIMING_FIELD_IDS.get(i)) : values[i]);
		return base;
	}
	
	@Override
	protected void compute() {
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++)
			if (getParameters().containsKey(TIMING_FIELD_NAMES.get(i)))
				values[i] = ((Number) getParameters().get(TIMING_FIELD_NAMES.get(i))).intValue();
		String name = (String) getParameters().get("keep");
		keep = TIMING_FIELD_NAMES.indexOf(name);
		if (keep < 0)
			throw new IllegalArgumentException(name + " is not a valid timing field name");
	}
}
