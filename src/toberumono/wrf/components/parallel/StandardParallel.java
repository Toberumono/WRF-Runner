package toberumono.wrf.components.parallel;

import java.util.logging.Logger;

import toberumono.wrf.scope.NamedScopeValue;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedMap;

/**
 * The default implementation of {@link Parallel}.
 * 
 * @author Toberumono
 */
public class StandardParallel extends AbstractParallel {
	private Boolean dmpar, bootLAM;
	private Integer numProcessors;
	
	/**
	 * Initializes a new instance of {@link StandardParallel} with a {@link Logger} derived from {@link Parallel#LOGGER_NAME}.
	 * 
	 * @param parameters
	 *            the parameters that define the instance as a {@link ScopedMap}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public StandardParallel(ScopedMap parameters, Scope parent) {
		super(parameters, parent);
		dmpar = null;
		bootLAM = null;
		numProcessors = null;
	}
	
	@Override
	@NamedScopeValue({"is-dmpar", "is-parallel"})
	public Boolean isParallel() {
		if (dmpar != null)
			return dmpar;
		synchronized (this) {
			if (dmpar == null) {
				dmpar = getParameters().containsKey("is-dmpar") ? evaluateToType(getParameters().get("is-dmpar"), "is-dmpar", Boolean.class)
						: (getParent() instanceof Parallel ? ((Parallel) getParent()).isParallel() : false);
			}
		}
		return dmpar;
	}
	
	/**
	 * @return {@code true} iff the "boot-lam" field was set to {@code true}
	 */
	@NamedScopeValue("boot-lam")
	public Boolean isBootLAM() {
		if (bootLAM != null)
			return bootLAM;
		synchronized (this) {
			if (bootLAM == null) {
				bootLAM = getParameters().containsKey("boot-lam") ? evaluateToType(getParameters().get("boot-lam"), "boot-lam", Boolean.class)
						: (getParent() instanceof StandardParallel ? ((StandardParallel) getParent()).isBootLAM() : false);
			}
		}
		return bootLAM;
	}
	
	@Override
	@NamedScopeValue({"processors", "num-processors"})
	public Integer getNumProcessors() {
		if (numProcessors != null)
			return numProcessors;
		synchronized (this) {
			if (numProcessors == null) {
				numProcessors = getParameters().containsKey("processors") ? evaluateToNumber(getParameters().get("processors"), "processors").intValue()
						: (getParent() instanceof Parallel ? ((Parallel) getParent()).getNumProcessors() : 2);
			}
		}
		return numProcessors;
	}
	
	@Override
	public String[] makeCommand(String executablePath, String logPath) {
		if (isParallel()) {
			if (isBootLAM())
				return new String[]{"mpiexec", "-boot", "-np", getNumProcessors().toString(), executablePath, "2>&1", "|", "tee", logPath};
			else
				return new String[]{"mpiexec", "-np", getNumProcessors().toString(), executablePath, "2>&1", "|", "tee", logPath};
		}
		return new String[]{executablePath, "2>&1", "|", "tee", logPath};
	}
	
}
