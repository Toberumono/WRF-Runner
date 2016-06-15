package toberumono.wrf.scope;

public interface Scope {
	
	public Scope getParent();
	
	public Object getValueByName(String name) throws InvalidVariableAccessException;
	
	public boolean hasValueByName(String name);
	
	public default Object getScopedValueByName(String name) throws InvalidVariableAccessException {
		for (Scope scope = this; scope != null; scope = scope.getParent())
			if (scope.hasValueByName(name))
				return scope.getValueByName(name);
		throw new InvalidVariableAccessException("Could not access " + name);
	}
}
