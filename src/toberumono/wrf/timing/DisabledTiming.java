package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.round.Round;

/**
 * This is a dummy class used solely for compatibility with {@link WRFRunnerComponentFactory}. It should not be constructed
 * in any other context.
 * 
 * @author Toberumono
 */
public class DisabledTiming extends AbstractScope<Timing> implements Timing {
	private static final Lock lock = new ReentrantLock();
	private static Timing instance = null;
	
	public static Timing getDisabledTimingInstance() {
		if (instance != null) //We don't want to acquire the lock unless we need to.
			return instance;
		try {
			lock.lock();
			if (instance != null)
				return instance;
			return instance = new DisabledTiming();
		}
		finally {
			lock.unlock();
		}
	}
	
	private DisabledTiming() {
		super(null);
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
