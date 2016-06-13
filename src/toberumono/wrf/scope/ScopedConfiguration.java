package toberumono.wrf.scope;

import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.structures.collections.iterators.WrappedIterator;
import toberumono.structures.sexpressions.ConsCell;

public class ScopedConfiguration implements Scope {
	private final Map<String, Object> backing;
	private Scope parent;
	private EntrySet entries;
	
	public ScopedConfiguration(Scope parent) {
		this.parent = parent;
		backing = new HashMap<>();
		entries = null;
	}
	
	private Object processOutput(String name, Object e) {
		return e instanceof ConsCell ? ScopedMathProcessor.processEquation((ConsCell) e, this, name).getCar() : e;
	}
	
	private Object processInput(Object e) {
		if (e instanceof String) {
			String str = (String) e;
			if (str.charAt(0) == '=')
				e = ScopedMathProcessor.getLexer().lex(str.substring(1));
			else if (str.charAt(0) == '\\')
				e = str.substring(1);
		}
		return e;
	}
	
	public Object get(String parameter) {
		if (!containsKey(parameter))
			throw new InvalidVariableAccessException(parameter + " is not a valid parameter name.");
		return processOutput(parameter, backing.get(parameter));
	}
	
	public boolean containsKey(String parameter) {
		return backing.containsKey(parameter);
	}
	
	public Object put(String parameter, Object value) {
		return processOutput(parameter, backing.get(backing.put(parameter, processInput(value))));
	}
	
	public Object remove(String parameter) {
		return processOutput(parameter, backing.remove(parameter));
	}
	
	public void clear() {
		backing.clear();
	}
	
	public Set<String> parameters() {
		return backing.keySet();
	}
	
	public Set<Entry<String, Object>> entries() {
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
			ScopedConfiguration.this.clear();
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
		return get(name);
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
	
	public static ScopedConfiguration buildFromJSON(JSONObject base) throws InvalidVariableAccessException {
		return buildFromJSON(base, null);
	}
	
	public static ScopedConfiguration buildFromJSON(JSONObject base, Scope parent) throws InvalidVariableAccessException {
		ScopedConfiguration out = new ScopedConfiguration(parent);
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
}
