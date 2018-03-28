package edu.vt.cs.changes;

import static java.nio.file.StandardOpenOption.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;

import partial.code.grapa.commit.CommitComparator;
import partial.code.grapa.commit.DependenceGraph;
import partial.code.grapa.dependency.graph.DataFlowAnalysisEngine;
import partial.code.grapa.mapping.ClientMethod;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.NodePair;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.TreeEditOperation;

import com.google.gson.Gson;
import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl.ConcreteJavaMethod;
import com.ibm.wala.cast.java.ssa.AstJavaInvokeInstruction;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCache;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.GetCaughtExceptionStatement;
import com.ibm.wala.ipa.slicer.PDG;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ipa.slicer.StatementWithInstructionIndex;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.ssa.SSASwitchInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.ssa.Value;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MemberReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.collections.Pair;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.StaxDriver;

import consolegsydit.Application;
import edu.uci.ics.jung.graph.DirectedSparseGraph;
import edu.vt.cs.graph.ChangeGraphUtil;
import edu.vt.cs.graph.ClientClass;
import edu.vt.cs.graph.ClientField;
import edu.vt.cs.graph.GraphUtil2;
import edu.vt.cs.graph.ReferenceEdge;
import edu.vt.cs.graph.ReferenceNode;
import edu.vt.cs.prediction.JsonTemplateContent;
import edu.vt.cs.prediction.JsonTemplateEvidence;
import edu.vt.cs.prediction.MethodPredictor;
import edu.vt.cs.prediction.NameProcessor;
import edu.vt.cs.prediction.SimilarityChecker;
import edu.vt.cs.prediction.SqlPredictData;
import edu.vt.cs.prediction.VariableTemplate;
import edu.vt.cs.prediction.ml.MethodAnalyzer;
import edu.vt.cs.prediction.neo.AstComparer;
import edu.vt.cs.prediction.neo.FieldNameVisitor;
import edu.vt.cs.prediction.neo.FieldReplacementChecker;
import edu.vt.cs.prediction.neo.FieldReplacementDatabaseInserter;
import edu.vt.cs.prediction.neo.NearStmtFinder;
import edu.vt.cs.prediction.neo.SimilarStatementMethodChecker;
import edu.vt.cs.sql.SqliteManager;
import edu.vt.cs.append.TopDownTreeMatcher;
import edu.vt.cs.changes.api.LineRange;
import edu.vt.cs.diffparser.util.SourceCodeRange;
import edu.vt.cs.editscript.json.EditScriptJson;
import edu.vt.cs.editscript.json.GraphDataJson;
import edu.vt.cs.editscript.json.GraphDataWithNodesJson;
import edu.vt.cs.extraction.PatternExtractor;
import edu.vt.cs.extraction.PatternDotUtil;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;

public class ChangeGrouper {

	
	public void groupBasedOnCallGraph(
			List<Pair<ClientMethod, ClientMethod>> mps,
			List<Pair<DependenceGraph, DependenceGraph>> list) {
		List<Set<ClientMethod>> lg = new ArrayList<Set<ClientMethod>>();
		List<Set<ClientMethod>> rg = new ArrayList<Set<ClientMethod>>();
		List<ClientMethod> lms = new ArrayList<ClientMethod>();
		List<ClientMethod> rms = new ArrayList<ClientMethod>();
		
		List<CallGraph> lcgs = new ArrayList<CallGraph>();
		List<CallGraph> rcgs = new ArrayList<CallGraph>();
		for (int i = 0; i < list.size(); i++) {
			Pair<ClientMethod, ClientMethod> mp = mps.get(i);
			lms.add(mp.fst);
			rms.add(mp.snd);
			Pair<DependenceGraph, DependenceGraph> p = list.get(i);
			lcgs.add(p.fst.sdg.getCallGraph());
			rcgs.add(p.snd.sdg.getCallGraph());
		}
		
		lg = groupMethods(lms, lcgs);
		rg = groupMethods(rms, rcgs);
		
		
		List<Set<Pair<ClientMethod, ClientMethod>>> groups = new ArrayList<Set<Pair<ClientMethod, ClientMethod>>>();
		for (int i = 0; i < lg.size(); i++) {
			Set<ClientMethod> mSet1 = lg.get(i);
			Set<Pair<ClientMethod, ClientMethod>> pairSet = new HashSet<Pair<ClientMethod, ClientMethod>>();
			Set<Integer> indexSet = new HashSet<Integer>();
			for (ClientMethod cm : mSet1) {
				int idx = lms.indexOf(cm);
				pairSet.add(mps.get(idx));
				indexSet.add(idx);
			}
			Set<Integer> indexSet2 = new HashSet<Integer>();
			for (int j = 0; j < rg.size(); j++) {
				Set<ClientMethod> mSet2 = rg.get(j);
				for (ClientMethod cm : mSet2) {
					int idx = rms.indexOf(cm);
					indexSet2.add(idx);
				}
				Set<Integer> copy = new HashSet<Integer>(indexSet2);
				copy.retainAll(indexSet);
				if (!copy.isEmpty()) {
					for (Integer idx : indexSet2) {
						pairSet.add(mps.get(idx));
					}
				}
			}
			groups.add(pairSet);
		}
	}
	
	
	public void groupChanges0(List<ChangeFact> cfList, CommitComparator comparator) {
		Set<MethodReference> oMRefs = new HashSet<MethodReference>();
		Set<ClientMethod> oldMethods = new HashSet<ClientMethod>();
		Set<MethodReference> nMRefs = new HashSet<MethodReference>();
		Set<ClientMethod> newMethods = new HashSet<ClientMethod>();
		
		Set<FieldReference> oFRefs = new HashSet<FieldReference>();
		Set<FieldReference> nFRefs = new HashSet<FieldReference>();
		
		Set<TypeReference> oCRefs = new HashSet<TypeReference>();
		Set<TypeReference> nCRefs = new HashSet<TypeReference>();
		
		DataFlowAnalysisEngine leftEngine = comparator.getLeftAnalysisEngine();
		DataFlowAnalysisEngine rightEngine = comparator.getRightAnalysisEngine();	
		
		ClientMethod m1 = null, m2 = null;
		for (ChangeFact cf : cfList) {
			for (Pair<ClientMethod, ClientMethod> p : cf.changedMethods) {
				m1 = p.fst;
				m2 = p.snd;
				leftEngine.getOrCreateMethodReference(m1);
				rightEngine.getOrCreateMethodReference(m2);
				oMRefs.add(m1.mRef);
				nMRefs.add(m2.mRef);
				oldMethods.add(m1);
				newMethods.add(m2);
			}
			for (ClientMethod cm : cf.insertedMethods) {
				rightEngine.getOrCreateMethodReference(cm);
				nMRefs.add(cm.mRef);				
			}
			newMethods.addAll(cf.insertedMethods);
			for (ClientMethod cm : cf.deletedMethods) {
				leftEngine.getOrCreateMethodReference(cm);
				oMRefs.add(cm.mRef);
			}
			oldMethods.addAll(cf.deletedMethods);
			ClientField f1 = null, f2 = null;
			for(Pair<ClientField, ClientField> p : cf.changedFields) {
				f1 = p.fst;
				f2 = p.snd;
				leftEngine.getOrCreateFieldReference(f1);
				rightEngine.getOrCreateFieldReference(f2);
				oFRefs.add(f1.fRef);
				nFRefs.add(f2.fRef);
			}
			for (ClientField f : cf.insertedFields) {
				rightEngine.getOrCreateFieldReference(f);
				nFRefs.add(f.fRef);
			}
			for (ClientField f : cf.deletedFields) {
				leftEngine.getOrCreateFieldReference(f);
				oFRefs.add(f.fRef);
			}
			
			ClientClass c1 = null, c2 = null;
			for (Pair<ClientClass, ClientClass> p : cf.changedClasses) {
				c1 = p.fst;
				c2 = p.snd;
				leftEngine.getOrCreateTypeReference(c1);
				rightEngine.getOrCreateTypeReference(c2);
				oCRefs.add(c1.tRef);
				nCRefs.add(c2.tRef);
			}
			for (ClientClass c : cf.insertedClasses) {
				rightEngine.getOrCreateTypeReference(c);
				nCRefs.add(c.tRef);
			}
			for (ClientClass c: cf.deletedClasses) {
				leftEngine.getOrCreateTypeReference(c);
				oCRefs.add(c.tRef);
			}
		}
		
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> lGraphs = new 
				ArrayList<DirectedSparseGraph<ReferenceNode, ReferenceEdge>>();
		
		relateChangesBasedOnIR(lGraphs, leftEngine, oldMethods, oMRefs, oFRefs, oCRefs);
		lGraphs = mergeChanges(lGraphs);			
		String dir = CommitComparator.resultDir + CommitComparator.bugName + "/";
		for (int i = 0; i < lGraphs.size(); i++) {
			ChangeGraphUtil.writeRelationGraph(lGraphs.get(i), dir + "left_" + i);
		}
		
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> rGraphs = new 
				ArrayList<DirectedSparseGraph<ReferenceNode, ReferenceEdge>>();
		relateChangesBasedOnIR(rGraphs, rightEngine, newMethods, nMRefs, nFRefs, nCRefs);
		rGraphs = mergeChanges(rGraphs);
		for (int i = 0; i < rGraphs.size(); i++) {
			ChangeGraphUtil.writeRelationGraph(rGraphs.get(i), dir + "right_" + i);
		}
		if(lGraphs.isEmpty() && rGraphs.isEmpty()) {
			System.out.println("No relationship");
		}		
//		combineChanges(lGraphs, rGraphs);
		
		// Added by Ye Wang, 10/02/2016
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> impactGraphs = new 
				ArrayList<DirectedSparseGraph<ReferenceNode, ReferenceEdge>>();
		relateAllChangesBasedOnIR(impactGraphs, leftEngine, rightEngine, oldMethods, newMethods,
				oMRefs, nMRefs, oFRefs, nFRefs, oCRefs, nCRefs);
		impactGraphs = mergeChanges(impactGraphs);
		for (int i = 0; i < impactGraphs.size(); i++) {
			ChangeGraphUtil.writeRelationGraph(impactGraphs.get(i), dir + "impact_" + i);
		}
	}
	
