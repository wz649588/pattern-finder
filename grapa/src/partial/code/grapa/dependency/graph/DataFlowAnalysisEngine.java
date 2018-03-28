package partial.code.grapa.dependency.graph;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import org.apache.commons.collections15.Transformer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.internal.compiler.CompilationResult;
import org.eclipse.jdt.internal.compiler.DefaultErrorHandlingPolicies;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileConstants;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.parser.Parser;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;
import org.eclipse.jdt.internal.compiler.problem.ProblemReporter;
import org.eclipse.jdt.internal.core.JavaProject;
import org.eclipse.jdt.internal.core.util.CommentRecorderParser;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ClasspathUtilCore;
import org.eclipse.pde.internal.core.PDECore;

import partial.code.grapa.mapping.ClientMethod;
import partial.code.grapa.mapping.ClientMethodVisitor;
import partial.code.grapa.tool.FileUtils;
import partial.code.grapa.tool.visual.JGraphTViewer;























import com.ibm.wala.cast.ipa.callgraph.AstCallGraph;
import com.ibm.wala.cast.java.client.JDTJavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.client.JavaSourceAnalysisEngine;
import com.ibm.wala.cast.java.ipa.callgraph.AstJavaZeroXCFABuilder;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.ipa.modref.AstJavaModRef;
import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl;
import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.ConcreteJavaMethod;
import com.ibm.wala.cast.java.translator.jdt.JDTClassLoaderFactory;
import com.ibm.wala.cast.java.translator.jdt.JDTSourceLoaderImpl;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraph;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.FlowGraphBuilder;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.FuncVertex;
import com.ibm.wala.cast.js.callgraph.fieldbased.flowgraph.vertices.PropVertex;
import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.classLoader.SourceFileModule;
import com.ibm.wala.classLoader.SyntheticMethod;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.dataflow.IFDS.TabulationResult;
import com.ibm.wala.examples.analysis.dataflow.ContextSensitiveReachingDefs;
import com.ibm.wala.examples.drivers.PDFTypeHierarchy;
import com.ibm.wala.ide.classloader.EclipseSourceFileModule;
import com.ibm.wala.ide.util.EclipseProjectPath;
import com.ibm.wala.ide.util.JavaEclipseProjectPath;
import com.ibm.wala.ide.util.EclipseProjectPath.AnalysisScopeType;
import com.ibm.wala.ide.util.EclipseProjectPath.ILoader;
import com.ibm.wala.ide.util.EclipseProjectPath.Loader;
import com.ibm.wala.ide.util.JavaEclipseProjectPath.JavaSourceLoader;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.AbstractRootMethod;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.impl.ExplicitCallGraph;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.ExceptionalReturnCallee;
import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.HeapStatement.HeapParamCaller;
import com.ibm.wala.ipa.slicer.MethodEntryStatement;
import com.ibm.wala.ipa.slicer.MethodExitStatement;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.ParamCallee;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAAbstractThrowInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeName;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.Predicate;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashMapFactory;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.IteratorUtil;
import com.ibm.wala.util.collections.MapUtil;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.config.SetOfClasses;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.AbstractGraph;
import com.ibm.wala.util.graph.EdgeManager;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.graph.NodeManager;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.strings.Atom;
import com.ibm.wala.viz.DotUtil;
import com.ibm.wala.viz.NodeDecorator;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

import edu.uci.ics.jung.algorithms.shortestpath.DijkstraShortestPath;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.uci.ics.jung.io.GraphMLMetadata;
import edu.uci.ics.jung.io.GraphMLWriter;
import edu.vt.cs.graph.ClientClass;
import edu.vt.cs.graph.ClientField;
import edu.vt.cs.graph.PackageDetector;

public class DataFlowAnalysisEngine extends JavaSourceAnalysisEngine{
	private IR ir;
	
	private AnalysisOptions options;
	private JDTSourceLoaderImpl jdtloader = null; //added by nameng to avoid instance checking each time
	DataDependenceOptions dOptions = null;
	ControlDependenceOptions cOptions = null;
	
	public DataFlowAnalysisEngine() {
		dOptions = DataDependenceOptions.NO_HEAP_NO_EXCEPTIONS; 
		cOptions = ControlDependenceOptions.NO_EXCEPTIONAL_EDGES;
	}

