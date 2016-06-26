package toberumono.wrf.scope;

import toberumono.wrf.Simulation;

/**
 * A component of the {@link Simulation} that has a {@link Scope}
 * 
 * @author Toberumono
 * @param <T>
 *            the type of the parent {@link Scope}
 */
public class ScopedComponent<T extends Scope> extends AbstractScope<T> {
	private final ScopedMap parameters;
	
	/**
	 * Initializes a new {@link ScopedComponent}.
	 * 
	 * @param parameters
	 *            the parameters that define the {@link ScopedComponent component} as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ScopedComponent(ScopedMap parameters, T parent) {
		super(parent);
		this.parameters = parameters;
	}
	
	/**
	 * @return the {@link ScopedMap} containing the parameters that define the {@link ScopedComponent component}
	 */
	@NamedScopeValue("parameters")
	public ScopedMap getParameters() {
		return parameters;
	}
	
	@Override
	public boolean hasValueByName(String name) {
		return super.hasValueByName(name) || (getParameters() != null && getParameters().containsKey(name));
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		try {
			return super.getValueByName(name);
		}
		catch (InvalidVariableAccessException e) {
			if (getParameters() != null && getParameters().containsKey(name))
				return getParameters().get(name);
			throw e;
		}
	}
}
