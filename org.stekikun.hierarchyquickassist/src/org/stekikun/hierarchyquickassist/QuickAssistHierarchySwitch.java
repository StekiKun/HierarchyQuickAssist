package org.stekikun.hierarchyquickassist;
import java.util.List;
import java.util.function.Function;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.IMemberValuePairBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NameQualifiedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StructuralPropertyDescriptor;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.ThrowStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IQuickAssistProcessor;
import org.eclipse.jdt.ui.text.java.correction.ASTRewriteCorrectionProposal;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.viewers.StyledString;

public class QuickAssistHierarchySwitch implements IQuickAssistProcessor {

	@Override
	public boolean hasAssists(IInvocationContext context) throws CoreException {
		ASTNode coveredNode = context.getCoveringNode();
		return coveredNode != null;
	}

	@Override
	public IJavaCompletionProposal[] 
		getAssists(IInvocationContext context, IProblemLocation[] locations)
			throws CoreException {
		// One proposal without action which prints out the nature of the selected
		// AST node. Very useful for debugging, but don't forget to disable
		// before deployment.
		@SuppressWarnings({ "restriction", "unused" })
		IJavaCompletionProposal astSpy =
		new org.eclipse.jdt.internal.ui.text.java.AbstractJavaCompletionProposal() {
			private final String display;
			{
				ASTNode coveredNode = context.getCoveringNode();
				if (coveredNode == null)
					display = "Covered node is null";
				else {
					display = "Covered node is: " + coveredNode.getClass().getName() + 
							" (" + coveredNode.getNodeType() + ")";
				}
			}
			
			@Override
			public String getDisplayString() {
				return display;
			}
			
			@Override
			public StyledString getStyledDisplayString() {
				return new StyledString(display);
			}
			
			@Override
			public String getSortString() {
				return getDisplayString();
			}

			public void apply(ITextViewer viewer, char trigger, int stateMastk, int offset) {
				return;
			}
		};
		
//		return new IJavaCompletionProposal[] { astSpy };
		
		// If applicable, the actual proposal that will generate the
		// hierarchy switch
		IJavaCompletionProposal[] hierSwitch = getHierarchySwitchProposals(context);
		
		if (hierSwitch == null) 
			return new IJavaCompletionProposal[] { };
		else
			return hierSwitch;
	}
	
	private static boolean logging = false;
	private static void log(String s) {
		if (logging) System.out.println(s);
	}
	private static void err(String s) {
		System.err.println(s);
	}
	
