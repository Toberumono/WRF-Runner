package toberumono.wrf.timing.rounding;

import java.util.Calendar;
import java.util.logging.Level;

import toberumono.json.JSONObject;
import toberumono.utils.general.Numbers;

import static toberumono.wrf.SimulationConstants.*;

public class FractionalRounding extends Rounding {
	private final int roundingPoint;
	private final double fraction;
	private final String diff;
	
	public FractionalRounding(JSONObject parameters, Rounding parent) { //TODO implement inheritance, existence checks
		String magnitude = ((String) parameters.get("magnitude").value()).toLowerCase();
		diff = ((String) parameters.get("diff").value()).toLowerCase(); //TODO require diff to be either next, previous, or none
		double fraction = ((Number) parameters.get("fraction").value()).doubleValue();
		roundingPoint = TIMING_FIELD_NAMES.indexOf(magnitude);
		if (roundingPoint == -1)
			throw new IllegalArgumentException(magnitude + " is not a valid timing field name.");
		if (fraction <= 0.0 || fraction > 1.0) {
			getLogger().log(Level.WARNING, "The fraction field in timing.rounding is outside of its valid range of (0.0, 1.0].  Assuming 1.0.");
			this.fraction = 1.0;
		}
		else
			this.fraction = fraction;
	}
	
	private void applyDiff(Calendar cal) {
		if (diff.equals("next"))
			cal.add(TIMING_FIELDS.get(roundingPoint), 1);
		else if (diff.equals("previous"))
			cal.add(TIMING_FIELDS.get(roundingPoint), -1);
	}

	@Override
	public Calendar apply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		//First, we handle the diff on the field that the user is rounding on
		int rp = roundingPoint;
		if (diff.equals("next"))
			out.add(TIMING_FIELDS.get(rp), 1);
		else if (diff.equals("previous"))
			out.add(TIMING_FIELDS.get(rp), -1);
		if (fraction < 1.0) { //If they want to keep some portion of the field before this, then fraction will be less than 1.0.
			--rp;
			int offset = 1 - out.getActualMinimum(TIMING_FIELDS.get(rp));
			out.set(TIMING_FIELDS.get(rp), (int) Numbers.semifloor(out.getActualMaximum(TIMING_FIELDS.get(rp)) + offset, fraction, out.get(TIMING_FIELDS.get(rp))));
		}
		for (int i = 0; i < rp; i++) //The logic here is that if we are rounding to something, then we want to set everything before it to 0.
			out.set(TIMING_FIELDS.get(i), out.getActualMinimum(TIMING_FIELDS.get(i)));
		return out;
	}
	
}
