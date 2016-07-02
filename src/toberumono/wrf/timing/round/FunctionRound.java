package toberumono.wrf.timing.round;

import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.structures.sexpressions.ConsCell;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;
import static toberumono.wrf.scope.ScopedFormulaProcessor.*;

/**
 * An implementation of {@link Round} that allows the user to define functions that convert the unrounded timing values to their final "rounded"
 * values.
 * 
 * @author Toberumono
 */
public class FunctionRound extends AbstractRound {
	private ConsCell[] functions;
	
	/**
	 * Initializes a new instance of {@link FunctionRound} described by the given {@code parameters} with a {@link Logger} derived from
	 * {@link Round#LOGGER_NAME} and the given parent {@link Scope}.
	 * 
	 * @param parameters
	 *            the parameters that describe the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public FunctionRound(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
		functions = null;
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		ScopedMap manufacturedScope = new ScopedMap(this);
		for (int i = 0; i < functions.length; i++)
			manufacturedScope.put(TIMING_FIELD_NAMES.get(i), base.get(TIMING_FIELD_IDS.get(i)));
		for (int i = 0; i < functions.length; i++)
			base.set(TIMING_FIELD_IDS.get(i), evaluateToNumber(process(functions[i], manufacturedScope, TIMING_FIELD_NAMES.get(i)).getCar(), TIMING_FIELD_NAMES.get(i)).intValue());
		return base;
	}
	
	@Override
	protected void compute() {
		functions = new ConsCell[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < functions.length; i++)
			functions[i] = preProcess(getParameters().containsKey(TIMING_FIELD_NAMES.get(i)) ? getParameters().get(TIMING_FIELD_NAMES.get(i)).toString() : TIMING_FIELD_NAMES.get(i));
	}
}
