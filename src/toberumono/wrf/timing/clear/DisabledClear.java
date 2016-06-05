package toberumono.wrf.timing.clear;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DisabledClear extends Clear {
	private static final Lock lock = new ReentrantLock();
	private static Clear instance = null;
	
	public static Clear getDisabledClearInstance() {
		if (instance != null) //We don't want to acquire the lock unless we need to.
			return instance;
		try {
			lock.lock();
			if (instance != null)
				return instance;
			return instance = new DisabledClear();
		}
		finally {
			lock.unlock();
		}
	}
	
	private DisabledClear() {
		super(null, null);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		return (Calendar) base.clone();
	}

	@Override
	protected void compute() {/* Nothing to do here */}
}
