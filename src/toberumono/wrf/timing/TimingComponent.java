package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.function.Function;
import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

/**
 * Provides a simple common mechanism by which lazy evaluation can implemented for components of {@link Timing}.
 * 
 * @author Toberumono
 * @param <T>
 *            the parent {@link Scope Scope's} type
 */
public abstract class TimingComponent<T extends Scope> extends TimingScope<T> implements Function<Calendar, Calendar> {
	private final Logger log;
	private boolean computed;
	
	/**
	 * Constructs a new {@link TimingComponent}.
	 * 
	 * @param parameters
	 *            the parameters that define the component
	 * @param parent
	 *            the component's parent {@link Scope}
	 * @param log
	 *            the {@link Logger} that the component should use
	 */
	public TimingComponent(ScopedMap parameters, T parent, Logger log) {
		super(parameters, parent);
		this.log = log;
	}
	
	/**
	 * Implementations of this method <i>must not</i> modify the {@link Calendar} passed to it.<br>
	 * {@inheritDoc}
	 * 
	 * @param base
	 *            the {@link Calendar} to modify with the {@link TimingComponent}
	 * @return a <i>copy</i> of the provided {@link Calendar} as modified by the {@link TimingComponent}
	 */
	@Override
	public final Calendar apply(Calendar base) {
		if (computed)
			return doApply(base);
		synchronized (log) {
			if (!computed) {
				compute();
				computed = true;
			}
		}
		return doApply(base);
	}
	
	protected abstract Calendar doApply(Calendar base);
	
	protected abstract void compute();
	
	protected Logger getLogger() {
		return log;
	}
}
