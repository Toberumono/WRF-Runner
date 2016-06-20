package toberumono.wrf.timing.offset;

import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

/**
 * Root interface for {@link Offset Offsets}. This is primarily for identification in {@link WRFRunnerComponentFactory}. Most {@link Offset Offsets}
 * should extend {@link AbstractOffset}.
 * 
 * @author Toberumono
 */
public interface Offset extends Scope, TimingComponent {
	/**
	 * The name that {@link Logger Loggers} in instances of {@link Offset} should be created from.
	 */
	public static final String LOGGER_NAME = SIMULATION_LOGGER_ROOT + ".Offset";
	
	/**
	 * @return {@code true} iff offsets should wrap according to the {@link Calendar Calendar's} model
	 */
	@NamedScopeValue("does-wrap")
	public boolean doesWrap();
	
	/**
	 * Performs the steps necessary to apply the modifications specified by the {@link Offset} to the given {@link Calendar}.
	 * 
	 * @param base
	 *            the {@link Calendar} to modify with the {@link Offset}
	 * @param inPlace
	 *            whether the modification should be performed in place (if {@code false} then the {@link Calendar} is cloned before being modified)
	 * @return {@code base} (or a copy thereof if {@code inPlace} is {@code false}) with the {@link Offset} applied
	 */
	@Override
	public Calendar apply(Calendar base, boolean inPlace);
}
