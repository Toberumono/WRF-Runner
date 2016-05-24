package toberumono.wrf.timing.rounding;

import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.timing.TimingComponent;

public abstract class Rounding extends TimingComponent<Rounding> {

	public Rounding(JSONObject parameters, Rounding parent) {
		super(parameters, parent, Logger.getLogger("Rounding"));
	}
}
