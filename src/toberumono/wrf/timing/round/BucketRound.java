package toberumono.wrf.timing.round;

import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import toberumono.utils.general.Numbers;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

/**
 * Implementation of {@link Round} that places values into buckets to round them.
 * 
 * @author Toberumono
 */
public class BucketRound extends AbstractRound {
	private Map<Integer, Function<Integer, Integer>> roundingActions;
	
	/**
	 * Initializes a new instance of {@link BucketRound} described by the given {@code parameters} with a {@link Logger} derived from
	 * {@link Round#LOGGER_NAME} and the given parent {@link Scope}.
	 * 
	 * @param parameters
	 *            the parameters that describe the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public BucketRound(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}
	
	private final Map<Integer, Function<Integer, Integer>> parseRoundingParameters(Map<?, ?> parameters) { //TODO implement input scrubbing and error messages
		Object enabled = parameters.get("enabled"); //enabled could be a List or Map
		Collection<String> keep = TIMING_FIELD_NAMES; //If enabled isn't a List or Map, every timing field will be counted as enabled
		if (enabled instanceof List)
			keep = ((List<?>) enabled).stream().filter(e -> e instanceof String && TIMING_FIELD_NAMES.contains(e)).map(e -> (String) e).collect(Collectors.toSet());
		else if (enabled instanceof Map) {
			keep = ((Map<?, ?>) enabled).entrySet().stream().filter(e -> TIMING_FIELD_NAMES.contains(e.getKey()) && (!(e.getValue() instanceof Boolean) || (Boolean) e.getValue()))
					.map(e -> (String) e.getKey()).collect(Collectors.toSet());
		}
		else if (enabled instanceof String) {
			keep = new HashSet<>();
			keep.add((String) enabled);
		}
		Map<Integer, Function<Integer, Integer>> roundingFunctions = new HashMap<>();
		Map<?, ?> arguments = (Map<?, ?>) parameters.get("arguments");
		String name, arg;
		Object value = arguments.get("rounding-mode");
		RoundingMode globalRM =
				value instanceof String ? RoundingMode.valueOf(((String) value).toUpperCase()) : (value instanceof Number ? RoundingMode.valueOf(((Number) value).intValue()) : RoundingMode.FLOOR);
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++) {
			name = TIMING_FIELD_NAMES.get(i);
			if (!keep.contains(name))
				continue;
			value = arguments.containsKey(name) ? arguments.get(name) : null;
			arg = name + "-rounding-mode";
			final RoundingMode rm = arguments.containsKey(arg) ? (arguments.get(arg) instanceof String //TODO try to reduce calls to get here
					? RoundingMode.valueOf(((String) arguments.get(arg)).toUpperCase()) : RoundingMode.valueOf(((Number) arguments.get(arg)).intValue())) : globalRM;
			if (value == null) { //Step-offset
				String field = name + "-step";
				Object num = arguments.get(field);
				final int step = evaluateToNumber(num, field).intValue();
				
				field = name + "-offset";
				num = arguments.get(field);
				final int offset = evaluateToNumber(num != null ? num : 0, field).intValue();
				roundingFunctions.put(TIMING_FIELD_IDS.get(i), inp -> Numbers.bucketRounding(inp, rm, step, offset));
			}
			else if (value instanceof List) { //Explicit buckets
				List<?> temp = (List<?>) value;
				final int[] buckets = new int[temp.size()];
				for (int b = 0; b < buckets.length; b++)
					buckets[b] = evaluateToNumber(temp.get(b), name + "[" + b + "]").intValue();
				roundingFunctions.put(TIMING_FIELD_IDS.get(i), inp -> Numbers.bucketRounding(inp, rm, buckets));
			}
			else if (value instanceof Map) {
				Map<?, ?> temp = (Map<?, ?>) value;
				final RoundingMode orm = temp.containsKey("rounding-mode") ? (temp.get("rounding-mode") instanceof String //TODO try to reduce calls to get here
						? RoundingMode.valueOf(((String) temp.get("rounding-mode")).toUpperCase()) : RoundingMode.valueOf(((Number) temp.get("rounding-mode")).intValue())) : rm;
				if (temp.containsKey("buckets")) {
					List<?> bucks = evaluateToType(temp.get("buckets"), name + ".buckets", List.class);
					final int[] buckets = new int[bucks.size()];
					for (int b = 0; b < buckets.length; b++)
						buckets[b] = evaluateToNumber(bucks.get(b), name + ".buckets[" + b + "]").intValue();
					roundingFunctions.put(TIMING_FIELD_IDS.get(i), inp -> Numbers.bucketRounding(inp, rm, buckets));
				}
				else {
					final int step = evaluateToNumber(arguments.get("step"), name).intValue();
					final int offset = evaluateToNumber(arguments.containsKey("offset") ? arguments.get("offset") : 0, "offset").intValue();
					roundingFunctions.put(TIMING_FIELD_IDS.get(i), inp -> Numbers.bucketRounding(inp, orm, step, offset));
				}
			}
			else if (value instanceof Number) {
				final int step = ((Number) value).intValue();
				final int offset = evaluateToNumber(arguments.containsKey(name + "-offset") ? arguments.get(name + "-offset") : 0, name + "-offset").intValue();
				roundingFunctions.put(TIMING_FIELD_IDS.get(i), inp -> Numbers.bucketRounding(inp, rm, step, offset));
			}
			else {
				throw new IllegalArgumentException("The value of " + name + " in BucketRound must be either undefined, null, or an instance of List, Map, or Number.");
			}
		}
		return roundingFunctions;
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		for (Entry<Integer, Function<Integer, Integer>> e : roundingActions.entrySet())
			base.set(e.getKey(), e.getValue().apply(base.get(e.getKey())));
		return base;
	}
	
	@Override
	protected void compute() {
		if (getParameters() == null)
			roundingActions = getParent() instanceof BucketRound ? parseRoundingParameters(((BucketRound) getParent()).getParameters()) : new HashMap<>();
		else
			roundingActions = parseRoundingParameters(getParameters());
	}
}
