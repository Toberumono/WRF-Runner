package toberumono.wrf.scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.structures.collections.iterators.WrappedIterator;
import toberumono.structures.sexpressions.ConsCell;

public class ScopedList implements Scope, List<Object> {
	private final List<Object> backing;
	private Scope parent;
	
	public ScopedList(Scope parent) {
		backing = new ArrayList<>();
		this.parent = parent;
	}
	
	private ScopedList(Scope parent, List<Object> backing) { //Used for subList
		this.backing = backing;
		this.parent = parent;
	}
	
	private Object processOutput(Object e) {
		return e instanceof ConsCell ? ScopedFormulaProcessor.process((ConsCell) e, this, null).getCar() : e;
	}
	
	private Object processInput(Object e) {
		if (e instanceof String) {
			String str = (String) e;
			if (str.charAt(0) == '=')
				e = ScopedFormulaProcessor.getLexer().lex(str.substring(1));
			else if (str.charAt(0) == '\\')
				e = str.substring(1);
		}
		return e;
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
		return backing.contains(processInput(o));
	}
	
	@Override
	public Iterator<Object> iterator() {
		return new WrappedIterator<>(backing.iterator(), this::processOutput);
	}
	
	@Override
	public Object[] toArray() {
		Object[] out = backing.toArray();
		for (int i = 0; i < out.length; i++)
			out[i] = processOutput(out[i]);
		return out;
	}
	
	@Override
	@SuppressWarnings("unchecked")
	public <T> T[] toArray(T[] a) {
		T[] out = backing.toArray(a);
		for (int i = 0; i < out.length; i++)
			out[i] = (T) processOutput(out[i]);
		return out;
	}
	
	@Override
	public boolean add(Object e) {
		return backing.add(processInput(e));
	}
	
	@Override
	public boolean remove(Object o) {
		return backing.remove(processInput(o));
	}
	
	@Override
	public boolean containsAll(Collection<?> c) {
		for (Object o : c)
			if (!contains(o))
				return false;
		return true;
	}
	
	@Override
	public boolean addAll(Collection<? extends Object> c) {
		int oldSize = size();
		for (Object o : c)
			add(o);
		return oldSize != size();
	}
	
	@Override
	public boolean addAll(int index, Collection<? extends Object> c) {
		int oldSize = size(), i = index;
		for (Object o : c)
			add(i++, o);
		return oldSize != size();
	}
	
	@Override
	public boolean removeAll(Collection<?> c) {
		int oldSize = size();
		for (Object o : c)
			remove(processInput(o));
		return oldSize != size();
	}
	
	@Override
	public boolean retainAll(Collection<?> c) {
		Collection<Object> processed = new ArrayList<>();
		for (Object o : c)
			processed.add(processInput(o));
		return backing.retainAll(processed);
	}
	
	@Override
	public void clear() {
		backing.clear();
	}
	
	@Override
	public Object get(int index) {
		return processOutput(backing.get(index));
	}
	
	@Override
	public Object set(int index, Object element) {
		return processOutput(backing.set(index, processInput(element)));
	}
	
	@Override
	public void add(int index, Object element) {
		backing.add(index, processInput(element));
	}
	
	@Override
	public Object remove(int index) {
		return processOutput(backing.remove(index));
	}
	
	@Override
	public int indexOf(Object o) {
		return backing.indexOf(processInput(o));
	}
	
	@Override
	public int lastIndexOf(Object o) {
		return backing.lastIndexOf(processInput(o));
	}
	
	class ScopedListIterator implements ListIterator<Object> {
		int cursor, index;
		boolean added, removed;
		
		public ScopedListIterator() {
			this(0);
		}
		
		public ScopedListIterator(int cursor) {
			this.cursor = cursor;
			this.index = -1;
			added = removed = false;
		}
		
		@Override
		public boolean hasNext() {
			return cursor < size();
		}
		
		@Override
		public Object next() {
			if (!hasNext())
				throw new NoSuchElementException();
			added = removed = false;
			return get(index = cursor++);
		}
		
		@Override
		public boolean hasPrevious() {
			return cursor > 0;
		}
		
		@Override
		public Object previous() {
			if (!hasPrevious())
				throw new NoSuchElementException();
			added = removed = false;
			return get(index = --cursor);
		}
		
		@Override
		public int nextIndex() {
			return cursor;
		}
		
		@Override
		public int previousIndex() {
			return cursor - 1;
		}
		
		@Override
		public void remove() throws IllegalStateException {
			if (index == -1)
				throw new IllegalStateException("Neither next nor previous have been called.");
			if (added)
				throw new IllegalStateException("add has been called since the most recent call to next or previous.");
			if (removed)
				throw new IllegalStateException("remove has been called since the most recent call to next or previous.");
			ScopedList.this.remove(index);
			if (index == cursor) //If this is true, then previous was the last one called
				cursor--;
			removed = true;
		}
		
		@Override
		public void set(Object e) throws IllegalStateException {
			if (index == -1)
				throw new IllegalStateException("Neither next nor previous have been called.");
			if (added)
				throw new IllegalStateException("add has been called since the most recent call to next or previous.");
			if (removed)
				throw new IllegalStateException("remove has been called since the most recent call to next or previous.");
			ScopedList.this.set(index, e);
		}
		
		@Override
		public void add(Object e) {
			ScopedList.this.add(cursor++, e);
			added = true;
		}
	}
	
	@Override
	public ListIterator<Object> listIterator() {
		return new ScopedListIterator();
	}
	
	@Override
	public ListIterator<Object> listIterator(int index) {
		return new ScopedListIterator(index);
	}
	
	@Override
	public List<Object> subList(int fromIndex, int toIndex) {
		return new ScopedSubList(backing.subList(fromIndex, toIndex));
	}
	
	class ScopedSubList extends ScopedList {
		
		public ScopedSubList(List<Object> backing) {
			super(null, backing);
		}
		
		@Override
		public Scope getParent() {
			return ScopedList.this.getParent();
		}
		
		@Override
		public synchronized void setParent(Scope parent) {
			ScopedList.this.setParent(parent);
		}
	}
	
	@Override
	public boolean hasValueByName(String name) {
		return 0 <= Integer.parseInt(name) && Integer.parseInt(name) < size();
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		return get(Integer.parseInt(name));
	}
	
	@Override
	public Scope getParent() {
		return parent;
	}
	
	/**
	 * Sets the parent {@link Scope}. This can only be done once and only if {@code null} was passed to the constructor.
	 * 
	 * @param parent
	 *            the parent {@link Scope}
	 */
	public synchronized void setParent(Scope parent) {
		if (this.parent == null)
			this.parent = parent;
		else
			throw new UnsupportedOperationException("The parent of a ScopedList object cannot be changed once set.");
	}
	
	public static ScopedList buildFromJSON(JSONArray base) throws InvalidVariableAccessException {
		return buildFromJSON(base, null);
	}
	
	public static ScopedList buildFromJSON(JSONArray base, Scope parent) {
		ScopedList out = new ScopedList(parent);
		for (JSONData<?> e : base) {
			if (e instanceof JSONObject)
				out.add(ScopedMap.buildFromJSON((JSONObject) e, out));
			else if (e instanceof JSONArray)
				out.add(buildFromJSON((JSONArray) e, out));
			else
				out.add(e.value());
		}
		return out;
	}
}
