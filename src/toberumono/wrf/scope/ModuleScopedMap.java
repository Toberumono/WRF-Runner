package toberumono.wrf.scope;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;

public class ModuleScopedMap extends ScopedMap {
	
	public ModuleScopedMap(Scope parent) {
		super(parent);
	}
	
	/**
	 * @return {@link #getParent()} so that the module's scope is correctly used as the scope for computations in parameter
	 *         maps
	 */
	@Override
	protected Scope getFormulaScope() {
		return getParent();
	}
	
	public static ModuleScopedMap buildFromJSON(JSONObject base) throws InvalidVariableAccessException {
		return buildFromJSON(base, null);
	}
	
	public static ModuleScopedMap buildFromJSON(JSONObject base, Scope parent) throws InvalidVariableAccessException {
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
