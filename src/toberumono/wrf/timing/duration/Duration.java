package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

public abstract class Duration extends TimingComponent<Duration> {
	
	public Duration(ScopedMap parameters, Duration parent) {
		super(parameters, parent, Logger.getLogger("Duration"));
	}
}
