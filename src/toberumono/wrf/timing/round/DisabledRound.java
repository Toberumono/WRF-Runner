package toberumono.wrf.timing.round;

import java.util.Calendar;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

/**
 * An implementation of {@link Round} that does not perform any actions.
 * 
 * @author Toberumono
 */
public class DisabledRound extends AbstractRound {
	
	/**
	 * Constructs a new instance of an implementation of {@link Round} that does not perform any actions.
	 * 
	 * @param parameters
	 *            the parameters that defined the instance as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public DisabledRound(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		return base;
	}

	@Override
	protected void compute() {/* Nothing to do here */}
}
