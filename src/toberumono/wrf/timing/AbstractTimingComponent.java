package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import toberumono.wrf.SimulationConstants;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedComponent;
import toberumono.wrf.scope.ScopedMap;

import static toberumono.wrf.SimulationConstants.TIMING_FIELD_NAMES;

/**
 * Provides a simple common mechanism by which lazy evaluation can implemented for components of {@link Timing}.
 * 
 * @author Toberumono
 */
public abstract class AbstractTimingComponent extends ScopedComponent<Scope> implements TimingComponent {
	private final Logger log;
	private boolean computed;
	
	/**
	 * Constructs a new {@link AbstractTimingComponent}.
	 * 
	 * @param parameters
	 *            the parameters that define the component
	 * @param parent
	 *            the component's parent {@link Scope}
	 * @param log
	 *            the {@link Logger} that the component should use
	 */
	public AbstractTimingComponent(ScopedMap parameters, Scope parent, Logger log) {
		super(parameters, parent);
		this.log = log;
	}
	
	@Override
	public Calendar apply(Calendar base, boolean inPlace) {
		Calendar out = inPlace ? base : (Calendar) base.clone();
		if (computed)
			return doApply(out);
		synchronized (log) {
			if (!computed) {
				compute();
				computed = true;
			}
		}
		return doApply(out);
	}
	
	@Override
	public final Calendar apply(Calendar base) {
		return apply(base, false);
	}
	
	/**
	 * Implementations of this method <i>must</i> modify the {@link Calendar} passed to them.
	 * 
	 * @param base
	 *            the {@link Calendar} that the {@link AbstractTimingComponent TimingComponent} is to modify
	 * @return the provided {@link Calendar} as modified by the {@link AbstractTimingComponent TimingComponent}
	 */
	protected abstract Calendar doApply(Calendar base);
	
	/**
	 * Implementations of this method should perform all possible preprocessing steps and store their results.
	 */
	protected abstract void compute();
	
	/**
	 * @return the {@link Logger} assigned to the {@link AbstractTimingComponent TimingComponent}
	 */
	protected Logger getLogger() {
		return log;
	}
	
	/**
	 * A convenience method that passes the <i>enabled</i> in {@link #getParameters()} (retrieved via {@code getParameters().get("enabled")}) to
	 * {@link #parseEnabled(Object)}.
	 * 
	 * @return the names of the timing fields for which the {@link AbstractTimingComponent TimingComponent} is enabled
	 * @see #parseEnabled(Object)
	 */
	protected Collection<String> parseEnabled() {
		return parseEnabled(getParameters().get("enabled"));
	}
	
	/**
	 * Processes the given value of the {@link AbstractTimingComponent TimingComponent's} {@code enabled} field depending on its type.
	 * <ul>
	 * <li>{@link Collection}: the enabled timing fields are the values that are in both the {@code enabled} {@link Collection} and
	 * {@link SimulationConstants#TIMING_FIELD_NAMES}</li>
	 * <li>{@link Map}: the enabled timing fields are those entries in the {@link Map} with keys in {@link SimulationConstants#TIMING_FIELD_NAMES} and
	 * values that evaluate to {@code true}</li>
	 * <li>{@link String}: if the {@link String} value of {@code enabled} is in {@link SimulationConstants#TIMING_FIELD_NAMES}, then the
	 * {@link String} value of {@code enabled}; otherwise, none</li>
	 * </ul>
	 * If {@code enabled} is not one of those three types, every field in {@link SimulationConstants#TIMING_FIELD_NAMES} is considered to be enabled.
	 * 
	 * @param enabled
	 *            the value of the <i>enabled</i> field as an {@link Object}; this can be {@code null}
	 * @return the names of the timing fields for which the {@link AbstractTimingComponent TimingComponent} is enabled
	 */
	protected Collection<String> parseEnabled(Object enabled) {
		if (enabled instanceof Collection) //Casting to String is safe because in order for TIMING_FIELD_NAMES to contain e, e must be a String
			return ((Collection<?>) enabled).stream().filter(TIMING_FIELD_NAMES::contains).map(e -> (String) e).collect(Collectors.toSet());
		else if (enabled instanceof Map)
			return ((Map<?, ?>) enabled).entrySet().stream().filter(e -> TIMING_FIELD_NAMES.contains(e.getKey()) && evaluateToType(e.getValue(), "enabled." + e.getKey(), Boolean.class))
					.map(e -> (String) e.getKey()).collect(Collectors.toSet());
		else if (enabled instanceof String)
			return TIMING_FIELD_NAMES.contains(enabled) ? Collections.singleton((String) enabled) : Collections.emptySet();
		else //If enabled isn't a List, Map, or String, every timing field is assumed to be enabled
			return TIMING_FIELD_NAMES;
	}
}
