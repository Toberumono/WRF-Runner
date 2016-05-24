package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.namelist.parser.NamelistSection;

import static toberumono.wrf.SimulationConstants.*;

public class NamelistDuration extends Duration {
	private int[] duration;
	private final NamelistSection timeControl;
	
	public NamelistDuration(NamelistSection timeControl) {
		super(null, null);
		this.timeControl = timeControl;
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
			if (timeControl.containsKey("run_"  + TIMING_FIELD_NAMES.get(i) + "s")) //TODO implement inheritance via checking for String values equal to "inherit"
				duration[i] = ((Number) timeControl.get("run_"  + TIMING_FIELD_NAMES.get(i) + "s").get(0).value()).intValue();
	}
}
