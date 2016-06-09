package toberumono.wrf.timing.offset;

import java.util.Calendar;

import toberumono.wrf.scope.ScopedConfiguration;

import static toberumono.wrf.SimulationConstants.*;

public class StandardOffset extends Offset {
	private int[] offsets;
	private Boolean wrap;
	
	public StandardOffset(ScopedConfiguration parameters, Offset parent) {
		super(parameters, parent);
		wrap = null;
	}
	
	@Override
	protected void compute() {
		offsets = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < offsets.length; i++)
			if (getParameters().contains(TIMING_FIELD_NAMES.get(i))) //TODO implement inheritance via checking for String values equal to "inherit"
				offsets[i] = ((Number) getParameters().get(TIMING_FIELD_NAMES.get(i))).intValue();
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		if (doesWrap())
			for (int i = 0; i < offsets.length; i++)
				out.add(TIMING_FIELDS.get(i), offsets[i]);
		else
			for (int i = 0; i < offsets.length; i++)
				out.set(TIMING_FIELDS.get(i), out.get(TIMING_FIELDS.get(i)) + offsets[i]);
		return out;
	}
	
	@Override
	public boolean doesWrap() {
		if (wrap == null) //TODO implement inheritance
			wrap = getParameters().contains("wrap") ? ((Boolean) getParameters().get("wrap")) : (getParent() != null ? getParent().doesWrap() : true);
		return wrap;
	}
}