	/**
	 * extract the changes, make graphs, and save data of graphs
	 * @param cfList list of ChangeFact
	 * @param comparator commit comparator
	 */
	public void groupChanges(List<ChangeFact> cfList, CommitComparator comparator)  {
//		Set<MethodReference> oMRefs = new HashSet<MethodReference>();
//		Set<ClientMethod> oldMethods = new HashSet<ClientMethod>();
//		Set<MethodReference> nMRefs = new HashSet<MethodReference>();
//		Set<ClientMethod> newMethods = new HashSet<ClientMethod>();
//		
//		Set<FieldReference> oFRefs = new HashSet<FieldReference>();
//		Set<FieldReference> nFRefs = new HashSet<FieldReference>();
//		
//		Set<TypeReference> oCRefs = new HashSet<TypeReference>();
//		Set<TypeReference> nCRefs = new HashSet<TypeReference>();
		
		// ----
		Set<ClientMethod> oldChangedMethods = new HashSet<ClientMethod>();
		Set<ClientMethod> newChangedMethods = new HashSet<ClientMethod>();
		Set<ClientMethod> deletedMethods = new HashSet<ClientMethod>();
		Set<ClientMethod> insertedMethods = new HashSet<ClientMethod>();
		
		
		
		// -------
//		Set<MethodReference> changedMethodRefs = new HashSet<MethodReference>();
		Set<MethodReference> oldChangedMethodRefs = new HashSet<MethodReference>();
		Set<MethodReference> newChangedMethodRefs = new HashSet<MethodReference>();
		Set<MethodReference> insertedMethodRefs = new HashSet<MethodReference>();
		Set<MethodReference> deletedMethodRefs = new HashSet<MethodReference>();
		Set<FieldReference> changedFieldRefs = new HashSet<FieldReference>();
		Set<FieldReference> insertedFieldRefs = new HashSet<FieldReference>();
		Set<FieldReference> deletedFieldRefs = new HashSet<FieldReference>();
		Set<TypeReference> changedClassRefs = new HashSet<TypeReference>();
		Set<TypeReference> insertedClassRefs = new HashSet<TypeReference>();
		Set<TypeReference> deletedClassRefs = new HashSet<TypeReference>();
		
		Set<FieldReference> oldChangedFieldRefs = new HashSet<>();
		Set<FieldReference> newChangedFieldRefs = new HashSet<>();
		
		DataFlowAnalysisEngine leftEngine = comparator.getLeftAnalysisEngine();
		DataFlowAnalysisEngine rightEngine = comparator.getRightAnalysisEngine();	
		
		Map<ClientMethod, List<SourceCodeRange>> oldMethodToRange =
				new HashMap<ClientMethod, List<SourceCodeRange>>();
		Map<ClientMethod, List<SourceCodeRange>> newMethodToRange =
				new HashMap<ClientMethod, List<SourceCodeRange>>();
		
		Map<MethodReference, ClientMethod> refToClientMap = new HashMap<>();
		
		Map<MethodReference, MethodReference> newToOldMethodRefMap = new HashMap<>();
		Map<MethodReference, MethodReference> oldToNewMethodRefMap = new HashMap<>();
		
		Map<FieldReference, FieldReference> newToOldFieldRefMap = new HashMap<>();
		
		Map<MethodReference, List<TreeEditOperation>> oldMethodRefToScript = new HashMap<>();
		Map<MethodReference, List<TreeEditOperation>> newMethodRefToScript = new HashMap<>();
		
		Map<MethodReference, HashSet<NodePair>> oldMethodRefToMatch = new HashMap<>();
		Map<MethodReference, HashSet<NodePair>> newMethodRefToMatch = new HashMap<>();
		
		Map<MethodReference, ClientMethod> oldMRefToClient = new HashMap<>();
		Map<MethodReference, ClientMethod> newMRefToClient = new HashMap<>();
		
		Map<MethodReference, ASTNode> oMRefToAST = new HashMap<>();
		Map<MethodReference, ASTNode> nMRefToAST = new HashMap<>();
		
		// 03/08/2018, handle AC/DC containing relationships
		Map<TypeReference, List<MemberReference>> acToAmsRef = new HashMap<>();
		Map<TypeReference, List<MemberReference>> acToAfsRef = new HashMap<>();
		Map<TypeReference, List<MemberReference>> dcToDmsRef = new HashMap<>();
		Map<TypeReference, List<MemberReference>> dcToDfsRef = new HashMap<>();
		
		ClientMethod m1 = null, m2 = null;
		for (ChangeFact cf : cfList) {
			
			for (ClientMethod m: cf.lMethods) {
				MethodReference ref = leftEngine.getOrCreateMethodReference(m);
				ASTNode body = m.methodbody;
//				if (body == null) {
//					System.out.println("Empty method body");
//					System.out.println(m.getSignature());
//					System.exit(-1);
//				}
				oMRefToAST.put(ref, body);
			}
			
			for (ClientMethod m: cf.rMethods) {
				MethodReference ref = rightEngine.getOrCreateMethodReference(m);
				ASTNode body = m.methodbody;
//				if (body == null) {
//					System.out.println("Empty method body");
//					System.out.println(m.getSignature());
//					System.exit(-1);
//				}
				nMRefToAST.put(ref, body);
			}
			
			for (Pair<ClientMethod, ClientMethod> p : cf.changedMethods) {
				m1 = p.fst;
				m2 = p.snd;
				leftEngine.getOrCreateMethodReference(m1);
				rightEngine.getOrCreateMethodReference(m2);
				
				oldMRefToClient.put(m1.mRef, m1);
				newMRefToClient.put(m2.mRef, m2);
				
				if (cf.oldMethodToScript.containsKey(m1))
					oldMethodRefToScript.put(m1.mRef, cf.oldMethodToScript.get(m1));
				if (cf.newMethodToScript.containsKey(m2))
					newMethodRefToScript.put(m2.mRef, cf.newMethodToScript.get(m2));
				
				if (cf.oldMethodToMatch.containsKey(m1))
					oldMethodRefToMatch.put(m1.mRef, cf.oldMethodToMatch.get(m1));
				if (cf.newMethodToMatch.containsKey(m2))
					newMethodRefToMatch.put(m2.mRef, cf.newMethodToMatch.get(m2));
					
				
				// ----
//				changedMethodRefs.add(m1.mRef);
				oldChangedMethodRefs.add(m1.mRef);
				newChangedMethodRefs.add(m2.mRef);
				oldChangedMethods.add(m1);
				newChangedMethods.add(m2);
				
				refToClientMap.put(m1.mRef, m1);
//				refToClientMap.put(m2.mRef, m2);
				
				newToOldMethodRefMap.put(m2.mRef, m1.mRef);
				oldToNewMethodRefMap.put(m1.mRef, m2.mRef);
				
//				oMRefs.add(m1.mRef);
//				nMRefs.add(m2.mRef);
//				oldMethods.add(m1);
//				newMethods.add(m2);
			}
			
			// extract SourceCodeRange
			for (ChangeMethodData data: cf.changedMethodData) {
				oldMethodToRange.put(data.oldMethod, data.oldASTRanges);
				newMethodToRange.put(data.newMethod, data.newASTRanges);
			}
			
			
			for (ClientMethod cm : cf.insertedMethods) {
				rightEngine.getOrCreateMethodReference(cm);
				
				newMRefToClient.put(cm.mRef, cm);
				
				if (cf.newMethodToScript.containsKey(cm))
					newMethodRefToScript.put(cm.mRef, cf.newMethodToScript.get(cm));
				
				// ----
				if (cm.mRef != null) {
					insertedMethodRefs.add(cm.mRef);
					insertedMethods.add(cm);
				}
				
				refToClientMap.put(cm.mRef, cm);
				
				
//				nMRefs.add(cm.mRef);
			}
//			newMethods.addAll(cf.insertedMethods);
			
			// ----
//			insertedMethods.addAll(cf.insertedMethods);
			
			for (ClientMethod cm : cf.deletedMethods) {
				leftEngine.getOrCreateMethodReference(cm);
				
				oldMRefToClient.put(cm.mRef, cm);
				
				if (cf.oldMethodToScript.containsKey(cm))
					oldMethodRefToScript.put(cm.mRef, cf.oldMethodToScript.get(cm));
				
				// ----
				if (cm.mRef != null) {
					deletedMethodRefs.add(cm.mRef);
					deletedMethods.add(cm);
				}
					
				
//				oMRefs.add(cm.mRef);
			}
//			oldMethods.addAll(cf.deletedMethods);
			
			// ----
//			deletedMethods.addAll(cf.deletedMethods);
			
			ClientField f1 = null, f2 = null;
			for(Pair<ClientField, ClientField> p : cf.changedFields) {
				f1 = p.fst;
				f2 = p.snd;
				leftEngine.getOrCreateFieldReference(f1);
				rightEngine.getOrCreateFieldReference(f2);
				
				// ----
				changedFieldRefs.add(f1.fRef);
				
				oldChangedFieldRefs.add(f1.fRef);
				newChangedFieldRefs.add(f2.fRef);
				
				newToOldFieldRefMap.put(f2.fRef, f1.fRef);
				
				
//				oFRefs.add(f1.fRef);
//				nFRefs.add(f2.fRef);
			}
			for (ClientField f : cf.insertedFields) {
				rightEngine.getOrCreateFieldReference(f);
				
				//---- 
				if (f.fRef != null)
					insertedFieldRefs.add(f.fRef);
				
//				nFRefs.add(f.fRef);
			}
			for (ClientField f : cf.deletedFields) {
				leftEngine.getOrCreateFieldReference(f);
				
				// ----
				if (f.fRef != null)
					deletedFieldRefs.add(f.fRef);
				
//				oFRefs.add(f.fRef);
			}
			
			ClientClass c1 = null, c2 = null;
			for (Pair<ClientClass, ClientClass> p : cf.changedClasses) {
				c1 = p.fst;
				c2 = p.snd;
				leftEngine.getOrCreateTypeReference(c1);
				rightEngine.getOrCreateTypeReference(c2);
				
				// ----
				changedClassRefs.add(c1.tRef);
				
//				oCRefs.add(c1.tRef);
//				nCRefs.add(c2.tRef);
			}
			for (ClientClass c : cf.insertedClasses) {
				rightEngine.getOrCreateTypeReference(c);
				
				//----
				insertedClassRefs.add(c.tRef);
				
//				nCRefs.add(c.tRef);
			}
			for (ClientClass c: cf.deletedClasses) {
				leftEngine.getOrCreateTypeReference(c);
				
				// ----
				deletedClassRefs.add(c.tRef);
				
//				oCRefs.add(c.tRef);
			}
			
			// 03/08/2018, handle AC/DC containing relationships
			for (ClientClass c: cf.acToAms.keySet()) {
				rightEngine.getOrCreateTypeReference(c);
				TypeReference tRef = c.tRef;
				if (tRef == null)
					continue;
				List<ClientMethod> ams = cf.acToAms.get(c);
				List<MemberReference> mRefs = new ArrayList<>();
				for (ClientMethod m: ams) {
					MethodReference mRef = rightEngine.getOrCreateMethodReference(m);
					if (mRef != null)
						mRefs.add(mRef);
				}
				acToAmsRef.put(tRef, mRefs);
			}
			for (ClientClass c: cf.acToAfs.keySet()) {
				TypeReference tRef = rightEngine.getOrCreateTypeReference(c);
				if (tRef == null)
					continue;
				List<ClientField> afs = cf.acToAfs.get(c);
				List<MemberReference> fRefs = new ArrayList<>();
				for (ClientField f: afs) {
					FieldReference fRef = rightEngine.getOrCreateFieldReference(f);
					if (fRef != null)
						fRefs.add(fRef);
				}
				acToAfsRef.put(tRef, fRefs);
			}
			for (ClientClass c: cf.dcToDms.keySet()) {
				TypeReference tRef = leftEngine.getOrCreateTypeReference(c);
				if (tRef == null)
					continue;
				List<ClientMethod> dms = cf.dcToDms.get(c);
				List<MemberReference> mRefs = new ArrayList<>();
				for (ClientMethod m: dms) {
					MethodReference mRef = leftEngine.getOrCreateMethodReference(m);
					if (mRef != null)
						mRefs.add(mRef);
				}
				dcToDmsRef.put(tRef, mRefs);
			}
			for (ClientClass c: cf.dcToDfs.keySet()) {
				TypeReference tRef = leftEngine.getOrCreateTypeReference(c);
				if (tRef == null)
					continue;
				List<ClientField> dfs = cf.dcToDfs.get(c);
				List<MemberReference> fRefs = new ArrayList<>();
				for (ClientField f: dfs) {
					FieldReference fRef = leftEngine.getOrCreateFieldReference(f);
					if (fRef != null)
						fRefs.add(fRef);
				}
				dcToDfsRef.put(tRef, fRefs);
			}
			
		}
		
		String dir = CommitComparator.resultDir + CommitComparator.bugName + "/";
		
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> impactGraphs = new 
				ArrayList<DirectedSparseGraph<ReferenceNode, ReferenceEdge>>();
		
		// make graphs
//		analyzeChangeLink(impactGraphs, leftEngine, rightEngine, oldChangedMethods, newChangedMethods,
//				insertedMethods, deletedMethods, changedMethodRefs, insertedMethodRefs, deletedMethodRefs,
//				insertedFieldRefs, deletedFieldRefs, oldMethodToRange, newMethodToRange);
		
		// analyze changed entities' links
		analyzeFieldAccess(impactGraphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				deletedFieldRefs, ReferenceNode.DF);
		analyzeFieldAccess(impactGraphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				oldChangedFieldRefs, ReferenceNode.CF);
		analyzeFieldAccess(impactGraphs, leftEngine, deletedMethods, ReferenceNode.DM,
				deletedFieldRefs, ReferenceNode.DF);
		analyzeFieldAccess(impactGraphs, leftEngine, deletedMethods, ReferenceNode.DM,
				oldChangedFieldRefs, ReferenceNode.CF);
		
		analyzeFieldAccess(impactGraphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				insertedFieldRefs, ReferenceNode.AF);
		analyzeFieldAccess(impactGraphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				newChangedFieldRefs, ReferenceNode.CF);
		analyzeFieldAccess(impactGraphs, rightEngine, insertedMethods, ReferenceNode.AM,
				insertedFieldRefs, ReferenceNode.AF);
		analyzeFieldAccess(impactGraphs, rightEngine, insertedMethods, ReferenceNode.AM,
				newChangedFieldRefs, ReferenceNode.CF);
		
		analyzeMethodToMethod(impactGraphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				oldMethodToRange, deletedMethodRefs, ReferenceNode.DM);
		analyzeMethodToMethod(impactGraphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				oldMethodToRange, oldChangedMethodRefs, ReferenceNode.CM);
		analyzeMethodToMethod(impactGraphs, leftEngine, deletedMethods, ReferenceNode.DM,
				null, deletedMethodRefs, ReferenceNode.DM);
		analyzeMethodToMethod(impactGraphs, leftEngine, deletedMethods, ReferenceNode.DM,
				null, oldChangedMethodRefs, ReferenceNode.CM);
		analyzeMethodToMethod(impactGraphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				newMethodToRange, insertedMethodRefs, ReferenceNode.AM);
		analyzeMethodToMethod(impactGraphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				newMethodToRange, newChangedMethodRefs, ReferenceNode.CM);
		analyzeMethodToMethod(impactGraphs, rightEngine, insertedMethods, ReferenceNode.AM,
				null, insertedMethodRefs, ReferenceNode.AM);
		analyzeMethodToMethod(impactGraphs, rightEngine, insertedMethods, ReferenceNode.AM,
				null, newChangedMethodRefs, ReferenceNode.CM);
		
		impactGraphs = convertNewVersionToOldVersion(impactGraphs, newToOldMethodRefMap);
		impactGraphs = convertNewVersionToOldVersion(impactGraphs, newToOldFieldRefMap);
		
		// analyzeClassContain
		addClassContainEdges(impactGraphs, acToAmsRef, ReferenceNode.AC, ReferenceNode.AM);
		addClassContainEdges(impactGraphs, acToAfsRef, ReferenceNode.AC, ReferenceNode.AF);
		addClassContainEdges(impactGraphs, dcToDmsRef, ReferenceNode.DC, ReferenceNode.DM);
		addClassContainEdges(impactGraphs, dcToDfsRef, ReferenceNode.DC, ReferenceNode.DF);
		
		// 02/18/2018, add isolated nodes
		addIsolatedNodes(impactGraphs, deletedMethodRefs, ReferenceNode.DM);
		addIsolatedNodes(impactGraphs, insertedMethodRefs, ReferenceNode.AM);
		addIsolatedNodes(impactGraphs, oldChangedMethodRefs, ReferenceNode.CM);
		addIsolatedNodes(impactGraphs, deletedFieldRefs, ReferenceNode.DF);
		addIsolatedNodes(impactGraphs, insertedFieldRefs, ReferenceNode.AF);
		addIsolatedNodes(impactGraphs, oldChangedFieldRefs, ReferenceNode.CF);
		
		
//		relateAllChangesBasedOnIR(impactGraphs, leftEngine, rightEngine, oldMethods, newMethods,
//				oMRefs, nMRefs, oFRefs, nFRefs, oCRefs, nCRefs);
		impactGraphs = mergeChanges(impactGraphs);
		
		
		
		
		
		
		
		// get all classes in IClass
		Set<IClass> allFoundClasses = new HashSet<>();
		IClassHierarchy leftHierarchy = leftEngine.getClassHierarchy();
//		Set<MethodReference> methodUsedToLookUpClass = new HashSet<>();
//		Set<FieldReference> fieldUsedToLookUpClass = new HashSet<>();
//		methodUsedToLookUpClass.addAll(oldChangedMethodRefs);
//		methodUsedToLookUpClass.addAll(deletedMethodRefs);
//		fieldUsedToLookUpClass.addAll(changedFieldRefs);
//		fieldUsedToLookUpClass.addAll(deletedFieldRefs);
//		for (MethodReference mRef: methodUsedToLookUpClass) {
//			TypeReference type = mRef.getDeclaringClass();
//			IClass klass = leftHierarchy.lookupClass(type);
//			allFoundClasses.add(klass);
//		}
//		for (FieldReference fRef: fieldUsedToLookUpClass) {
//			TypeReference type = fRef.getDeclaringClass();
//			IClass klass = leftHierarchy.lookupClass(type);
//			allFoundClasses.add(klass);
//		}
//		for (TypeReference tRef: deletedClassRefs) {
//			IClass klass = leftHierarchy.lookupClass(tRef);
//			allFoundClasses.add(klass);
//		}

		
		for (ChangeFact cf: cfList) {
			for (ClientClass c: cf.lClasses) {
				TypeReference ref = leftEngine.getOrCreateTypeReference(c);
				if (ref != null) {
					IClass klass = leftHierarchy.lookupClass(ref);
					if (klass != null)
						allFoundClasses.add(klass);
				}
			}
		}
		
		
		
		// store the package for every class
		Map<IClass, String> classToPackage = new HashMap<>();
		for (IClass klass: allFoundClasses) {
			String packageName = this.getPackageOfClass(klass);
			classToPackage.put(klass, packageName);
		}
		
		
		
		
		
		List<SqlPredictData> predictCMSqlValues = new ArrayList<>();
		
		List<String> graphDataValues = new ArrayList<>();
		List<String> editScriptValues = new ArrayList<>();
		Gson gson = new Gson();
		for (int i = 0; i < impactGraphs.size(); i++) {
			// write XML file
//			writeRelationGraph(impactGraphs.get(i), dir + "impact_" + i);
			
			// write impact graphs to PDF files
			String resultOutputPath = dir + "impact_" + i + ".pdf";
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> jung = impactGraphs.get(i);
			Graph<ReferenceNode, ReferenceEdge> jgrapht = convertJungToJGraphT(jung);
//			PatternDotUtil.dotify(jgrapht, resultOutputPath);
			
			//#################EDIT SCRIPT TABLE#############
			boolean storeNodes = true;
			if (storeNodes) {
				GraphDataWithNodesJson graphJson = new GraphDataWithNodesJson();
				for (ReferenceNode node: jgrapht.vertexSet()) {
					graphJson.addNode(node);
				}
				for (ReferenceEdge edge: jgrapht.edgeSet()) {
					graphJson.addEdge(edge);
				}
				graphDataValues.add(gson.toJson(graphJson));
			} else {
				GraphDataJson graphDataJson = new GraphDataJson();
				for (ReferenceEdge edge: jgrapht.edgeSet()) {
					graphDataJson.addEdge(edge);
				}
				graphDataValues.add(gson.toJson(graphDataJson));
			}
			
			EditScriptJson editScriptJson = new EditScriptJson();
			for (MethodReference mRef: oldMethodRefToScript.keySet()) {
				String sig = mRef.getSignature();
				if (oldMethodRefToScript.containsKey(mRef))
					editScriptJson.addOldScript(sig, oldMethodRefToScript.get(mRef));
			}
			for (MethodReference mRef: newMethodRefToScript.keySet()) {
				String sig = mRef.getSignature();
				if (newMethodRefToScript.containsKey(mRef))
					editScriptJson.addNewScript(sig, newMethodRefToScript.get(mRef));
			}
			editScriptValues.add(gson.toJson(editScriptJson));
			//#####################################
			
			
			
			
			
			// analyze peer variables
			// *********Don't delete this*************
//			analyzePeerVariable(jgrapht, newToOldMethodRefMap, leftEngine);
			// ***************************************
			
			
			// Predict methods that should use AF
			// *********Don't delete this*************
//			for (ReferenceNode node: jgrapht.vertexSet()) {
//				if (node.type != ReferenceNode.AF) // ensure the node is a AF node
//					continue;
//				predictChangedMethods(node, jgrapht, newToOldMethodRefMap, leftEngine, rightEngine, allFoundClasses, predictCMSqlValues);
//			}
			// ***************************************
			
			// Predict CMs with AF and 1 CM
			// ***************************************
//			for (ReferenceNode node: jgrapht.vertexSet()) {
//				if (node.type != ReferenceNode.AF)
//					continue;
//				predictCmWithAfCm(node, jgrapht, newToOldMethodRefMap, oldToNewMethodRefMap,
//						leftEngine, rightEngine, allFoundClasses, oldMRefToClient, newMRefToClient,
//						oldMethodRefToMatch, newMethodRefToMatch, oMRefToAST, nMRefToAST);
//			}
			// ***************************************
			
			// Analyze CMs -> AM graphs
			//****************************************
//			for (ReferenceNode node: jgrapht.vertexSet()) {
//				if (node.type == ReferenceNode.AM) {
//					analyzeCmAmGraphs(node, jgrapht, newToOldMethodRefMap, leftEngine, rightEngine);
//				}
//			}
			//****************************************
			
		}
		
		//###########DO NOT DELETE, EDIT SCRIPT TABLE#################
		final boolean withCommitType = false;
		final boolean outputEmptyData = false;
		if (!impactGraphs.isEmpty()) {
			StringBuilder editScriptSqlBuilder = new StringBuilder();
			String resultTable = Application.editScriptTable;
			if (withCommitType) {
				editScriptSqlBuilder.append("INSERT INTO " + resultTable + " (commit_type, bug_name, graph_num, graph_data, edit_script) VALUES ");
				for (int sqlValueNum = 0; sqlValueNum < impactGraphs.size() - 1; sqlValueNum++) {
					editScriptSqlBuilder.append("(\"");
					editScriptSqlBuilder.append(Application.currentCommitType);
					editScriptSqlBuilder.append("\",\"");
					editScriptSqlBuilder.append(CommitComparator.bugName);
					editScriptSqlBuilder.append("\",");
					editScriptSqlBuilder.append(sqlValueNum);
					editScriptSqlBuilder.append(",?,?),");
				}
				editScriptSqlBuilder.append("(\"");
				editScriptSqlBuilder.append(Application.currentCommitType);
				editScriptSqlBuilder.append("\",\"");
				editScriptSqlBuilder.append(CommitComparator.bugName);
				editScriptSqlBuilder.append("\",");
				editScriptSqlBuilder.append(impactGraphs.size() - 1);
				editScriptSqlBuilder.append(",?,?)");
			} else {
				editScriptSqlBuilder.append("INSERT INTO " + resultTable + " (bug_name, graph_num, graph_data, edit_script) VALUES ");
				for (int sqlValueNum = 0; sqlValueNum < impactGraphs.size() - 1; sqlValueNum++) {
					editScriptSqlBuilder.append("(\"");
					editScriptSqlBuilder.append(CommitComparator.bugName);
					editScriptSqlBuilder.append("\",");
					editScriptSqlBuilder.append(sqlValueNum);
					editScriptSqlBuilder.append(",?,?),");
				}
				editScriptSqlBuilder.append("(\"");
				editScriptSqlBuilder.append(CommitComparator.bugName);
				editScriptSqlBuilder.append("\",");
				editScriptSqlBuilder.append(impactGraphs.size() - 1);
				editScriptSqlBuilder.append(",?,?)");
			}
			
			Connection connection = SqliteManager.getConnection();
			try {
				java.sql.PreparedStatement stmt = connection.prepareStatement(editScriptSqlBuilder.toString());
				for (int i = 0; i < impactGraphs.size(); i++) {
					stmt.setString(2 * i + 1, graphDataValues.get(i));
					stmt.setString(2 * i + 2, editScriptValues.get(i));
				}
				stmt.executeUpdate();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} else if (outputEmptyData) { // 02/18/2018
			if (withCommitType) {
				String resultTable = Application.editScriptTable;
				String sql = "INSERT INTO " + resultTable + " (bug_name,graph_num,graph_data,edit_script) VALUES (?,0,NULL,NULL)";
				Connection connection = SqliteManager.getConnection();
				try {
					java.sql.PreparedStatement stmt = connection.prepareStatement(sql);
					stmt.setString(1, CommitComparator.bugName);
					stmt.executeUpdate();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				} finally {
					try {
						connection.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			} else {
				String resultTable = Application.editScriptTable;
				String sql = "INSERT INTO " + resultTable + " (commit_type,bug_name,graph_num,graph_data,edit_script) VALUES (?,?,0,NULL,NULL)";
				Connection connection = SqliteManager.getConnection();
				try {
					java.sql.PreparedStatement stmt = connection.prepareStatement(sql);
					stmt.setString(1, Application.currentCommitType);
					stmt.setString(2, CommitComparator.bugName);
					stmt.executeUpdate();
					stmt.close();
				} catch (SQLException e) {
					e.printStackTrace();
				} finally {
					try {
						connection.close();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			}
			
		}
		
		//#################################################
		
		
		// Predict methods that should use AF
		// *********Don't delete this*************
//		insertPredictCMDataToDatabase(predictCMSqlValues);
		// ***************************************
		
	}
	
	private void addClassContainEdges(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
			Map<TypeReference, List<MemberReference>> typeToMemberList,
			int fromNodeType, int toNodeType) {
		for (TypeReference tRef: typeToMemberList.keySet()) {
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph =
					new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
			graphs.add(graph);
			ReferenceNode root = new ReferenceNode(tRef, fromNodeType);
			List<? extends MemberReference> memberList = typeToMemberList.get(tRef);
			for (MemberReference ref: memberList) {
				ReferenceNode n = new ReferenceNode(ref, toNodeType);
				ReferenceEdge edge = new ReferenceEdge(root, n, ReferenceEdge.CLASS_CONTAIN);
				graph.addEdge(edge, root, n);
				edge.increaseCount();
			}
		}
	}
	
	private void addIsolatedNodes(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
			Set<? extends MemberReference> refs, int nodeType) {
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphsToBeAdded = new ArrayList<>();
		for (MemberReference ref: refs) {
			boolean refExistsInGraphs = false;
			for (DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph: graphs) {
				for (ReferenceNode node: graph.getVertices()) {
					if (node.ref == ref) {
						refExistsInGraphs = true;
						break;
					}
				}
				if (refExistsInGraphs)
					break;
			}
			if (!refExistsInGraphs) {
				DirectedSparseGraph<ReferenceNode, ReferenceEdge> g = new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
				ReferenceNode node = new ReferenceNode(ref, nodeType);
				g.addVertex(node);
				graphsToBeAdded.add(g);
			}
		}
		graphs.addAll(graphsToBeAdded);
	}
	
	// Use AF and 1 CM to predict other CMs
	private void predictCmWithAfCm(ReferenceNode afNode, Graph<ReferenceNode, ReferenceEdge> jgrapht,
			Map<MethodReference, MethodReference> newToOldMethodRefMap,
			Map<MethodReference, MethodReference> oldToNewMethodRefMap,
			DataFlowAnalysisEngine leftEngine, DataFlowAnalysisEngine rightEngine,
			Set<IClass> allFoundClasses, Map<MethodReference, ClientMethod> oldMRefToClient,
			Map<MethodReference, ClientMethod> newMRefToClient,
			Map<MethodReference, HashSet<NodePair>> oldMethodRefToMatch,
			Map<MethodReference, HashSet<NodePair>> newMethodRefToMatch,
			Map<MethodReference, ASTNode> oMRefToAST,
			Map<MethodReference, ASTNode> nMRefToAST) {
		FieldReference afRef = (FieldReference) afNode.ref;
		TypeReference afClass = afRef.getDeclaringClass();
		String afClassName = afClass.getName().getClassName().toString();
		String afName = afRef.getName().toString();
		String afClassSig = afClass.getName().toString().substring(1).replace('/', '.');
		
		// check whether AF is field replacement
		if (afIsFieldReplacement(CommitComparator.bugName, afClassName, afName))
			return;
		
		// get CM set, old version
		Set<MethodReference> cmSet = new HashSet<>();
		Map<MethodReference, String> refToRealAccessMode = new HashMap<>();  // old reference to AF access mode
		for (ReferenceEdge edge: jgrapht.edgesOf(afNode)) {
			if (edge.type != ReferenceEdge.FIELD_ACCESS)
				continue;
			ReferenceNode srcNode = edge.from;
			if (srcNode.type != ReferenceNode.CM)
				continue;
			MethodReference newCmRef = (MethodReference) srcNode.ref;
			MethodReference oldCmRef = newToOldMethodRefMap.get(newCmRef);
			cmSet.add(oldCmRef);
			
			// check AF access mode
			ClientMethod newClient = newMRefToClient.get(newCmRef);
			ASTNode newAstNode = newClient.methodbody;
			FieldNameVisitor fieldNameVisitor = new FieldNameVisitor(newAstNode, afClassSig);
			fieldNameVisitor.execute();
			Set<String> readFields = fieldNameVisitor.getReadFields();
			Set<String> writtenFields = fieldNameVisitor.getWrittenFields();
			if (readFields.contains(afName) && writtenFields.contains(afName))
				refToRealAccessMode.put(oldCmRef, "rw");
			else if (readFields.contains(afName))
				refToRealAccessMode.put(oldCmRef, "r");
			else
				refToRealAccessMode.put(oldCmRef, "w");
			
		}
		if (cmSet.size() < 2)
			return;
		
		// Check accessibility of AF
		IClassHierarchy leftHierarchy = leftEngine.getClassHierarchy();
		IClassHierarchy rightHierarchy = rightEngine.getClassHierarchy();
		IField iField = rightHierarchy.resolveField(afRef);
		IClass afIClass = leftHierarchy.lookupClass(afClass);
		if (afIClass == null)
			afIClass = rightHierarchy.lookupClass(afClass);
		String fieldAccess = null;
		if (iField.isPrivate())
			fieldAccess = "private";
		else if (iField.isProtected())
			fieldAccess = "protected";
		else if (iField.isPublic() || afIClass.isInterface())
			fieldAccess = "public";
		else
			fieldAccess = "package";
		
		
		// Build method to class map
		Map<MethodReference, IClass> mRefToIClassMap = new HashMap<>();
		for (IClass iClass: allFoundClasses) {
			Collection<IMethod> declaredMethods = iClass.getDeclaredMethods();
			for (IMethod iMethod: declaredMethods) {
				MethodReference ref = iMethod.getReference();
				mRefToIClassMap.put(ref, iClass);
			}
		}
		
		// Find classes of methods candidates
		Set<IClass> classesToProcess = new HashSet<>();
		if (fieldAccess.equals("private")) {
			classesToProcess.add(afIClass);
		} else if (fieldAccess.equals("protected") || fieldAccess.equals("package")) {
			// classes in the same package
			String fieldPackage = this.getPackageOfClass(afIClass);
			for (IClass klass: allFoundClasses) {
				String packageName = this.getPackageOfClass(klass);
				if (fieldPackage.equals(packageName))
					classesToProcess.add(klass);
			}
			if (fieldAccess.equals("protected")) {
				// subclasses
				for (IClass klass: allFoundClasses) {
					IClass superClass = klass;
					do {
						superClass = superClass.getSuperclass();
						if (afIClass.equals(superClass)) {
							classesToProcess.add(klass);
							break;
						}
					} while (superClass != null);
				}
			}
		} else if (fieldAccess.equals("public")) {
			classesToProcess.addAll(allFoundClasses);
		}
		
		// Find method candidates
		Set<MethodReference> methodCandidateSet = new HashSet<>();
		for (IClass klass: classesToProcess) {
			for (IMethod iMethod: klass.getDeclaredMethods()) {
				if (iMethod.isClinit())
					continue;
//				if (iMethod.isInit())
//					continue;
				MethodReference mRef = iMethod.getReference();
				methodCandidateSet.add(mRef);
			}
		}
		
		// iterate over every CM
		for (MethodReference usedCmRef: cmSet) {
			// Check whether the usage of AF is replacing an existing field or a literal
			MethodReference usedCmRefNew = oldToNewMethodRefMap.get(usedCmRef);
			ClientMethod oldClient = oldMRefToClient.get(usedCmRef);
			ClientMethod newClient = newMRefToClient.get(usedCmRefNew);
			ASTNode oldAstNode = oldClient.methodbody;
			ASTNode newAstNode = newClient.methodbody;
			
			// Field Replacement Checker
			//-------------------------------------
//			HashSet<NodePair> treeMatches = oldMethodRefToMatch.get(usedCmRef);
//			if (treeMatches != null) {
//				FieldReplacementDatabaseInserter fpInserter =
//						new FieldReplacementDatabaseInserter(CommitComparator.bugName, afClassName,
//								afName, fieldAccess, usedCmRef.getSignature(), Application.fieldReplacementTable);
//				TopDownTreeMatcher treeMatcher = new TopDownTreeMatcher(afName, afClassName);
//				FieldReplacementChecker fieldChecker =
//						new FieldReplacementChecker(fpInserter, treeMatcher, treeMatches, oldAstNode, newAstNode);
//				fieldChecker.execute();
//			}
			//-------------------------------------
			
			
			
			// Get used CM's IR
//			IClass iClassOfUsedCm = mRefToIClassMap.get(usedCmRef);
//			leftEngine.getCFGBuilder(usedCmRef, iClassOfUsedCm);
//			IR ir = leftEngine.getCurrentIR();
			
			// Inside the CM, get the fields whose class is the same as AF
			boolean afIsConstantNamingPattern = isConstantNamingPattern(afName); 
			Set<String> targetPeerFieldSet = null;
			FieldNameVisitor fieldNameVisitor = new FieldNameVisitor(oldAstNode, afClassSig);
			fieldNameVisitor.execute();
//			targetPeerFieldSet = fieldNameVisitor.getFields();
			Set<String> readFieldsInUsedCm = fieldNameVisitor.getReadFields();
			Set<String> writtenFieldsInUsedCm = fieldNameVisitor.getWrittenFields();
			Set<String> toBeDeletedReadFieldsInUsedCm = new HashSet<>();
			for (String n: readFieldsInUsedCm) {
				if (isConstantNamingPattern(n) != afIsConstantNamingPattern)
					toBeDeletedReadFieldsInUsedCm.add(n);
			}
			readFieldsInUsedCm.removeAll(toBeDeletedReadFieldsInUsedCm);
			Set<String> toBeDeletedWrittenFieldsInUsedCm = new HashSet<>();
			for (String n: writtenFieldsInUsedCm) {
				if (isConstantNamingPattern(n) != afIsConstantNamingPattern)
					toBeDeletedWrittenFieldsInUsedCm.add(n);
			}
			writtenFieldsInUsedCm.removeAll(toBeDeletedWrittenFieldsInUsedCm);
			
			FieldNameVisitor fVisitor = new FieldNameVisitor(newAstNode, afClassSig);
			fVisitor.execute();
			boolean afIsRead = fVisitor.getReadFields().contains(afName);
			boolean afIsWritten = fVisitor.getWrittenFields().contains(afName);
			
			Set<String> readAndWrittenTargetPeerFields = new HashSet<>();
			Set<String> onlyReadTargetPeerFields = new HashSet<>();
			Set<String> onlyWrittenTargetPeerFields = new HashSet<>();
			if (afIsRead && afIsWritten) {
				readAndWrittenTargetPeerFields.addAll(readFieldsInUsedCm);
				readAndWrittenTargetPeerFields.retainAll(writtenFieldsInUsedCm);
//				readAndWrittenTargetPeerFields.remove(afName);
				targetPeerFieldSet = readAndWrittenTargetPeerFields;
			} else if (afIsRead) {
				onlyReadTargetPeerFields.addAll(readFieldsInUsedCm);
				onlyReadTargetPeerFields.removeAll(writtenFieldsInUsedCm);
//				onlyReadTargetPeerFields.remove(afName);
				targetPeerFieldSet = onlyReadTargetPeerFields;
			} else if (afIsWritten) {
				onlyWrittenTargetPeerFields.addAll(writtenFieldsInUsedCm);
				onlyWrittenTargetPeerFields.removeAll(readFieldsInUsedCm);
//				onlyWrittenTargetPeerFields.remove(afName);
				targetPeerFieldSet = onlyWrittenTargetPeerFields;
			} else {
				targetPeerFieldSet = Collections.emptySet();
			}
			
			boolean shouldCheckAccessMode = true; // default plan A, care read and write access
			if (targetPeerFieldSet.size() < 2)  {
				// Plan B, ignore read and write access
				shouldCheckAccessMode = false;
//				targetPeerFieldSet = fieldNameVisitor.getFields();
//				targetPeerFieldSet.remove(afName);
			}
			
			
			Set<MethodReference> predictedCm = new HashSet<>();
			
//			if (targetPeerFieldSet.isEmpty()) {
				// Get the surrounding statements of the statement containing AF
				// 0. Get the AST of new version of used CM
				// 1. Locate the AF statement from AST. To be concise, we only 
				//    locate the first AF statement.
				// 2. Find 2 stmts above AF stmt and 2 stmts below AF stmt
				// 3. Search within every method candidate AST, find statements
				//    similar to the surrounding statements
//				NearStmtFinder nearFinder = new NearStmtFinder(newAstNode, afName, afClassSig);
//				nearFinder.execute();
//				List<ASTNode> stmtsAboveAF = nearFinder.getAboves();
//				List<ASTNode> stmtsBelowAF = nearFinder.getBelows();
//				for (MethodReference mRef: methodCandidateSet) {
//					if (mRef.equals(usedCmRef))
//						continue;
//					ASTNode ast = oMRefToAST.get(mRef);
//					if (ast == null)
//						continue;
//					SimilarStatementMethodChecker statementChecker = 
//							new SimilarStatementMethodChecker(ast, stmtsAboveAF, stmtsBelowAF);
//					statementChecker.execute();
//					if (statementChecker.hasSimilarStatements())
//						predictedCm.add(mRef);
//				}
//			}
			
			
			
			
			
			
			
			// Check every method candidate
			Map<MethodReference, Set<String>> refToFieldsMap = new HashMap<>();
			Map<MethodReference, String> refToPredictedAccessMode = new HashMap<>();  // 3 access modes: "r", "w", "rw"
			for (MethodReference mRef: methodCandidateSet) {
				if (mRef.equals(usedCmRef))
					continue;
//				leftEngine.getCFGBuilder(mRef, mRefToIClassMap.get(mRef));
//				IR candidateIr = leftEngine.getCurrentIR();
				Set<String> peerFieldSet = null;
//				if (mRef.isInit()) {
					ASTNode ast = oMRefToAST.get(mRef);
					if (ast == null) {
						continue;
					}
					FieldNameVisitor fieldVisitor = new FieldNameVisitor(ast, afClassSig);
					fieldVisitor.execute();
//					peerFieldSet = fieldVisitor.getFields();
					Set<String> readFields = fieldVisitor.getReadFields();
					Set<String> writtenFields = fieldVisitor.getWrittenFields();
//					Set<String> toBeDeletedReadFields = new HashSet<>();
//					for (String n: readFields) {
//						if (isConstantNamingPattern(n) != afIsConstantNamingPattern)
//							toBeDeletedReadFields.add(n);
//					}
//					readFields.removeAll(toBeDeletedReadFields);
//					Set<String> toBeDeletedWrittenFields = new HashSet<>();
//					for (String n: writtenFields) {
//						if (isConstantNamingPattern(n) != afIsConstantNamingPattern)
//							toBeDeletedWrittenFields.add(n);
//					}
//					writtenFields.removeAll(toBeDeletedWrittenFields);
					if (shouldCheckAccessMode) {
						// Plan A, peer fields in a candidate method should be accessed in the same way,
						// but their access modes in the candidate method are not necessarily same with
						// those in the known method
//						Set<String> readPeerSet = new HashSet<>();
//						readPeerSet.addAll(readFields);
//						readPeerSet.retainAll(targetPeerFieldSet);
//						Set<String> writtenPeerSet = new HashSet<>();
//						writtenPeerSet.addAll(writtenFields);
//						writtenPeerSet.retainAll(targetPeerFieldSet);
						
						Set<String> onlyReadPeerSet = new HashSet<>();
						onlyReadPeerSet.addAll(readFields);
						onlyReadPeerSet.removeAll(writtenFields);
						onlyReadPeerSet.retainAll(targetPeerFieldSet);
						
						Set<String> onlyWrittenPeerSet = new HashSet<>();
						onlyWrittenPeerSet.addAll(writtenFields);
						onlyWrittenPeerSet.removeAll(readFields);
						onlyWrittenPeerSet.retainAll(targetPeerFieldSet);
						
						Set<String> readAndWrittenPeerSet = new HashSet<>();
						readAndWrittenPeerSet.addAll(readFields);
						readAndWrittenPeerSet.retainAll(writtenFields);
						readAndWrittenPeerSet.retainAll(targetPeerFieldSet);
						
						if (onlyReadPeerSet.size() == 0
								&& onlyWrittenPeerSet.size() == 0
								&& readAndWrittenPeerSet.size() == 0) {
							peerFieldSet = Collections.emptySet();
						} else {
							Comparator<Set<String>> comparator = new Comparator<Set<String>>() {
								@Override
								public int compare(Set<String> o1, Set<String> o2) {
									return o2.size() - o1.size();
								}};
							PriorityQueue<Set<String>> queue = new PriorityQueue<>(3, comparator);
							queue.add(onlyReadPeerSet);
							queue.add(onlyWrittenPeerSet);
							queue.add(readAndWrittenPeerSet);
							peerFieldSet = queue.peek();
							
							// predict the access mode of AF, which be the same as that of peers
							if (peerFieldSet == onlyReadPeerSet)
								refToPredictedAccessMode.put(mRef, "r");
							else if (peerFieldSet == onlyWrittenPeerSet)
								refToPredictedAccessMode.put(mRef, "w");
							else
								refToPredictedAccessMode.put(mRef, "rw");
							
							Set<String> largestPeerSet = queue.poll();
							Set<String> secondLargestPeerSet = queue.poll();
							if (largestPeerSet.size() == secondLargestPeerSet.size())
								refToPredictedAccessMode.put(mRef, "NA");
						}
						
						
					} else {
						// Plan B, ignore read and write access
//						peerFieldSet = fieldVisitor.getFields();
//						peerFieldSet.retainAll(targetPeerFieldSet);
						
						// Now there is no plan B
						peerFieldSet = Collections.emptySet();
						
					}
//					if (afIsRead && afIsWritten) {
//						peerFieldSet.addAll(readFields);
//						peerFieldSet.retainAll(writtenFields);
//						peerFieldSet.retainAll(readAndWrittenTargetPeerFields);
//					} else if (afIsRead) {
//						peerFieldSet.addAll(readFields);
//						peerFieldSet.retainAll(onlyReadTargetPeerFields);
//					} else if (afIsWritten) {
//						peerFieldSet.addAll(writtenFields);
//						peerFieldSet.retainAll(onlyWrittenTargetPeerFields);
//					}
					
//				} else {
//					peerFieldSet = getFieldNames(candidateIr, afClass);
//				}
//				peerFieldSet.retainAll(targetPeerFieldSet);
				refToFieldsMap.put(mRef, peerFieldSet);
			}
			
			// Find the method candidate with most peer fields
			int maxPeerFieldsNum = -1;
			for (Set<String> peerFieldSet: refToFieldsMap.values()) {
				if (peerFieldSet.size() > maxPeerFieldsNum)
					maxPeerFieldsNum = peerFieldSet.size();
			}
			
			if (maxPeerFieldsNum >= 2) {
				for (MethodReference mRef: refToFieldsMap.keySet()) {
					Set<String> peerFieldSet = refToFieldsMap.get(mRef);
					if (peerFieldSet.size() == maxPeerFieldsNum)
						predictedCm.add(mRef);
				}
			}
			
			Set<String> truePositives = new HashSet<>();
			Set<String> falsePositives = new HashSet<>();
			Set<String> falseNegatives = new HashSet<>();
			Map<String, Set<String>> tpVars = new HashMap<>();
			Map<String, Set<String>> fpVars = new HashMap<>();
			Map<String, Set<String>> fnVars = new HashMap<>();
			Set<String> correctAccessPrediction = new HashSet<>();
			Set<String> predictionWithAccess = new HashSet<>();
			Map<String, Map<String, String>> accessDetail = new HashMap<>();
			
			for (MethodReference mRef: cmSet) {
				if (mRef.equals(usedCmRef))
					continue;
				if (predictedCm.contains(mRef)) {
					truePositives.add(mRef.getSignature());
					tpVars.put(mRef.getSignature(), refToFieldsMap.get(mRef));
					
					// process predict_access and access_precision
					
					String realAccess = refToRealAccessMode.get(mRef);
					String predictedAccess = refToPredictedAccessMode.get(mRef);
					if (!predictedAccess.equals("NA")) {
						Map<String, String> realAndPredictedAccess = new HashMap<>();
						realAndPredictedAccess.put("real", realAccess);
						realAndPredictedAccess.put("predicted", predictedAccess);
						accessDetail.put(mRef.getSignature(), realAndPredictedAccess);
						if (realAccess.equals(predictedAccess)) {
							correctAccessPrediction.add(mRef.getSignature());
						}
						predictionWithAccess.add(mRef.getSignature());
					}
				
					
				} else {
					falseNegatives.add(mRef.getSignature());
					fnVars.put(mRef.getSignature(), refToFieldsMap.get(mRef));
				}
			}
			for (MethodReference mRef: predictedCm) {
				if (!cmSet.contains(mRef)) {
					falsePositives.add(mRef.getSignature());
					fpVars.put(mRef.getSignature(), refToFieldsMap.get(mRef));
					
					// process access prediction
					String predictedAccess = refToPredictedAccessMode.get(mRef);
					if (!predictedAccess.equals("NA")) {
						Map<String, String> realAndPredictedAccess = new HashMap<>();
						realAndPredictedAccess.put("real", "none");
						realAndPredictedAccess.put("predicted", predictedAccess);
						accessDetail.put(mRef.getSignature(), realAndPredictedAccess);
						predictionWithAccess.add(mRef.getSignature());
					}
				}
			}
			
			int recall = 100 * truePositives.size() / (cmSet.size() - 1);
			int precision = -1;
			if (!predictedCm.isEmpty())
				precision = 100 * truePositives.size() / predictedCm.size();
			
			int accessPrecision = -1;
			if (!predictionWithAccess.isEmpty())
				accessPrecision = 100 * correctAccessPrediction.size() / predictionWithAccess.size();
			
			// Insert data into database
			Gson gson = new Gson();
			Connection conn = SqliteManager.getConnection();
			try {
				// prediction_cm_neo_derby
				PreparedStatement ps = conn.prepareStatement("INSERT INTO " + Application.neoResultTable
						+ " (bug_name,af_class,af_name,field_access,cm_num,predict_cm_num,recall,precision,"
						+ "used_cm,used_cm_vars,tp,tp_vars,fp,fp_vars,fn,fn_vars,access_precision,access_detail) VALUES "
						+ "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
				ps.setString(1, CommitComparator.bugName);
				ps.setString(2, afClassName);
				ps.setString(3, afName);
				ps.setString(4, fieldAccess);
				ps.setInt(5, cmSet.size());
				ps.setInt(6, predictedCm.size());
				ps.setInt(7, recall);
				if (precision == -1)
					ps.setNull(8, java.sql.Types.INTEGER);
				else
					ps.setInt(8, precision);
				ps.setString(9, usedCmRef.getSignature());
				ps.setString(10, gson.toJson(targetPeerFieldSet));
				ps.setString(11, gson.toJson(truePositives));
				ps.setString(12, gson.toJson(tpVars));
				ps.setString(13, gson.toJson(falsePositives));
				ps.setString(14, gson.toJson(fpVars));
				ps.setString(15, gson.toJson(falseNegatives));
				ps.setString(16, gson.toJson(fnVars));
				if (accessPrecision == -1)
					ps.setNull(17, java.sql.Types.INTEGER);
				else
					ps.setInt(17, accessPrecision);
				ps.setString(18, gson.toJson(accessDetail));
				ps.executeUpdate();
				ps.close();
				conn.close();
				
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			
			// Insert data to TABLE false_positives
			conn = SqliteManager.getConnection();
			try {
				PreparedStatement ps = conn.prepareStatement("INSERT INTO " + Application.fpTable
						+ " (bug_name, fp_package, fp_class, fp_method, fp_sig, fp_used_cm,"
						+ "af_class, af_name, af_sig)"
						+ " VALUES (?,?,?,?,?,?,?,?,?)");
				Set<MethodReference> fpSet = new HashSet<>();
				fpSet.addAll(predictedCm);
				fpSet.removeAll(cmSet);
				ps.setString(7, afClassName);
				ps.setString(8, afName);
				ps.setString(9, afRef.getSignature());
				for (MethodReference m: fpSet) {
					ps.setString(1, CommitComparator.bugName);
					ps.setString(2, m.getDeclaringClass().getName().getPackage().toString().replace('/', '.'));
					ps.setString(3, m.getDeclaringClass().getName().getClassName().toString());
					ps.setString(4, m.getName().toString());
					ps.setString(5, m.getSignature());
					ps.setString(6, usedCmRef.getSignature());
					ps.executeUpdate();
				}
				ps.close();
				conn.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			//--------------------------------
		}
		
	}
	
	private boolean afIsFieldReplacement(String bugName, String afClass, String afName) {
		Connection conn = SqliteManager.getConnection();
		PreparedStatement ps;
		boolean result = false;
		try {
			ps = conn.prepareStatement("SELECT result FROM field_replacement_result "
					+ "WHERE bug_name=? AND af_class=? AND af_name=?");
			ps.setString(1, bugName);
			ps.setString(2, afClass);
			ps.setString(3, afName);
			ResultSet rs = ps.executeQuery();
			
			if (rs.next())
				result = rs.getString(1).equals("true");
			ps.close();
			conn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}
	
	private void analyzeCmAmGraphs(ReferenceNode node, Graph<ReferenceNode, ReferenceEdge> jgrapht,
			Map<MethodReference, MethodReference> newToOldMethodRefMap,
			DataFlowAnalysisEngine leftEngine, DataFlowAnalysisEngine rightEngine) {
		MethodReference amRef = (MethodReference) node.ref;
		TypeReference amClass = amRef.getDeclaringClass();
		String amClassName = amClass.getName().getClassName().toString();
		String amName = amRef.getName().toString();
		for (ReferenceEdge edge: jgrapht.edgesOf(node)) {
			if (edge.type != ReferenceEdge.METHOD_INVOKE)
				continue;
			ReferenceNode srcNode = edge.from;
			if (srcNode.type != ReferenceNode.CM)
				continue;
			MethodReference newRef = (MethodReference) srcNode.ref;
			MethodReference mRef = newToOldMethodRefMap.get(newRef);
			
			TypeReference cmClass = mRef.getDeclaringClass();
			String cmClassName = cmClass.getName().getClassName().toString();
			String cmName = mRef.getName().toString();
			
			Connection conn = SqliteManager.getConnection();
			try {
				PreparedStatement ps = conn.prepareStatement("INSERT INTO predict_cmam_initial_analysis (bug_name,am_class,am_name,am_sig,cm_class,cm_name,cm_sig) VALUES (?,?,?,?,?,?,?)");
				ps.setString(1, CommitComparator.bugName);
				ps.setString(2, amClassName);
				ps.setString(3, amName);
				ps.setString(4, amRef.getSignature());
				ps.setString(5, cmClassName);
				ps.setString(6, cmName);
				ps.setString(7, mRef.getSignature());
				ps.executeUpdate();
				ps.close();
				conn.close();
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}
	}
	
	/**
	 * Check if the var name is like AA_BBER_FWE1, i.e. only contains
	 * Uppercase letters, underscore, or number 
	 * @return
	 */
	private static boolean isConstantNamingPattern(String name) {
		for (char c: name.toCharArray()) {
			if (!Character.isUpperCase(c) && !Character.isDigit(c) && c != '_')
				return false;
		}
		return true;
	}
	
	private boolean hasSwitchInMethod(IR ir) {
		if (ir == null)
			return false;
		for (SSAInstruction instr: ir.getInstructions()) {
			if (instr != null && instr instanceof SSASwitchInstruction) {
				return true;
			}
		}
		return false;
	}
	
	private void insertPredictCMDataToDatabase(List<SqlPredictData> sqlValues) {
		if (!sqlValues.isEmpty()) {
			String resultTable = consolegsydit.Application.predictionCmResultTable;
			if (resultTable == null)
				resultTable = "prediction_cm";
			
			StringBuilder sqlBuilder = new StringBuilder();
			sqlBuilder.append("INSERT INTO ");
			sqlBuilder.append(resultTable);
			sqlBuilder.append(" (bug_name, af_class, af_name, field_access, cm_num, ");
			sqlBuilder.append("predict_cm_num, recall, precision, decisive_template, tp, tp_vars, fp, fp_vars, fn, fn_vars) ");
			sqlBuilder.append("VALUES ");
			
			
			for (int i = 0; i < sqlValues.size() - 1; i++) {
				sqlBuilder.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?),");
			}
			sqlBuilder.append("(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			
			String sql = sqlBuilder.toString();
			
			Connection connection = SqliteManager.getConnection();
			try {
				PreparedStatement stmt = connection.prepareStatement(sql);
				int colNum = 15;
				for (int i = 0; i < sqlValues.size(); i++) {
					SqlPredictData d = sqlValues.get(i); 
					stmt.setString(colNum * i + 1, d.bugName);
					stmt.setString(colNum * i + 2, d.afClass);
					stmt.setString(colNum * i + 3, d.afName);
					stmt.setString(colNum * i + 4, d.fieldAccess);
					stmt.setInt(colNum * i + 5, d.cmNum);
					stmt.setInt(colNum * i + 6, d.predictCmNum);
					stmt.setInt(colNum * i + 7, d.recall);
					if (d.precision == -1)
						stmt.setNull(colNum * i + 8, java.sql.Types.INTEGER);
					else
						stmt.setInt(colNum * i + 8, d.precision);
					stmt.setString(colNum * i + 9, d.decisiveTemplate);
					stmt.setString(colNum * i + 10, d.tp);
					stmt.setString(colNum * i + 11, d.tpVars);
					stmt.setString(colNum * i + 12, d.fp);
					stmt.setString(colNum * i + 13, d.fpVars);
					stmt.setString(colNum * i + 14, d.fn);
					stmt.setString(colNum * i + 15, d.fnVars);
				}
				stmt.executeUpdate();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
				// System.exit(-1);
			}
			
			try {
				connection.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Added by Ye Wang, 03/22/2017
	// Predict changed methods that should use the added field
	private void predictChangedMethods(ReferenceNode node, Graph<ReferenceNode, ReferenceEdge> jgrapht,
			Map<MethodReference, MethodReference> newToOldMethodRefMap, DataFlowAnalysisEngine leftEngine,
			DataFlowAnalysisEngine rightEngine, Set<IClass> allFoundClasses, List<SqlPredictData> sqlValues) {
		String afName = ((MemberReference) node.ref).getName().toString();
		String afClassName = ((MemberReference)node.ref).getDeclaringClass().getName().getClassName().toString();
		
		// get changed methods
		Set<MethodReference> changedMethodSet = new HashSet<>();
		for (ReferenceEdge edge: jgrapht.edgesOf(node)) {
			ReferenceNode mNode = jgrapht.getEdgeSource(edge);
			MethodReference newRef = (MethodReference)mNode.ref;
			MethodReference mRef = newToOldMethodRefMap.get(newRef);
			changedMethodSet.add(mRef);
		}
		
		//********************************************************
		// This part is for finding the methods with switch-case
//		for (MethodReference mRef: changedMethodSet) {
//			TypeReference typeRef = mRef.getDeclaringClass();
//			IClassHierarchy leftHierarchy = leftEngine.getClassHierarchy();
//			IClass klass = leftHierarchy.lookupClass(typeRef);
//			leftEngine.getCFGBuilder(mRef, klass);
//			IR ir = leftEngine.getCurrentIR();
//			if (hasSwitchInMethod(ir)) {
//				Connection conn = SqliteManager.getConnection();
//				java.sql.Statement stmt = null;
//				try {
//					stmt = conn.createStatement();
//					stmt.executeUpdate("CREATE TABLE IF NOT EXISTS prediction_cm_methods_with_similarly_used_vars (bug_name TEXT,af_class TEXT,af_name TEXT,method TEXT)");
//					
//					PreparedStatement ps = conn.prepareStatement("INSERT INTO prediction_cm_methods_with_similarly_used_vars (bug_name,af_class,af_name,method) VALUES (?,?,?,?)");
//					ps.setString(1, CommitComparator.bugName);
//					ps.setString(2, afClassName);
//					ps.setString(3, afName);
//					ps.setString(4, mRef.getSignature());
//					ps.executeUpdate();
//					ps.close();
//					
//					stmt.close();
//					conn.close();
//				} catch (SQLException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}	
//			}
//		}
		//********************************************************
		
		
		FieldReference fieldRef = (FieldReference)node.ref;
		TypeReference afClass = fieldRef.getDeclaringClass();
		IClassHierarchy rightHierarchy = rightEngine.getClassHierarchy();
		IClass afIClass = rightHierarchy.lookupClass(afClass);
		IField iField = rightHierarchy.resolveField(fieldRef);
		
		String fieldAccessControl;
		Set<IClass> classesToProcess = new HashSet<>();
		IClassHierarchy leftHierarchy = leftEngine.getClassHierarchy();
		if (iField.isPrivate()) {
			// private
			// methods in the same class
			fieldAccessControl = "private";
			TypeReference typeRef = fieldRef.getDeclaringClass();
			IClass klass = leftHierarchy.lookupClass(typeRef);
			classesToProcess = new HashSet<>();
			classesToProcess.add(klass);
			
		} else if (iField.isProtected()) {
			fieldAccessControl = "protected";
			// classes in the same package
			TypeReference typeRef = fieldRef.getDeclaringClass();
			IClass fieldClass = leftHierarchy.lookupClass(typeRef);
			if (fieldClass == null)
				fieldClass = rightHierarchy.lookupClass(typeRef);
			String fieldPackage = this.getPackageOfClass(fieldClass);
			for (IClass klass: allFoundClasses) {
				String packageName = this.getPackageOfClass(klass);
				if (fieldPackage.equals(packageName))
					classesToProcess.add(klass);
			}
			// and subclasses
			for (IClass klass: allFoundClasses) {
				IClass superClass = klass;
				do {
					superClass = superClass.getSuperclass();
					if (fieldClass.equals(superClass)) {
						classesToProcess.add(klass);
						break;
					}
				} while (superClass != null);
			}
			
		} else if (iField.isPublic()  || afIClass.isInterface()) {
			fieldAccessControl = "public";
			// scan all
			classesToProcess = allFoundClasses;
			
		} else {
			// package-private
			fieldAccessControl = "package";
			// classes in the same package
			TypeReference typeRef = fieldRef.getDeclaringClass();
			IClass fieldClass = leftHierarchy.lookupClass(typeRef);
			if (fieldClass == null)
				fieldClass = rightHierarchy.lookupClass(typeRef);
			String fieldPackage = this.getPackageOfClass(fieldClass);
			for (IClass klass: allFoundClasses) {
				String packageName = this.getPackageOfClass(klass);
				if (fieldPackage.equals(packageName))
					classesToProcess.add(klass);
			}
			
		}
		
		Gson gson = new Gson();
		
		// predict output methods
		Map<MethodReference, JsonTemplateEvidence> methodToEvidence = new HashMap<>();
		Set<MethodReference> outputMethods = new HashSet<MethodReference>();
		Map<MethodReference, Set<String>> similarVarsInMethod = new HashMap<>();
		Map<MethodReference, Set<String>> allVarsInMethod = new HashMap<>();
		Map<MethodReference, String> VarRawInfoInMethod = new HashMap<>(); 
		List<MethodReference> scannedMethods = new ArrayList<>();
		
		// Map: method -> templates
		// It's used for ranking all template
		Map<MethodReference, Map<List<String>, List<String>>> methodToTemplates = new HashMap<>();
		
		for (IClass klass: classesToProcess) {
			Collection<IMethod> methods = klass.getDeclaredMethods();
			for (IMethod method: methods) {
				if (method.isClinit())
					continue;
				
				// Don't predict constructors and init()
				if (method.isInit())
					continue;
				MethodReference mRef = method.getReference();
				String methodName = mRef.getName().toString();
				if (methodName.equals("init"))
					continue;
				
				
				
				leftEngine.getCFGBuilder(mRef, klass);
				IR ir = leftEngine.getCurrentIR();
//				Set<String> varSet = this.getVarNames(ir);
				
				
				/* If a method name starts with "get", "set", or "is", and
				 * the name covers all words in the name of one field used
				 * in the method, we don't predict this method.
				 */
				if (methodNameStartsWithGetSetIs(methodName) && methodNameCoverFieldName(methodName, getFieldNames(ir, klass.getReference()))) {
					continue;
				}
				
				
				
				
				Set<String> varSet = getFieldNames(ir, afClass);
				scannedMethods.add(mRef);
				allVarsInMethod.put(mRef, varSet);
				
				// extract template
				Map<List<String>, List<String>> templateToVars = new HashMap<>();
				for (String var: varSet) {
					List<String> complexTemplate = MethodPredictor.extractTemplate(afName, var);
					List<String> template = MethodPredictor.simplifyTemplate(complexTemplate);
					if (!template.isEmpty()) {
						if (templateToVars.containsKey(template)) {
							templateToVars.get(template).add(var);
						} else {
							List<String> vars = new ArrayList<>();
							vars.add(var);
							templateToVars.put(template, vars);
						}
					}
				}
				Map<List<String>, List<String>> mergedTtv = MethodPredictor.mergeTemplates(templateToVars);
//				Map<List<String>, List<String>> mergedTtv = templateToVars;
				methodToTemplates.put(mRef, mergedTtv);
				
				
//				// Analyze the number of words and the length of words in a template
//				List<VariableTemplate> templates = new ArrayList<>();
//				for (List<String> t: templateToVars.keySet()) {
//					List<String> similarVars = templateToVars.get(t);
//					VariableTemplate template = new VariableTemplate(t, similarVars);
//					templates.add(template);
//				}
//				VariableTemplate strongEvidence = null;
//				for (VariableTemplate t: templates) {
//					if (t.isSignificant()) {
//						strongEvidence = t;
//						break;
//					}
//				}
//				List<VariableTemplate> weakEvidence = null;
//				if (strongEvidence == null && VariableTemplate.isSignificant(templates)) {
//					// No strong evidence, has weak evidence
//					weakEvidence = VariableTemplate.extractWeakEvidence(templates);
//				}
//				if (strongEvidence != null || weakEvidence != null) {
//					outputMethods.add(mRef);
//				}
//				
//				List<VariableTemplate> nonEvidence = new ArrayList<>();
//				for (VariableTemplate t: templates) {
//					if (t == strongEvidence)
//						break;
//					boolean isWeakEvidence = false;
//					if (weakEvidence != null) {
//						for (VariableTemplate weak: weakEvidence) {
//							if (t == weak) {
//								isWeakEvidence = true;
//								break;
//							}
//						}
//					}
//					if (!isWeakEvidence)
//						nonEvidence.add(t);
//				}
//				
//				// {
//				//   name: "methodSig",
//				//   evidence: [
//				//     {template: ["mike", "*"], vars:["mikeKan", "mikeGi", "mikeFi"], rating:"weak"},
//				//     {template: [...], vars:[...], rating:"none"}
//				//   ]
//				// }
//				JsonTemplateEvidence jsonEvidence = new JsonTemplateEvidence(mRef.getSignature());
//				if (strongEvidence != null) {
//					jsonEvidence.addTemplate(new JsonTemplateContent(strongEvidence.getTemplate(), strongEvidence.getSimilarVars(), "strong"));
//				} else if (weakEvidence != null) {
//					for (VariableTemplate weak: weakEvidence) {
//						jsonEvidence.addTemplate(new JsonTemplateContent(weak.getTemplate(), weak.getSimilarVars(), "weak"));
//					}
//				}
//				for (VariableTemplate t: nonEvidence) {
//					jsonEvidence.addTemplate(new JsonTemplateContent(t.getTemplate(), t.getSimilarVars(), "none"));
//				}
//				methodToEvidence.put(mRef, jsonEvidence);
				
				JsonTemplateEvidence jsonEvidence = new JsonTemplateEvidence(mRef.getSignature());
				for (List<String> template: mergedTtv.keySet()) {
					List<String> vars = mergedTtv.get(template);
					jsonEvidence.addTemplate(new JsonTemplateContent(template, vars));
				}
				MethodAnalyzer methodAnalyzer = new MethodAnalyzer(ir);
				jsonEvidence.fieldNameList = methodAnalyzer.getFieldNameList();
				jsonEvidence.localNameList = methodAnalyzer.getLocalNameList();
				jsonEvidence.methodNameList = methodAnalyzer.getMethodNameList();
				methodToEvidence.put(mRef, jsonEvidence);
				
				
				
				
				
//				// choose template with more matching variables
//				int maxSize = 0;
//				for (List<String> varList: templateToVars.values()) {
//					if (varList.size() > maxSize)
//						maxSize = varList.size();
//				}
//				Set<List<String>> mostPopularTemplates = new HashSet<>();
//				for (List<String> t: templateToVars.keySet()) {
//					if (templateToVars.get(t).size() == maxSize)
//						mostPopularTemplates.add(t);
//				}
//				List<String> winningTemplate = null;
//				int maxComplexity = 0;
//				for (List<String> t: mostPopularTemplates) {
//					if (t.size() > maxComplexity) {
//						maxComplexity = t.size();
//						winningTemplate = t;
//					}
//				}
//				
//				Set<List<String>> rankCTemplates = new HashSet<>(templateToVars.keySet());
//				rankCTemplates.removeAll(mostPopularTemplates);
//				Set<List<String>> rankBTemplates = new HashSet<>(mostPopularTemplates);
//				rankBTemplates.remove(winningTemplate);
//				
//				if (maxSize >= 2) {
//					outputMethods.add(mRef);
//				}
//				
//				List<List<String>> templateList = new ArrayList<>();
//				List<String> templateRank = new ArrayList<>();
//				
//				if (winningTemplate != null) {
//					templateList.add(winningTemplate);
//					templateRank.add("A");
//				}
//				for (List<String> t: rankBTemplates) {
//					templateList.add(t);
//					templateRank.add("B");
//				}
//				for (List<String> t: rankCTemplates) {
//					templateList.add(t);
//					templateRank.add("C");
//				}
//				
//				StringBuilder rawInfo = new StringBuilder();
//				rawInfo.append("{");
//				for (int j = 0; j < templateList.size(); j++) {
//					rawInfo.append("[");
//					List<String> template = templateList.get(j);
//					if (template == null)
//						System.out.print("");
//					for (String s: template) {
//						rawInfo.append(s);
//						rawInfo.append(",");
//					}
//					rawInfo.append("][");
//					rawInfo.append(templateRank.get(j));
//					rawInfo.append("](");
//					List<String> vars = templateToVars.get(template);
//					for (String var: vars) {
//						rawInfo.append(var);
//						rawInfo.append(",");
//					}
//					rawInfo.append("),");
//				}
//				rawInfo.append("}/{");
//				for (String var: varSet) {
//					rawInfo.append(var);
//					rawInfo.append(",");
//				}
//				rawInfo.append("}");
//				VarRawInfoInMethod.put(mRef, rawInfo.toString());
				
				
				
				
				
				
				// get variable names sharing a pattern with AF name
//				Set<String> patternVars = MethodPredictor.getPatternVars(afName, varSet);
//				similarVarsInMethod.put(mRef, patternVars);
//				
//				if (patternVars.size() >= 2) {
//					outputMethods.add(mRef);
//				}
				
//				// use the majority of variables with similar names
//				int similarNum = 0;
//				for (String var: varSet) {
//					SimilarityChecker checker = new SimilarityChecker(afName, var);
//					if (checker.containsSimilarWord())
//						similarNum++;
//				}
//				if (similarNum >= 2) {
//					outputMethods.add(method.getReference());
//				}
			}
		} // for-loop on class
		
		// Analyze the templates across all methods
		Map<List<String>, List<MethodReference>> templateToMethods = new HashMap<>();
		for (MethodReference mRef: methodToTemplates.keySet()) {
			Map<List<String>, List<String>> ttv = methodToTemplates.get(mRef);
			for (List<String> t: ttv.keySet()) {
				List<String> varList = ttv.get(t);
				if (varList.size() >= 2) {
					if (!templateToMethods.containsKey(t)) {
						templateToMethods.put(t, new ArrayList<MethodReference>());
					}
					templateToMethods.get(t).add(mRef);
				}
			}
		}
		
		// Find the template that has the most characters
		int maxTemplateCharNum = 0;
		List<String> templateHavingMostChars = null;
		for (List<String> template: templateToMethods.keySet()) {
			int templateCharNum = 0;
			for (String w: template) {
				if (!w.equals("*"))
					templateCharNum += w.length();
				if (templateCharNum > maxTemplateCharNum) {
					maxTemplateCharNum = templateCharNum;
					templateHavingMostChars = template;
				}
			}
		}
		if (templateHavingMostChars != null) {
			outputMethods.addAll(templateToMethods.get(templateHavingMostChars));
		}
		
		// compare expected CMs with actual CMs
		// get TP, FN, FP
		// recall = TP / (TP + FN)
		// precision = TP / (TP + FP)
		Set<MethodReference> truePositive = new HashSet<>();
		Set<MethodReference> falsePositive = new HashSet<>();
		Set<MethodReference> falseNegative = new HashSet<>();
		
		for (MethodReference m: outputMethods) {
			if (changedMethodSet.contains(m))
				truePositive.add(m);
			else
				falsePositive.add(m);
		}
		for (MethodReference m: changedMethodSet) {
			if (!outputMethods.contains(m))
				falseNegative.add(m);
		}
		
		int recall = 100 * truePositive.size() / (truePositive.size() + falseNegative.size());
		int precision = -1;
		if (outputMethods.size() > 0)
			precision = 100 * truePositive.size() / (truePositive.size() + falsePositive.size());
		
		SqlPredictData sqlData = new SqlPredictData();
		sqlData.bugName = CommitComparator.bugName;
		sqlData.afClass = afClassName;
		sqlData.afName = afName;
		sqlData.fieldAccess = fieldAccessControl;
		sqlData.cmNum = changedMethodSet.size();
		sqlData.predictCmNum = outputMethods.size();
		sqlData.recall = recall;
		sqlData.precision = precision;
		sqlData.decisiveTemplate = gson.toJson(templateHavingMostChars);
		List<String> tpJson = new ArrayList<>();
		List<JsonTemplateEvidence> tpVarsJson = new ArrayList<>();
		for (MethodReference m: truePositive) {
			tpJson.add(m.getSignature());
			tpVarsJson.add(methodToEvidence.get(m));
		}
		sqlData.tp = gson.toJson(tpJson);
		sqlData.tpVars = gson.toJson(tpVarsJson);
		List<String> fpJson = new ArrayList<>();
		List<JsonTemplateEvidence> fpVarsJson = new ArrayList<>();
		for (MethodReference m: falsePositive) {
			fpJson.add(m.getSignature());
			fpVarsJson.add(methodToEvidence.get(m));
		}
		sqlData.fp = gson.toJson(fpJson);
		sqlData.fpVars = gson.toJson(fpVarsJson);
		List<String> fnJson = new ArrayList<>();
		List<JsonTemplateEvidence> fnVarsJson = new ArrayList<>();
		for (MethodReference m: falseNegative) {
			fnJson.add(m.getSignature());
			fnVarsJson.add(methodToEvidence.get(m));
		}
		sqlData.fn = gson.toJson(fnJson);
		sqlData.fnVars = gson.toJson(fnVarsJson);
		sqlValues.add(sqlData);
		
		
		// output
		// bugName,AFClass,AFName,field_access,CM_num,predictCM_num,recall(%),precision(%),TP,varsInTP,FP,varsInFP,FN,varsInFN
//		StringBuilder output = new StringBuilder();
//		output.append("(");
//		output.append("\"");
//		output.append(CommitComparator.bugName);
//		output.append("\",\"");
//		output.append(afClassName);
//		output.append("\",\"");
//		output.append(afName);
//		output.append("\",\"");
//		output.append(fieldAccessControl);
//		output.append("\",");
//		output.append(changedMethodSet.size());
//		output.append(",");
//		output.append(outputMethods.size());
//		output.append(",");
//		output.append(recall);
//		output.append(",");
//		if (precision == -1)
//			output.append("NULL");
//		else
//			output.append(precision);
//		output.append(",");
//		
//		output.append("\"");
//		for (MethodReference m: truePositive) {
//			output.append(m.getSignature());
//			output.append(",");
//		}
//		output.append("\"");
//		output.append(",");
//		
//		output.append("\"");
//		for (MethodReference m: truePositive) {
//			output.append(m.getSignature());
//			output.append("=");
//			output.append(VarRawInfoInMethod.get(m));
//			output.append(",");
//		}
//		output.append("\"");
//		output.append(",");
//		
//		output.append("\"");
//		for (MethodReference m: falsePositive) {
//			output.append(m.getSignature());
//			output.append(",");
//		}
//		output.append("\"");
//		output.append(",");
//		
//		output.append("\"");
//		for (MethodReference m: falsePositive) {
//			output.append(m.getSignature());
//			output.append("=");
//			output.append(VarRawInfoInMethod.get(m));
//			output.append(",");
//		}
//		output.append("\"");
//		output.append(",");
//		
//		output.append("\"");
//		for (MethodReference m: falseNegative) {
//			output.append(m.getSignature());
//			output.append(",");
//		}
//		output.append("\"");
//		output.append(",");
//		
//		output.append("\"");
//		for (MethodReference m: falseNegative) {
//			output.append(m.getSignature());
//			output.append("=");
//			output.append(VarRawInfoInMethod.get(m));
//			output.append(",");
//		}
//		output.append("\"");
//		output.append(")");
//		sqlValues.add(output.toString());
	}
	
	private boolean methodNameStartsWithGetSetIs(String methodName) {
		return methodName.startsWith("get") || methodName.startsWith("set") || methodName.startsWith("is");
	}
	
	
	/**
	 * Check if a method name contains all words in the name of any of the fields used in the method
	 * @param methodName
	 * @param fieldNames
	 * @return true if the method name covers field name words.
	 */
	private boolean methodNameCoverFieldName(String methodName, Set<String> fieldNames) {
		String lowerMethodName = methodName.toLowerCase();
		for (String field: fieldNames) {
			List<String> fieldWords = NameProcessor.split(field);
			boolean containsAllWords = true;
			for (String w: fieldWords) {
				if (!lowerMethodName.contains(w.toLowerCase())) {
					containsAllWords = false;
					break;
				}
			}
			if (containsAllWords)
				return true;
		}
		
		return false;
	}


	// added by Ye Wang, 03/20/2017
	// Used to find the package of a class
	private String getPackageOfClass(IClass klass) {
		com.ibm.wala.types.TypeName typeName = klass.getName();
		String packageName = null;
		try {
			Object typeNameKey = this.getPrivateFieldValue(typeName, "key");
			Object packageNameAtom = this.getPrivateFieldValue(typeNameKey, "packageName");
			if (packageNameAtom == null)
				return null;
			else
				packageName = packageNameAtom.toString();
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
			consolegsydit.ExceptionHandler.process(e);
		}
		return packageName;
	}
	
	
	// added by Ye Wang, 02/08/2017
	private void analyzePeerVariable(Graph<ReferenceNode, ReferenceEdge> graph, Map<MethodReference, MethodReference> newToOldMethodRefMap, DataFlowAnalysisEngine leftEngine) {
		for (ReferenceNode node: graph.vertexSet()) {
			// for every AF
			if (node.type == ReferenceNode.AF) {
				String afName = ((MemberReference) node.ref).getName().toString();
				String afClassName = ((MemberReference) node.ref).getDeclaringClass().getName().getClassName().toString();
				// get changed methods
				Set<MethodReference> changedMethodSet = new HashSet<>();
				for (ReferenceEdge edge: graph.edgesOf(node)) {
					ReferenceNode mNode = graph.getEdgeSource(edge);
					MethodReference newRef = (MethodReference)mNode.ref;
					MethodReference mRef = newToOldMethodRefMap.get(newRef);
					changedMethodSet.add(mRef);
				}
				// get all classes using a changed method
				Set<IClass> classSet = new HashSet<>();
				for (MethodReference m: changedMethodSet) {
					TypeReference typeRef = m.getDeclaringClass();
					IClassHierarchy cha = leftEngine.getClassHierarchy();
					IClass klass = cha.lookupClass(typeRef);
					classSet.add(klass);
				}
				Set<IMethod> allIMethodSet = new HashSet<>();
				Map<IClass, Set<IMethod>> classToMethodMap = new HashMap<>();
				for (IClass klass: classSet) {
					allIMethodSet.addAll(klass.getDeclaredMethods());
					classToMethodMap.put(klass, new HashSet<IMethod>(klass.getDeclaredMethods()));
				}
				Map<MethodReference, IR> mRefToIR = new HashMap<>();
				for (IClass klass: classToMethodMap.keySet()) {
					for (IMethod m: classToMethodMap.get(klass)) {
						leftEngine.getCFGBuilder(m.getReference(), klass);
						IR ir = leftEngine.getCurrentIR();
						mRefToIR.put(m.getReference(), ir);
					}
				}
				Set<MethodReference> allMethodSet = new HashSet<>();
				allMethodSet.addAll(mRefToIR.keySet());
				
				List<Set<String>> varSetList = new ArrayList<Set<String>>();
				Map<MethodReference, Set<String>> mRefToVarMap = new HashMap<>();
				for (MethodReference mRef: mRefToIR.keySet()) {
					IR ir = mRefToIR.get(mRef);
					Set<String> varNames = this.getVarNames(ir);
					varSetList.add(varNames);
					mRefToVarMap.put(mRef, varNames);
				}
				
				if (changedMethodSet.size() >= 2) {
					Map<String, Integer> varInChangedMethod = new HashMap<>();
					Map<String, Integer> varInAllMethod = new HashMap<>();
					for (MethodReference m: changedMethodSet) {
						Set<String> vars = mRefToVarMap.get(m);
						for (String v: vars) {
							if (!varInChangedMethod.containsKey(v))
								varInChangedMethod.put(v, 1);
							else
								varInChangedMethod.put(v, varInChangedMethod.get(v) + 1);
						}
					}
					for (MethodReference m: allMethodSet) {
						Set<String> vars = mRefToVarMap.get(m);
						for (String v: vars) {
							if (!varInAllMethod.containsKey(v))
								varInAllMethod.put(v, 1);
							else
								varInAllMethod.put(v, varInAllMethod.get(v) + 1);
						}
					}
					List<Map.Entry<String, Integer>> sortedVarNumInCM = new ArrayList<>(varInChangedMethod.entrySet());
					Comparator<Map.Entry<String, Integer>> comparatorForVarNum = new Comparator<Map.Entry<String, Integer>>() {
						@Override
						public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
							return b.getValue() - a.getValue();
						}
					};
					Collections.sort(sortedVarNumInCM, comparatorForVarNum);
					List<String> completeVarList = new ArrayList<>();
					List<String> partialVarList = new ArrayList<>();
					int cmNum = changedMethodSet.size();
					for (Map.Entry<String, Integer> e: sortedVarNumInCM) {
						if (((Integer)e.getValue()).intValue() == cmNum)
							completeVarList.add(e.getKey());
						else
							partialVarList.add(e.getKey());
					}
					
					// write info to file
					// bugName,AFClass,AFName,cmNum,allMethodNum,classNum,
					// completeVarNum,partialVarNum,completeVars(numInAllM),partialVars(numInCM/numInAllM)
					StringBuilder output = new StringBuilder(CommitComparator.bugName);
					output.append(",");
					output.append(afClassName);
					output.append(",");
					output.append(afName);
					output.append(",");
					output.append(changedMethodSet.size());
					output.append(",");
					output.append(allMethodSet.size());
					output.append(",");
					output.append(classSet.size());
					output.append(",");
					output.append(completeVarList.size());
					output.append(",");
					output.append(partialVarList.size());
					output.append(",");
					for (String var: completeVarList) {
						output.append(var);
						output.append("(");
						output.append(varInAllMethod.get(var));
						output.append(");");
					}
					output.append(",");
					for (String var: partialVarList) {
						output.append(var);
						output.append("(");
						output.append(varInChangedMethod.get(var));
						output.append("/");
						output.append(varInAllMethod.get(var));
						output.append(");");
					}
					output.append("\n");
					
					Path peerOutputPath = FileSystems.getDefault().getPath("/Users/Vito/Documents/VT/2016fall/SE/peerVars.csv");
					try {
						Files.write(peerOutputPath, output.toString().getBytes(), APPEND);
					} catch (IOException e) {
						e.printStackTrace();
					}
					
				}

			}
		}
	}
	
	/**
	 * Get the names of fields used in a method, and the field must be in the same
	 * class of AF. 
	 * @param ir the IR of a method
	 * @param afClass class of AF
	 * @return set of field names
	 */
	private Set<String> getFieldNames(IR ir, TypeReference afClass) {
		if (ir == null)
			return Collections.emptySet();
		// get fields
		Set<String> fieldNames = new HashSet<String>();
		for (SSAInstruction instr: ir.getInstructions()) {
			if (instr instanceof SSAFieldAccessInstruction) {
				SSAFieldAccessInstruction fInstr = (SSAFieldAccessInstruction)instr;
				FieldReference fieldRef = fInstr.getDeclaredField();
				
				// check if the field is in the same class as AF
				if (afClass.equals(fieldRef.getDeclaringClass())) {
					String fieldName = fieldRef.getName().toString();
					fieldNames.add(fieldName);
				}
			}
		}
		return fieldNames;
	}
	
	/**
	 * Get the names of fields and local variables of a method
	 * @param ir the IR of a method
	 * @return set of variable names
	 */
	private Set<String> getVarNames(IR ir) {
		if (ir == null)
			return Collections.emptySet();
		// get fields
		Set<String> fieldNames = new HashSet<String>();
		for (SSAInstruction instr: ir.getInstructions()) {
			if (instr instanceof SSAFieldAccessInstruction) {
				SSAFieldAccessInstruction fInstr = (SSAFieldAccessInstruction)instr;
				FieldReference fieldRef = fInstr.getDeclaredField();
				String fieldName = fieldRef.getName().toString();
				fieldNames.add(fieldName);
			}
		}
		
		// get local variables
//		Set<String> localVars = new HashSet<String>();
//		SSAInstruction[] instructions = ir.getInstructions();
//		for (int instrIndex = 0; instrIndex < instructions.length; instrIndex++) {
//			SSAInstruction instr = instructions[instrIndex];
//			if (instr != null) {
//				try {
//					int def = instr.getDef();
//					if (def != -1) {
//						String[] localNames = ir.getLocalNames(instrIndex, def);
//						if (localNames.length == 1) {
//							String localName = localNames[0];
//							localVars.add(localName);
//						}
//					}
//				} catch (NullPointerException x) {
//					// do nothing, just skip this one
//				}
//			}
//		}
		
		// get local vars through valueNumberNames[][]
		// Indices start from 1, 0 is not used. For non-static method, index 1 is this.
		String[][] valueNumberNames = null;
		try {
			Object localMap = this.getPrivateFieldValue(ir, "localMap");
			Object dontKnowWhatItIs = this.getPrivateFieldValue(localMap, "this$0");
			Object debugInfo = this.getPrivateFieldValueInSuperClass(dontKnowWhatItIs, "debugInfo");
			valueNumberNames = (String[][])this.getPrivateFieldValue(debugInfo, "valueNumberNames");
		} catch (NoSuchFieldException e) {
			e.printStackTrace();
			consolegsydit.ExceptionHandler.process(e);
		}
		Set<String> localVars = new HashSet<String>();
		// check if the method is a static method
		if (valueNumberNames.length >= 2 && valueNumberNames[1].length == 1 && !valueNumberNames[1][0].equals("this")) {
			localVars.add(valueNumberNames[1][0]);
		}
		for (int i = 2; i < valueNumberNames.length; i++) {
			if (valueNumberNames[i].length == 1)
				localVars.add(valueNumberNames[i][0]);
		}
		
		// merge fields and local variables
		Set<String> fieldsAndLocals = new HashSet<String>();
		fieldsAndLocals.addAll(fieldNames);
		fieldsAndLocals.addAll(localVars);
		
		return fieldsAndLocals;
	}
	
	private Object getPrivateFieldValue(Object c, String fieldName) throws NoSuchFieldException {
		Class<?> klass = c.getClass();
		Field field = null;
		try {
			field = klass.getDeclaredField(fieldName);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			consolegsydit.ExceptionHandler.process(e);
		}
		
		field.setAccessible(true);
		Object fieldObject = null;
		try {
			fieldObject = field.get(c);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			consolegsydit.ExceptionHandler.process(e);
		}
//		field.setAccessible(false);
		
		return fieldObject;
	}
	
	private Object getPrivateFieldValueInSuperClass(Object c, String fieldName) throws NoSuchFieldException {
		Class<?> klass = c.getClass();
		Class<?> superClass = (Class<?>) klass.getGenericSuperclass();
		Field field = null;
		try {
			field = superClass.getDeclaredField(fieldName);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			consolegsydit.ExceptionHandler.process(e);
		}
		
		field.setAccessible(true);
		Object fieldObject = null;
		try {
			fieldObject = field.get(c);
		} catch (IllegalArgumentException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			consolegsydit.ExceptionHandler.process(e);
		}
//		field.setAccessible(false);
		
		return fieldObject;
	}
	
	// Added by Ye Wang, 10/29/2016
	// store the found graphs in the first parameter graphs
	@Deprecated
	private void analyzeChangeLink(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
			DataFlowAnalysisEngine leftEngine, DataFlowAnalysisEngine rightEngine,
			Set<ClientMethod> oldChangedMethods, Set<ClientMethod> newChangedMethods,
			Set<ClientMethod> insertedMethods, Set<ClientMethod> deletedMethods,
			Set<MethodReference> changedMethodRefs, Set<MethodReference> insertedMethodRefs,
			Set<MethodReference> deletedMethodRefs, Set<FieldReference> insertedFieldRefs,
			Set<FieldReference> deletedFieldRefs, Map<ClientMethod, List<SourceCodeRange>> oldMethodToRange,
			Map<ClientMethod, List<SourceCodeRange>> newMethodToRange) {	
		analyzeFieldAccess(graphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				deletedFieldRefs, ReferenceNode.DF);
		analyzeFieldAccess(graphs, leftEngine, deletedMethods, ReferenceNode.DM,
				deletedFieldRefs, ReferenceNode.DF);
		analyzeFieldAccess(graphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				insertedFieldRefs, ReferenceNode.AF);
		analyzeFieldAccess(graphs, rightEngine, insertedMethods, ReferenceNode.AM,
				insertedFieldRefs, ReferenceNode.AF);
		
		analyzeMethodToMethod(graphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				oldMethodToRange, deletedMethodRefs, ReferenceNode.DM);
		analyzeMethodToMethod(graphs, leftEngine, oldChangedMethods, ReferenceNode.CM,
				oldMethodToRange, changedMethodRefs, ReferenceNode.CM);
		analyzeMethodToMethod(graphs, leftEngine, deletedMethods, ReferenceNode.DM,
				null, deletedMethodRefs, ReferenceNode.DM);
		analyzeMethodToMethod(graphs, leftEngine, deletedMethods, ReferenceNode.DM,
				null, changedMethodRefs, ReferenceNode.CM);
		analyzeMethodToMethod(graphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				newMethodToRange, insertedMethodRefs, ReferenceNode.AM);
		analyzeMethodToMethod(graphs, rightEngine, newChangedMethods, ReferenceNode.CM,
				newMethodToRange, changedMethodRefs, ReferenceNode.CM);
		analyzeMethodToMethod(graphs, rightEngine, insertedMethods, ReferenceNode.AM,
				null, insertedMethodRefs, ReferenceNode.AM);
		analyzeMethodToMethod(graphs, rightEngine, insertedMethods, ReferenceNode.AM,
				null, changedMethodRefs, ReferenceNode.CM);
	}
	
	private void analyzeFieldAccess(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
			DataFlowAnalysisEngine engine, Set<ClientMethod> methods, int rootMethodNodeType,
			Set<FieldReference> fieldRefs, int fieldNodeType) {
		if (fieldRefs.isEmpty()) return;
		for (ClientMethod m: methods) {
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph =
					new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
			graphs.add(graph);
			ReferenceNode root = new ReferenceNode(m.mRef, rootMethodNodeType);
			engine.getCFGBuilder(m);
			IR ir = engine.getCurrentIR();
			if (ir == null)
				continue;
			for (SSAInstruction instr: ir.getInstructions()) {
				if (instr == null)
					continue;
				if (!fieldRefs.isEmpty() && instr instanceof SSAFieldAccessInstruction) {
					SSAFieldAccessInstruction sInstr = (SSAFieldAccessInstruction)instr;
					FieldReference fRef = sInstr.getDeclaredField();
					if (fieldRefs.contains(fRef)) {
						ReferenceNode n = new ReferenceNode(fRef, fieldNodeType);
						ReferenceEdge edge = graph.findEdge(root, n);
						if (edge == null) {
							edge = new ReferenceEdge(root, n, ReferenceEdge.FIELD_ACCESS);
							graph.addEdge(edge, root, n);
						}
						edge.increaseCount();
					}
				}
			}
		}
	}
	
	private void analyzeMethodToMethod(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
			DataFlowAnalysisEngine engine, Set<ClientMethod> methods, int rootMethodNodeType,
			Map<ClientMethod, List<SourceCodeRange>> methodToRange,
			Set<MethodReference> methodRefs, int methodNodeType) {
		if (methodRefs.isEmpty()) return;
		for (ClientMethod m: methods) {
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph =
					new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
			graphs.add(graph);
			ReferenceNode root = new ReferenceNode(m.mRef, rootMethodNodeType);
					
			// Analyze method invocation
			engine.getCFGBuilder(m);
			IR ir = engine.getCurrentIR();
			if (ir == null)
				continue;
			
			// Build sdg for dependency analysis
			boolean dependencyAnalysisHasProblem = false;
			SDG sdg = engine.buildSystemDependencyGraph2(m);
			if (sdg == null)
				dependencyAnalysisHasProblem = true;
			CGNode cgNode = null;
			PDG pdg = null;
			Map<SSAInstruction, Integer> instructionIndices = null;
			ConcreteJavaMethod concreteJavaMethod = null;
			List<LineRange> lineRanges = null;
			if (!dependencyAnalysisHasProblem) {
				CallGraph callGraph = sdg.getCallGraph();
				Set<CGNode> cgNodeSet =callGraph.getNodes(m.mRef);
				boolean cgNodeSetIsEmpty = false;
				if (cgNodeSet.isEmpty()) {
					System.err.println("cgNodeSet is empty! ");
					final boolean writeEmptyCGNodeLog = false;
					if (writeEmptyCGNodeLog) {
						String emptyCGNodeLog = "/Users/Vito/Documents/VT/2016fall/SE/emptyCGNode.txt";
						Path logPath = FileSystems.getDefault().getPath(emptyCGNodeLog);
						String logInfo = m.toString() + "\n";
						try {
							Files.write(logPath, logInfo.getBytes(), CREATE, APPEND);
						} catch (IOException e) {
							System.err.println(e.getMessage());
						}
					}
					cgNodeSetIsEmpty = true;
					dependencyAnalysisHasProblem = true;
				}
				if (!cgNodeSetIsEmpty) {
	//			CGNode cgNode = callGraph.getNode(0);
					cgNode = cgNodeSet.iterator().next();
					pdg = sdg.getPDG(cgNode);
					instructionIndices = PDG.computeInstructionIndices(ir);
					
					
					concreteJavaMethod = (ConcreteJavaMethod)cgNode.getMethod();
					lineRanges = null;
					// Changed Method, not AM or DM
					if (methodToRange != null) {
						// build a map from instruction index to line number
						Map<Integer, Integer> indexToLine = new HashMap<Integer, Integer>();
						Iterator<Statement> it = pdg.iterator();
						while (it.hasNext()) {
							Statement stmt = it.next();
							int instructionIndex;
							if (stmt instanceof StatementWithInstructionIndex) {
								StatementWithInstructionIndex stmtWithIndex = (StatementWithInstructionIndex)stmt;
								instructionIndex = stmtWithIndex.getInstructionIndex();
							} else {
								continue;
							}
							int lineNumber = concreteJavaMethod.getLineNumber(instructionIndex);
							indexToLine.put(instructionIndex, lineNumber);
						}
						
						// find the changed line range
						List<SourceCodeRange> ranges = methodToRange.get(m);
						lineRanges = new ArrayList<LineRange>();
						for (SourceCodeRange range: ranges) {
							CompilationUnit cu = (CompilationUnit)m.ast;
							LineRange lineRange = LineRange.get(cu, range);
							lineRanges.add(lineRange);
						}
					}
				}
			
			}
			
			
			
			
			for (SSAInstruction instr: ir.getInstructions()) {
				if (instr == null)
					continue;
				if (!methodRefs.isEmpty() && (instr instanceof SSAAbstractInvokeInstruction)) {
					MethodReference mRef = ((SSAAbstractInvokeInstruction)instr).getDeclaredTarget();
					if (methodRefs.contains(mRef)) {
						ReferenceNode n = new ReferenceNode(mRef, methodNodeType);
						ReferenceEdge edge = graph.findEdge(root, n);
						if (edge == null) {
							edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_INVOKE);
							graph.addEdge(edge, root, n);
						}
						edge.increaseCount();
						
						if (!dependencyAnalysisHasProblem) {
							// Analyze dependency
							Statement statement = PDG.ssaInstruction2Statement(cgNode, instr, instructionIndices, ir);
							
							
							
	//						Iterator<Statement> DepSuccIter = pdg.getSuccNodes(statement);
	//						while (DepSuccIter.hasNext()) {
	//							Statement succStmt = DepSuccIter.next();
	//							if (pdg.isControlDependend(statement, succStmt)) {
	//								// another statement is control-dependent on callee
	//								edge.dep |= ReferenceEdge.OTHER_CONTROL_DEP_CALLEE;
	//							} else {
	//								// another statement is data-dependent on callee
	//								edge.dep |= ReferenceEdge.OTHER_DATA_DEP_CALLEE;
	//							}
	//						}
							
							
							Iterator<Statement> stmtIter = pdg.iterator();
							while (stmtIter.hasNext()) {
								// check whether callee is dependent on another statement
								Statement stmt = stmtIter.next();
								
								
								// check if stmt is changed statement
								boolean statementIsChanged = false;
								if (methodToRange != null) { // for CM
									boolean canGetIndex = true;
									// from Statement to instruction index
									int instructionIndex = -1;
									if (stmt instanceof StatementWithInstructionIndex) {
										StatementWithInstructionIndex stmtWithIndex = (StatementWithInstructionIndex)stmt;
										instructionIndex = stmtWithIndex.getInstructionIndex();
									} else if (stmt instanceof GetCaughtExceptionStatement) {
										GetCaughtExceptionStatement s = (GetCaughtExceptionStatement)stmt;
										SSAInstruction instruction = s.getInstruction();
										instructionIndex = instruction.iindex;
									} else {
										canGetIndex = false;
									}
									if (canGetIndex) {
										// from instruction index to line number
										int lineNumber = concreteJavaMethod.getLineNumber(instructionIndex);
										
										// check if line number is in line ranges
										for (LineRange range: lineRanges) {
											if (lineNumber >= range.startLine && lineNumber <= range.endLine) {
												statementIsChanged = true;
												break;
											}
										}
									}
								} else { // for AM or DM
									statementIsChanged = true;
								}
								
								if (!statementIsChanged)
									continue;
								
								
								if (pdg.hasEdge(stmt, statement)) {
									// callee is dependent on another statement
									if (pdg.isControlDependend(stmt, statement)) {
										// control dependent
										edge.dep |= ReferenceEdge.CALLEE_CONTROL_DEP_OTHER;
									} else {
										// data dependent
										edge.dep |= ReferenceEdge.CALLEE_DATA_DEP_OTHER;
									}
								}
								
								if (pdg.hasEdge(statement, stmt)) {
									// another statement is dependent on callee
									if (pdg.isControlDependend(statement, stmt)) {
										// control dependent
										edge.dep |= ReferenceEdge.OTHER_CONTROL_DEP_CALLEE;
									} else {
										// data dependent
										edge.dep |= ReferenceEdge.OTHER_DATA_DEP_CALLEE;
									}
								}
								
							}
						}
						
					}
				}
			}
			
			
			
			// Analyze method overriding
			MethodReference mRef = m.mRef;
			if (mRef == null)
				continue;
			TypeReference typeRef = mRef.getDeclaringClass();
			IClassHierarchy cha = engine.getClassHierarchy();
			IMethod method = cha.resolveMethod(mRef);
			Selector selector = method.getSelector();
			IClass klass = cha.lookupClass(typeRef);
			Collection<? extends IClass> directInterfaces = klass.getDirectInterfaces();
			IClass superclass = klass.getSuperclass();
			Set<IClass> supertypes = new HashSet<IClass>(directInterfaces);
			if (superclass != null)
				supertypes.add(superclass);
			for (IClass supertype: supertypes) {
				Collection<IMethod> supertypeMethods = supertype.getDeclaredMethods();
				for (IMethod supertypeMethod: supertypeMethods) {
					Selector supertypeMethodSelector = supertypeMethod.getSelector();
					if (selector.equals(supertypeMethodSelector)) {
						MethodReference suptertypeMRef = supertypeMethod.getReference();
						if (methodRefs.contains(suptertypeMRef)) {
							ReferenceNode n = new ReferenceNode(suptertypeMRef, methodNodeType);
							ReferenceEdge edge = graph.findEdge(root, n);
							if (edge == null) {
								edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_OVERRIDE);
								graph.addEdge(edge, root, n);
							}
							edge.increaseCount();
						}
					}
				}
			}
			
		}
	}
	
//	private void analyzeMethodOverride(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
//			DataFlowAnalysisEngine engine, Set<ClientMethod> methods, int rootMethodNodeType,
//			Set<MethodReference> methodRefs, int methodNodeType) {
//		for (ClientMethod m: methods) {
//			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph =
//					new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
//			graphs.add(graph);
//			ReferenceNode root = new ReferenceNode(m.mRef, rootMethodNodeType);
//			MethodReference mRef = m.mRef;
//			TypeReference typeRef = mRef.getDeclaringClass();
//			IClassHierarchy cha = engine.getClassHierarchy();
//			IMethod method = cha.resolveMethod(mRef);
//			Selector selector = method.getSelector();
//			IClass klass = cha.lookupClass(typeRef);
//			Collection<? extends IClass> directInterfaces = klass.getDirectInterfaces();
//			IClass superclass = klass.getSuperclass();
//			Set<IClass> supertypes = new HashSet<IClass>(directInterfaces);
//			if (superclass != null)
//				supertypes.add(superclass);
//			for (IClass supertype: supertypes) {
//				Collection<IMethod> supertypeMethods = supertype.getDeclaredMethods();
//				for (IMethod supertypeMethod: supertypeMethods) {
//					Selector supertypeMethodSelector = supertypeMethod.getSelector();
//					if (selector.equals(supertypeMethodSelector)) {
//						MethodReference suptertypeMRef = supertypeMethod.getReference();
//						if (methodRefs.contains(suptertypeMRef)) {
//							ReferenceNode n = new ReferenceNode(suptertypeMRef, methodNodeType);
//							ReferenceEdge edge = graph.findEdge(root, n);
//							if (edge == null) {
//								edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_OVERRIDE);
//								graph.addEdge(edge, root, n);
//							}
//							edge.increaseCount();
//						}
//					}
//				}
//			}
//		}
//	}
	
//	private void analyzePartialChangeLink(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
//			DataFlowAnalysisEngine engine, Set<ClientMethod> methods,
//			Set<MethodReference> changedMethodRefs, int changedMethodNodeType,
//			Set<MethodReference> methodRefs2, int methodNodeType2,
//			Set<FieldReference> fieldRefs, int fieldNodeType) {
//		for (ClientMethod m: methods) {
//			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph = new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
//			graphs.add(graph);
//			ReferenceNode root = new ReferenceNode(m.mRef, ReferenceNode.CM);
//			engine.getCFGBuilder(m);
//			IR ir = engine.getCurrentIR();
//			if (ir == null)
//				continue;
//			for (SSAInstruction instr: ir.getInstructions()) {
//				if (instr == null)
//					continue;
//				if (instr instanceof SSAAbstractInvokeInstruction) {
//					MethodReference mRef = ((SSAAbstractInvokeInstruction)instr).getDeclaredTarget();
//					if (changedMethodRefs.contains(mRef)) {
//						// The node is a changed method node
//						ReferenceNode n = new ReferenceNode(mRef, changedMethodNodeType);
//						ReferenceEdge edge = graph.findEdge(root, n);
//						if (edge == null) {
//							edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_INVOKE);
//							graph.addEdge(edge, root, n);
//						}
//						edge.increaseCount();
//					} else if (methodRefs2.contains(mRef)) {
//						ReferenceNode n = new ReferenceNode(mRef, methodNodeType2);
//						ReferenceEdge edge = graph.findEdge(root, n);
//						if (edge == null) {
//							edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_INVOKE);
//							graph.addEdge(edge, root, n);
//						}
//						edge.increaseCount();
//					}
//				}
//			}
//		}
//	}

	private static List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> convertNewVersionToOldVersion(
			List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,
			Map<? extends MemberReference, ? extends MemberReference> newToOldVersionMap) {
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> result = new ArrayList<>();
		for (DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph: graphs) {
			
			if (graph.getVertexCount() == 0)
				continue;
			
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> modifiedGraph =
					new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
			
			Map<MemberReference, ReferenceNode> refToNode = new HashMap<>();
			
			Collection<ReferenceNode> vertices = graph.getVertices();
			for (ReferenceNode vertex: vertices) {
				if (newToOldVersionMap.containsKey(vertex.ref)) {
					MemberReference oldVersionRef = newToOldVersionMap.get(vertex.ref);
					ReferenceNode oldVersionVertex = new ReferenceNode(oldVersionRef, vertex.type);
					modifiedGraph.addVertex(oldVersionVertex);
					refToNode.put((MemberReference) vertex.ref, oldVersionVertex);
				} else {
					modifiedGraph.addVertex(vertex);
					refToNode.put((MemberReference) vertex.ref, vertex);
				}
			}
			
			Collection<ReferenceEdge> edges = graph.getEdges();
			for (ReferenceEdge edge: edges) {
				ReferenceNode src = edge.from;
				ReferenceNode dst = edge.to;
				ReferenceNode newSrc = refToNode.get(src.ref);
				ReferenceNode newDst = refToNode.get(dst.ref);
				
				ReferenceEdge modifiedEdge = new ReferenceEdge(newSrc, newDst, edge.type);
				modifiedEdge.dep = edge.dep;
//				System.out.println("src: " + src);
//				System.out.println("dst: " + dst);
//				System.out.println("newSrc: " + newSrc);
//				System.out.println("newDst: " + newDst);
				modifiedGraph.addEdge(modifiedEdge, newSrc, newDst);
			}
			
			result.add(modifiedGraph);
		}
		
		return result;
	}
	
	private List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> mergeChanges(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs) {
		List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> result = new ArrayList<DirectedSparseGraph<ReferenceNode, ReferenceEdge>>();
	
		DirectedSparseGraph<ReferenceNode, ReferenceEdge> g1 = null, g2 = null;
		boolean isChanged = true;
		Set<ReferenceNode> nSet1 = null, nSet2 = null;
		while(isChanged) {
			isChanged = false;
			for (int i = 0; i < graphs.size() - 1; i++) {
				g1 = graphs.get(i);
				if (g1.getVertexCount() == 0)
					continue;
				nSet1 = new HashSet<ReferenceNode>(g1.getVertices());
				for (int j = i + 1; j < graphs.size(); j++) {
					// added by Ye Wang, to fix bug
					boolean isChangedThisTime = false;
					
					g2 = graphs.get(j);
					if (g2.getVertexCount() == 0)
						continue;
					for (ReferenceNode n : nSet1) {
						if (g2.containsVertex(n)) {
							// added by Ye Wang, to fix bug
							isChangedThisTime = true;
							
							isChanged = true;
							break;
						}
					}
					// if (isChanged) {
					// changed by Ye Wang, to fix bug
					if (isChangedThisTime) {
						for (ReferenceNode n : g2.getVertices()) {
							if (!g1.containsVertex(n)) {								
								g1.addVertex(n);
							}
						}
						for (ReferenceEdge e : g2.getEdges()) {
							if (!g1.containsEdge(e)) {
								g1.addEdge(e, e.from, e.to);
							}
						}
						nSet2 = new HashSet<ReferenceNode>(g2.getVertices());
						for (ReferenceNode n : nSet2) {
							g2.removeVertex(n);
						}
						break;
					}
				}
			}
		}
		for (int i = 0; i < graphs.size(); i++) {
			g1 = graphs.get(i);
			if (g1.getVertexCount() != 0) {
				result.add(g1);
//				for (ReferenceEdge e : g1.getEdges()) {
//					System.out.println(e);
//				}
			}
		}
		return result;
	}
	
	private void relateChangesBasedOnIR(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,  
			DataFlowAnalysisEngine engine, Set<ClientMethod> methods, 
			Set<MethodReference> mRefs, Set<FieldReference> fRefs, 
			Set<TypeReference> cRefs) {
		for (ClientMethod m : methods) {
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph = new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
			graphs.add(graph);
			ReferenceNode root = new ReferenceNode(m.mRef);
			engine.getCFGBuilder(m);
			IR ir = engine.getCurrentIR();
			if (ir == null)
				continue;
			for (SSAInstruction instr : ir.getInstructions()) {
				if (instr == null)
			        continue;
				if (!fRefs.isEmpty() && instr instanceof SSAFieldAccessInstruction) {
					SSAFieldAccessInstruction sInstr = (SSAFieldAccessInstruction)instr;
					FieldReference fRef = sInstr.getDeclaredField();
					if (fRefs.contains(fRef)) {
						ReferenceNode n = new ReferenceNode(fRef);
						ReferenceEdge edge = graph.findEdge(root, n);
						if (edge == null) {
							edge = new ReferenceEdge(root, n, ReferenceEdge.FIELD_ACCESS);
							graph.addEdge(edge, root, n);
						}								
						edge.increaseCount();						
					}
				} 
				if (!mRefs.isEmpty() && (instr instanceof SSAAbstractInvokeInstruction)) {
					MethodReference mRef = ((SSAAbstractInvokeInstruction)instr).getDeclaredTarget();
					if (mRefs.contains(mRef)) {
						ReferenceNode n = new ReferenceNode(mRef);						
						ReferenceEdge edge = graph.findEdge(root, n);
						if (edge == null) {
							edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_INVOKE);
							graph.addEdge(edge, root, n);
						}								
						edge.increaseCount();
					}
				}
			}
		}
	}
	
	// Added by Ye Wang, since 10/02/2016
	private void relateAllChangesBasedOnIR(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,  
			DataFlowAnalysisEngine leftEngine, DataFlowAnalysisEngine rightEngine, Set<ClientMethod> oldMethods,
			Set<ClientMethod> newMethods, Set<MethodReference> oMRefs, Set<MethodReference> nMRefs,
			Set<FieldReference> oFRefs, Set<FieldReference> nFRefs, 
			Set<TypeReference> oCRefs, Set<TypeReference> nCRefs) {
		relatePartialChanges(graphs, leftEngine, oldMethods, oMRefs, nMRefs, oFRefs, nFRefs, oCRefs, nCRefs);
		relatePartialChanges(graphs, rightEngine, newMethods, oMRefs, nMRefs, oFRefs, nFRefs, oCRefs, nCRefs);
	}
	
	// added by Ye Wang, since 10/18/2016
	// avoid copy-paste in relateAllChangesBasedOnIR()
	private void relatePartialChanges(List<DirectedSparseGraph<ReferenceNode, ReferenceEdge>> graphs,  
			DataFlowAnalysisEngine engine, Set<ClientMethod> methods,
			Set<MethodReference> oMRefs, Set<MethodReference> nMRefs,
			Set<FieldReference> oFRefs, Set<FieldReference> nFRefs, 
			Set<TypeReference> oCRefs, Set<TypeReference> nCRefs) {
		for (ClientMethod m : methods) {
			DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph = new DirectedSparseGraph<ReferenceNode, ReferenceEdge>();
			graphs.add(graph);
			ReferenceNode root = new ReferenceNode(m.mRef);
			engine.getCFGBuilder(m);
			IR ir = engine.getCurrentIR();
			if (ir == null)
				continue;
			for (SSAInstruction instr : ir.getInstructions()) {
				if (instr == null)
					continue;
				if ((!oFRefs.isEmpty() || !nFRefs.isEmpty()) && instr instanceof SSAFieldAccessInstruction) {
					SSAFieldAccessInstruction sInstr = (SSAFieldAccessInstruction)instr;
					FieldReference fRef = sInstr.getDeclaredField();
					if (oFRefs.contains(fRef) || nFRefs.contains(fRef)) {
						ReferenceNode n = new ReferenceNode(fRef);
						ReferenceEdge edge = graph.findEdge(root, n);
						if (edge == null) {
							edge = new ReferenceEdge(root, n, ReferenceEdge.FIELD_ACCESS);
							graph.addEdge(edge, root, n);
						}								
						edge.increaseCount();						
					}
				} 
				if ((!oMRefs.isEmpty() || !nMRefs.isEmpty()) && (instr instanceof SSAAbstractInvokeInstruction)) {
					MethodReference mRef = ((SSAAbstractInvokeInstruction)instr).getDeclaredTarget();
					if (oMRefs.contains(mRef) || nMRefs.contains(mRef)) {
						ReferenceNode n = new ReferenceNode(mRef);						
						ReferenceEdge edge = graph.findEdge(root, n);
						if (edge == null) {
							edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_INVOKE);
							graph.addEdge(edge, root, n);
						}								
						edge.increaseCount();
					}
				}
			}

			MethodReference mRef = m.mRef;
			TypeReference typeRef = mRef.getDeclaringClass();
			IClassHierarchy cha = engine.getClassHierarchy();
			IMethod method = cha.resolveMethod(mRef);
			Selector selector = method.getSelector();
			IClass klass = cha.lookupClass(typeRef);
			Collection<? extends IClass> directInterfaces = klass.getDirectInterfaces();
			IClass superclass = klass.getSuperclass();
			Set<IClass> supertypes = new HashSet<IClass>(directInterfaces);
			if (superclass != null) 
				supertypes.add(superclass);
			for (IClass supertype: supertypes) {
//				System.out.println(supertype);
				Collection<IMethod> supertypeMethods = supertype.getDeclaredMethods();
				for (IMethod supertypeMethod: supertypeMethods) {
					Selector supertypeMethodSelector = supertypeMethod.getSelector();
					if (selector.equals(supertypeMethodSelector)) {
						MethodReference suptertypeMRef = supertypeMethod.getReference();
						if (oMRefs.contains(suptertypeMRef) || nMRefs.contains(suptertypeMRef)) {
							ReferenceNode n = new ReferenceNode(suptertypeMRef);
							ReferenceEdge edge = graph.findEdge(root, n);
							if (edge == null) {
								edge = new ReferenceEdge(root, n, ReferenceEdge.METHOD_OVERRIDE);
								graph.addEdge(edge, root, n);
							}
							edge.increaseCount();
						}
					}
				}
			}
			
			
		}
	}
	
	/**
	 * Convert JUNG to JGraphT
	 * @param g JUNG graph
	 * @return JGraphT graph
	 */
	public static Graph<ReferenceNode, ReferenceEdge>
			convertJungToJGraphT(DirectedSparseGraph<ReferenceNode, ReferenceEdge> graph) {
		Graph<ReferenceNode, ReferenceEdge> g =
				new DefaultDirectedGraph<ReferenceNode, ReferenceEdge>(ReferenceEdge.class);
		
		Collection<ReferenceNode> vertices = graph.getVertices();
		for (ReferenceNode vertex: vertices) {
			g.addVertex(vertex);
		}
		
		Collection<ReferenceEdge> edges = graph.getEdges();
		for (ReferenceEdge edge: edges) {
			ReferenceNode sourceVertex = edge.from;
			ReferenceNode targetVertex = edge.to;
			try {
				g.addEdge(sourceVertex, targetVertex, edge);
			} catch (Exception e) {
				e.printStackTrace();
				consolegsydit.ExceptionHandler.process(e);
			}
		}
		
		return g;
	}
	
	public static void writeRelationGraph(DirectedSparseGraph<ReferenceNode, ReferenceEdge> g, String filename) {
		String xmlFile = filename + ".xml";
		xmlFile = xmlFile.replaceAll("<", "");
		xmlFile = xmlFile.replaceAll(">", "");
		XStream xstream = new XStream(new StaxDriver());
		try{
			 File file = new File(xmlFile);
			 FileWriter writer=new FileWriter(file);
			 String content = xstream.toXML(g);
			 writer.write(content);
			 writer.close();
		} catch (IOException e){
			 e.printStackTrace();
			 consolegsydit.ExceptionHandler.process(e);
		}
	}

	
	
	private List<Set<ClientMethod>> groupMethods(List<ClientMethod> ms, List<CallGraph> cgs) {
//		System.out.print("");
		List<Set<ClientMethod>> g = new ArrayList<Set<ClientMethod>>();
		for (int i = 0; i < ms.size(); i++) {
			CallGraph cg = cgs.get(i);
			ClientMethod m = ms.get(i);
			Set<CGNode> cgnodes = cg.getNodes(m.mRef);
			Set<ClientMethod> groupedMethods = new HashSet<ClientMethod>();
			for (int j = i + 1; j < ms.size(); j++) {
				ClientMethod m2 = ms.get(j);
				Set<CGNode> cgnodes2 = cg.getNodes(m2.mRef);
				for (CGNode n1 : cgnodes) {
					for (CGNode n2 : cgnodes2) {
						if (cg.hasEdge(n1, n2) || cg.hasEdge(n2, n1)) {
							groupedMethods.add(ms.get(i));
							groupedMethods.add(ms.get(j));
							break;
						}
					}
				}
			}
			g.add(groupedMethods);
		}
		boolean isChanged = true;
		while (isChanged) { // merge groups as much as we can 
			isChanged = false;
			for (int i = 0; i < g.size(); i++) {
				Set<ClientMethod> mSet1 = g.get(i);
				for (int j = i + 1; j < g.size(); j++) {
					Set<ClientMethod> mSet2 = g.get(j);
					Set<ClientMethod> copy = new HashSet<ClientMethod>(mSet2);
					copy.retainAll(mSet2);
					if (!copy.isEmpty()) {
						mSet1.addAll(mSet2);
						mSet2.clear();
						isChanged = true;
						break;
					}
				}
			}
		}				
		return g;
	}
}
