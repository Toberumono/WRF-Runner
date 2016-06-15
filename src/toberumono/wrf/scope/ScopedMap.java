package toberumono.wrf.scope;

import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.structures.collections.iterators.WrappedIterator;
import toberumono.structures.sexpressions.ConsCell;

public class ScopedMap implements Scope, Map<String, Object> {
	private final Function<Entry<String, Object>, Object> valuesConverter = e -> processOutput(e.getKey(), e.getValue());
	
	private final Map<String, Object> backing;
	private Scope parent;
	private EntrySet entries;
	private Collection<Object> values;
	
	public ScopedMap(Scope parent) {
		this.parent = parent;
		backing = new HashMap<>();
		entries = null;
		values = null;
	}
	
	private Object processOutput(String name, Object e) {
		return e instanceof ConsCell ? ScopedFormulaProcessor.process((ConsCell) e, this, name).getCar() : e;
	}
	
	private Object processInput(Object e) {
		if (e instanceof String) {
			String str = (String) e;
			if (str.charAt(0) == '=')
				return ScopedFormulaProcessor.preProcess(str.substring(1));
			else if (str.charAt(0) == '\\')
				return str.substring(1);
		}
		return e;
	}
	
	@Override
	public boolean containsKey(Object key) {
		return backing.containsKey(key);
	}
	
	@Override
	public Object get(Object key) {
		if (!(key instanceof String))
			return null;
		return processOutput((String) key, backing.get(key));
	}
	
	@Override
	public Object put(String key, Object value) {
		return processOutput(key, backing.put(key, processInput(value)));
	}
	
	@Override
	public Object remove(Object key) {
		if (!(key instanceof String))
			return null;
		return processOutput((String) key, backing.remove(key));
	}
	
	@Override
	public void clear() {
		backing.clear();
	}
	
	@Override
	public Set<String> keySet() {
		return backing.keySet();
	}
	
	@Override
	public Set<Entry<String, Object>> entrySet() {
		return entries == null ? entries = new EntrySet(backing.entrySet()) : entries;
	}
	
	final class ScopedEntry implements Entry<String, Object> {
		private final Entry<String, Object> back;
		
		public ScopedEntry(Entry<String, Object> back) {
			this.back = back;
		}
		
		@Override
		public String getKey() {
			return back.getKey();
		}
		
		@Override
		public Object getValue() {
			return processOutput(getKey(), back.getValue());
		}
		
		@Override
		public Object setValue(Object value) {
			return processOutput(getKey(), back.setValue(processInput(value)));
		}
	}
	
	final class EntrySet extends AbstractSet<Entry<String, Object>> {
		private final Set<Entry<String, Object>> back;
		
		EntrySet(Set<Entry<String, Object>> back) {
			this.back = back;
		}
		
		@Override
		public final int size() {
			return back.size();
		}
		
		@Override
		public final void clear() {
			ScopedMap.this.clear();
		}
		
		@Override
		public final Iterator<Entry<String, Object>> iterator() {
			return new WrappedIterator<>(back.iterator(), ScopedEntry::new);
		}
		
		@Override
		@SuppressWarnings("unchecked")
		public final boolean contains(Object o) {
			if (!(o instanceof Entry))
				return false;
			return back.contains(new ScopedEntry((Entry<String, Object>) o));
		}
		
		@Override
		@SuppressWarnings("unchecked")
		public final boolean remove(Object o) {
			if (!(o instanceof Entry))
				return false;
			return back.remove(new ScopedEntry((Entry<String, Object>) o));
		}
	}
	
	@Override
	public boolean hasValueByName(String name) {
		return containsKey(name);
	}
	
	@Override
	public Object getValueByName(String name) throws InvalidVariableAccessException {
		Object out = get(name);
		if (out == null)
			throw new InvalidVariableAccessException(name + " is not a valid parameter name.");
		return out;
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
			throw new UnsupportedOperationException("The parent of a ScopedConfiguration object cannot be changed once set.");
	}
	
	public static ScopedMap buildFromJSON(JSONObject base) throws InvalidVariableAccessException {
		return buildFromJSON(base, null);
	}
	
	public static ScopedMap buildFromJSON(JSONObject base, Scope parent) throws InvalidVariableAccessException {
		ScopedMap out = new ScopedMap(parent);
		for (Entry<String, JSONData<?>> entry : base.entrySet()) {
			if (entry.getValue() instanceof JSONObject)
				out.put(entry.getKey(), buildFromJSON((JSONObject) entry.getValue(), out));
			else if (entry.getValue() instanceof JSONArray)
				out.put(entry.getKey(), ScopedList.buildFromJSON((JSONArray) entry.getValue(), out));
			else
				out.put(entry.getKey(), entry.getValue().value());
		}
		return out;
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
	public boolean containsValue(Object value) {
		if (value == null)
			return false;
		if (backing.values().contains(processInput(value)))
			return true;
		for (Entry<String, Object> val : backing.entrySet())
			if (val.getValue().equals(value))
				return true;
		return false;
	}
	
	@Override
	public void putAll(Map<? extends String, ? extends Object> m) {
		for (Entry<? extends String, ? extends Object> e : m.entrySet())
			put(e.getKey(), e.getValue());
	}
	
	class ValuesCollection extends AbstractCollection<Object> {
		
		@Override
		public final int size() {
			return ScopedMap.this.size();
		}
		
		@Override
		public final void clear() {
			ScopedMap.this.clear();
		}
		
		@Override
		public final Iterator<Object> iterator() {
			return new WrappedIterator<>(entrySet().iterator(), valuesConverter);
		}
		
		@Override
		public final boolean contains(Object o) {
			return containsValue(o);
		}
	}
	
	@Override
	public Collection<Object> values() {
		return values == null ? values = new ValuesCollection() : values;
	}
}
