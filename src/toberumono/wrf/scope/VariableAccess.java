package toberumono.wrf.scope;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class VariableAccess {
	/**
	 * The default {@link Pattern} used to split the variable access {@link String} into individual steps.
	 */
	public static final Pattern DEFAULT_SPLITTER = Pattern.compile(".", Pattern.LITERAL);
	private final List<String> steps;
	
	public VariableAccess(String access) {
		this(access, DEFAULT_SPLITTER);
	}
	
	public VariableAccess(String access, String splitter) {
		this(access, Pattern.compile(splitter, Pattern.LITERAL));
	}
	
	public VariableAccess(String access, Pattern splitter) {
		steps = Collections.unmodifiableList(Arrays.asList(splitter.split(access)));
	}
	
	public List<String> getSteps() {
		return steps;
	}
	
	public Object accessScope(Scope scope) {
		int i = 0, lim = getSteps().size() - 1;
		String step = getSteps().get(i);
		for (; i < lim; i++, step = getSteps().get(i)) {
			switch (step) {
				case "super":
				case "parent":
					scope = scope.getParent();
					if (scope == null)
						throw new InvalidVariableAccessException("The current scope does not have a parent");
					break;
				case "this":
				case "current":
					break; //Don't change this scope in this case
				default:
					scope = (Scope) scope.getScopedValueByName(step);
					break;
			}
		}
		return scope.getScopedValueByName(step);
	}
}
