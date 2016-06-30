package toberumono.wrf.upgrader;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import toberumono.json.JSONArray;
import toberumono.json.JSONData;
import toberumono.json.JSONObject;
import toberumono.structures.collections.lists.SortedList;
import toberumono.structures.tuples.Pair;
import toberumono.structures.versioning.VersionNumber;

/**
 * A class that defines the logic needed to upgrade data in a {@link JSONObject}.
 * 
 * @author Toberumono
 */
public abstract class JSONUpgrader {
	private final SortedList<Pair<VersionNumber, Consumer<JSONObject>>> steps;
	private final Collection<JSONUpgradeProblemHandler> upgradeProblemHandlers;
	
	/**
	 * Constructs a new {@link JSONUpgrader} with an empty list of steps and {@link UpgradeProblemHandler UpgradeProblemHandlers}.
	 */
	public JSONUpgrader() {
		steps = new SortedList<>((a, b) -> a.getX().compareTo(b.getX()));
		upgradeProblemHandlers = new ArrayList<>();
	}
	
	/**
	 * Registers an action to perform to upgrade data in a {@link JSONObject} whose version is lower than the given one.
	 * 
	 * @param newVersion
	 *            the maximum version for which this upgrade action is applicable as a {@link String} that conforms to the format described by
	 *            <a href="http://semver.org/">Semantic Versioning 2.0.0</a>
	 * @param upgradeAction
	 *            the action to perform as a {@link Consumer}
	 * @return {@code true} if the action was successfully added
	 */
	public boolean addUpgradeAction(String newVersion, Consumer<JSONObject> upgradeAction) {
		return addUpgradeAction(new VersionNumber(newVersion), upgradeAction);
	}
	
	/**
	 * Registers an action to perform to upgrade data in a {@link JSONObject} whose version is lower than the given one.
	 * 
	 * @param newVersion
	 *            the maximum version for which this upgrade action is applicable as a {@link VersionNumber}
	 * @param upgradeAction
	 *            the action to perform as a {@link Consumer}
	 * @return {@code true} if the action was successfully added
	 */
	public boolean addUpgradeAction(VersionNumber newVersion, Consumer<JSONObject> upgradeAction) {
		return steps.add(new Pair<>(newVersion, upgradeAction));
	}
	
	/**
	 * Adds the given {@link JSONUpgradeProblemHandler} to the list of potential problem handlers that the {@link JSONUpgradeProblemHandler} will go
	 * through when upgrading a {@link JSONObject JSONObject's} data.
	 * 
	 * @param upgradeProblemHandler
	 *            the {@link JSONUpgradeProblemHandler} to add
	 * @return {@code true} iff the {@link JSONUpgradeProblemHandler} was successfully added
	 */
	public boolean addUpgradeProblemHandler(JSONUpgradeProblemHandler upgradeProblemHandler) {
		return upgradeProblemHandlers.add(upgradeProblemHandler);
	}
	
	/**
	 * Upgrades the data in the given {@link JSONObject} non-interactively. Equivalent to {@code performUpgrade(root, null, null)}. The
	 * {@link JSONObject} is copied before being upgraded - the {@link JSONObject} passed as {@code root} is not modified.
	 * 
	 * @param root
	 *            the {@link JSONObject} to upgrade
	 * @return a {@link JSONObject} with the upgraded data and a {@link Collection} containing any {@link UpgradeWarning UpgradeWarnings} that were
	 *         not resolved
	 */
	public Pair<JSONObject, Collection<UpgradeWarning>> performUpgrade(JSONObject root) {
		return performUpgrade(root, null, null);
	}
	
	/**
	 * Upgrades the data in the given {@link JSONObject} with optional interactivity. The upgrade is performed interactively iff both
	 * {@code interactiveInput} and {@code interactiveOutput} are not {@code null}. The {@link JSONObject} is copied before being upgraded - the
	 * {@link JSONObject} passed as {@code root} is not modified.
	 * 
	 * @param root
	 *            the {@link JSONObject} containing the data to be upgraded
	 * @param interactiveInput
	 *            a {@link Supplier} that, when called, provides a single line of user input (equivalent to {@link Scanner#nextLine()}) as a
	 *            {@link String} (this can be {@code null})
	 * @param interactiveOutput
	 *            a {@link Consumer} that, when called, writes the passed {@link String} to an output (equivalent to {@link PrintStream#print(String)}
	 *            or {@code System.out.print(String)}) (this can be {@code null})
	 * @return a {@link JSONObject} with the upgraded data and a {@link Collection} containing any {@link UpgradeWarning UpgradeWarnings} that were
	 *         not resolved
	 */
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
	
