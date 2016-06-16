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

import toberumono.utils.general.Numbers;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.scope.ScopedList;

import static toberumono.wrf.SimulationConstants.*;

public class BucketRounding extends Rounding {
	private Map<Integer, Function<Integer, Integer>> roundingActions;
	
	public BucketRounding(ScopedMap parameters, Rounding parent) {
		super(parameters, parent);
	}
	
	private static final Map<Integer, Function<Integer, Integer>> parseRoundingParameters(ScopedMap parameters) { //TODO implement input scrubbing and error messages
		Object enabled = parameters.get("enabled");
		Collection<String> keep = TIMING_FIELD_NAMES;
		if (enabled instanceof ScopedList)
			keep = ((ScopedList) enabled).stream().filter(e -> e instanceof String && TIMING_FIELD_NAMES.contains(e)).map(e -> (String) e).collect(Collectors.toSet());
		else if (enabled instanceof ScopedMap) {
			keep = ((ScopedMap) enabled).entrySet().stream().filter(e -> TIMING_FIELD_NAMES.contains(e.getKey()) && (!(e.getValue() instanceof Boolean) || (Boolean) e.getValue()))
					.map(e -> e.getKey()).collect(Collectors.toSet());
		}
		else if (enabled instanceof String) {
			keep = new HashSet<>();
			keep.add((String) enabled);
		}
		Map<Integer, Function<Integer, Integer>> roundingFunctions = new HashMap<>();
		ScopedMap arguments = (ScopedMap) parameters.get("arguments");
		String name, arg;
		RoundingMode globalRM = arguments.containsKey("rounding-mode") ? (arguments.get("rounding-mode") instanceof String ? RoundingMode.valueOf(((String) arguments.get("rounding-mode")).toUpperCase())
				: RoundingMode.valueOf(((Number) arguments.get("rounding-mode")).intValue())) : RoundingMode.FLOOR;
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++) {
			if (!keep.contains(name = TIMING_FIELD_NAMES.get(i)))
				continue;
			Object value = arguments.containsKey(name) ? arguments.get(name) : null;
			arg = name + "-rounding-mode";
			final RoundingMode rm = arguments.containsKey(arg) ? (arguments.get(arg) instanceof String //TODO try to reduce calls to get here
					? RoundingMode.valueOf(((String) arguments.get(arg)).toUpperCase()) : RoundingMode.valueOf(((Number) arguments.get(arg)).intValue())) : globalRM;
			if (value == null) { //Step-offset
				//TODO implement existence checks and enforce Integer type requirement
				final int step = ((Number) arguments.get(name + "-step")).intValue();
				final int offset = arguments.containsKey(name + "-offset") ? ((Number) arguments.get(name + "-offset")).intValue() : 0;
				roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, step, offset));
			}
			else if (value instanceof ScopedList) { //Explicit buckets
				ScopedList temp = (ScopedList) value;
				int[] buckets = new int[temp.size()];
				for (int b = 0; b < buckets.length; b++)
					buckets[b] = ((Number) temp.get(b)).intValue(); //TODO enforce Integer type requirement
				roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, buckets));
			}
			else if (value instanceof ScopedMap) {
				ScopedMap temp = (ScopedMap) value;
				final RoundingMode orm = temp.containsKey("rounding-mode") ? (temp.get("rounding-mode") instanceof String //TODO try to reduce calls to get here
						? RoundingMode.valueOf(((String) temp.get("rounding-mode")).toUpperCase()) : RoundingMode.valueOf(((Number) temp.get("rounding-mode")).intValue())) : rm;
				if (temp.containsKey("buckets")) {
					ScopedList bucks = (ScopedList) temp.get("buckets"); //TODO enforce array type requirement
					int[] buckets = new int[bucks.size()];
					for (int b = 0; b < buckets.length; b++)
						buckets[b] = ((Number) bucks.get(b)).intValue(); //TODO enforce Integer type requirement
					roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, buckets));
				}
				else {
					//TODO implement existence checks and enforce Integer type requirement
					final int step = ((Number) arguments.get("step")).intValue();
					final int offset = arguments.containsKey("offset") ? ((Number) arguments.get("offset")).intValue() : 0;
					roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, orm, step, offset));
				}
			}
			else if (value instanceof Number) {
				final int step = ((Number) value).intValue();
				final int offset = arguments.containsKey(name + "-offset") ? ((Number) arguments.get(name + "-offset")).intValue() : 0;
				roundingFunctions.put(TIMING_FIELDS.get(i), inp -> Numbers.bucketRounding(inp, rm, step, offset));
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