	public void initClassHierarchy() {
		// TODO Auto-generated method stub
		IClassHierarchy cha = buildClassHierarchy();
		setClassHierarchy(cha);
		for (IClassLoader loader : cha.getLoaders()) {
			if (loader instanceof JDTSourceLoaderImpl) {
				jdtloader = (JDTSourceLoaderImpl) loader;
				break;
			}
		}
	}

	public void clearAnalysisScope() throws JavaModelException {
		// TODO Auto-generated method stub
		if(scope!=null){
			JavaSourceAnalysisScope js = (JavaSourceAnalysisScope)scope;
			ClassLoaderReference loader = js.getSourceLoader();
			List<Module> s = js.getModules(loader);
			s.clear();
		}
	}
	//added by nameng
	public TypeReference getOrCreateTypeReference(ClientClass c) {
		TypeReference tRef = c.tRef;
		if (tRef == null) {				
			TypeName name2 = TypeName.findOrCreate(c.sig);
			tRef = TypeReference.findOrCreate(jdtloader.getReference(), name2);
			c.tRef = tRef;			
		}
		return tRef;
	}
	
	
	// added by nameng
	public FieldReference getOrCreateFieldReference(ClientField field) {
		FieldReference fRef = field.fRef;
		if (fRef == null) {
			IClassHierarchy cha = this.getClassHierarchy();
			final Atom mainField = Atom.findOrCreateAsciiAtom(field.fieldName);
			IField f = null;
			
			TypeName name = TypeName.findOrCreate(field.key);
			IClass type = jdtloader.lookupClass(name);
			
			TypeName name2 = TypeName.findOrCreate(field.sig);
			TypeReference tf = TypeReference.findOrCreate(jdtloader.getReference(), name2);
			
			if (type != null && tf != null) {
				fRef = FieldReference.findOrCreate(type.getReference(), mainField, tf);
				field.fRef = fRef;
			}			
		}
		return fRef;
	}
	
	//added by nameng
	public MethodReference getOrCreateMethodReference(ClientMethod method) {
		MethodReference mRef = method.mRef;
		if (mRef == null) {		
			final Atom mainMethod = Atom.findOrCreateAsciiAtom(method.methodName);
			TypeName name = TypeName.findOrCreate(method.key);
			IClass type = jdtloader.lookupClass(name);
			if(type!=null){				
				mRef = getAmbiguousRef(type, mainMethod, method);
				method.mRef = mRef; 
			}
		}		
		return mRef;
	}
	//added by nameng
	public MethodReference getAmbiguousRef(IClass type, Atom mainMethod, ClientMethod method) {
		
		// modified by Ye Wang, 11/13/2016
		// Iterator<IMethod> iter = type.getAllMethods().iterator();
		Iterator<IMethod> iter = type.getDeclaredMethods().iterator();
		
		Map<String, IMethod> map = new HashMap<String, IMethod>();
		IMethod im = null;
		MethodReference mRef = null;
		IMethodBinding mb = method.methodbody.resolveBinding();
		String key = mb.getDeclaringClass().getQualifiedName();
		String methodName = ClientMethodVisitor.getName(mb);
		ITypeBinding[] pTypes = mb.getParameterTypes();
		int paramLen = pTypes.length;
		System.out.print("");
		
		while(iter.hasNext()) {
			im = iter.next();			
			if (im.getName().equals(mainMethod)) {
				int imparam = im.getNumberOfParameters();
				if (im.isStatic()) {
					if (imparam == paramLen)
						map.put(im.getSignature(), im);
				} else {
					if (imparam == paramLen + 1) {
						map.put(im.getSignature(), im);
					}
				}				
			}
		}
		if (map.size() == 1) {//fast path
			return map.entrySet().iterator().next().getValue().getReference();
		} else {	//slow path
			im = null;
			for (Entry<String, IMethod> entry : map.entrySet()) {
				im = entry.getValue();
				boolean isStatic = im.isStatic();
				boolean isSame = true;
				int delta = isStatic? 0 : 1;
				for (int i = delta; i < im.getNumberOfParameters(); i++) {
					TypeReference tRef = im.getParameterType(i);
					ITypeBinding pType = pTypes[i - delta];
					String tName = tRef.getName().toString();
					if (tName.startsWith("L")) {
						tName = tName.substring(1);
						tName = tName.replaceAll("/", ".");
						tName = tName.replaceAll("\\$", ".");
					}
					if (!tName.equals(pType.getBinaryName())) {
						isSame = false;
						break;
					}
				}
				if (isSame) {
					break; 
				}
 			}
			if (im != null) {
				mRef = im.getReference();
			} else {
				System.out.println("Need more process");
			}
		}
		return mRef;
	}
	
