package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

/**
 * Root interface for {@link Duration Durations}. This is primarily for identification in {@link WRFRunnerComponentFactory}. Most {@link Duration Durations}
 * should extend {@link AbstractDuration}.
 * 
 * @author Toberumono
 */
public interface Duration extends Scope, TimingComponent {
	/**
	 * The name that {@link Logger Loggers} in instances of {@link Duration} should be created from.
	 */
	public static final String LOGGER_NAME = SIMULATION_LOGGER_ROOT + ".Duration";
}
