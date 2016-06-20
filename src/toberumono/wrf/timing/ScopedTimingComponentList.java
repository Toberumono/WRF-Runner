package toberumono.wrf.timing;

import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import toberumono.wrf.scope.InvalidVariableAccessException;
import toberumono.wrf.scope.Scope;

public class ScopedTimingComponentList<E extends TimingComponent> implements TimingComponent, List<E> {
	private final List<E> backing;
	private final Scope parent;
	
	public ScopedTimingComponentList(List<E> backing, Scope parent) {
		this.backing = backing;
		this.parent = parent;
	}
	
	@Override
	public Scope getParent() {
		return parent;
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		try {
			return get(Integer.parseInt(name));
		}
		catch (NumberFormatException e) {
			return get(0).getValueByName(name);
		}
	}
	
	@Override
	public boolean hasValueByName(String name) {
		if (size() == 0)
			return false;
		try {
			int index = Integer.parseInt(name);
			return 0 <= index && index < size(); //Returns true iff index is a valid index into this List
		}
		catch (NumberFormatException e) {
			return get(0).hasValueByName(name);
		}
	}

	@Override
	public Calendar apply(Calendar base) {
		return apply(base, false);
	}

	@Override
	public Calendar apply(Calendar base, boolean inPlace) {
		Calendar out = inPlace ? base : (Calendar) base.clone();
		for (TimingComponent component : this) //We don't want to make a bunch of clones of the Calendar if we don't have to
			out = component.apply(out, true);
		return out;
	}
	
	/**
	 * @return the {@link ScopedTimingComponentList ScopedComponentList's} backing {@link List}
	 */
	protected List<E> getBacking() {
		return backing;
	}
	
	@Override
	public int size() {
		return backing.size();
	}
	
	@Override
	public boolean isEmpty() {
		return backing.isEmpty();
	}
	
	@Override
	public boolean contains(Object o) {
		return backing.contains(o);
	}
	
	@Override
	public Iterator<E> iterator() {
		return backing.iterator();
	}
	
	@Override
	public Object[] toArray() {
		return backing.toArray();
	}
	
	@Override
	public <T> T[] toArray(T[] a) {
		return backing.toArray(a);
	}
	
	@Override
	public boolean add(E e) {
		return backing.add(e);
	}
	
	@Override
	public boolean remove(Object o) {
		return backing.remove(o);
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		return backing.containsAll(c);
	}
	
	@Override
	public boolean addAll(Collection<? extends E> c) {
		return backing.addAll(c);
	}
	
	@Override
	public boolean addAll(int index, Collection<? extends E> c) {
		return backing.addAll(index, c);
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		return backing.removeAll(c);
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		return backing.retainAll(c);
	}
	
	@Override
	public void clear() {
		backing.clear();
	}
	
	@Override
	public E get(int index) {
		return backing.get(index);
	}
	
	@Override
	public E set(int index, E element) {
		return backing.set(index, element);
	}
	
	@Override
	public void add(int index, E element) {
		backing.add(index, element);
	}
	
	@Override
	public E remove(int index) {
		return backing.remove(index);
	}
	
	@Override
	public int indexOf(Object o) {
		return backing.indexOf(o);
	}
	
	@Override
	public int lastIndexOf(Object o) {
		return backing.lastIndexOf(o);
	}
	
	@Override
	public ListIterator<E> listIterator() {
		return backing.listIterator();
	}
	
	@Override
	public ListIterator<E> listIterator(int index) {
		return backing.listIterator(index);
	}
	
	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		return new ScopedTimingComponentList<>(backing.subList(fromIndex, toIndex), getParent());
	}
}
