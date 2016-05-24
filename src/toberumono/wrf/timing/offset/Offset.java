package toberumono.wrf.timing.offset;

import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.json.JSONObject;
import toberumono.wrf.timing.TimingElement;

public abstract class Offset extends TimingElement<Offset> {
	
	public Offset(JSONObject parameters, Offset parent) {
		super(parameters, parent, Logger.getLogger("Offset"));
	}
	
	/**
	 * @return {@code true} iff offsets should wrap according to the {@link Calendar Calendar's} model
	 */
	public abstract boolean doesWrap();
}
