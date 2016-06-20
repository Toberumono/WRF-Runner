package toberumono.wrf.timing.clear;

import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.AbstractTimingComponent;

/**
 * Convenience class to make the necessary extensions and implementations needed to implement a new type of {@link Clear} easier to follow.
 * 
 * @author Toberumono
 */
public abstract class AbstractClear extends AbstractTimingComponent implements Clear {
	
	/**
	 * Initializes a new instance of {@link AbstractClear} with a {@link Logger} derived from {@link Clear#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public AbstractClear(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_NAME));
	}
}
