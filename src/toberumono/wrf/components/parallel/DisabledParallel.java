package toberumono.wrf.components.parallel;

import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

/**
 * An implementation of {@link Parallel} that only generates serial commands.
 * 
 * @author Toberumono
 */
public class DisabledParallel extends AbstractParallel {
	
	/**
	 * Constructs a new instance of an implementation of {@link Parallel} that only generates serial commands.
	 * 
	 * @param parameters
	 *            the parameters that defined the instance as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public DisabledParallel(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
	}
	
	@Override
	public Boolean isParallel() {
		return false;
	}
	
	@Override
	public Integer getNumProcessors() {
		return 1;
	}
	
	@Override
	public String[] makeCommand(String executablePath, String logPath) {
		return Parallel.makeSerialCommand(executablePath, logPath);
	}
}
