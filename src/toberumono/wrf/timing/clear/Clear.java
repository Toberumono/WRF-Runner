package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.LOGGER_ROOT;

public abstract class Clear extends TimingComponent<Clear> {

	public Clear(ScopedMap parameters, Clear parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_ROOT + ".Clear"));
	}
}
