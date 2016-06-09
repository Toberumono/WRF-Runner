package toberumono.wrf.scope;

/**
 * Thrown when a {@link VariableAccess} is used on a {@link Scope} that doesn't have data under that name.
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
