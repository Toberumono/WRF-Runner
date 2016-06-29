package toberumono.wrf.components.parallel;

import java.util.logging.Logger;

import toberumono.wrf.scope.LoggedScopedComponent;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

/**
 * Convenience class to make the extensions and methods needed to implement a new type of {@link Parallel} easier to follow.
 * 
 * @author Toberumono
 */
public abstract class AbstractParallel extends LoggedScopedComponent<Scope> implements Parallel {
	
	/**
	 * Initializes a new instance of {@link AbstractParallel} with a {@link Logger} derived from {@link Parallel#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public AbstractParallel(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_NAME));
	}
}
