package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.wrf.scope.ScopedConfiguration;

import static toberumono.wrf.SimulationConstants.*;

public class StandardDuration extends Duration {
	private int[] duration;
	
	public StandardDuration(ScopedConfiguration parameters, Duration parent) {
		super(parameters, parent);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		for (int i = 0; i < duration.length; i++)
			out.add(TIMING_FIELDS.get(i), duration[i]);
		return out;
	}

	@Override
	protected void compute() {
		duration = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < duration.length; i++)
			if (getParameters().contains(TIMING_FIELD_NAMES.get(i))) //TODO implement inheritance via checking for String values equal to "inherit"
				duration[i] = ((Number) getParameters().get(TIMING_FIELD_NAMES.get(i))).intValue();
	}
}
