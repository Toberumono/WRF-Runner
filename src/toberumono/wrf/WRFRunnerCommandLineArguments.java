package toberumono.wrf;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A container that processes and holds the command-line arguments passed to the {@link WRFRunner}.
 * 
 * @author Toberumono
 */
public class WRFRunnerCommandLineArguments {
	private final Path configurationPath;
	private final boolean cacheUpdates, ignoreUpgradeProblems, performInteractiveUpgrade;
	
	/**
	 * Constructs a new {@link WRFRunnerCommandLineArguments} container from the given command-line arguments.
	 * 
	 * @param args
	 *            the command-line arguments
	 */
	public WRFRunnerCommandLineArguments(String[] args) {
		Path configurationPath = Paths.get("configuration.json");
		boolean cacheUpdates = false, ignoreUpgradeProblems = false, performInteractiveUpgrade = false;
		for (String arg : args) {
			switch (arg) {
				case "--no-upgrade-writing":
					cacheUpdates = true;
					break;
				case "--ignore-upgrade-problems":
					ignoreUpgradeProblems = true;
					break;
				case "--interactive-upgrade":
					performInteractiveUpgrade = true;
					break;
				default:
					configurationPath = Paths.get(arg);
			}
		}
		this.configurationPath = configurationPath;
		this.cacheUpdates = cacheUpdates;
		this.ignoreUpgradeProblems = ignoreUpgradeProblems;
		this.performInteractiveUpgrade = performInteractiveUpgrade;
	}
	
	/**
	 * Constructs a new {@link WRFRunnerCommandLineArguments} container with the given preprocessed arguments.
	 * 
	 * @param configurationPath
	 *            the {@link Path} to the configuration file
	 * @param cacheUpdates
	 *            whether updates to the configuration file should be written back to disk
	 * @param ignoreUpgradeProblems
	 *            whether the {@link Simulation} should continue with potential upgrade problems
	 * @param performInteractiveUpgrade
	 *            whether the potential upgrade problems should be resolved interactively
	 */
	public WRFRunnerCommandLineArguments(Path configurationPath, boolean cacheUpdates, boolean ignoreUpgradeProblems, boolean performInteractiveUpgrade) {
		this.configurationPath = configurationPath;
		this.cacheUpdates = cacheUpdates;
		this.ignoreUpgradeProblems = ignoreUpgradeProblems;
		this.performInteractiveUpgrade = performInteractiveUpgrade;
	}
	
	/**
	 * @return the {@link Path} to the configuration file
	 */
	public Path getConfigurationPath() {
		return configurationPath;
	}
	
	/**
	 * @return whether updates to the configuration file should be written back to disk
	 */
	public boolean cacheUpdates() {
		return cacheUpdates;
	}
	
	/**
	 * @return whether the {@link Simulation} should continue with potential upgrade problems
	 */
	public boolean ignoreUpgradeProblems() {
		return ignoreUpgradeProblems;
	}
	
	/**
	 * @return whether the potential upgrade problems should be resolved interactively
	 */
	public boolean isPerformInteractiveUpgrade() {
		return performInteractiveUpgrade;
	}
}