	private static /* NULLABLE */ 
		IJavaCompletionProposal[]
		getHierarchySwitchProposals(IInvocationContext context) {
		ASTNode coveringNode = context.getCoveringNode();
		if (coveringNode == null) return null;
		
		// Is it a switch statement?
		if (coveringNode.getNodeType() != ASTNode.SWITCH_STATEMENT) {
			// Or maybe is it the expression in a switch statement?
			// (or a sub-expression thereof)
			ASTNode heir = coveringNode;
			ASTNode parent;
			while (true) {
				parent = heir.getParent();
				if (parent == null) return null;
				if (parent.getNodeType() == ASTNode.SWITCH_STATEMENT) break;
				heir = parent;
			}
			// Check that we weren't in the body part of a switch, in which
			// case it's too dangerous to propose to replace the whole switch
			// (Imagine a cascade of generated switches, and applying one of
			// the sub-switches incorrectly will apply to the outer switch...)
			if (((SwitchStatement) parent).getExpression() != heir)
				return null;
			coveringNode = (SwitchStatement) parent;
		}
		SwitchStatement ss = (SwitchStatement) coveringNode;
		if (ss.getExpression() == null) return null;
		Expression sw = ss.getExpression();
		// Now that we have a switch expression, we are interested in either
		// an expression whose type is an enum, obtained by a method annotated
		// as @Hierarchy, or a base hierarchy class, in which case we'll have
		// to add the method invocation / field access to retrieve the enum.
		ITypeBinding ty = sw.resolveTypeBinding();
		if (ty == null) return null;
		log("Type of switch expression is " + ty.getName());
		final IBinding binding;
		final Expression receiver;
		if (ty.isEnum()) {
			// If the type is an enum, it can't be the base of a class
			// hierarchy, so it has to be the result of an external kind method
			if (!(sw instanceof MethodInvocation)) return null;
			MethodInvocation m = (MethodInvocation) sw;
			IMethodBinding method = m.resolveMethodBinding();
			if (method == null) return null;
			if (m.arguments() == null || m.arguments().size() != 1) return null;
			Expression arg = (Expression) m.arguments().get(0);
			if (arg == null) return null;
			binding = method;
			receiver = arg;
		}
		else {
			// If the type is not an enum, we must be switching on
			// an instance of a base hierarchy class
			binding = ty;
			receiver = sw;
		}
		
		// Find the Hierarchy annotation on the class/method declaration
		// (note: we stop at the first one we find, it doesn't
		//  make sense to add more than one anyway)
		IAnnotationBinding[] annots = binding.getAnnotations();
		IAnnotationBinding hannot = null;
		if (annots.length != 0) {
			log("Annotations on binding: ");
			for (IAnnotationBinding annot : annots) {
				log(annot.toString());
				if (annot.getName().equals("Hierarchy")) {
					hannot = annot;
				}
			}
		}
		if (hannot == null) return null;
		
		// Interpret the configuration in the annotation
		final HierarchyConfig config =
			HierarchyConfig.of(ss.getAST(), binding, hannot, receiver);
		if (config == null) return null;
		
		EnumDeclaration enumDecl = getEnumDeclaration(context, config.enumType);
		if (enumDecl == null) {
			err("Could not find enum declaration for hierarchy kind");
			return null;
		}
		@SuppressWarnings("unchecked")
		List<EnumConstantDeclaration> kindDecls =
			(List<EnumConstantDeclaration>) enumDecl.enumConstants();
		
		final Function<AST, Statement> returnStatement = (AST ast) -> {
			ReturnStatement ret = ast.newReturnStatement();
			// 'return null' or simply 'return' depending on the return type
			// of the enclosing function
			if (!inVoidFunction(ss))
				ret.setExpression(ast.newNullLiteral());
			return ret;
		};
		ASTRewriteCorrectionProposal rewReturn =
			getHierarchySwitchRewrite(
					"Generate hierarchy switch (return)", 12,
					context, ss, config, kindDecls,
					returnStatement, true);
		ASTRewriteCorrectionProposal rewBreak =
			getHierarchySwitchRewrite(
					"Generate hierarchy switch (break)", 11,
					context, ss, config, kindDecls,
					(AST ast) -> ast.newBreakStatement(), false);
		
		return new IJavaCompletionProposal[] { rewReturn, rewBreak };
	}
	
