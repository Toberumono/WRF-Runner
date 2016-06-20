package toberumono.wrf.timing;

import java.util.Calendar;

import toberumono.wrf.Simulation;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopeUtils;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.round.Round;

/**
 * This interface defines the accessor methods used to interact with {@link Timing} Objects.
 * 
 * @author Toberumono
 */
public interface Timing extends Scope {
	
	/**
	 * @return the {@link Calendar} from which {@link #getStart() start} and {@link #getEnd() end} are derived
	 */
	public Calendar getBase();
	
	/**
	 * @return a {@link ScopedMap} generated from the {@link Calendar} returned by {@link #getBase()} via
	 *         {@link ScopeUtils#makeScopeFromCalendar(Calendar, Scope)}
	 */
	@NamedScopeValue("base")
	public default ScopedMap getScopedBase() {
		return ScopeUtils.makeScopeFromCalendar(getBase(), this);
	}
	
	/**
	 * @return the {@link Calendar} denoting the time that the {@link Simulation} will start
	 */
	public Calendar getStart();
	
	/**
	 * @return a {@link ScopedMap} generated from the {@link Calendar} returned by {@link #getStart()} via
	 *         {@link ScopeUtils#makeScopeFromCalendar(Calendar, Scope)}
	 */
	@NamedScopeValue("start")
	public default ScopedMap getScopedStart() {
		return ScopeUtils.makeScopeFromCalendar(getStart(), this);
	}
	
	/**
	 * @return the {@link Calendar} denoting the time that the {@link Simulation} will end
	 */
	public Calendar getEnd();
	
	/**
	 * @return a {@link ScopedMap} generated from the {@link Calendar} returned by {@link #getEnd()} via
	 *         {@link ScopeUtils#makeScopeFromCalendar(Calendar, Scope)}
	 */
	@NamedScopeValue("end")
	public default ScopedMap getScopedEnd() {
		return ScopeUtils.makeScopeFromCalendar(getEnd(), this);
	}
	
	/**
	 * @return the {@link Offset} added to the rounded {@link #getBase() base} to derive {@link #getStart() start}
	 */
	@NamedScopeValue("offset")
	public Offset getOffset();
	
	/**
	 * @return the {@link Round} applied to {@link #getBase() base} to derive {@link #getStart() start}
	 */
	@NamedScopeValue("round")
	public Round getRound();
	
	/**
	 * @return the {@link Duration} of the {@link Simulation}
	 */
	@NamedScopeValue("duration")
	public Duration getDuration();
	
	/**
	 * @return the {@link Clear} of the {@link Simulation}
	 */
	@NamedScopeValue("clear")
	public Clear getClear();
}
