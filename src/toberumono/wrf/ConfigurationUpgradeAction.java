package toberumono.wrf;

/**
 * A {@link FunctionalInterface} for a function that doesn't take any arguments or return any value.
 * 
 * @author Toberumono
 */
@FunctionalInterface
public interface ConfigurationUpgradeAction {
	
	/**
	 * Performs the action.
	 */
	public void perform();
}