	private static ASTRewriteCorrectionProposal
		getHierarchySwitchRewrite(
			String name, int relevance, IInvocationContext context,
			SwitchStatement ss, HierarchyConfig config, List<EnumConstantDeclaration> kindDecls,
			Function<AST, Statement> caseCloser, boolean withThrow) {
		final SwitchContext swCtxt =
			SwitchContext.of(ss, withThrow && config.unmatched != null);
		final AST ast = swCtxt.ast;
		final Expression sw = ss.getExpression();

		// Create the new switch to generate, initially empty
		SwitchStatement newss = ast.newSwitchStatement();
		
		// First create the expression inside the switch(),
		// it uses the expression which was initially in the switch()
		// as the receiver for either a method invocation, or a field
		// read (depending on the config) to retrieve the instance kind
		newss.setExpression(makeInstanceKind(sw, config, swCtxt.rew));
		
		// Now fill in the statements of the switch, which make up
		// all cases and their inner blocks
		@SuppressWarnings("unchecked")
		List<Statement> statements = (List<Statement>) newss.statements();
		for (EnumConstantDeclaration kindDecl : kindDecls) {
			SwitchCase scase = ast.newSwitchCase();
			// case EnumValue: ...
			scase.setExpression(ast.newSimpleName(kindDecl.getName().getIdentifier()));
			statements.add(scase);
			// .. : { ... }
			Block block = ast.newBlock();
			statements.add(block);
			@SuppressWarnings("unchecked")
			List<Statement> bstatements = (List<Statement>) block.statements();
			
			// The constant is initialized with a type literal T.class,
			// let's fetch T!
			Expression arg = (Expression) kindDecl.arguments().get(0);
			if (!(arg instanceof TypeLiteral))
				return null;
			Type ctype = ((TypeLiteral) arg).getType();
			ITypeBinding ctypeBinding = ctype.resolveBinding();
			String cid = variableNameOf(ctype);
			Type typeref = swCtxt.addImport(ctypeBinding);
			
			// final A a = (A) receiver
			VariableDeclarationFragment vdeclf = ast.newVariableDeclarationFragment();
			vdeclf.setName(ast.newSimpleName(cid));
			CastExpression ce = ast.newCastExpression();
			
			// NB: do not copy from newsw, it is lazily copied when rew is applied...
			ce.setExpression((Expression) ASTNode.copySubtree(ast, config.receiver));
			ce.setType(typeref);
			vdeclf.setInitializer(ce);
			
			VariableDeclarationStatement vdecl = ast.newVariableDeclarationStatement(vdeclf);
			@SuppressWarnings("unchecked")
			List<IExtendedModifier> modifiers = (List<IExtendedModifier>) vdecl.modifiers();
			modifiers.add(ast.newModifier(ModifierKeyword.FINAL_KEYWORD));
			vdecl.setType((Type) ASTNode.copySubtree(ast, typeref));
			
			bstatements.add(vdecl);
			
			// closing the case block
			bstatements.add(caseCloser.apply(ast));
		}

		// Record the new switch statement
		swCtxt.replaceSwitch(newss);
		
		// If a post statement is necessary, let's add it
		if (withThrow && config.unmatched != null) {
			// throw new ...
			ThrowStatement newThrow = ast.newThrowStatement();
			ClassInstanceCreation newNew = ast.newClassInstanceCreation();
			newThrow.setExpression(newNew);
			
			// .. ExnType(<expression returning the kind>)
			Type exnType = swCtxt.addImport(config.unmatched);
			newNew.setType(exnType);
			@SuppressWarnings("unchecked")
			List<Expression> newArgs = newNew.arguments();
			// We cannot just copy newss.getExpression() because its receiver
			// is a lazy copy of sw and thus is empty for now...
			newArgs.add(makeInstanceKind(sw, config, swCtxt.rew));
			
			// Record the new throw statement
			swCtxt.addThrow(newThrow);
		}
		
		// Apply changes to ASTRewrite and return the
		// corresponding proposal
		swCtxt.commit();
		ICompilationUnit cu = context.getCompilationUnit();
		ASTRewriteCorrectionProposal proposal =
			new ASTRewriteCorrectionProposal(name, cu, swCtxt.rew, relevance);
		proposal.setImportRewrite(swCtxt.imports);
		return proposal;
	}
	
	private static Expression makeInstanceKind(
			Expression sw, HierarchyConfig config, ASTRewrite rew) {
		final AST ast = rew.getAST();
		switch (config.dispatcherKind) {
		case METHOD: {
			Expression newReceiver = (Expression) rew.createCopyTarget(config.receiver);
			MethodInvocation mi = ast.newMethodInvocation();
			mi.setName(ast.newSimpleName(config.name));
			mi.setExpression(newReceiver);
			return mi;
		}
		case FIELD: {
			Expression newReceiver = (Expression) rew.createCopyTarget(config.receiver);
			FieldAccess fa = ast.newFieldAccess();
			fa.setName(ast.newSimpleName(config.name));
			fa.setExpression(newReceiver);
			return fa;
		}
		case EXTERNAL:
			Expression newSw = (Expression) rew.createCopyTarget(sw);
			return newSw;
		}
		throw new IllegalStateException("Unknown dispatcher kind: " + config.dispatcherKind);
	}
	
