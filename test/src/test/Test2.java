package test;

/**
 * Test with external hierarchy,
 * and a local class shadowing one of the external ones
 * 
 * @author slescuyer
 */
public class Test2 {
	
	@SuppressWarnings("unused")
	private static final class B { 
		// Trilili
	}

	public static void test(test.Test1.Base base) {
		// quick-fix me, and see how imports
		// are added when possible
		switch (base) {}
	}
	
}
