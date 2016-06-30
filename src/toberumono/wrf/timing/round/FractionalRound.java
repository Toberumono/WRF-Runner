package toberumono.wrf.timing.round;

import java.util.Calendar;
import java.util.logging.Level;
import java.util.logging.Logger;

import toberumono.utils.general.Numbers;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedFormulaProcessor;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.*;

/**
 * An implementation of {@link Round} that provides support for the fraction rounding from version 3.x.x. If use of the fraction rounding
 * functionality in a configuration file that is being upgraded is detected, this will automatically be used.
 * 
 * @author Toberumono
 */
public class FractionalRound extends AbstractRound {
	private int roundingPoint;
	private double fraction;
	private String diff;
	
	/**
	 * Initializes a new instance of {@link FractionalRound} based on the given {@code parameters} with a {@link Logger} derived from
	 * {@link Round#LOGGER_NAME} and the given parent {@link Scope}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public FractionalRound(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		//First, we handle the diff on the field that the user is rounding on
		int rp = roundingPoint;
		if (diff.equals("next"))
			base.add(TIMING_FIELD_IDS.get(rp), 1);
		else if (diff.equals("previous"))
			base.add(TIMING_FIELD_IDS.get(rp), -1);
		if (fraction < 1.0) { //If the fraction is less than 1.0, then some portion of the field after the one specified in magnitude should be kept.
			--rp;
			int offset = 1 - base.getActualMinimum(TIMING_FIELD_IDS.get(rp));
			base.set(TIMING_FIELD_IDS.get(rp), (int) Numbers.semifloor(base.getActualMaximum(TIMING_FIELD_IDS.get(rp)) + offset, fraction, base.get(TIMING_FIELD_IDS.get(rp))));
		}
		for (int i = 0; i < rp; i++) //The logic here is that if we are rounding to something, then we want to set everything before it to 0.
			base.set(TIMING_FIELD_IDS.get(i), base.getActualMinimum(TIMING_FIELD_IDS.get(i)));
		return base;
	}
	
	@Override
	protected void compute() {
		String diff = getParameters().get("diff") instanceof String ? ((String) getParameters().get("diff")).toLowerCase() : "none";
		if (!diff.equals("next") && !diff.equals("previous") && !diff.equals("none") && !diff.equals("prev"))
			diff = (String) ScopedFormulaProcessor.process(diff, this, "diff");
		if (diff.equals("prev")) //This allows previous to have a 4-character variant
			diff = "previous";
		if (!diff.equals("next") && !diff.equals("previous") && !diff.equals("none"))
			throw new IllegalArgumentException("The diff field in fractional rounding must equal, or evaluate to, \"next\", \"previous\", or \"none\". \"" + diff.toString() + "\" is not a valid value.");
		this.diff = diff;
		
		Object frac = getParameters().containsKey("fraction") ? getParameters().get("fraction") : (Double) 1.0;
		if (frac instanceof String)
			frac = ScopedFormulaProcessor.process((String) frac, this, "fraction");
		if (!(frac instanceof Number))
			throw new IllegalArgumentException("The fraction field in fractional rounding must be, or evaluate to, an instance of Number if it exists.");
		double fraction = ((Number) frac).doubleValue();
		if (fraction <= 0.0 || fraction > 1.0) {
			getLogger().log(Level.WARNING, "The fraction field in round is outside of its valid range of (0.0, 1.0].  Assuming 1.0.");
			this.fraction = 1.0;
		}
		else {
			this.fraction = fraction;
		}
		
		Object mag = getParameters().get("magnitude");
		if (!(mag instanceof String))
			throw new IllegalArgumentException("The magnitude field in fractional rounding must exist and be, or evaluate to, a String.");
		String magnitude = ((String) mag).toLowerCase();
		int roundingPoint = TIMING_FIELD_NAMES.indexOf(magnitude);
		if (roundingPoint == -1) //If it is invalid, we can try inheritance
			mag = ScopedFormulaProcessor.process((String) mag, this, "magnitude");
		if (!(mag instanceof String))
			throw new IllegalArgumentException("The magnitude field in fractional rounding must exist and be, or evaluate to, a String.");
		magnitude = ((String) mag).toLowerCase();
		if (roundingPoint == -1)
			throw new IllegalArgumentException(magnitude + " is not a valid timing field name.");
		this.roundingPoint = roundingPoint;
	}
}
