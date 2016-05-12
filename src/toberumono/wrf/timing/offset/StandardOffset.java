package toberumono.wrf.timing.offset;

import java.util.Calendar;

import toberumono.json.JSONBoolean;
import toberumono.json.JSONObject;

import static toberumono.wrf.SimulationConstants.*;

public class StandardOffset extends Offset {
	private final int[] offsets;
	private final boolean wrap;
	
	public StandardOffset(JSONObject parameters, Offset parent) {
		wrap = parameters.containsKey("wrap") ? ((JSONBoolean) parameters.get("wrap")).value() : parent.doesWrap();
		offsets = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < offsets.length; i++)
			if (parameters.containsKey(TIMING_FIELD_NAMES.get(i))) //TODO implement inheritance via checking for String values equal to "inherit"
				offsets[i] = ((Number) parameters.get(TIMING_FIELD_NAMES.get(i)).value()).intValue();
	}
	
	@Override
	public Calendar apply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		if (doesWrap()) {
			for (int i = 0; i < offsets.length; i++)
				out.add(TIMING_FIELDS.get(i), offsets[i]);
		}
		else {
			for (int i = 0; i < offsets.length; i++)
				out.set(TIMING_FIELDS.get(i), out.get(TIMING_FIELDS.get(i)) + offsets[i]);
		}
		return out;
	}
	
	@Override
	public boolean doesWrap() {
		return wrap;
	}
}
