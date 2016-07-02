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
	
	/**
	 * Takes the given {@code value} and attempts to produce an instance of {@link Number} from it. If it cannot, the given {@code field} name is used
	 * in the error message.<br>
	 * If {@code value} is an instance of {@link String}, it is evaluated via {@link ScopedFormulaProcessor#process(String, Scope, String)} where
	 * {@code field} is used as the fieldName and the instance of {@link ScopedComponent} upon which the method was called is used as the {@link Scope}.
	 * 
	 * @param value
	 *            the value to attempt to evaluate to an instance of {@link Number}
	 * @param field
	 *            the name of the field
	 * @return an instance of {@link Number} produced from {@code value} if possible
	 * @throws IllegalArgumentException
	 *             if an instance of {@link Number} cannot be produced from {@code value}
	 */
	protected Number evaluateToNumber(Object value, String field) {
		if (value instanceof String)
			value = ScopedFormulaProcessor.process((String) value, this, field);
		if (!(value instanceof Number))
			throw new IllegalArgumentException("The value of " + field + " for " + getClass().getSimpleName() + " must be or evaluate to a Number");
		return (Number) value;
	}
	
	/**
	 * Takes the given {@code value} and attempts to produce an instance of {@code T} from it. If it cannot, the given {@code field} name is used in
	 * the error message.<br>
	 * If {@code value} is an instance of {@link String}, it is evaluated via {@link ScopedFormulaProcessor#process(String, Scope, String)} where
	 * {@code field} is used as the fieldName and the instance of {@link ScopedComponent} upon which the method was called is used as the {@link Scope}.
	 * 
	 * @param <R>
	 *            the type of value to be returned
	 * @param value
	 *            the value to attempt to evaluate to an instance of {@code T}
	 * @param field
	 *            the name of the field
	 * @param type
	 *            the {@link Class} object for {@code T}
	 * @return an instance of {@code T} produced from {@code value} if possible
	 * @throws IllegalArgumentException
	 *             if an instance of {@code T} cannot be produced from {@code value}
	 */
	protected <R> R evaluateToType(Object value, String field, Class<R> type) {
		if (value instanceof String)
			value = ScopedFormulaProcessor.process((String) value, this, field);
		if (!type.isInstance(value))
			throw new IllegalArgumentException("The value of " + field + " for " + getClass().getSimpleName() + " must be or evaluate to an instance of " + type.getSimpleName());
		return type.cast(value);
	}
}
