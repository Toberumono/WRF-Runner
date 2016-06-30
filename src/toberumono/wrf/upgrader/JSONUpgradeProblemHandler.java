package toberumono.wrf.upgrader;

import toberumono.json.JSONData;

/**
 * Quick abstract class for naming consistency.  Used with instances of {@link JSONUpgrader}.
 * @author Toberumono
 *
 * @see JSONUpgrader
 */
public abstract class JSONUpgradeProblemHandler extends UpgradeProblemHandler<JSONData<?>> {

	/**
	 * Constructs a new {@link JSONUpgradeProblemHandler} with the given handled problem ID.
	 * 
	 * @param handledProblemID
	 *            the ID of the handled problem
	 */
	public JSONUpgradeProblemHandler(String handledProblemID) {
		super(handledProblemID);
	}
}
