package toberumono.wrf.timing.round;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DisabledRound extends AbstractRound {
	private static final Lock lock = new ReentrantLock();
	private static Round instance = null;
	
	public static Round getDisabledRoundInstance() {
		if (instance != null) //We don't want to acquire the lock unless we need to.
			return instance;
		try {
			lock.lock();
			if (instance != null)
				return instance;
			return instance = new DisabledRound();
		}
		finally {
			lock.unlock();
		}
	}
	
	private DisabledRound() {
		super(null, null);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		return base;
	}

	@Override
	protected void compute() {/* Nothing to do here */}
}
