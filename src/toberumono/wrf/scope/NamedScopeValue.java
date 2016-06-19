package toberumono.wrf.scope;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * A simple annotation used in conjunction with {@link AbstractScope} to make creating accessible objects easier.
 * 
 * @author Toberumono
 */
@Documented
@Retention(RUNTIME)
@Target({FIELD, METHOD})
public @interface NamedScopeValue {
	/**
	 * @return the name by which the annotated value can be accessed within the {@link Scope}
	 */
	String[] value();
}
