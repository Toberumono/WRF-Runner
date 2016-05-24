package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.function.Function;
import java.util.logging.Logger;

import toberumono.json.JSONObject;

public abstract class TimingElement<T extends TimingElement<T>> implements Function<Calendar, Calendar> {
	private final JSONObject parameters;
	private final T parent;
	private final Logger log;
	private boolean computed;
	
	public TimingElement(JSONObject parameters, T parent, Logger log) {
		this.parameters = parameters;
		this.parent = parent;
		this.log = log;
	}
	
	/**
	 * Implementations of this method <i>must not</i> modify the {@link Calendar} passed to it.<br>
	 * {@inheritDoc}
	 * 
	 * @param base
	 *            the {@link Calendar} to modify with the {@link TimingElement}
	 * @return a <i>copy</i> of the provided {@link Calendar} as modified by the {@link TimingElement}
	 */
	@Override
	public final Calendar apply(Calendar base) {
		if (computed)
			return doApply(base);
		synchronized (parameters) {
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
	
	protected JSONObject getParameters() {
		return parameters;
	}
	
	protected T getParent() {
		return parent;
	}
}
