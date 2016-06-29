package toberumono.wrf.timing.duration;

import java.util.Calendar;

import toberumono.namelist.parser.Namelist;
import toberumono.namelist.parser.NamelistSection;
import toberumono.wrf.scope.Scope;

import static toberumono.wrf.SimulationConstants.*;

/**
 * An implementation of {@link Duration} that loads its information from the "time_control" section of {@link Namelist Namelists}.
 * 
 * @author Toberumono
 */
public class NamelistDuration extends AbstractDuration {
	private int[] duration;
	private final NamelistSection timeControl;
	
	/**
	 * Constructs a new instance of {@link NamelistDuration} based on the information in {@code timeControl}.
	 * 
	 * @param timeControl
	 *            the "time_control" section of a {@link Namelist} file as a {@link NamelistSection}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public NamelistDuration(NamelistSection timeControl, Scope parent) {
		super(null, parent);
		this.timeControl = timeControl;
	}
	
	@Override
	protected Calendar doApply(Calendar base) {
		for (int i = 0; i < duration.length; i++)
			base.add(TIMING_FIELD_IDS.get(i), duration[i]);
		return base;
	}
	
	@Override
	protected void compute() {
		duration = new int[TIMING_FIELD_NAMES.size()];
		for (int i = 0; i < duration.length; i++)
			if (timeControl.containsKey("run_" + TIMING_FIELD_NAMES.get(i)))
				duration[i] = ((Number) timeControl.get("run_" + TIMING_FIELD_NAMES.get(i)).get(0).value()).intValue();
	}
}