	private static EnumDeclaration 
		getEnumDeclaration(IInvocationContext context, ITypeBinding enumTypeBinding) {
		assert (enumTypeBinding.isEnum());

		// Java-doc ensures that getJavaElement should be non-null in
		// the case of an enum type, hence I'm not being cautious
		IType enumTypeModel = (IType) (enumTypeBinding.getJavaElement());
		
		// If the type is local, we already have an AST, no need to parse
		ICompilationUnit cuKind = enumTypeModel.getCompilationUnit();
		if (cuKind.equals(context.getCompilationUnit())) {
			ASTNode decl = context.getASTRoot().findDeclaringNode(enumTypeBinding);
			if (!(decl instanceof EnumDeclaration))
				/* catches null, mostly */ return null;
			return ((EnumDeclaration) decl);
		}
		
		// The type is external, we have to parse the corresponding resource
		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setResolveBindings(true);
		parser.setSource(cuKind);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		CompilationUnit root = (CompilationUnit) parser.createAST(null); // parse
		ASTNode decl = root.findDeclaringNode(enumTypeBinding.getKey());
		if (!(decl instanceof EnumDeclaration))
			/* catches null, mostly */ return null;
		return ((EnumDeclaration) decl);
	}
		
	private static enum DispatcherKind {
		METHOD, FIELD, EXTERNAL;
	}
	
	private static final class HierarchyConfig {
		final String name;
		final DispatcherKind dispatcherKind;
		final /* NULLABLE */ ITypeBinding unmatched;
		final Expression receiver;
		
		final ITypeBinding enumType;
		@SuppressWarnings("unused")
		final IMethodBinding unmatchedCtor;
		
		private HierarchyConfig(
			String name, DispatcherKind dispatcherKind, /* NULLABLE */ ITypeBinding unmatched,
			Expression receiver, ITypeBinding enumType, IMethodBinding unmatchedCtor) {
			this.name = name;
			this.dispatcherKind = dispatcherKind;
			this.unmatched = unmatched;
			this.receiver = receiver;
			this.enumType = enumType;
			this.unmatchedCtor = unmatchedCtor;
		}
		
