package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.LOGGER_ROOT;

public abstract class Duration extends TimingComponent<Duration> {
	
	public Duration(ScopedMap parameters, Duration parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_ROOT + ".Duration"));
	}
}
