package test;

/**
 * A special class of exceptions to be raised when
 * a switch on values from an enumeration happens
 * to be incomplete.
 * 
 * @author slescuyer
 */
public class ValueUnmatchedException extends RuntimeException {
	private static final long serialVersionUID = 1756507326051818013L;

	public ValueUnmatchedException(Enum<?> unmatchedValue) {
		super("Unmatched value " + unmatchedValue.toString() 
				+ " in switch on enum " + unmatchedValue.getDeclaringClass());
	}	
}
