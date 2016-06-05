package toberumono.wrf.timing.rounding;

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

import static toberumono.wrf.SimulationConstants.*;

public class BucketRounding extends Rounding {
	private Map<Integer, Function<Integer, Integer>> roundingActions;
	
	public BucketRounding(JSONObject parameters, Rounding parent) {
		super(parameters, parent);
	}
	
	private static final Map<Integer, Function<Integer, Integer>> parseRoundingParameters(JSONObject parameters) { //TODO implement input scrubbing and error messages
		JSONData<?> enabled = parameters.get("enabled");
		Collection<String> keep = TIMING_FIELD_NAMES;
		if (enabled instanceof JSONArray)
			keep = ((JSONArray) enabled).stream().filter(e -> e.value() instanceof String && TIMING_FIELD_NAMES.contains(e.value())).map(e -> (String) e.value()).collect(Collectors.toSet());
		else if (enabled instanceof JSONObject) {
			keep = ((JSONObject) enabled).entrySet().stream().filter(e -> TIMING_FIELD_NAMES.contains(e.getKey()) && (!(e.getValue() instanceof JSONBoolean) || ((Boolean) e.getValue().value())))
					.map(e -> e.getKey()).collect(Collectors.toSet());
		}
		else if (enabled instanceof JSONString) {
			keep = new HashSet<>();
			keep.add((String) enabled.value());
		}
		Map<Integer, Function<Integer, Integer>> roundingFunctions = new HashMap<>();
		JSONObject arguments = (JSONObject) parameters.get("arguments");
		String name, arg;
		RoundingMode globalRM = arguments.containsKey("rounding-mode") ? (arguments.get("rounding-mode").value() instanceof String
				? RoundingMode.valueOf(((String) arguments.get("rounding-mode").value()).toUpperCase()) : RoundingMode.valueOf(((Number) arguments.get("rounding-mode").value()).intValue()))
				: RoundingMode.FLOOR;
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++) {
			if (!keep.contains(name = TIMING_FIELD_NAMES.get(i)))
				continue;
			JSONData<?> value = arguments.get(name);
			arg = name + "-rounding-mode";
			final RoundingMode rm = arguments.containsKey(arg) ? (arguments.get(arg).value() instanceof String //TODO try to reduce calls to get here
					? RoundingMode.valueOf(((String) arguments.get(arg).value()).toUpperCase()) : RoundingMode.valueOf(((Number) arguments.get(arg).value()).intValue()))
					: globalRM;
			if (value == null) { //Step-offset
				//TODO implement existence checks and enforce Integer type requirement
				final int step = ((Number) arguments.get(name + "-step").value()).intValue();
				final int offset = arguments.containsKey(name + "-offset") ? ((Number) arguments.get(name + "-offset").value()).intValue() : 0;
				roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, step, offset));
			}
			else if (value.value() instanceof JSONArray) { //Explicit buckets
				JSONArray temp = (JSONArray) value;
				int[] buckets = new int[temp.size()];
				for (int b = 0; b < buckets.length; b++)
					buckets[b] = ((Number) temp.get(b).value()).intValue(); //TODO enforce Integer type requirement
				roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, buckets));
			}
			else if (value.value() instanceof JSONObject) {
				JSONObject temp = (JSONObject) value;
				final RoundingMode orm = temp.containsKey("rounding-mode") ? (temp.get("rounding-mode").value() instanceof String //TODO try to reduce calls to get here
						? RoundingMode.valueOf(((String) temp.get("rounding-mode").value()).toUpperCase()) : RoundingMode.valueOf(((Number) temp.get("rounding-mode").value()).intValue()))
						: rm;
				if (temp.containsKey("buckets")) {
					JSONArray bucks = (JSONArray) temp.get("buckets"); //TODO enforce array type requirement
					int[] buckets = new int[bucks.size()];
					for (int b = 0; b < buckets.length; b++)
						buckets[b] = ((Number) bucks.get(b).value()).intValue(); //TODO enforce Integer type requirement
					roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, buckets));
				}
				else {
					//TODO implement existence checks and enforce Integer type requirement
					final int step = ((Number) arguments.get("step").value()).intValue();
					final int offset = arguments.containsKey("offset") ? ((Number) arguments.get("offset").value()).intValue() : 0;
					roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, orm, step, offset));
				}
			}
		}
		return roundingFunctions;
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		for (Entry<Integer, Function<Integer, Integer>> e : roundingActions.entrySet())
			out.set(e.getKey(), e.getValue().apply(out.get(e.getKey())));
		return out;
	}
	
	@Override
	protected void compute() { //TODO implement inheritance
		roundingActions = parseRoundingParameters(getParameters());
	}
}
