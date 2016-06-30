package toberumono.wrf.upgrader.configuration;

import toberumono.structures.versioning.VersionNumber;
import toberumono.wrf.upgrader.JSONUpgradeProblemHandler;

/**
 * Extension on {@link JSONUpgradeProblemHandler} that defines the logic shared by all {@link ConfigurationUpgradeProblemHandler
 * ConfigurationUpgradeProblemHandlers}
 * 
 * @author Toberumono
 * @see ConfigurationUpgrader
 */
public abstract class ConfigurationUpgradeProblemHandler extends JSONUpgradeProblemHandler {
	private final VersionNumber maxVersion;
	
	/**
	 * Constructs a new {@link ConfigurationUpgradeProblemHandler} with the given handled problem ID and maximum applicable {@link VersionNumber}.
	 * 
	 * @param handledProblemID
	 *            the ID of the handled problem
	 * @param maxVersion
	 *            the maximum version of the configuration file in which the problem can occur
	 */
	public ConfigurationUpgradeProblemHandler(String handledProblemID, VersionNumber maxVersion) {
		super(handledProblemID);
		this.maxVersion = maxVersion;
	}
	
	@Override
	public boolean isApplicableToVersion(VersionNumber version) {
		return version.compareTo(maxVersion) < 0;
	}
}
