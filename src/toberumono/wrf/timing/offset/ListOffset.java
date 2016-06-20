package toberumono.wrf.timing.offset;

import java.util.ArrayList;
import java.util.List;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedList;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.ScopedTimingComponentList;

/**
 * An implementation of {@link Offset} that wraps a {@link List} of individual {@link Offset} implementations (which can be of type {@link ListOffset})
 * and applies them iteratively.
 * 
 * @author Toberumono
 */
public class ListOffset extends ScopedTimingComponentList<Offset> implements Offset {
	
	/**
	 * Initializes a new {@link ListOffset} wrapper around an existing {@link List} of {@link Offset} instances.
	 * 
	 * @param backing
	 *            the {@link List} of {@link Offset} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListOffset(List<Offset> backing, Scope parent) {
		super(backing, parent);
	}
	
	/**
	 * Initializes a new {@link ListOffset} wrapper around a {@link List} of {@link Offset} instances derived from the "items" field of the provided
	 * {@link ScopedMap}. This is for compatibility with {@link WRFRunnerComponentFactory}.
	 * 
	 * @param parameters
	 *            a {@link ScopedMap} with "items" mapping to a {@link ScopedList}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListOffset(ScopedMap parameters, Scope parent) {
		this((ScopedList) parameters.get("items"), parent);
	}
	
	/**
	 * Initializes a new {@link ListOffset} wrapper around a {@link List} of {@link Offset} instances derived from the elements of the given
	 * {@link ScopedList}.
	 * 
	 * @param items
	 *            a {@link ScopedList} exclusively containing {@link ScopedMap ScopedMaps} that define {@link Offset} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListOffset(ScopedList items, Scope parent) {
		this(new ArrayList<>(), parent);
		for (Object o : items)
			add(WRFRunnerComponentFactory.generateComponent(Offset.class, (ScopedMap) o, WRFRunnerComponentFactory.willInherit((ScopedMap) o) ? parent : this));
	}

	@Override
	public boolean doesWrap() {
		return get(0).doesWrap();
	}
}
