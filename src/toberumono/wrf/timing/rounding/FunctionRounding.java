package toberumono.wrf.timing.rounding;

import java.util.Calendar;

import toberumono.structures.sexpressions.ConsCell;
import toberumono.wrf.scope.ScopedFormulaProcessor;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

public class FunctionRounding extends Rounding {
	private ConsCell[] functions;
	
	public FunctionRounding(ScopedMap parameters, Rounding parent) {
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
			out.set(TIMING_FIELDS.get(i), ((Number) ScopedFormulaProcessor.process(functions[i], manufacturedScope, TIMING_FIELD_NAMES.get(i)).getCar()).intValue());
		return out;
	}
	
	@Override
	protected void compute() {
		functions = new ConsCell[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < functions.length; i++) {
			functions[i] =
					ScopedFormulaProcessor.preProcess(getParameters().containsKey(TIMING_FIELD_NAMES.get(i)) ? (String) getParameters().get(TIMING_FIELD_NAMES.get(i)) : TIMING_FIELD_NAMES.get(i));
		}
	}
}
