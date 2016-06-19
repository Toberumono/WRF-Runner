package toberumono.wrf.timing;

import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.InvalidVariableAccessException;
import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

public class TimingScope extends AbstractScope<Scope> {
	private final ScopedMap parameters;

	public TimingScope(ScopedMap parameters, Scope parent) {
		super(parent);
		this.parameters = parameters;
	}
	
	@Override
	public boolean hasValueByName(String name) {
		return super.hasValueByName(name) || parameters.containsKey(name);
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		try {
			return super.getValueByName(name);
		}
		catch (InvalidVariableAccessException e) {
			if (getParameters().containsKey(name))
				return getParameters().get(name);
			throw e;
		}
	}
	
	@NamedScopeValue("parameters")
	public ScopedMap getParameters() {
		return parameters;
	}
}
