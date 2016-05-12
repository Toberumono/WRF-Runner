package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.namelist.parser.NamelistSection;

import static toberumono.wrf.SimulationConstants.*;

public class NamelistDuration extends Duration {
	private final int[] duration;
	
	public NamelistDuration(NamelistSection timeControl) {
		duration = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < duration.length; i++)
			if (timeControl.containsKey("run_"  + TIMING_FIELD_NAMES.get(i) + "s")) //TODO implement inheritance via checking for String values equal to "inherit"
				duration[i] = ((Number) timeControl.get("run_"  + TIMING_FIELD_NAMES.get(i) + "s").get(0).value()).intValue();
	}
	
	@Override
	public Calendar apply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		for (int i = 0; i < duration.length; i++)
			out.add(TIMING_FIELDS.get(i), duration[i]);
		return out;
	}
}
