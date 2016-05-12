package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.json.JSONObject;

import static toberumono.wrf.SimulationConstants.*;

public class StandardDuration extends Duration {
	private final int[] duration;
	
	public StandardDuration(JSONObject parameters, Duration parent) {
		duration = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < duration.length; i++)
			if (parameters.containsKey(TIMING_FIELD_NAMES.get(i))) //TODO implement inheritance via checking for String values equal to "inherit"
				duration[i] = ((Number) parameters.get(TIMING_FIELD_NAMES.get(i)).value()).intValue();
	}
	
	@Override
	public Calendar apply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		for (int i = 0; i < duration.length; i++)
			out.add(TIMING_FIELDS.get(i), duration[i]);
		return out;
	}
}