	/**
	 * Entry method for {@link #findPotentialProblems(JSONData, String, Supplier, Consumer, List, Collection, Consumer)}. This should only be called
	 * from within {@link #performUpgrade(JSONObject, Supplier, Consumer)}. Any found problems are resolved interactively iff both
	 * {@code interactiveInput} and {@code interactiveOutput} are not {@code null}.
	 * 
	 * @param root
	 *            the {@link JSONObject} containing the data to be upgraded
	 * @param interactiveInput
	 *            a {@link Supplier} that, when called, provides a single line of user input (equivalent to {@link Scanner#nextLine()}) as a
	 *            {@link String} (this can be {@code null})
	 * @param interactiveOutput
	 *            a {@link Consumer} that, when called, writes the passed {@link String} to an output (equivalent to {@link PrintStream#print(String)}
	 *            or {@code System.out.print(String)}) (this can be {@code null})
	 * @return a {@link Collection} containing any {@link UpgradeWarning UpgradeWarnings} that were not resolved
	 */
	protected Collection<UpgradeWarning> findPotentialProblems(JSONObject root, Supplier<String> interactiveInput, Consumer<String> interactiveOutput) {
		VersionNumber currentVersion = getVersion(root);
		Collection<UpgradeWarning> warnings = new ArrayList<>();
		for (Entry<String, JSONData<?>> entry : root.entrySet())
			findPotentialProblems(entry.getValue(), entry.getKey(), interactiveInput, interactiveOutput, //upgradeProblemHandlers is filtered before being passed to findPotentialProblems
					upgradeProblemHandlers.stream().filter(handler -> handler.isApplicableToVersion(currentVersion)).collect(Collectors.toList()), warnings, entry::setValue);
		return warnings;
	}
	
	/**
	 * Recursively searches the given {@link JSONData} for potential problems. This should only be called from within itself or
	 * {@link #performUpgrade(JSONObject, Supplier, Consumer)}. Any found problems are resolved interactively iff both {@code interactiveInput} and
	 * {@code interactiveOutput} are not {@code null}.
	 * 
	 * @param value
	 *            the value of the field currently being inspected
	 * @param path
	 *            the path from the root of the {@link JSONData} to the field currently being inspected
	 * @param interactiveInput
	 *            a {@link Supplier} that, when called, provides a single line of user input (equivalent to {@link Scanner#nextLine()}) as a
	 *            {@link String} (this can be {@code null})
	 * @param interactiveOutput
	 *            a {@link Consumer} that, when called, writes the passed {@link String} to an output (equivalent to {@link PrintStream#print(String)}
	 *            or {@code System.out.print(String)}) (this can be {@code null})
	 * @param applicableHandlers
	 *            a {@link List} containing the {@link JSONUpgradeProblemHandler JSONUpgradeProblemHandlers} that apply to the {@link JSONData} being
	 *            upgraded
	 * @param warnings
	 *            the {@link Collection} into which unresolved {@link UpgradeWarning UpgradeWarnings} are placed
	 * @param valueUpdater
	 *            the method used to write the updated value back into the {@link JSONObject} containing the field currently being inspected
	 */
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
	
	/**
	 * Given a {@link JSONObject} that contains data of the type that the {@link JSONUpgrader} handles, this method returns a {@link VersionNumber}
	 * containing the version of the data in the {@link JSONObject}.
	 * 
	 * @param root
	 *            the {@link JSONObject} containing the versioned data
	 * @return a {@link VersionNumber} containing the version of the data in the {@link JSONObject}
	 */
	protected abstract VersionNumber getVersion(JSONObject root);
	
	/**
	 * This method updates the version of the data in the given {@link JSONObject} to match the provided {@link VersionNumber}.
	 * 
	 * @param root
	 *            the {@link JSONObject} containing the versioned data
	 * @param version
	 *            the new version of the data in the {@link JSONObject} as a {@link VersionNumber}
	 */
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
