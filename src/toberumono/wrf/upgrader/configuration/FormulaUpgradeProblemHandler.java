package toberumono.wrf.upgrader.configuration;

import java.util.function.Consumer;
import java.util.function.Supplier;

import toberumono.json.JSONData;
import toberumono.json.JSONString;
import toberumono.structures.versioning.VersionNumber;

public class FormulaUpgradeProblemHandler extends ConfigurationUpgradeProblemHandler {
	
	public FormulaUpgradeProblemHandler() {
		super("formula", new VersionNumber("4.0.0"));
	}
	
	@Override
	public String generateWarning(String path, JSONData<?> value) {
		StringBuilder warning = new StringBuilder();
		warning.append("The value of ").append(path).append(" starts with ");
		if (((String) value.value()).charAt(0) == '=')
			warning.append("an '='.  It will therefore be treated as a formula.");
		else //Then it starts with \=
			warning.append("\"\\=\".  It will therefore be treated as a literal String equal to \"" + ((String) value.value()).substring(1) + "\".");
		warning.append("\nIf this is not correct, add a '\\' to the start of the value.");
		warning.append("\nSee https://github.com/Toberumono/WRF-Runner/wiki/Configuration-Formula-System for more information.");
		return warning.toString();
	}
	
	@Override
	public boolean fieldHasProblem(JSONData<?> value) {
		return (value instanceof JSONString) && (((String) value.value()).charAt(0) == '=' || ((String) value.value()).startsWith("\\="));
	}
	
	@Override
	public JSONData<?> interactiveFix(String path, JSONData<?> oldValue, Supplier<String> input, Consumer<String> output) {
		if (promptForValueCorrectness(path, oldValue, input, output))
			return oldValue;
		oldValue = new JSONString("\\" + oldValue.value());
		output.accept("Is \"" + oldValue.value() + "\" correct (a preceeding '\\' was added)? [Y/n] ");
		if (!input.get().toLowerCase().startsWith("n"))
			return oldValue;
		output.accept("Please enter the correct value here (it must be on a single line):\n");
		return stringToValue(oldValue, input.get());
	}
	
	@Override
	protected JSONData<?> stringToValue(JSONData<?> oldValue, String newValue) {
		return new JSONString(newValue);
	}
}
