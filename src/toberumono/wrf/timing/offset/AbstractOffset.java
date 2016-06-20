package toberumono.wrf.timing.offset;

import java.util.logging.Logger;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.AbstractTimingComponent;

/**
 * Convenience class to make the necessary extensions and implementations needed to implement a new type of {@link Offset} easier to follow.
 * 
 * @author Toberumono
 */
public abstract class AbstractOffset extends AbstractTimingComponent implements Offset {
	
	/**
	 * Initializes a new instance of {@link AbstractOffset} with a {@link Logger} derived from {@link Offset#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the implementation as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public AbstractOffset(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_NAME));
	}
}
