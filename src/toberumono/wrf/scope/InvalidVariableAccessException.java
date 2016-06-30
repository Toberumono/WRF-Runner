package toberumono.wrf.scope;

/**
 * Thrown when a named variable is requested from {@link Scope} that doesn't have variable with that name.
 * 
 * @author Toberumono
 */
public class InvalidVariableAccessException extends RuntimeException {
	
	/**
	 * Constructs a new {@link InvalidVariableAccessException} without initializing the cause or setting the detail message. The cause can be
	 * initialized later via a call to {@link #initCause(Throwable)}.
	 */
	public InvalidVariableAccessException() {
		super();
	}
	
	/**
	 * Constructs a new {@link InvalidVariableAccessException} with the given detail {@code message} without initializing the cause. The cause can be
	 * initialized later via a call to {@link #initCause(Throwable)}.
	 * 
	 * @param message
	 *            the detail message as a {@link String}
	 */
	public InvalidVariableAccessException(String message) {
		super(message);
	}
	
	/**
	 * Constructs a new {@link InvalidVariableAccessException} with the given detail {@code message} and {@code cause}.
	 * 
	 * @param message
	 *            the detail message as a {@link String}
	 * @param cause
	 *            the cause of the {@link InvalidVariableAccessException} as a {@link Throwable}
	 */
	public InvalidVariableAccessException(String message, Throwable cause) {
		super(message, cause);
	}
	
	/**
	 * Constructs a new {@link InvalidVariableAccessException} that inherits its detail message from its {@code cause}. This essentially makes it a
	 * wrapper around {@code cause}.
	 *
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A <tt>null</tt> value is permitted, and indicates
	 *            that the cause is nonexistent or unknown.)
	 */
	public InvalidVariableAccessException(Throwable cause) {
		super(cause);
	}
}
