package toberumono.wrf.timing.offset;

import java.util.Calendar;
import java.util.Collection;
import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

/**
 * The default implementation of {@link Offset}.
 * 
 * @author Toberumono
 */
public class StandardOffset extends AbstractOffset {
	private int[] offsets;
	private Boolean wrap;
	
	/**
	 * Initializes a new instance of {@link StandardOffset} with a {@link Logger} derived from {@link Offset#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public StandardOffset(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
		wrap = null;
	}
	
	@Override
	protected void compute() {
		Collection<String> enabled = parseEnabled();
		offsets = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < offsets.length; i++)
			if (enabled.contains(TIMING_FIELD_NAMES.get(i)) && getParameters().containsKey(TIMING_FIELD_NAMES.get(i)))
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
			wrap = getParameters().containsKey("wrap") ? ((Boolean) getParameters().get("wrap")) : ((getParent() instanceof Offset) ? ((Offset) getParent()).doesWrap() : true);
		return wrap;
	}
}
