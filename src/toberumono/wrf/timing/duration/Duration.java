package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.timing.TimingElement;

public abstract class Duration extends TimingElement<Duration> {
	
	public Duration(JSONObject parameters, Duration parent) {
		super(parameters, parent, Logger.getLogger("Duration"));
	}
}
