package toberumono.wrf.timing;

import java.util.Calendar;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.round.Round;

/**
 * This is a dummy class used solely for compatibility with {@link WRFRunnerComponentFactory}. It should not be constructed in any context.
 * 
 * @author Toberumono
 */
public class DisabledTiming extends AbstractScope<Scope> implements Timing {
	
	/**
	 * This constructor should <i>not</i> be called - it will just throw an {@link UnsupportedOperationException}
	 * 
	 * @param parameters
	 *            ignored - here for compatibility only
	 * @param parent
	 *            ignored - here for compatibility only
	 * @throws UnsupportedOperationException
	 *             always - if this constructor has been called, something went wrong.
	 */
	public DisabledTiming(ScopedMap parameters, Scope parent) throws UnsupportedOperationException {
		super(parent);
		throw new UnsupportedOperationException("Cannot have a DisabledTiming Object");
	}
	
	@Override
	public Calendar getBase() {
		return null;
	}
	
	@Override
	public Calendar getStart() {
		return null;
	}
	
	@Override
	public Calendar getEnd() {
		return null;
	}
	
	@Override
	public Offset getOffset() {
		return null;
	}
	
	@Override
	public Round getRound() {
		return null;
	}
	
	@Override
	public Duration getDuration() {
		return null;
	}
	
	@Override
	public Clear getClear() {
		return null;
	}
}
