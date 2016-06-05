package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.timing.TimingComponent;

public abstract class Clear extends TimingComponent<Clear> {

	public Clear(JSONObject parameters, Clear parent) {
		super(parameters, parent, Logger.getLogger("Clear"));
	}
}