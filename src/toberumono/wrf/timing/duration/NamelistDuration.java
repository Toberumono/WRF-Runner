package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.namelist.parser.NamelistSection;
import toberumono.wrf.scope.Scope;

import static toberumono.wrf.SimulationConstants.*;

public class NamelistDuration extends AbstractDuration {
	private int[] duration;
	private final NamelistSection timeControl;
	
	public NamelistDuration(NamelistSection timeControl, Scope parent) {
		super(null, parent);
		this.timeControl = timeControl;
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
			if (timeControl.containsKey("run_"  + TIMING_FIELD_NAMES.get(i))) //TODO implement inheritance via checking for String values equal to "inherit"
				duration[i] = ((Number) timeControl.get("run_"  + TIMING_FIELD_NAMES.get(i)).get(0).value()).intValue();
	}
}
