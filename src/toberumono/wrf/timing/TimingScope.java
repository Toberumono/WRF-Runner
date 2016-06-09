package toberumono.wrf.timing;

import toberumono.wrf.scope.AbstractScope;
import toberumono.wrf.scope.InvalidVariableAccessException;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedConfiguration;

public class TimingScope<T extends Scope> extends AbstractScope<T> {
	private final ScopedConfiguration parameters;

	public TimingScope(ScopedConfiguration parameters, T parent) {
		super(parent);
		this.parameters = parameters;
	}
	
	@Override
	public boolean hasValueByName(String name) {
		return super.hasValueByName(name) || parameters.contains(name);
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		try {
			return super.getValueByName(name);
		}
		catch (InvalidVariableAccessException e) {
			if (getParameters().contains(name))
				return getParameters().get(name);
			throw e;
		}
	}
	
	protected ScopedConfiguration getParameters() {
		return parameters;
	}
}
