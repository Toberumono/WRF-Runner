package toberumono.wrf.timing.rounding;

import java.util.Calendar;
import java.util.logging.Level;

import toberumono.utils.general.Numbers;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

public class FractionalRounding extends Rounding {
	private int roundingPoint;
	private double fraction;
	private String diff;
	
	public FractionalRounding(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}

	@Override
	protected Calendar doApply(Calendar base) {
		Calendar out = (Calendar) base.clone();
		//First, we handle the diff on the field that the user is rounding on
		int rp = roundingPoint;
		if (diff.equals("next"))
			out.add(TIMING_FIELD_IDS.get(rp), 1);
		else if (diff.equals("previous"))
			out.add(TIMING_FIELD_IDS.get(rp), -1);
		if (fraction < 1.0) { //If they want to keep some portion of the field before this, then fraction will be less than 1.0.
			--rp;
			int offset = 1 - out.getActualMinimum(TIMING_FIELD_IDS.get(rp));
			out.set(TIMING_FIELD_IDS.get(rp), (int) Numbers.semifloor(out.getActualMaximum(TIMING_FIELD_IDS.get(rp)) + offset, fraction, out.get(TIMING_FIELD_IDS.get(rp))));
		}
		for (int i = 0; i < rp; i++) //The logic here is that if we are rounding to something, then we want to set everything before it to 0.
			out.set(TIMING_FIELD_IDS.get(i), out.getActualMinimum(TIMING_FIELD_IDS.get(i)));
		return out;
	}
	
	@Override
	protected void compute() { //TODO implement inheritance, existence checks
		String magnitude = ((String) getParameters().get("magnitude")).toLowerCase();
		diff = getParameters().containsKey("diff") ? ((String) getParameters().get("diff")).toLowerCase() : "none"; //TODO require diff to be either next, previous, or none
		double fraction = ((Number) getParameters().get("fraction")).doubleValue();
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
}
