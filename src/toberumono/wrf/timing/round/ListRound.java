package toberumono.wrf.timing.round;

import java.util.ArrayList;
import java.util.List;

import toberumono.wrf.WRFRunnerComponentFactory;
import toberumono.wrf.scope.Scope;
import toberumono.wrf.scope.ScopedList;
import toberumono.wrf.scope.ScopedMap;
import toberumono.wrf.timing.ScopedTimingComponentList;

/**
 * An implementation of {@link Round} that wraps a {@link List} of individual {@link Round} implementations (which can be of type {@link ListRound})
 * and applies them iteratively.
 * 
 * @author Toberumono
 */
public class ListRound extends ScopedTimingComponentList<Round> implements Round {
	
	/**
	 * Initializes a new {@link ListRound} wrapper around an existing {@link List} of {@link Round} instances.
	 * 
	 * @param backing
	 *            the {@link List} of {@link Round} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListRound(List<Round> backing, Scope parent) {
		super(backing, parent);
	}
	
	/**
	 * Initializes a new {@link ListRound} wrapper around a {@link List} of {@link Round} instances derived from the "items" field of the provided
	 * {@link ScopedMap}. This is for compatibility with {@link WRFRunnerComponentFactory}.
	 * 
	 * @param parameters
	 *            a {@link ScopedMap} with "items" mapping to a {@link ScopedList}
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListRound(ScopedMap parameters, Scope parent) {
		this((ScopedList) parameters.get("items"), parent);
	}
	
	/**
	 * Initializes a new {@link ListRound} wrapper around a {@link List} of {@link Round} instances derived from the elements of the given
	 * {@link ScopedList}.
	 * 
	 * @param items
	 *            a {@link ScopedList} exclusively containing {@link ScopedMap ScopedMaps} that define {@link Round} instances
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public ListRound(ScopedList items, Scope parent) {
		this(new ArrayList<>(), parent);
		for (Object o : items)
			add(WRFRunnerComponentFactory.generateComponent(Round.class, (ScopedMap) o, WRFRunnerComponentFactory.willInherit((ScopedMap) o) ? parent : this));
	}
}
