package toberumono.wrf.timing.round;

import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.logging.Logger;

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
	
	private final Map<Integer, Function<Integer, Integer>> parseRoundingParameters(Map<?, ?> parameters) {
		Collection<String> keep = parseEnabled();
		Map<Integer, Function<Integer, Integer>> roundingFunctions = new HashMap<>(); //We use a map because the key is the TIMING_FIELD_ID
		Map<?, ?> arguments = parameters.containsKey("arguments") ? (Map<?, ?>) parameters.get("arguments") : parameters; //Having the arguments sub-object was pointless
		String name;
		Object value;
		RoundingMode globalRM = roundingModeFromField(arguments.get("rounding-mode"), RoundingMode.FLOOR);
		for (int i = 0; i < TIMING_FIELD_NAMES.size(); i++) {
			name = TIMING_FIELD_NAMES.get(i);
			if (!keep.contains(name))
				continue;
			value = arguments.get(name); //If arguments does not contain name, arguments.get(name) returns null
			final RoundingMode rm = roundingModeFromField(arguments.get(name + "-rounding-mode"), globalRM);
			if (value == null && arguments.containsKey(name + "-step")) { //Step-offset
				String field = name + "-step";
				final int step = evaluateToNumber(arguments.get(field), field).intValue();
				
				field = name + "-offset";
				final int offset = evaluateToNumber(arguments.containsKey(field) ? arguments.get(field) : 0, field).intValue();
				roundingFunctions.put(TIMING_FIELD_IDS.get(i), stepOffsetProcessor(rm, step, offset));
			}
			else if (value instanceof List) { //Explicit buckets
				roundingFunctions.put(TIMING_FIELD_IDS.get(i), explicitBucketsProcessor(rm, name, (List<?>) value));
			}
			else if (value instanceof Map) {
				Map<?, ?> temp = (Map<?, ?>) value;
				final RoundingMode orm = roundingModeFromField(temp.get("rounding-mode"), rm);
				if (temp.containsKey("buckets"))
					roundingFunctions.put(TIMING_FIELD_IDS.get(i), explicitBucketsProcessor(orm, name + ".buckets", evaluateToType(temp.get("buckets"), name + ".buckets", List.class)));
				else
					roundingFunctions.put(TIMING_FIELD_IDS.get(i), stepOffsetProcessor(orm, evaluateToNumber(temp.get("step"), name + ".step").intValue(),
							evaluateToNumber(temp.containsKey("offset") ? arguments.get("offset") : 0, name + ".offset").intValue()));
			}
			else if (value instanceof Number) { //Step-offset without the -step tag
				String field = name + "-offset";
				roundingFunctions.put(TIMING_FIELD_IDS.get(i),
						stepOffsetProcessor(rm, ((Number) value).intValue(), evaluateToNumber(arguments.containsKey(field) ? arguments.get(field) : 0, field).intValue()));
			}
			else if (value != null) {
				throw new IllegalArgumentException("The value of " + name + " in BucketRound must be either undefined, null, or an instance of List, Map, or Number.");
			}
		}
		return roundingFunctions;
	}
	
	private Function<Integer, Integer> stepOffsetProcessor(RoundingMode rm, int step, int offset) {
		return inp -> Numbers.bucketRounding(inp, rm, step, offset);
	}
	
	private Function<Integer, Integer> explicitBucketsProcessor(RoundingMode rm, String name, List<?> buckets) {
		final int[] bucketsArr = new int[buckets.size()];
		for (int b = 0; b < bucketsArr.length; b++)
			bucketsArr[b] = evaluateToNumber(buckets.get(b), name + "[" + b + "]").intValue();
		return inp -> Numbers.bucketRounding(inp, rm, bucketsArr);
	}
	
	private RoundingMode roundingModeFromField(Object value, RoundingMode defaultRM) {
		if (value == null)
			return defaultRM;
		if (value instanceof String) {
			try {
				return RoundingMode.valueOf(((String) value).toUpperCase());
			}
			catch (IllegalArgumentException e) {/*Nothing to do here - we're just trying to get the Enum value from the String.*/}
		}
		return RoundingMode.valueOf(evaluateToNumber(value, "rounding-mode").intValue());
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
