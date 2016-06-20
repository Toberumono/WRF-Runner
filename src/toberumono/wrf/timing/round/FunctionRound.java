package toberumono.wrf.timing.round;

import java.util.Calendar;

import toberumono.structures.sexpressions.ConsCell;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;
import static toberumono.wrf.scope.ScopedFormulaProcessor.*;

public class FunctionRound extends AbstractRound {
	private ConsCell[] functions;
	
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
			base.set(TIMING_FIELD_IDS.get(i), ((Number) process(functions[i], manufacturedScope, TIMING_FIELD_NAMES.get(i)).getCar()).intValue());
		return base;
	}
	
	@Override
	protected void compute() {
		functions = new ConsCell[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < functions.length; i++)
			functions[i] = preProcess(getParameters().containsKey(TIMING_FIELD_NAMES.get(i)) ? getParameters().get(TIMING_FIELD_NAMES.get(i)).toString() : TIMING_FIELD_NAMES.get(i));
	}
}
