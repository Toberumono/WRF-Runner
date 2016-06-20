package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

/**
 * Root interface for {@link Clear Clears}. This is primarily for identification in {@link WRFRunnerComponentFactory}. Most {@link Clear Clears}
 * should extend {@link AbstractClear}.
 * 
 * @author Toberumono
 */
public interface Clear extends Scope, TimingComponent {
	/**
	 * The name that {@link Logger Loggers} in instances of {@link Clear} should be created from.
	 */
	public static final String LOGGER_NAME = SIMULATION_LOGGER_ROOT + ".Clear";
}
