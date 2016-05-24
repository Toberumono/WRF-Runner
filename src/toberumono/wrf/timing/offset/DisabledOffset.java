package toberumono.wrf.timing.offset;

import java.util.Calendar;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class DisabledOffset extends Offset {
	private static final Lock lock = new ReentrantLock();
	private static Offset instance = null;
	
	public static Offset getDisabledOffsetInstance() {
		if (instance != null) //We don't want to acquire the lock unless we need to.
			return instance;
		try {
			lock.lock();
			if (instance != null)
				return instance;
			return instance = new DisabledOffset();
		}
		finally {
			lock.unlock();
		}
	}
	
	private DisabledOffset() {
		super(null, null);
	}
	
	@Override
	protected void compute() {/* Nothing to do here */};

	@Override
	protected Calendar doApply(Calendar base) {
		return (Calendar) base.clone();
	}

	@Override
	public boolean doesWrap() {
		return false;
	}
}
