package toberumono.wrf.timing.rounding;

import java.util.Calendar;
import java.util.function.Function;
import java.util.logging.Logger;

public abstract class Rounding implements Function<Calendar, Calendar> {
	private static final Logger log = Logger.getLogger("Rounding");
	
	/**
	 * Implementations of this method <i>must not</i> modify the {@link Calendar} passed to it.
	 * 
	 * @param base
	 *            the {@link Calendar} to which the {@link Rounding} will be applied
	 * @return a <i>copy</i> of the provided {@link Calendar} with the {@link Rounding} applied
	 */
	@Override
	public abstract Calendar apply(Calendar base);
	
	protected Logger getLogger() {
		return log;
	}
}
