package toberumono.wrf.components.parallel;

import java.util.logging.Logger;

import toberumono.wrf.scope.LoggedScopedComponent;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

public abstract class AbstractParallel extends LoggedScopedComponent<Scope> implements Parallel {
	
	public AbstractParallel(ScopedMap parameters, Scope parent) {
		super(parameters, parent, Logger.getLogger(LOGGER_NAME));
	}
}
