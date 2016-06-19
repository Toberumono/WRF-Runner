package toberumono.wrf.timing.offset;

import java.util.Calendar;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

public class StandardOffset extends Offset {
	private int[] offsets;
	private Boolean wrap;
	
	public StandardOffset(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
		wrap = null;
	}
	
	@Override
	protected void compute() {
		offsets = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < offsets.length; i++)
			if (getParameters().containsKey(TIMING_FIELD_NAMES.get(i)))
				offsets[i] = ((Number) getParameters().get(TIMING_FIELD_NAMES.get(i))).intValue();
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		if (doesWrap())
			for (int i = 0; i < offsets.length; i++)
				out.add(TIMING_FIELD_IDS.get(i), offsets[i]);
		else
			for (int i = 0; i < offsets.length; i++)
				out.set(TIMING_FIELD_IDS.get(i), out.get(TIMING_FIELD_IDS.get(i)) + offsets[i]);
		return out;
	}
	
	@Override
	public boolean doesWrap() {
		if (wrap == null)
			wrap = getParameters().containsKey("wrap") ? ((Boolean) getParameters().get("wrap")) : ((getParent() instanceof Offset) ? ((Offset) getParent()).doesWrap() : true);
		return wrap;
	}
}
