package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.wrf.scope.ScopedConfiguration;
import toberumono.wrf.timing.TimingComponent;

public abstract class Duration extends TimingComponent<Duration> {
	
	public Duration(ScopedConfiguration parameters, Duration parent) {
		super(parameters, parent, Logger.getLogger("Duration"));
	}
}
