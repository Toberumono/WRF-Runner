package toberumono.wrf.timing.clear;

import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import toberumono.json.JSONArray;
import toberumono.json.JSONBoolean;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.json.JSONString;
import toberumono.utils.general.Numbers;
import toberumono.wrf.SimulationConstants;

import static toberumono.wrf.SimulationConstants.*;

public class StandardClear extends Clear {
	private int keep;
	
	public StandardClear(JSONObject parameters, Clear parent) {
		super(parameters, parent);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		for (int i = 0; i < keep; i++)
			out.set(TIMING_FIELDS.get(i), out.getActualMinimum(TIMING_FIELDS.get(i)));
		return out;
	}
	
	@Override
	protected void compute() {
		String name = (String) getParameters().get("keep").value();
		keep = TIMING_FIELD_NAMES.indexOf(name);
		if (keep < 0)
			throw new IllegalArgumentException(name + " is not a valid timing field name");
	}
}
