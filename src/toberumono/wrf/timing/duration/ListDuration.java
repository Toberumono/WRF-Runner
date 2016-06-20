package toberumono.wrf.timing.duration;

import java.util.ArrayList;
import java.util.List;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedList;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.ScopedTimingComponentList;

/**
 * An implementation of {@link Duration} that wraps a {@link List} of individual {@link Duration} implementations (which can be of type {@link ListDuration})
 * and applies them iteratively.
 * 
 * @author Toberumono
 */
public class ListDuration extends ScopedTimingComponentList<Duration> implements Duration {
	
	/**
	 * Initializes a new {@link ListDuration} wrapper around an existing {@link List} of {@link Duration} instances.
	 * 
	 * @param backing
	 *            the {@link List} of {@link Duration} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListDuration(List<Duration> backing, Scope parent) {
		super(backing, parent);
	}
	
	/**
	 * Initializes a new {@link ListDuration} wrapper around a {@link List} of {@link Duration} instances derived from the "items" field of the provided
	 * {@link ScopedMap}. This is for compatibility with {@link WRFRunnerComponentFactory}.
	 * 
	 * @param parameters
	 *            a {@link ScopedMap} with "items" mapping to a {@link ScopedList}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListDuration(ScopedMap parameters, Scope parent) {
		this((ScopedList) parameters.get("items"), parent);
	}
	
	/**
	 * Initializes a new {@link ListDuration} wrapper around a {@link List} of {@link Duration} instances derived from the elements of the given
	 * {@link ScopedList}.
	 * 
	 * @param items
	 *            a {@link ScopedList} exclusively containing {@link ScopedMap ScopedMaps} that define {@link Duration} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListDuration(ScopedList items, Scope parent) {
		this(new ArrayList<>(), parent);
		for (Object o : items)
			add(WRFRunnerComponentFactory.generateComponent(Duration.class, (ScopedMap) o, WRFRunnerComponentFactory.willInherit((ScopedMap) o) ? parent : this));
	}
}
