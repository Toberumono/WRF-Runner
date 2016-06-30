package toberumono.wrf.scope;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.wrf.Module;

/**
 * This extension of {@link ScopedMap} is used to avoid the infinite loop that can occur if the {@link ScopedFormulaProcessor} tries to reference the
 * parent {@link Scope} from the root of the {@link ScopedMap} passed to a {@link Module}.
 * 
 * @author Toberumono
 */
public class ModuleScopedMap extends ScopedMap {
	
	/**
	 * Constructs a new {@link ModuleScopedMap}.
	 * 
	 * @param parent
	 *            the parent {@link Scope}; if it is {@code null}, the parent {@link Scope} can be set later via a call to {@link #setParent(Scope)}
	 */
	public ModuleScopedMap(Scope parent) {
		super(parent);
	}
	
	/**
	 * @return {@link #getParent()} so that the module's scope is correctly used as the scope for computations in parameter maps
	 */
	@Override
	protected Scope getFormulaScope() {
		return getParent();
	}

	/**
	 * Builds a {@link ModuleScopedMap} with a {@code null} parent {@link Scope} from the given {@link JSONObject}
	 * 
	 * @param base
	 *            the given {@link JSONObject}
	 * @return a {@link ModuleScopedMap} with a {@code null} parent {@link Scope} based on the given {@link JSONObject}
	 */
	public static ModuleScopedMap buildFromJSON(JSONObject base) {
		return buildFromJSON(base, null);
	}
	
	/**
	 * Builds a {@link ModuleScopedMap} with the given parent {@link Scope} from the given {@link JSONObject}
	 * 
	 * @param base
	 *            the given {@link JSONObject}
	 * @param parent
	 *            the given parent {@link Scope}
	 * @return a {@link ModuleScopedMap} with the given parent {@link Scope} based on the given {@link JSONObject}
	 */
	public static ModuleScopedMap buildFromJSON(JSONObject base, Scope parent) {
		ModuleScopedMap out = new ModuleScopedMap(parent);
		for (Entry<String, JSONData<?>> entry : base.entrySet()) {
			if (entry.getValue() instanceof JSONObject)
				out.put(entry.getKey(), ScopedMap.buildFromJSON((JSONObject) entry.getValue(), out));
			else if (entry.getValue() instanceof JSONArray)
				out.put(entry.getKey(), ScopedList.buildFromJSON((JSONArray) entry.getValue(), out));
			else
				out.put(entry.getKey(), entry.getValue().value());
		}
		return out;
	}
}
