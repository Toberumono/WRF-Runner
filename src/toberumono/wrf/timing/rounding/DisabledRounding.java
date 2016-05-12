package toberumono.wrf.timing.rounding;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DisabledRounding extends Rounding {
	private static final Lock lock = new ReentrantLock();
	private static Rounding instance = null;
	
	public static Rounding getDisabledRoundingInstance() {
		if (instance != null) //We don't want to acquire the lock unless we need to.
			return instance;
		try {
			lock.lock();
			if (instance != null)
				return instance;
			return instance = new DisabledRounding();
		}
		finally {
			lock.unlock();
		}
	}
	
	private DisabledRounding() {/* This is a singleton */}
	
	@Override
	public Calendar apply(Calendar base) {
		return (Calendar) base.clone();
	}
}
