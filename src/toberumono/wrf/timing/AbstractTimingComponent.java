package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedComponent;
import toberumono.wrf.scope.ScopedMap;

/**
 * Provides a simple common mechanism by which lazy evaluation can implemented for components of {@link Timing}.
 * 
 * @author Toberumono
 */
public abstract class AbstractTimingComponent extends ScopedComponent<Scope> implements TimingComponent {
	private final Logger log;
	private boolean computed;
	
	/**
	 * Constructs a new {@link AbstractTimingComponent}.
	 * 
	 * @param parameters
	 *            the parameters that define the component
	 * @param parent
	 *            the component's parent {@link Scope}
	 * @param log
	 *            the {@link Logger} that the component should use
	 */
	public AbstractTimingComponent(ScopedMap parameters, Scope parent, Logger log) {
		super(parameters, parent);
		this.log = log;
	}
	
	@Override
	public Calendar apply(Calendar base, boolean inPlace) {
		Calendar out = inPlace ? base : (Calendar) base.clone();
		if (computed)
			return doApply(out);
		synchronized (log) {
			if (!computed) {
				compute();
				computed = true;
			}
		}
		return doApply(out);
	}
	
	@Override
	public final Calendar apply(Calendar base) {
		return apply(base, false);
	}

	/**
	 * Implementations of this method <i>must</i> modify the {@link Calendar} passed to it.<br>
	 * 
	 * @param base
	 *            the {@link Calendar} to modify with the {@link AbstractTimingComponent TimingComponent}
	 * @return the provided {@link Calendar} as modified by the {@link AbstractTimingComponent TimingComponent}
	 */
	protected abstract Calendar doApply(Calendar base);
	
	protected abstract void compute();
	
	protected Logger getLogger() {
		return log;
	}
}
