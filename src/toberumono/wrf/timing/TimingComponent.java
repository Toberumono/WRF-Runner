package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.function.Function;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.clear.Clear;
import toberumono.wrf.timing.duration.Duration;
import toberumono.wrf.timing.offset.Offset;
import toberumono.wrf.timing.round.Round;

/**
 * Root interface for {@link TimingComponent TimingComponents}. This is for use with the root interfaces of each type of {@link TimingComponent}.
 * 
 * @author Toberumono
 * @see Offset
 * @see Clear
 * @see Duration
 * @see Round
 */
public interface TimingComponent extends Function<Calendar, Calendar>, Scope {
	
	/**
	 * Performs the steps necessary to apply the modifications specified by the {@link TimingComponent} to the given {@link Calendar}.
	 * 
	 * @param base
	 *            the {@link Calendar} to modify with the {@link TimingComponent}
	 * @param inPlace
	 *            whether the modification should be performed in place (if {@code false} then the {@link Calendar} is cloned before being modified)
	 * @return {@code base} (or a clone thereof if {@code inPlace} is {@code false}) with the {@link TimingComponent TimingComponent's} changes
	 *         applied
	 */
	public Calendar apply(Calendar base, boolean inPlace);
	
	/**
	 * Performs the steps necessary to apply the modifications specified by the {@link TimingComponent} to the given {@link Calendar}.<br>
	 * This is equivalent to {@link #apply(Calendar, boolean)} with {@code inPlace} set to {@code false}.
	 * 
	 * @param base
	 *            the {@link Calendar} to modify with the {@link TimingComponent}
	 * @return a clone of {@code base} with the {@link TimingComponent TimingComponent's} changes applied
	 */
	@Override
	public Calendar apply(Calendar base);
}
