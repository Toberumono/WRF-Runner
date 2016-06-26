package toberumono.wrf.scope;

import java.util.logging.Logger;

/**
 * An extension of {@link ScopedComponent} for components that use a {@link Logger}.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of the parent {@link Scope}
 */
public class LoggedScopedComponent<T extends Scope> extends ScopedComponent<T> {
	private final Logger logger;
	
	/**
	 * Initializes a new {@link LoggedScopedComponent}.
	 * 
	 * @param parameters
	 *            the parameters that define the {@link LoggedScopedComponent component} as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 * @param logger
	 *            the {@link Logger} assigned to the {@link LoggedScopedComponent component}
	 */
	public LoggedScopedComponent(ScopedMap parameters, T parent, Logger logger) {
		super(parameters, parent);
		this.logger = logger;
	}
	
	/**
	 * @return the {@link Logger} assigned to the {@link LoggedScopedComponent component}
	 */
	public Logger getLogger() {
		return logger;
	}
}
