package toberumono.wrf.upgrader;

import toberumono.json.JSONData;

public abstract class JSONUpgradeProblemHandler extends UpgradeProblemHandler<JSONData<?>> {

	public JSONUpgradeProblemHandler(String handledProblemID) {
		super(handledProblemID);
	}
}
