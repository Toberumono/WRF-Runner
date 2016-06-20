package toberumono.wrf.timing.round;

import java.util.logging.Logger;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

/**
 * Root interface for {@link Round Rounds}. This is primarily for identification in {@link WRFRunnerComponentFactory}. Most {@link Round Rounds}
 * should extend {@link AbstractRound}.
 * 
 * @author Toberumono
 */
public interface Round extends Scope, TimingComponent {
	/**
	 * The name that {@link Logger Loggers} in instances of {@link Round} should be created from.
	 */
	public static final String LOGGER_NAME = SIMULATION_LOGGER_ROOT + ".Round";
}
