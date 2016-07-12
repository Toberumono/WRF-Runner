package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.Objects;

import toberumono.wrf.Simulation;
import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedComponent;
import toberumono.wrf.scope.ScopedList;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.clear.ListClear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.duration.ListDuration;
import toberumono.wrf.timing.offset.ListOffset;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.round.ListRound;
import toberumono.wrf.timing.round.Round;

/**
 * An implementation of {@link Timing} wherein every value is computed.
 * 
 * @author Toberumono
 */
public class ComputedTiming extends ScopedComponent<Scope> implements Timing {
	private volatile Calendar base, start, end;
	private volatile Offset offset;
	private volatile Round round;
	private volatile Duration duration;
	private volatile Clear clear;
	private boolean appliedClear;
	
	/**
	 * Constructs a {@link ComputedTiming} instance without an explicitly defined base {@link Calendar}. This is the general use constructor.
	 * 
	 * @param parameters
	 *            the parameters that define the {@link ComputedTiming} instance as a {@link ScopedMap}
	 * @param parent
	 *            the {@link ComputedTiming} instance's parent {@link Scope}
	 */
	public ComputedTiming(ScopedMap parameters, Scope parent) {
		this(parameters, null, parent);
	}
	
	/**
	 * Constructs a {@link ComputedTiming} instance with an (optionally) explicitly defined base {@link Calendar}. This is generally only used by
	 * {@link Simulation} during the initialization process.
	 * 
	 * @param parameters
	 *            the parameters that define the {@link ComputedTiming} instance as a {@link ScopedMap}
	 * @param base
	 *            the base {@link Calendar} for the {@link ComputedTiming} instance
	 * @param parent
	 *            the {@link ComputedTiming} instance's parent {@link Scope}
	 */
	public ComputedTiming(ScopedMap parameters, Calendar base, Scope parent) {
		super(parameters, parent);
		this.base = parent instanceof Timing ? base : Objects.requireNonNull(base, "The base Calendar cannot be null if the parent is not an instance of Timing");
		start = end = null;
		offset = null;
		round = null;
		duration = null;
		clear = null;
		appliedClear = false;
	}
	
	@Override
	public Calendar getBase() {
		if (base == null) {
			synchronized (this) {
				if (base == null) { //Have to re-check for synchronization
					base = getClear().apply((getParent() instanceof Timing) ? ((Timing) getParent()).getBase() : null);
					appliedClear = true;
				}
			}
		}
		if (!appliedClear) {
			synchronized (this) {
				if (!appliedClear) { //Have to re-check for synchronization
					base = getClear().apply(base);
					appliedClear = true;
				}
			}
		}
		return base;
	}
	
	@Override
	public Calendar getStart() {
		if (start != null)
			return start;
		synchronized (this) {
			if (start == null)
				start = getOffset().apply(getRound().apply(getBase()));
		}
		return start;
	}
	
	@Override
	public Calendar getEnd() {
		if (end != null)
			return end;
		synchronized (this) {
			if (end == null)
				end = getDuration().apply(getStart());
		}
		return end;
	}
	
	@Override
	public Offset getOffset() {
		if (offset != null)
			return offset;
		synchronized (this) {
			if (offset == null) {
				if (getParameters().get("offset") instanceof ScopedList)
					offset = new ListOffset((ScopedList) getParameters().get("offset"), (getParent() instanceof Timing) ? ((Timing) getParent()).getOffset() : this);
				else
					offset = WRFRunnerComponentFactory.generateComponent(Offset.class, (ScopedMap) getParameters().get("offset"),
							(getParent() instanceof Timing) ? ((Timing) getParent()).getOffset() : this);
			}
		}
		return offset;
	}
	
	@Override
	public Round getRound() {
		if (round != null)
			return round;
		synchronized (this) {
			if (round == null) {
				if (getParameters().get("round") instanceof ScopedList)
					round = new ListRound((ScopedList) getParameters().get("round"), (getParent() instanceof Timing) ? ((Timing) getParent()).getRound() : this);
				else
					round = WRFRunnerComponentFactory.generateComponent(Round.class, (ScopedMap) getParameters().get("round"),
							(getParent() instanceof Timing) ? ((Timing) getParent()).getRound() : this);
			}
		}
		return round;
	}
	
	@Override
	public Duration getDuration() {
		if (duration != null)
			return duration;
		synchronized (this) {
			if (duration == null) {
				if (getParameters().get("duration") instanceof ScopedList)
					duration = new ListDuration((ScopedList) getParameters().get("duration"), (getParent() instanceof Timing) ? ((Timing) getParent()).getDuration() : this);
				else
					duration = WRFRunnerComponentFactory.generateComponent(Duration.class, (ScopedMap) getParameters().get("duration"),
							(getParent() instanceof Timing) ? ((Timing) getParent()).getDuration() : this);
			}
		}
		return duration;
	}
	
	@Override
	public Clear getClear() {
		if (clear != null)
			return clear;
		synchronized (this) {
			if (clear == null) {
				if (getParameters().get("clear") instanceof ScopedList)
					clear = new ListClear((ScopedList) getParameters().get("clear"), (getParent() instanceof Timing) ? ((Timing) getParent()).getClear() : this);
				else
					clear = WRFRunnerComponentFactory.generateComponent(Clear.class, (ScopedMap) getParameters().get("clear"),
							(getParent() instanceof Timing) ? ((Timing) getParent()).getClear() : this);
			}
		}
		return clear;
	}
}
