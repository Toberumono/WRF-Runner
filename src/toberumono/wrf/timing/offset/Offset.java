package toberumono.wrf.timing.offset;

import java.util.Calendar;
import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.TimingComponent;

import static toberumono.wrf.SimulationConstants.SIMULATION_LOGGER_ROOT;

public abstract class Offset extends TimingComponent {
	
	public Offset(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(SIMULATION_LOGGER_ROOT + ".Offset"));
	}
	
	/**
	 * @return {@code true} iff offsets should wrap according to the {@link Calendar Calendar's} model
	 */
	public abstract boolean doesWrap();
}
