package toberumono.wrf.timing.offset;

import java.util.Calendar;
import java.util.function.Function;
import java.util.logging.Logger;

public abstract class Offset implements Function<Calendar, Calendar> {
	private static final Logger log = Logger.getLogger("Offset");
	
	/**
	 * Implementations of this method <i>must not</i> modify the {@link Calendar} passed to it.
	 * 
	 * @param base
	 *            the {@link Calendar} to which the {@link Offset} will be added
	 * @return a <i>copy</i> of the provided {@link Calendar} with the {@link Offset} added
	 */
	@Override
	public abstract Calendar apply(Calendar base);
	
	/**
	 * @return {@code true} iff offsets should wrap according to the {@link Calendar Calendar's} model
	 */
	public abstract boolean doesWrap();
	
	protected Logger getLogger() {
		return log;
	}
}
