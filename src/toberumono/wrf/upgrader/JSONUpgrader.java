package toberumono.wrf.upgrader;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;
import toberumono.structures.versioning.VersionNumber;

public abstract class JSONUpgrader {
	private final SortedList<Pair<VersionNumber, Consumer<JSONObject>>> steps;
	private final Collection<JSONUpgradeProblemHandler> upgradeProblemHandlers;
	
	public JSONUpgrader() {
		steps = new SortedList<>((a, b) -> a.getX().compareTo(b.getX()));
		upgradeProblemHandlers = new ArrayList<>();
	}
	
	public boolean addUpgradeAction(String newVersion, Consumer<JSONObject> upgradeAction) {
		return addUpgradeAction(new VersionNumber(newVersion), upgradeAction);
	}
	
	public boolean addUpgradeAction(VersionNumber newVersion, Consumer<JSONObject> upgradeAction) {
		return steps.add(new Pair<>(newVersion, upgradeAction));
	}
	
	public boolean addUpgradeProblemHandler(JSONUpgradeProblemHandler potentialProblem) {
		return upgradeProblemHandlers.add(potentialProblem);
	}
	
	public Pair<JSONObject, Collection<UpgradeWarning>> performUpgrade(JSONObject root) {
		return performUpgrade(root, null, null);
	}
	
	public Pair<JSONObject, Collection<UpgradeWarning>> performUpgrade(JSONObject root, Supplier<String> interactiveInput, Consumer<String> interactiveOutput) {
		JSONObject out = root.deepCopy();
		Collection<UpgradeWarning> warnings = findPotentialProblems(out, interactiveInput, interactiveOutput);
		for (Pair<VersionNumber, Consumer<JSONObject>> step : steps) {
			if (getVersion(out).compareTo(step.getX()) <= 0) {
				step.getY().accept(out);
				setVersion(out, step.getX());
			}
		}
		return new Pair<>(out, warnings);
	}
	
	protected Collection<UpgradeWarning> findPotentialProblems(JSONObject root, Supplier<String> interactiveInput, Consumer<String> interactiveOutput) {
		VersionNumber currentVersion = getVersion(root);
		Collection<UpgradeWarning> warnings = new ArrayList<>();
		for (Entry<String, JSONData<?>> entry : root.entrySet())
			findPotentialProblems(entry.getValue(), entry.getKey(), interactiveInput, interactiveOutput,
					upgradeProblemHandlers.stream().filter(handler -> handler.isApplicableToVersion(currentVersion)).collect(Collectors.toList()), warnings, entry::setValue);
		return warnings;
	}
	
	protected void findPotentialProblems(JSONData<?> value, String path, Supplier<String> interactiveInput, Consumer<String> interactiveOutput, List<JSONUpgradeProblemHandler> applicableHandlers,
			Collection<UpgradeWarning> warnings, Consumer<JSONData<?>> valueUpdater) {
		JSONUpgradeProblemHandler handler;
		if (value instanceof JSONArray) {
			JSONArray array = (JSONArray) value;
			for (int i = 0; i < array.size(); i++) {
				final int setI = i;
				findPotentialProblems(array.get(i), path + "[" + i + "]", interactiveInput, interactiveOutput, applicableHandlers, warnings, element -> array.set(setI, element));
			}
		}
		else if (value instanceof JSONObject) {
			for (Entry<String, JSONData<?>> entry : ((JSONObject) value).entrySet())
				findPotentialProblems(entry.getValue(), (path.length() > 0 ? path + "->" : path) + entry.getKey(), interactiveInput, interactiveOutput, applicableHandlers, warnings, entry::setValue);
		}
		JSONData<?> val = value;
		for (int j = 0; j < applicableHandlers.size(); j++) {
			handler = applicableHandlers.get(j);
			if (handler.fieldHasProblem(val)) {
				if (interactiveInput != null && interactiveOutput != null) { //If it is interactive, we don't want to store the warnings
					val = handler.interactiveFix(path, val, interactiveInput, interactiveOutput); //We change val here so that the new value can be used in later iterations
					valueUpdater.accept(val);
					j = -1; //Have to re-check the previous handlers
				}
				else {
					warnings.add(new UpgradeWarning(handler.getHandledProblemID(), handler.generateWarning(path, val)));
				}
			}
		}
	}
	
	protected abstract VersionNumber getVersion(JSONObject root);
	
	protected abstract void setVersion(JSONObject root, VersionNumber version);
	
	/**
	 * Recurses through the JSON data in {@code root} and renames fields with the name {@code oldName} to {@code newName}.
	 * 
	 * @param root
	 *            an instance of either {@link JSONArray} or {@link JSONObject}
	 * @param oldName
	 *            the current name of the field
	 * @param newName
	 *            the name to which the field should be renamed
	 */
	public static final void recursiveRenameField(JSONData<?> root, String oldName, String newName) {
		if (root instanceof JSONArray)
			for (JSONData<?> e : (JSONArray) root)
				recursiveRenameField(e, oldName, newName);
		else if (root instanceof JSONObject) {
			JSONObject obj = (JSONObject) root;
			if (obj.containsKey(oldName))
				obj.put(newName, obj.remove(oldName));
			for (JSONData<?> v : obj.values())
				recursiveRenameField(v, oldName, newName);
		}
	}
}
