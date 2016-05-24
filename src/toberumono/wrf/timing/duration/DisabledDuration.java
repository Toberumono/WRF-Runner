package toberumono.wrf.timing.duration;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DisabledDuration extends Duration {
	private static final Lock lock = new ReentrantLock();
	private static Duration instance = null;
	
	public static Duration getDisabledDurationInstance() {
		if (instance != null) //We don't want to acquire the lock unless we need to.
			return instance;
		try {
			lock.lock();
			if (instance != null)
				return instance;
			return instance = new DisabledDuration();
		}
		finally {
			lock.unlock();
		}
	}
	
	private DisabledDuration() {
		super(null, null);
	}

	@Override
	protected Calendar doApply(Calendar base) {
		return (Calendar) base.clone();
	}

	@Override
	protected void compute() {/* Nothing to do here */}
}
