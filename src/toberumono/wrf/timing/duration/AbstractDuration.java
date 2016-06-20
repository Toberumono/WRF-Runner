package toberumono.wrf.timing.duration;

import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.AbstractTimingComponent;

/**
 * Convenience class to make the necessary extensions and implementations needed to implement a new type of {@link Duration} easier to follow.
 * 
 * @author Toberumono
 */
public abstract class AbstractDuration extends AbstractTimingComponent implements Duration {
	
	/**
	 * Initializes a new instance of {@link AbstractDuration} with a {@link Logger} derived from {@link Duration#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public AbstractDuration(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_NAME));
	}
}
