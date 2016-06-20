package toberumono.wrf.timing.offset;

import java.util.Calendar;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

public class StandardOffset extends AbstractOffset {
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
		if (doesWrap())
			for (int i = 0; i < offsets.length; i++)
				base.add(TIMING_FIELD_IDS.get(i), offsets[i]);
		else
			for (int i = 0; i < offsets.length; i++)
				base.set(TIMING_FIELD_IDS.get(i), base.get(TIMING_FIELD_IDS.get(i)) + offsets[i]);
		return base;
	}
	
	@Override
	public boolean doesWrap() {
		if (wrap == null)
			wrap = getParameters().containsKey("wrap") ? ((Boolean) getParameters().get("wrap")) : ((getParent() instanceof AbstractOffset) ? ((AbstractOffset) getParent()).doesWrap() : true);
		return wrap;
	}
}
