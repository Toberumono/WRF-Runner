package toberumono.wrf.timing.duration;

import java.util.Calendar;
import java.util.function.Function;
import java.util.logging.Logger;

public abstract class Duration implements Function<Calendar, Calendar> {
	private static final Logger log = Logger.getLogger("Duration");
	
	/**
	 * Implementations of this method <i>must not</i> modify the {@link Calendar} passed to it.
	 * 
	 * @param base
	 *            the {@link Calendar} to which the {@link Duration} will be added
	 * @return a <i>copy</i> of the provided {@link Calendar} with the {@link Duration} added
	 */
	@Override
	public abstract Calendar apply(Calendar base);
	
	protected Logger getLogger() {
		return log;
	}
}
