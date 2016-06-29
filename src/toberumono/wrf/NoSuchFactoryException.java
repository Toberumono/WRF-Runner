package toberumono.wrf;

/**
 * An exception that is thrown when a request for a {@link WRFRunnerComponentFactory} cannot be completed.
 * 
 * @author Toberumono
 */
public class NoSuchFactoryException extends RuntimeException {
	
	/**
	 * Constructs a new {@link NoSuchFactoryException} without initializing the cause or setting the detail message. The cause can be initialized
	 * later via a call to {@link #initCause(Throwable)}.
	 */
	public NoSuchFactoryException() {
		super();
	}
	
	/**
	 * Constructs a new {@link NoSuchFactoryException} with the given detail {@code message} without initializing the cause. The cause can be
	 * initialized later via a call to {@link #initCause(Throwable)}.
	 * 
	 * @param message
	 *            the detail message as a {@link String}
	 */
	public NoSuchFactoryException(String message) {
		super(message);
	}
	
	/**
	 * Constructs a new {@link NoSuchFactoryException} with the given detail {@code message} and {@code cause}.
	 * 
	 * @param message
	 *            the detail message as a {@link String}
	 * @param cause
	 *            the cause of the {@link NoSuchFactoryException} as a {@link Throwable}
	 */
	public NoSuchFactoryException(String message, Throwable cause) {
		super(message, cause);
	}
	
	/**
	 * Constructs a new {@link NoSuchFactoryException} that inherits its detail message from its {@code cause}. This essentially makes it a wrapper
	 * around {@code cause}.
	 *
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A <tt>null</tt> value is permitted, and indicates
	 *            that the cause is nonexistent or unknown.)
	 */
	public NoSuchFactoryException(Throwable cause) {
		super(cause);
	}
}
