package toberumono.wrf.timing.clear;

import java.util.Arrays;
import java.util.Calendar;

import toberumono.wrf.scope.ScopedConfiguration;

import static toberumono.wrf.SimulationConstants.*;

public class StandardClear extends Clear {
	private final int[] values;
	private int keep;
	
	public StandardClear(ScopedConfiguration parameters, Clear parent) {
		super(parameters, parent);
		values = new int[TIMING_FIELD_NAMES.size()];
		Arrays.fill(values, -1);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		for (int i = 0; i < keep; i++)
			out.set(TIMING_FIELDS.get(i), values[i] == -1 ? out.getActualMinimum(TIMING_FIELDS.get(i)) : values[i]);
		return out;
	}
	
	@Override
	protected void compute() {
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++)
			if (getParameters().contains(TIMING_FIELD_NAMES.get(i)))
				values[i] = ((Number) getParameters().get(TIMING_FIELD_NAMES.get(i))).intValue();
		String name = (String) getParameters().get("keep");
		keep = TIMING_FIELD_NAMES.indexOf(name);
		if (keep < 0)
			throw new IllegalArgumentException(name + " is not a valid timing field name");
	}
}
