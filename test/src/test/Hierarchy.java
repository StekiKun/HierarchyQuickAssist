package test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation class is used to annotate classes
 * which are the base class of a number of sub-classes
 * identifiable by some "kind" value in a Java enum.
 * <p>
 * It is suitable for use by the "Generate hierarchy switch"
 * quick-assist. This quick-assist can work with any
 * annotation whose type name is 'Hierarchy' and which 
 * defines a {@link String}-typed {@link #value()} field.
 * The {@link #value()} field is used to specify the
 * name of the <i>instance method</i> in the base class
 * which returns the enum kind to which the instance 
 * belongs. Therefore, a basic usage of such an annotation
 * looks like this:
 * <pre>
 * public enum Kind {
 * 	...
 * }
 * 
 * @Hierarchy("getKind")
 * public abstract class MyBaseClass {
 * 	...
 * 
 * 	public abstract Kind getKind();
 * }
 * </pre>
 * <p>
 * The hierarchy annotation can also define two other
 * fields to further customize the use of the quick-assist.
 * <ul>
 * <li> a field named {@link #field()} of type boolean,
 * 	which when set means that the value of {@link #value()}
 *  is not to be interpreted as an instance method name,
 *  but as an instance field name;
 * <li> a field named {@link #unmatched()} of type
 * 	{@code Class<? extends RuntimeException>}, specifying an 
 *  exception type to use after the switch when generating switch 
 * 	hierarchies with 'return' statements in each case (doing
 * 	something after the switch in these situations is mandatory
 * 	because the JDT can't figure out that portion of the code is
 * 	dead and will thus issue errors if nothing is returned or
 * 	thrown).
 * </ul>
 * Both these fields are optional in the annotation type,
 * and if absent the default behaviour is assumed (i.e.
 * using a method instead of a field to retrieve the instance
 * kind, and not generating any exception when none of
 * the enum kinds matched).
 * <p>
 * This annotation class shows every possibility and defines
 * all optional fields. It could be used as follows:
 * <pre>
 * public enum Kind {
 * 	...
 * }
 * 
 * public class UnmatchedException extends RuntimeException {
 * 	UnmatchedException(Enum<?> kind) {
 *   ...
 *  }
 *  ...
 * }
 * 
 * @Hierarchy("kind", field=true, unmatched=UnmatchedException.class)
 * public abstract class MyBaseClass {
 * 	...
 * 
 * 	public final Kind kind;
 * 
 *  private MyBaseClass(Kind kind) { this.kind = kind; }
 *  
 *  // Each sub-class must call super() with the adequate
 *  // kind value
 * }
 * 
 * </pre>
 * <p>
 * 
 * @author slescuyer
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface Hierarchy {

	/**
	 * @return the name of the method or field in 
	 *  the annotated type which returns the kind
	 *  that describes the direct type hierarchy
	 *  below this type
	 * @see #field()
	 */
	public String value();
	
	/**
	 * @return {@code true} if the kind value
	 * 	should be fetched from a field instead
	 * 	of an instance method. By default here, 
	 *  a method is expected.
	 */
	public boolean field()
	default false;
	
	/**
	 * @return the class object for a custom class
	 * 	of runtime exceptions which can be raised when 
	 *  a switch on a type hierarchy enumeration happens
	 *  to be incomplete at run-time. The exception
	 *  must have a constructor with the following
	 *  signature:
	 *  <pre>
	 *  MyException(Enum<?> kind)
	 *  </pre> 
	 *  <p>
	 *  By default here, {@link ValueUnmatchedException} 
	 *  is used.
	 */
	public Class<? extends RuntimeException> unmatched()
	default ValueUnmatchedException.class;
}
