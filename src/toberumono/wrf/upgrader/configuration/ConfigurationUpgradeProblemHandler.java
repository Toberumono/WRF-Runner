package toberumono.wrf.upgrader.configuration;

import toberumono.structures.versioning.VersionNumber;
import toberumono.wrf.upgrader.JSONUpgradeProblemHandler;

public abstract class ConfigurationUpgradeProblemHandler extends JSONUpgradeProblemHandler {
	private final VersionNumber maxVersion;
	
	public ConfigurationUpgradeProblemHandler(String handledProblemID, VersionNumber maxVersion) {
		super(handledProblemID);
		this.maxVersion = maxVersion;
	}
	
	@Override
	public boolean isApplicableToVersion(VersionNumber version) {
		return version.compareTo(maxVersion) < 0;
	}
}
