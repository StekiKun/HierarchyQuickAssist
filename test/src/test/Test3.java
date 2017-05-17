package test;

/**
 * Test with an unannotated class hierarchy
 * and a separate dispatcher.
 * <p>
 * Technically, it's not even a class hierarchy but
 * an interface with a finite number of known 
 * implementations. The example is inspired by a
 * common pattern found when coding plug-ins in Eclipse.
 * 
 * @author slescuyer
 */
public class Test3 {

	private Test3() {
		// Static utility only
	}
	
	// The base interface
	public interface IResource {
		static final int WORKSPACE_ROOT = 0;
		static final int PROJECT = 1;
		static final int FOLDER = 2;
		static final int FILE = 3;
		
		public int getType();
	}
	
	// The four dummy implementations
	// No-one else is allowed to extend that interface
	// because we said so
	public static class File implements IResource {
		public String name;
		
		public final int getType() {
			return FILE;
		}
	}
	
	public static class Folder implements IResource {
		public String folderName;
		
		public final int getType() {
			return FOLDER;
		}
	}
	
	public static class Project implements IResource {
		public String projectName;
		
		public final int getType() {
			return PROJECT;
		}
	}
	
	public static class WorkspaceRoot implements IResource {
		public final int getType() {
			return WORKSPACE_ROOT;
		}
	}
	
	// The external dispatcher
	public static enum IResourceKind {
		FILE(File.class), 
		FOLDER(Folder.class), 
		PROJECT(Project.class), 
		WORKSPACE_ROOT(WorkspaceRoot.class);
		
		private IResourceKind(Class<?> witness) {}
		
		@Hierarchy("")
		public static IResourceKind of(IResource res) {
			switch (res.getType()) {
			case IResource.WORKSPACE_ROOT: return WORKSPACE_ROOT;
			case IResource.PROJECT: return PROJECT;
			case IResource.FOLDER: return FOLDER;
			case IResource.FILE: return FILE;
			default:
				throw new IllegalArgumentException("Unknown resource type " + res.getType());
			}
		}
	}
	
	
	public String printNameOfResource(IResource ires) {
		switch (IResourceKind.of(ires)) { }
	}
}
