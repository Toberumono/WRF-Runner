package toberumono.wrf.scope;

import toberumono.wrf.Simulation;

/**
 * An interface that defines the basic methods needed to implement the tree-based scoping used throughout the {@link Simulation}.
 * 
 * @author Toberumono
 * @see AbstractScope
 */
public interface Scope {
	/**
	 * @return the parent {@link Scope}; if this is the root of the {@link Scope} tree, this method returns {@code null}
	 */
	public Scope getParent();
	
	/**
	 * This method <i>only</i> checks the current {@link Scope Scope's} values - it does not query the parent {@link Scope}.
	 * 
	 * @param name
	 *            the name of the variable to retrieve as a {@link String}
	 * @return the named variable's value if it exists
	 * @throws InvalidVariableAccessException
	 *             if the current {@link Scope} (not including its parent {@link Scope}) does not contain a variable with the given name
	 */
	public Object getValueByName(String name) throws InvalidVariableAccessException;
	
	/**
	 * This method <i>only</i> checks the current {@link Scope Scope's} values - it does not query the parent {@link Scope}.
	 * 
	 * @param name
	 *            the name of the variable to retrieve as a {@link String}
	 * @return {@code true} iff the current {@link Scope} (not including its parent {@link Scope}) contains a variable with the given name
	 */
	public boolean hasValueByName(String name);
	
	/**
	 * This method follows the {@link Scope} tree up to the root until it finds a variable with the given name.
	 * 
	 * @param name
	 *            the name of the variable to retrieve as a {@link String}
	 * @return the named variable's value if it exists
	 * @throws InvalidVariableAccessException
	 *             if the neither current {@link Scope} nor any of its parent {@link Scope Scopes} contain a variable with the given name
	 */
	public default Object getScopedValueByName(String name) throws InvalidVariableAccessException {
		for (Scope scope = this; scope != null; scope = scope.getParent())
			if (scope.hasValueByName(name))
				return scope.getValueByName(name);
		throw new InvalidVariableAccessException("Could not access " + name);
	}
	
	/**
	 * This method follows the {@link Scope} tree up to the root until it finds a variable with the given name.
	 * 
	 * @param name
	 *            the name of the variable to retrieve as a {@link String}
	 * @return {@code true} iff the current {@link Scope} or any of its parent {@link Scope Scopes} contain a variable with the given name
	 */
	public default boolean hasScopedValueByName(String name) {
		for (Scope scope = this; scope != null; scope = scope.getParent())
			if (scope.hasValueByName(name))
				return true;
		return false;
	}
}
