package toberumono.wrf.timing.clear;

import java.util.ArrayList;
import java.util.List;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedList;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.ScopedTimingComponentList;

/**
 * An implementation of {@link Clear} that wraps a {@link List} of individual {@link Clear} implementations (which can be of type {@link ListClear})
 * and applies them iteratively.
 * 
 * @author Toberumono
 */
public class ListClear extends ScopedTimingComponentList<Clear> implements Clear {
	
	/**
	 * Initializes a new {@link ListClear} wrapper around an existing {@link List} of {@link Clear} instances.
	 * 
	 * @param backing
	 *            the {@link List} of {@link Clear} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListClear(List<Clear> backing, Scope parent) {
		super(backing, parent);
	}
	
	/**
	 * Initializes a new {@link ListClear} wrapper around a {@link List} of {@link Clear} instances derived from the "items" field of the provided
	 * {@link ScopedMap}. This is for compatibility with {@link WRFRunnerComponentFactory}.
	 * 
	 * @param parameters
	 *            a {@link ScopedMap} with "items" mapping to a {@link ScopedList}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListClear(ScopedMap parameters, Scope parent) {
		this((ScopedList) parameters.get("items"), parent);
	}
	
	/**
	 * Initializes a new {@link ListClear} wrapper around a {@link List} of {@link Clear} instances derived from the elements of the given
	 * {@link ScopedList}.
	 * 
	 * @param items
	 *            a {@link ScopedList} exclusively containing {@link ScopedMap ScopedMaps} that define {@link Clear} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListClear(ScopedList items, Scope parent) {
		this(new ArrayList<>(), parent);
		for (Object o : items)
			add(WRFRunnerComponentFactory.generateComponent(Clear.class, (ScopedMap) o, WRFRunnerComponentFactory.willInherit((ScopedMap) o) ? parent : this));
	}
}
