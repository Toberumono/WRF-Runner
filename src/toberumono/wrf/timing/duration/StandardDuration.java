package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

public class StandardDuration extends AbstractDuration {
	private int[] duration;
	
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
