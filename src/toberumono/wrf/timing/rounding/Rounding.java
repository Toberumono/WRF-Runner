package toberumono.wrf.timing.rounding;

import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.timing.TimingElement;

public abstract class Rounding extends TimingElement<Rounding> {

	public Rounding(JSONObject parameters, Rounding parent) {
		super(parameters, parent, Logger.getLogger("Rounding"));
	}
}
