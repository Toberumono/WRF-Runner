package toberumono.wrf.upgrader;

import java.util.function.Consumer;
import java.util.function.Supplier;

import toberumono.structures.versioning.VersionNumber;

public abstract class UpgradeProblemHandler<V> {
	private final String handledProblemID;
	
	public UpgradeProblemHandler(String handledProblemID) {
		this.handledProblemID = handledProblemID;
	}
	
	public String getHandledProblemID() {
		return handledProblemID;
	}
	
	public abstract String generateWarning(String path, V value);
	
	public abstract boolean isApplicableToVersion(VersionNumber version);
	
	public abstract boolean fieldHasProblem(V value);
	
	protected abstract V stringToValue(V oldValue, String newValue);
	
	protected boolean promptForValueCorrectness(String path, V value, Supplier<String> input, Consumer<String> output) {
		output.accept(generateWarning(path, value));
		output.accept("\n");
		output.accept("Is this value (" + value.toString() + ") correct? [Y/n] ");
		return !input.get().toLowerCase().startsWith("n");
	}
	
	public V interactiveFix(String path, V oldValue, Supplier<String> input, Consumer<String> output) {
		if (promptForValueCorrectness(path, oldValue, input, output))
			return oldValue; //If current the value is correct, we can just return it
		output.accept("Please enter the correct value:\n");
		return stringToValue(oldValue, input.get());
	}
}
