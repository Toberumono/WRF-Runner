package toberumono.wrf.timing.offset;

import java.util.Calendar;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

/**
 * An implementation of {@link Offset} that does not perform any actions.
 * 
 * @author Toberumono
 */
public class DisabledOffset extends AbstractOffset {
	
	/**
	 * Constructs a new instance of an implementation of {@link Offset} that does not perform any actions.
	 * 
	 * @param parameters
	 *            the parameters that defined the instance as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public DisabledOffset(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}
	
	@Override
	protected void compute() {/* Nothing to do here */};

	@Override
	protected Calendar doApply(Calendar base) {
		return base;
	}

	@Override
	public boolean doesWrap() {
		return true;
	}
}
