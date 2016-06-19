package toberumono.wrf.timing.rounding;

import java.util.logging.Logger;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

/**
 * Root class for rounding methods. Used primarily as an identification method in {@link WRFRunnerComponentFactory}.
 * 
 * @author Toberumono
 */
public abstract class Rounding extends TimingComponent {
	
	/**
	 * Constructs a new {@link Rounding} instance.
	 * 
	 * @param parameters
	 *            the parameters that describe the {@link Rounding} instance
	 * @param parent
	 *            the {@link Rounding} instance's parent {@link Scope}
	 */
	public Rounding(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(SIMULATION_LOGGER_ROOT + ".Rounding"));
	}
}
