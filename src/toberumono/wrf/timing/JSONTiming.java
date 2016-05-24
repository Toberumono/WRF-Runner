package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.concurrent.locks.ReentrantLock;

import toberumono.json.JSONObject;
import toberumono.wrf.InheritableItem;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.rounding.Rounding;

public class JSONTiming extends InheritableItem<Timing> implements Timing {
	private final ReentrantLock computationLock;
	private final JSONObject parameters;
	
	private Calendar base, start, end;
	private Offset offset;
	private Rounding rounding;
	private Duration duration;
	
	public JSONTiming(JSONObject parameters, Calendar base) {
		super(null);
		computationLock = new ReentrantLock();
		this.parameters = parameters;
		start = end = null;
		offset = null;
		rounding = null;
		duration = null;
		this.base = base;
	}
	
	public JSONTiming(JSONObject parameters, Timing parent) { //TODO implement existence checks
		super(parent);
		computationLock = new ReentrantLock();
		this.parameters = parameters;
		base = start = end = null;
		offset = null;
		rounding = null;
		duration = null;
	}
	
	@Override
	public Calendar getBase() {
		return base == null ? base = getParent().getBase() : base;
	}
	
	@Override
	public Calendar getStart() {
		if (start != null)
			return start;
		try {
			computationLock.lock();
			if (start == null)
				start = getOffset().apply(getRounding().apply(getBase()));
		}
		finally {
			computationLock.unlock();
		}
		return start;
	}
	
	@Override
	public Calendar getEnd() {
		if (end != null)
			return end;
		try {
			computationLock.lock();
			if (end == null)
				end = getDuration().apply(getStart());
		}
		finally {
			computationLock.unlock();
		}
		return end;
	}
	
	@Override
	public Offset getOffset() {
		if (offset != null)
			return offset;
		try {
			computationLock.lock();
			if (offset == null)
				offset = WRFRunnerComponentFactory.generateComponent(Offset.class, (JSONObject) parameters.get("offset"), getParent() != null ? getParent().getOffset() : null);
		}
		finally {
			computationLock.unlock();
		}
		return offset;
	}
	
	@Override
	public Rounding getRounding() {
		if (rounding != null)
			return rounding;
		try {
			computationLock.lock();
			if (rounding == null)
				rounding = WRFRunnerComponentFactory.generateComponent(Rounding.class, (JSONObject) parameters.get("rounding"), getParent() != null ? getParent().getRounding() : null);
		}
		finally {
			computationLock.unlock();
		}
		return rounding;
	}
	
	@Override
	public Duration getDuration() {
		if (duration != null)
			return duration;
		try {
			computationLock.lock();
			if (duration == null)
				duration = WRFRunnerComponentFactory.generateComponent(Duration.class, (JSONObject) parameters.get("duration"), getParent() != null ? getParent().getDuration() : null);
		}
		finally {
			computationLock.unlock();
		}
		return duration;
	}
}
