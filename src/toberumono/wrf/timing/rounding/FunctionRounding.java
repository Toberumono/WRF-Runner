package toberumono.wrf.timing.rounding;

import java.util.Calendar;

import toberumono.structures.sexpressions.ConsCell;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;
import static toberumono.wrf.scope.ScopedFormulaProcessor.*;

public class FunctionRounding extends Rounding {
	private ConsCell[] functions;
	
	public FunctionRounding(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
		functions = null;
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		ScopedMap manufacturedScope = new ScopedMap(this);
		for (int i = 0; i < functions.length; i++)
			manufacturedScope.put(TIMING_FIELD_NAMES.get(i), out.get(TIMING_FIELDS.get(i)));
		for (int i = 0; i < functions.length; i++)
			out.set(TIMING_FIELDS.get(i), ((Number) process(functions[i], manufacturedScope, TIMING_FIELD_NAMES.get(i)).getCar()).intValue());
		return out;
	}
	
	@Override
	protected void compute() {
		functions = new ConsCell[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < functions.length; i++)
			functions[i] = preProcess(getParameters().containsKey(TIMING_FIELD_NAMES.get(i)) ? getParameters().get(TIMING_FIELD_NAMES.get(i)).toString() : TIMING_FIELD_NAMES.get(i));
	}
}
