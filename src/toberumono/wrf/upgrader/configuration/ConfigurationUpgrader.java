package toberumono.wrf.upgrader.configuration;

import toberumono.json.JSONObject;
import toberumono.structures.versioning.VersionNumber;
import toberumono.wrf.upgrader.JSONUpgrader;

/**
 * An implementation of {@link JSONUpgrader} for upgrading configuration files.
 * 
 * @author Toberumono
 */
public class ConfigurationUpgrader extends JSONUpgrader {
	private static final VersionNumber MINIMUM_VERSION = new VersionNumber("1.0.0");
	
	@Override
	protected VersionNumber getVersion(JSONObject root) {
		return root.containsKey("version") && root.get("version").value() instanceof String ? new VersionNumber((String) root.get("version").value()) : MINIMUM_VERSION;
	}
	
	@Override
	protected void setVersion(JSONObject root, VersionNumber version) {
		root.put("version", version.toString());
	}
}
