package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.wrf.scope.ScopedConfiguration;
import toberumono.wrf.timing.TimingComponent;

public abstract class Clear extends TimingComponent<Clear> {

	public Clear(ScopedConfiguration parameters, Clear parent) {
		super(parameters, parent, Logger.getLogger("Clear"));
	}
}