	public IBinding lookupBinding(IBinding b) {
		IBinding result = b;
		if (b instanceof IMethodBinding) {
			IMethodBinding mb = (IMethodBinding)b;
			String typeName = ClientMethodVisitor.getTypeKey(mb);
			TypeName name = TypeName.string2TypeName(typeName);
			IClass type = jdtloader.lookupClass(name);			
			if (type != null) {
				Iterator<IMethod> iter = type.getAllMethods().iterator();
				Map<String, IMethod> map = new HashMap<String, IMethod>();
				IMethod im = null;
				IMethod foundIm = null;
				String methodName = ClientMethodVisitor.getName(mb);
				ITypeBinding[] pTypes = mb.getParameterTypes();
				int paramLen = pTypes.length;
				while (iter.hasNext()) {
					im = iter.next();
					if (im.getName().equals(methodName)) {
						int imparam = im.getNumberOfParameters();
						if (im.isStatic()) {
							if (imparam == paramLen) {
								foundIm = im;
								break;
							}
						} else {
							if (imparam == paramLen + 1) {
								foundIm = im;
								break;
							}
						}
					}
				}
				if (foundIm != null) {
					return null;
				}
			} else{
				result = mb.getDeclaringClass();
			}
		}
		return result;
	}
	
	public AstJavaZeroXCFABuilder getCFGBuilder(ClientMethod method) {
		IClassHierarchy cha = this.getClassHierarchy();
		MethodReference mainRef = getOrCreateMethodReference(method);
		TypeName name = TypeName.findOrCreate(method.key);
		IClass type = jdtloader.lookupClass(name);
		// changed by Ye Wang
//		IMethod m = type.getMethod(mainRef.getSelector());
		IMethod m = mainRef == null ? null : type.getMethod(mainRef.getSelector());
		HashSet<Entrypoint> eps = HashSetFactory.make();
		if (m != null) {
			eps.add(new DefaultEntrypoint(m, cha));
		} 
				 
		AstJavaZeroXCFABuilder builder = null;
		if(eps.size()>0){
			options = getDefaultOptions(eps);
			options.setOnlyClientCode(true);
			try {
				builder = (AstJavaZeroXCFABuilder)buildCallGraph(cha, options, true, null);
				AnalysisCache ce =  builder.getAnalysisCache();
	        	ir = ce.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, options.getSSAOptions() );
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				exdist.ExceptionHandler.process(e);
			} catch (CancelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				exdist.ExceptionHandler.process(e);
			}
		}else{
		  System.out.println("Here!");
		}
		
