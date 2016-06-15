package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

public abstract class Clear extends TimingComponent<Clear> {

	public Clear(ScopedMap parameters, Clear parent) {
		super(parameters, parent, Logger.getLogger("Clear"));
	}
}
