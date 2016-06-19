package toberumono.wrf.scope;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import toberumono.utils.functions.ExceptedSupplier;

/**
 * An implementation of {@link Scope} that works with the {@link NamedScopeValue} annotation to simplify the process of
 * adding values to a {@link Scope}.
 * 
 * @author Toberumono
 * @param <T>
 *            the type of the parent {@link Scope}
 */
public class AbstractScope<T extends Scope> implements Scope {
	private final T parent;
	private final Map<String, ExceptedSupplier<Object>> namedItems;
	
	/**
	 * Constructs the {@link AbstractScope} and builds the scopes variable table from fields and methods annotated with
	 * {@link NamedScopeValue}.
	 * 
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public AbstractScope(T parent) {
		this.parent = parent;
		namedItems = new HashMap<>();
		for (Field f : getClass().getFields())
			addFieldIfNamed(f);
		for (Field f : getClass().getDeclaredFields()) //Second pass is to allow names declared in the current type to override those declared in its supertypes
			addFieldIfNamed(f);
		for (Method m : getClass().getMethods())
			addMethodIfNamed(m);
		for (Method m : getClass().getDeclaredMethods()) //Second pass is to allow names declared in the current type to override those declared in its supertypes
			addMethodIfNamed(m);
	}
	
	private void addFieldIfNamed(Field f) {
		NamedScopeValue nsv = f.getAnnotation(NamedScopeValue.class);
		if (nsv != null) {
			f.setAccessible(true);
			addNamedValue(nsv, () -> f.get(this));
		}
	}
	
	private void addMethodIfNamed(Method m) {
		NamedScopeValue nsv = m.getAnnotation(NamedScopeValue.class);
		if (nsv != null) {
			m.setAccessible(true);
			addNamedValue(nsv, () -> m.invoke(this));
		}
	}
	
	private void addNamedValue(NamedScopeValue nsv, ExceptedSupplier<Object> supplier) {
		for (String name : nsv.value())
			namedItems.put(name, supplier);
	}
	
	@Override
	public T getParent() {
		return parent;
	}
	
	@Override
	public boolean hasValueByName(String name) {
		return namedItems.containsKey(name);
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		try {
			if (namedItems.containsKey(name))
				return namedItems.get(name).get();
			else
				throw new InvalidVariableAccessException("'" + name + "' does not exist in the current scope.");
		}
		catch (Throwable t) {
			throw new InvalidVariableAccessException("Could not access '" + name + "'.", t);
		}
	}
}
