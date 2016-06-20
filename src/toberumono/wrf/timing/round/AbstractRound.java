package toberumono.wrf.timing.round;

import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.AbstractTimingComponent;

/**
 * Convenience class to make the necessary extensions and implementations needed to implement a new type of {@link Round} easier to follow.
 * 
 * @author Toberumono
 */
public abstract class AbstractRound extends AbstractTimingComponent implements Round {
	
	/**
	 * Initializes a new instance of {@link AbstractRound} with a {@link Logger} derived from {@link Round#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public AbstractRound(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_NAME));
	}
}
