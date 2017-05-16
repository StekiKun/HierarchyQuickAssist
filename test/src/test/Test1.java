package test;

/**
 * Test with local nested hierarchy
 * 
 * @author slescuyer
 */
public class Test1 {
	
	static enum Kind {
		A(A.class), B(B.class), C(C.class);
		
		// NB We don't even have to store the class witness,
		// it's sufficient that it appears as a parameter of
		// the enum constant declarations
		@SuppressWarnings("unused")
		final Class<?> witness;
		private Kind(Class<?> clazz) {
			this.witness = clazz;
		}
	}
	
	@Hierarchy("getKind")
	static abstract class Base {
		abstract Kind getKind();
	}
	
	static final class A extends Base {
		@Override Kind getKind() { return Kind.A; }
	}

	static final class B extends Base {
		@Override Kind getKind() { return Kind.B; }
	}
	
	static enum CKind {
		CA(CA.class), CB(CB.class), CC(CC.class);
		
		private CKind(Class<?> clazz) {
			// tralala
		}
	}
	
	@Hierarchy(value="ckind", field=true)
	static abstract class C extends Base {
		Kind getKind() { return Kind.C; }
		
		protected final CKind ckind;
		private C(CKind ckind) { this.ckind = ckind; }
	}
	
	
	static final class CA extends C {
		protected CA() { super(CKind.CA); }
	}
	
	static final class CB extends C {
		protected CB() { super(CKind.CB); }
	}
	
	static final class CC extends C {
		protected CC() { super(CKind.CC); }
	}
	
	public static void test(Base base) {
		switch (base) {}
	}
}