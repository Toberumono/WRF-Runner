package toberumono.wrf.scope;

/**
 * Thrown when a named variable is requested from {@link Scope} that doesn't have variable with that name.
 * 
 * @author Toberumono
 */
public class InvalidVariableAccessException extends RuntimeException {
	
	public InvalidVariableAccessException() {
		super();
	}
	
	public InvalidVariableAccessException(String message) {
		super(message);
	}
	
	public InvalidVariableAccessException(String message, Throwable cause) {
		super(message, cause);
	}
	
	public InvalidVariableAccessException(Throwable cause) {
		super(cause);
	}
	
}