		static HierarchyConfig of(AST ast, IBinding binding,
				IAnnotationBinding annot, Expression receiver) {
			String methodName = null;									// has to be filled below
			DispatcherKind dispatcherKind = DispatcherKind.METHOD;		// if missing, method
			ITypeBinding unmatched = null;								// if missing, null
			
			// Iterate on member-value pairs, including defaults
			for (IMemberValuePairBinding e : annot.getAllMemberValuePairs()) {
				String name = e.getName();
				Object o = e.getValue();
				if (name.equals("value")) {
					if (!(o instanceof String)) {
						err("In Hierarchy annotation, 'value' member is not a String");
						// Terminal error
						return null;
					}
					methodName = (String) o;
				} else if (name.equals("field")) {
					if (!(o instanceof Boolean)) {
						err("In Hierarchy annotation, ignored non-boolean 'field' member");
						continue;
					}
					dispatcherKind = ((Boolean) o) ? DispatcherKind.FIELD : DispatcherKind.METHOD;
				} else if (e.getName().equals("unmatched")) {
					if (!(o instanceof ITypeBinding)) {
						err("In Hierarchy annotation, 'unmatched' member is not a class");
						continue;
					}
					unmatched = (ITypeBinding) o;
				}
			}
			if (methodName == null) {
				err("In Hierarchy annotation, no 'value' member was found");
				return null;
			}
			
			// Now find the return type of the method, or the type of the field
			// with the specified name.
			final ITypeBinding enumType;
			if (binding instanceof ITypeBinding) {
				// If we are looking at the base of a class hierarchy,
				// look for the specified method/field
				ITypeBinding ty = (ITypeBinding) binding;
				if (dispatcherKind == DispatcherKind.METHOD) {
					IMethodBinding zeMethod = null;
					for (IMethodBinding mb : ty.getDeclaredMethods()) {
						if (mb.getName().equals(methodName)) {
							zeMethod = mb;
							break;
						}
					}
					if (zeMethod == null) {
						err("Could not find specified method " + methodName);
						return null;
					}
					log("Method on which to switch: " + zeMethod);
					enumType = zeMethod.getReturnType();
				}
				else {	// FIELD
					IVariableBinding zeField = null;
					for (IVariableBinding vb : ty.getDeclaredFields()) {
						if (vb.getName().equals(methodName)) {
							zeField = vb;
							break;
						}
					}
					if (zeField == null) {
						err("Could not find specified field " + methodName);
						return null;
					}
					log("Field on which to switch: " + zeField);
					enumType = zeField.getType();
				}
			}
			else {
				IMethodBinding meth = (IMethodBinding) binding;
				// If we are looking at an external method, the method
				// spec should be "", the field is ignored, and we simply
				// look at the method's return type
				if (!methodName.equals(""))
					err("Annotation value should be \"\" when using external dispatcher " + meth);
				
				log("Switch using external dispatcher: " + meth);
				enumType = meth.getReturnType();
				dispatcherKind = DispatcherKind.EXTERNAL;
			}
			
			// That type must be an enum
			if (enumType == null || !enumType.isEnum()) {
				err("Type of specified " 
					+ (dispatcherKind == DispatcherKind.FIELD ? "field " : "method ")
					+ methodName + " is not an Enum type");
				return null;
			}
			
			// Check the validity of the provided unmatched exception class
			IMethodBinding ctor = null;
			if (unmatched != null) {
				// First it must be a subtype of java.lang.Exception
				ITypeBinding exn = ast.resolveWellKnownType("java.lang.RuntimeException");
				if (!unmatched.isSubTypeCompatible(exn)) {
					err("Specified unmatched class " + unmatched.getQualifiedName()
						+ " is not a subtype of java.lang.RuntimeException");
					return null;
				}
				// Second find a constructor whose signature expects an Enum<>
				for (IMethodBinding idb : unmatched.getDeclaredMethods()) {
					log("Visiting method " + idb);
					if (!idb.isConstructor()) continue;
					ITypeBinding[] params = idb.getParameterTypes();
					if (params.length != 1) continue;
					ITypeBinding param = params[0];
					log(param.getQualifiedName());
					if (!param.isEnum() &&
						!param.getQualifiedName().equals("java.lang.Enum<?>"))
						continue;
					// Not quite sure how to really ensure param is Enum<?>
					// because Enum is not a 'well-known' type. So I'm
					// happy if I see any enum there.
					ctor = idb;
					log("Found suitable constructor for exception: " + idb);
					break;
				}
				if (ctor == null) {
					err("Specified unmatched class " + unmatched.getQualifiedName()
						+ " has no suitable constructor");
					return null;
				}
			}
			
			return new HierarchyConfig(methodName, dispatcherKind, unmatched,
					receiver, enumType, ctor);
		}
	}
	
	private static String variableNameOf(Type type) {
		if (type instanceof SimpleType) {
			SimpleType st = (SimpleType) type;
			Name n = st.getName();
			return variableNameOf(n);
		}
		else if (type instanceof QualifiedType) {
			QualifiedType qt = (QualifiedType) type;
			return qt.getName().getIdentifier().toLowerCase();
		}
		else if (type instanceof NameQualifiedType) {
			NameQualifiedType nqt = (NameQualifiedType) type;
			return nqt.getName().getIdentifier().toLowerCase();
		}
		else 
			return "default";
	}
	
	private static String variableNameOf(Name n) {
		SimpleName sn;
		if (n instanceof QualifiedName) {
			QualifiedName qn = (QualifiedName) n;
			sn = qn.getName();
		}
		else
			sn = (SimpleName) n;
		return sn.getIdentifier().toLowerCase();
	}
	
	private static boolean inVoidFunction(ASTNode node) {
		if (node.getNodeType() == ASTNode.METHOD_DECLARATION) {
			MethodDeclaration methDecl = (MethodDeclaration) node;
			Type retType = methDecl.getReturnType2();
			if (!retType.isPrimitiveType()) return false;
			PrimitiveType primType = (PrimitiveType) retType;
			return primType.getPrimitiveTypeCode() == PrimitiveType.VOID;
		}
		else {
			ASTNode parent = node.getParent();
			if (parent == node || parent == null) return false;
			return inVoidFunction(parent);
		}
	}
	
	private static abstract class SwitchContext {
		final AST ast;
		final Statement focus;
		protected final ASTNode parent;
		
		final ASTRewrite rew;
		final ImportRewrite imports;
		private final ImportRewriteContext importContext;
		
