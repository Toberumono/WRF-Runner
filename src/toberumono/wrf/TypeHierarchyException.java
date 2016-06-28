package toberumono.wrf;

import toberumono.wrf.scope.Scope;

/**
 * An exception that is thrown when an operation cannot be completed due to a conflict with or problem within the {@link Scope Scoped} type hierarchy.
 * 
 * @author Toberumono
 */
public class TypeHierarchyException extends RuntimeException {
	
	/**
	 * Constructs a new {@link TypeHierarchyException} without initializing the cause or setting the detail message. The cause can be initialized
	 * later via a call to {@link #initCause(Throwable)}.
	 */
	public TypeHierarchyException() {
		super();
	}
	
	/**
	 * Constructs a new {@link TypeHierarchyException} with the given detail {@code message} without initializing the cause. The cause can be
	 * initialized later via a call to {@link #initCause(Throwable)}.
	 * 
	 * @param message
	 *            the detail message as a {@link String}
	 */
	public TypeHierarchyException(String message) {
		super(message);
	}
	
	/**
	 * Constructs a new {@link TypeHierarchyException} with the given detail {@code message} and {@code cause}.
	 * 
	 * @param message
	 *            the detail message as a {@link String}
	 * @param cause
	 *            the cause of the {@link TypeHierarchyException} as a {@link Throwable}
	 */
	public TypeHierarchyException(String message, Throwable cause) {
		super(message, cause);
	}
	
	/**
	 * Constructs a new {@link TypeHierarchyException} that inherits its detail message from its {@code cause}. This essentially makes it a wrapper
	 * around {@code cause}.
	 *
	 * @param cause
	 *            the cause (which is saved for later retrieval by the {@link #getCause()} method). (A <tt>null</tt> value is permitted, and indicates
	 *            that the cause is nonexistent or unknown.)
	 */
	public TypeHierarchyException(Throwable cause) {
		super(cause);
	}
}
