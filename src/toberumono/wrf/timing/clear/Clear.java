package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.LOGGER_ROOT;

public abstract class Clear extends TimingComponent<Scope> {

	public Clear(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_ROOT + ".Clear"));
	}
}