		private SwitchContext(Statement statement) {
			this.ast = statement.getAST();
			this.focus = statement;
			this.parent = statement.getParent();
			
			this.rew = ASTRewrite.create(ast);
			this.imports = ImportRewrite.create((CompilationUnit) statement.getRoot(), true);
			@SuppressWarnings("restriction")	// OK, internal but why should we reimplement that??
			final ImportRewriteContext importContext_ =
				new org.eclipse.jdt.internal.corext.codemanipulation.
					ContextSensitiveImportRewriteContext(statement, imports);
			this.importContext = importContext_;
		}
		
		Type addImport(ITypeBinding typeBinding) {
			return imports.addImport(typeBinding, ast, importContext);
		}
		
		abstract void replaceSwitch(Statement newSwitch);
		abstract void addThrow(Statement throwStatement);
		abstract void commit();
		
		static class InPlace extends SwitchContext {
			private InPlace(Statement statement) {
				super(statement);
				QuickAssistHierarchySwitch.log("Using in-place replacement");
			}

			@Override
			void replaceSwitch(Statement newSwitch) {
				rew.replace(focus, newSwitch, null);				
			}
			
			@Override
			void addThrow(Statement throwStatement) {
				throw new UnsupportedOperationException();
			}
			
			@Override
			void commit() {
				// Nothing to do
			}
		}

		static class NewBlock extends SwitchContext {
			private final Block newBlock;
			
			private NewBlock(Statement statement) {
				super(statement);
				QuickAssistHierarchySwitch.log("Using replacement by new block");
				newBlock = ast.newBlock();
			}

			@SuppressWarnings("unchecked")
			@Override
			void replaceSwitch(Statement newSwitch) {
				newBlock.statements().add(newSwitch);				
			}
			
			@SuppressWarnings("unchecked")
			@Override
			void addThrow(Statement throwStatement) {
				newBlock.statements().add(throwStatement);
			}
			
			@Override
			void commit() {
				rew.replace(focus, newBlock, null);
			}
		}
		
		static class ChildList extends SwitchContext {
			private final ListRewrite listRew;
			private final ASTNode nextElt;
			
			private ChildList(Statement statement, ChildListPropertyDescriptor descr) {
				super(statement);
				QuickAssistHierarchySwitch.log("Using child list insertion in parent");
				listRew = rew.getListRewrite(parent, descr);
				// Check that statement is in the parent's described list and
				// remember the node that follows, if any (for easier insertion
				// later in #addThrow)
				@SuppressWarnings("rawtypes")
				List l = (List) parent.getStructuralProperty(descr);
				int i = 0;
				for (; i < l.size(); ++i) {
					if (l.get(i) == statement) break;
				}
				if (i == l.size()) throw new IllegalStateException();
				if (i + 1 < l.size())
					this.nextElt = (ASTNode) (l.get(i+1));
				else
					this.nextElt = null;
			}

			@Override
			void replaceSwitch(Statement newSwitch) {
				listRew.replace(focus, newSwitch, null);
			}

			@Override
			void addThrow(Statement throwStatement) {
				if (nextElt == null)
					listRew.insertLast(throwStatement, null);
				else
					listRew.insertBefore(throwStatement, nextElt, null);
			}

			@Override
			void commit() {
				// Nothing to do
			}
		}
		
		static SwitchContext of(Statement statement, boolean withThrow) {
			// If no throw statement, it should always be possible
			// to make the changes in place
			if (!withThrow) return new InPlace(statement);
			
			StructuralPropertyDescriptor spd = statement.getLocationInParent();
			// No location should mean no parent, so we can assume standalone
			if (spd == null) return new NewBlock(statement);
			// If the statement is not part of a list, we'll have to introduce
			// a block if we have to add more than one statement
			if (spd.isChildProperty()) return new NewBlock(statement);
			// So the statement is part of a child list, we can be smart
			// and avoid a new block when inserting more than one statement
			if (spd.isChildListProperty()) 
				return new ChildList(statement, (ChildListPropertyDescriptor) spd);
			// Not supposed to encounter a simple property here
			throw new IllegalStateException();
		}
	}
	
	
	
}