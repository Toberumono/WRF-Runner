package toberumono.wrf.upgrader;

import java.io.PrintStream;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.function.Supplier;

import toberumono.structures.versioning.VersionNumber;

/**
 * Root class for all {@link UpgradeProblemHandler}. Defines the common methods and provides default implementations whereever possible.
 * 
 * @author Toberumono
 * @param <V>
 *            the type of values handled by the {@link UpgradeProblemHandler}
 * @see JSONUpgradeProblemHandler
 */
public abstract class UpgradeProblemHandler<V> {
	private final String handledProblemID;
	
	/**
	 * Constructs a new {@link UpgradeProblemHandler} with the given handled problem ID.
	 * 
	 * @param handledProblemID
	 *            the ID of the handled problem
	 */
	public UpgradeProblemHandler(String handledProblemID) {
		this.handledProblemID = handledProblemID;
	}
	
	/**
	 * @return the ID of the problem handled by the {@link UpgradeProblemHandler}
	 */
	public String getHandledProblemID() {
		return handledProblemID;
	}
	
	/**
	 * Generates a warning for the given path to the problematic field and the field's value.
	 * 
	 * @param path
	 *            the path to the field as a {@link String}
	 * @param value
	 *            the problematic value
	 * @return a warning for the given pat hand value as a {@link String}
	 */
	public abstract String generateWarning(String path, V value);
	
	/**
	 * @param version
	 *            the {@link VersionNumber} of the current configuration file
	 * @return {@code true} iff the {@link UpgradeProblemHandler} handles a problem applicable to configuration files with the given
	 *         {@link VersionNumber}
	 */
	public abstract boolean isApplicableToVersion(VersionNumber version);
	
	/**
	 * @param value
	 *            the value of the field being checked
	 * @return {@code true} iff the field has the problem being checked for by the {@link UpgradeProblemHandler}
	 */
	public abstract boolean fieldHasProblem(V value);
	
	/**
	 * Converts the value that the user provided as a {@link String} into an instance of {@code V}.
	 * 
	 * @param oldValue
	 *            the old (and incorrect) value - provided for casting and other reference purposes
	 * @param newValue
	 *            the new value (provided by the user) as a {@link String}
	 * @return an instance of {@code V} based on the user-provided {@link String}
	 */
	protected abstract V stringToValue(V oldValue, String newValue);
	
	/**
	 * Prompts the user to determine if the current value is correct. input and output are provided as a {@link Supplier} and {@link Consumer} for
	 * maximum portability.
	 * 
	 * @param path
	 *            the path to the potentially problematic field
	 * @param value
	 *            the value of the potentially problematic field
	 * @param input
	 *            a {@link Supplier} that, when called, provides a single line of user input (equivalent to {@link Scanner#nextLine()}) as a
	 *            {@link String}
	 * @param output
	 *            a {@link Consumer} that, when called, writes the passed {@link String} to an output (equivalent to {@link PrintStream#print(String)}
	 *            or {@code System.out.print(String)})
	 * @return {@code true} iff the user indicated that the value was correct
	 */
	protected boolean promptForValueCorrectness(String path, V value, Supplier<String> input, Consumer<String> output) {
		output.accept(generateWarning(path, value));
		output.accept("\n");
		output.accept("Is this value (" + value.toString() + ") correct? [Y/n] ");
		return !input.get().toLowerCase().startsWith("n");
	}
	
	/**
	 * Prompts the user for value correctness and, if needed, prompts the user for the new (correct) value.
	 * 
	 * @param path
	 *            the path to the potentially problematic field
	 * @param oldValue
	 *            the value of the potentially problematic field
	 * @param input
	 *            a {@link Supplier} that, when called, provides a single line of user input (equivalent to {@link Scanner#nextLine()}) as a
	 *            {@link String}
	 * @param output
	 *            a {@link Consumer} that, when called, writes the passed {@link String} to an output (equivalent to {@link PrintStream#print(String)}
	 *            or {@code System.out.print(String)})
	 * @return the correct value for the field (this will be {@code oldValue} if the existing value was correct)
	 */
	public V interactiveFix(String path, V oldValue, Supplier<String> input, Consumer<String> output) {
		if (promptForValueCorrectness(path, oldValue, input, output))
			return oldValue; //If current the value is correct, we can just return it
		output.accept("Please enter the correct value:\n");
		return stringToValue(oldValue, input.get());
	}
}
