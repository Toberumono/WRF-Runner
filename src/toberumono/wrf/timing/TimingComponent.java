package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.function.Function;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.InheritableItem;

public abstract class TimingComponent<T extends TimingComponent<T>> extends InheritableItem<T> implements Function<Calendar, Calendar> {
	private final JSONObject parameters;
	private final Logger log;
	private boolean computed;
	
	public TimingComponent(JSONObject parameters, T parent, Logger log) {
		super(parent);
		this.parameters = parameters;
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
	
	protected JSONObject getParameters() {
		return parameters;
	}
}