		return builder;
	}
	
	// By Ye Wang, since 01/25/2017
	public AstJavaZeroXCFABuilder getCFGBuilder(MethodReference mainRef, IClass type) {
		IClassHierarchy cha = this.getClassHierarchy();
//		MethodReference mainRef = getOrCreateMethodReference(method);
//		TypeName name = TypeName.findOrCreate(method.key);
//		IClass type = jdtloader.lookupClass(name);
		IMethod m = type.getMethod(mainRef.getSelector());
		HashSet<Entrypoint> eps = HashSetFactory.make();
		if (m != null) {
			eps.add(new DefaultEntrypoint(m, cha));
		} 
				 
		AstJavaZeroXCFABuilder builder = null;
		if(eps.size()>0){
			options = getDefaultOptions(eps);
			options.setOnlyClientCode(true);
			try {
				builder = (AstJavaZeroXCFABuilder)buildCallGraph(cha, options, true, null);
				AnalysisCache ce =  builder.getAnalysisCache();
	        	ir = ce.getSSACache().findOrCreateIR(m, Everywhere.EVERYWHERE, options.getSSAOptions() );
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				exdist.ExceptionHandler.process(e);
			} catch (CancelException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				exdist.ExceptionHandler.process(e);
			}
		}else{
		  System.out.println("Here!");
		}
		
		return builder;
	}

	//added by nameng to prune SDG in a different way
	public SDG buildSystemDependencyGraph2(ClientMethod method) {
		AstJavaZeroXCFABuilder  builder =  getCFGBuilder(method);
		
		SDG sdg = null;
		System.out.print("");
//		Graph<Statement> g = null;
		if(builder!=null){			
			PointerAnalysis<InstanceKey> pointer = builder.getPointerAnalysis();
			sdg = new SDG(cg, pointer, new AstJavaModRef(), dOptions, cOptions);			
//			g = pruneSDG2(sdg);
		}
		return sdg;
	}
	
	private static Graph<Statement> pruneSDG2(final SDG sdg) {
		Predicate<Statement> f = new Predicate<Statement>() {
		      @Override public boolean test(Statement s) {
		    	  IMethod m = s.getNode().getMethod();
		    	  if (!(m instanceof ConcreteJavaMethod)) {
		    		  return false;
		    	  } else if (!(s instanceof NormalStatement)) {
		    		  return false;
		    	  } else {
		    		  return true;
		    	  }
		      }
		    };
		    return GraphSlicer.prune(sdg, f);
	}
	
	
	public SDGwithPredicate buildSystemDependencyGraph(ClientMethod method)  {
		// TODO Auto-generated method stub
		AstJavaZeroXCFABuilder  builder =  getCFGBuilder(method);
		SDGwithPredicate g = null;
		if(builder!=null){
			DataDependenceOptions dOptions = DataDependenceOptions.FULL; 
			ControlDependenceOptions cOptions = ControlDependenceOptions.NONE;
			PointerAnalysis<InstanceKey> pointer = builder.getPointerAnalysis();
			SDG sdg = new SDG(cg, pointer, new AstJavaModRef(), dOptions, cOptions);			
			g = pruneSDG(sdg, method);
		}
		return g;
	}
	
	private SDGwithPredicate pruneSDG(SDG g, ClientMethod method) {
		// TODO Auto-generated method stub
//		typeName = "L"+methodname.replace(".", "/");
		IntraPredicate p = new IntraPredicate(method);
		if (g == null) {
		     throw new IllegalArgumentException("g is null");
		}
		SDGwithPredicate graph = new SDGwithPredicate(g, p);
		return graph;
	}
	
	

	@Override
	protected ClassLoaderFactory getClassLoaderFactory(SetOfClasses exclusions) {
		// TODO Auto-generated method stub
		return new JDTClassLoaderFactory(exclusions);	
	}

	public ArrayList<ASTNode> addtoScope(String prn, Hashtable<File, String> oldPackageTable, String j2seDir, String libDir, String otherLibDir, String version,
			ArrayList<File> files) {
		// TODO Auto-generated method stub
		ArrayList<ASTNode> trees = null;
		try {
			super.buildAnalysisScope();
			resolveJ2sePathEntry(j2seDir);
			String clibDir = null;
			if (version != null) {
				clibDir = libDir+version;
				resolveLibraryPathEntry(clibDir);
			} 			
			resolveLibraryPathEntry(otherLibDir);
			//modified by Na Meng
//			trees = resolveSourcePathyEntry(prn,  oldPackageTable, files,  clibDir, otherLibDir);
			trees = resolveSourcePathyEntry(prn, oldPackageTable, files, clibDir, j2seDir);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			exdist.ExceptionHandler.process(e);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			exdist.ExceptionHandler.process(e);
		}
		return trees;
	}
	
	public void addtoScope(String prn, Hashtable<File, String> pTable,
			String j2seDir, String jarFileName, String otherLibDir, ArrayList<File> files) {
		// TODO Auto-generated method stub
		try {
			super.buildAnalysisScope();
			resolveJ2sePathEntry(j2seDir);	
			
			resolveSourcePathyEntry(prn,  pTable, files,  jarFileName, "");
			
			resolveJarFileName(jarFileName);
			File d = new File(otherLibDir);
			for(File f:d.listFiles()){
				resolveJarFileName(f.getAbsolutePath());				
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			exdist.ExceptionHandler.process(e);
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			exdist.ExceptionHandler.process(e);
		}
	}

	private JarFile resolveJarFileName(String jarFileName) throws IOException {
		File f = new File(jarFileName);
		JarFile j = new JarFile(f);
		Module module = (f.isDirectory() ? (Module) new BinaryDirectoryTreeModule(f) : (Module) new JarFileModule(j));
		scope.addToScope(scope.getApplicationLoader(), module);
		return j;
	}

	
	public void addtoScope(String j2seDir, String jarFileName){
	    try {
			super.buildAnalysisScope();
			resolveJ2sePathEntry(j2seDir);	
			resolveJarFileName(jarFileName);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			exdist.ExceptionHandler.process(e);
		}
	}
	
	private ArrayList<ASTNode> resolveSourcePathyEntry(String prn,
			Hashtable<File, String> pTable, ArrayList<File> files,
			String clibDir, String otherLibDir) throws CoreException {
		// TODO Auto-generated method stub
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		IWorkspaceRoot root= workspace.getRoot();
		IProject project= root.getProject(prn);
		if (project.exists()) {
			project.delete(true, null);
		}
//		if(!project.exists()){
			project.create(null);
			project.open(null);
			//set the Java nature
			IProjectDescription description = project.getDescription();
			description.setNatureIds(new String[] { JavaCore.NATURE_ID });
			 
			//create the project
			project.setDescription(description, null);
//		}
		IJavaProject javaProject = JavaCore.create(project);
		IClasspathEntry[] buildPath = {
				JavaCore.newSourceEntry(project.getFullPath().append("src")),
						JavaRuntime.getDefaultJREContainerEntry() };
		 
		javaProject.setRawClasspath(buildPath, project.getFullPath().append(
						"bin"), null);
		
		//create folder by using resources package
		IFolder folder = project.getFolder("src");
		if(folder.exists()){
			folder.delete(true, null);
		}
		folder.create(true, true, null);
		
		Set<IClasspathEntry> entries = new HashSet<IClasspathEntry>();
		entries.addAll(Arrays.asList(javaProject.getRawClasspath()));
		File d = null;
		if (clibDir != null) {
			d = new File(clibDir);
			if(d.isDirectory()){
				for(File f:d.listFiles()){
					if(f.getName().endsWith(".jar")){
						 IClasspathEntry libEntry = JavaCore.newLibraryEntry(
								    new Path(f.getAbsolutePath()), // library location
								    null, // source archive location
								    null, // source archive root path
								    true); // exported
						 entries.add(libEntry);
					}
				}
			}else if(d.isFile()){
				 IClasspathEntry libEntry = JavaCore.newLibraryEntry(
						    new Path(d.getAbsolutePath()), // library location
						    null, // source archive location
						    null, // source archive root path
						    true); // exported
				 entries.add(libEntry);
			}
		}
		
		if (otherLibDir != null) {
			d = new File(otherLibDir);
			if(d.exists()){
				for(File f:d.listFiles()){
					if(f.getName().endsWith(".jar")){
						 IClasspathEntry libEntry = JavaCore.newLibraryEntry(
								    new Path(f.getAbsolutePath()), // library location
								    null, // source archive location
								    null, // source archive root path
								    true); // exported
						 entries.add(libEntry);
					}
				}
			}
		}
		javaProject.setRawClasspath(entries.toArray(new IClasspathEntry[entries.size()]), null);

		//Add folder to Java element
		IPackageFragmentRoot srcFolder = javaProject
						.getPackageFragmentRoot(folder);
		// added by Na Meng
		List<IPackageFragment> srcPackages = new ArrayList<IPackageFragment>();
		for(File file:files){
			String pn = null;
			if (pTable != null)
				pn = pTable.get(file);
			else {
				ASTParser parser = ASTParser.newParser(AST.JLS8);
				parser.setSource(FileUtils.getContent(file).toCharArray());
				ASTNode cu = parser.createAST(null);
				PackageDetector pd = new PackageDetector(cu);
				pn = pd.packageName;
			}
			//create package fragment
			IPackageFragment pf = srcFolder.getPackageFragment(pn);
			if(!pf.exists()){
				pf = srcFolder.createPackageFragment(
						pn, true, null);
				srcPackages.add(pf); // added by Na Meng
			}
			String source = FileUtils.getContent(file);
			ICompilationUnit cu = pf.createCompilationUnit(file.getName(), source,
					false, null);
			IResource rs = cu.getResource();
			IFile ifile= (IFile)rs;
			EclipseSourceFileModule module = EclipseSourceFileModule.createEclipseSourceFileModule(ifile);
			JavaSourceAnalysisScope s = (JavaSourceAnalysisScope)scope;
			this.scope.addToScope(s.getSourceLoader(), module);			
		}
		
		ArrayList<ASTNode> trees = new  ArrayList<ASTNode>();
		//added by nameng--comment out these trees because they are useless
		/*
		for(IPackageFragment pf:srcPackages){
//			if(pf.getKind() == IPackageFragmentRoot.K_SOURCE){
				for(ICompilationUnit cu:pf.getCompilationUnits()){		
//					CompilerOptions options = new CompilerOptions();
//				    options.docCommentSupport = true;
//				    options.complianceLevel = ClassFileConstants.JDK1_8;
//				    options.sourceLevel = ClassFileConstants.JDK1_8;
//				    options.targetJDK = ClassFileConstants.JDK1_8;
//				    
//				    CommentRecorderParser commentParser =  new CommentRecorderParser(new ProblemReporter(
//				                DefaultErrorHandlingPolicies.proceedWithAllProblems(),
//				                options,
//				                new DefaultProblemFactory()), false);
//				    CompilationResult compilationResult =  new CompilationResult((org.eclipse.jdt.internal.compiler.env.ICompilationUnit) cu, 0, 0, options.maxProblemsPerUnit);
//				    CompilationUnitDeclaration ast = commentParser.parse((org.eclipse.jdt.internal.compiler.env.ICompilationUnit) cu, compilationResult);
//				    JavaCompilation jcu = new JavaCompilation(ast, commentParser.scanner);
				    
				    ASTParser parser = ASTParser.newParser(AST.JLS8);
					parser.setKind(ASTParser.K_COMPILATION_UNIT);
					parser.setProject(javaProject);
					parser.setSource(cu);
					parser.setResolveBindings(true);
					ASTNode tree = parser.createAST(null);
				    trees.add(tree);
				}
//			}
		}
		*/
		return trees;
	}



	private void resolveLibraryPathEntry(String dir) throws IOException {
		//added by Na in order to enable analysis even though the libDir is empty
		if (dir == null)
			return;
		File d = new File(dir);
		
		// Added by Ye Wang, to handle non-existing libDir
		if (!d.exists())
			return;
		
		for(File f:d.listFiles()){
			if(f.getName().endsWith(".jar")){
				JarFile j = new JarFile(f);
				Module module = (f.isDirectory() ? (Module) new BinaryDirectoryTreeModule(f) : (Module) new JarFileModule(j));
				scope.addToScope(scope.getApplicationLoader(), module);
			}
		}
	}

	private void resolveJ2sePathEntry(String j2seDir) throws IOException {
		// TODO Auto-generated method stub
		File file = new File(j2seDir+"rt.jar");
		JarFile j = new JarFile(file);
		Module module = (file.isDirectory() ? (Module) new BinaryDirectoryTreeModule(file) : (Module) new JarFileModule(j));
		scope.addToScope(scope.getPrimordialLoader(), module);
		
		File d = new File(j2seDir+"/ext/");
		for(File f:d.listFiles()){
			if(f.getName().endsWith(".jar")){
				j = new JarFile(f);
				module = (f.isDirectory() ? (Module) new BinaryDirectoryTreeModule(f) : (Module) new JarFileModule(j));
				scope.addToScope(scope.getExtensionLoader(), module);
			}
		}
	}

	

	public IR getCurrentIR() {
		// TODO Auto-generated method stub
		return ir;
	}

	
	



	
}
