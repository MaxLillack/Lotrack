package soot.jimple.toolkits.ide.icfg;

import heros.DontSynchronize;
import heros.SynchronizedBy;
import heros.solver.IDESolver;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.SootMethod;
import soot.Unit;
import soot.UnitBox;
import soot.Value;
import soot.jimple.Stmt;
import soot.toolkits.exceptions.UnitThrowAnalysis;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.ExceptionalUnitGraph;

import com.google.common.base.Predicate;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Iterables;


public abstract class AbstractJimpleBasedICFG implements BiDiInterproceduralCFG<Unit,SootMethod> {

	@DontSynchronize("written by single thread; read afterwards")
	protected final Map<Unit,Body> unitToOwner = new HashMap<Unit,Body>();
	
	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<Body,DirectedGraph<Unit>> bodyToUnitGraph = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<Body,DirectedGraph<Unit>>() {
					@Override
					public DirectedGraph<Unit> load(Body body) throws Exception {
						return makeGraph(body);
					}
				});
	
	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<SootMethod,List<Value>> methodToParameterRefs = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<SootMethod,List<Value>>() {
					@Override
					public List<Value> load(SootMethod m) throws Exception {
						return m.getActiveBody().getParameterRefs();
					}
				});

	@SynchronizedBy("by use of synchronized LoadingCache class")
	protected final LoadingCache<SootMethod,Set<Unit>> methodToCallsFromWithin = IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<SootMethod,Set<Unit>>() {
					@Override
					public Set<Unit> load(SootMethod m) throws Exception {
						Set<Unit> res = new LinkedHashSet<Unit>();
						for(Unit u: m.getActiveBody().getUnits()) {
							if(isCallStmt(u))
								res.add(u);
						}
						return res;
					}
				});

	@Override
	public SootMethod getMethodOf(Unit u) {
		assert unitToOwner.containsKey(u);
		return unitToOwner.get(u).getMethod();
	}

	@Override
	public List<Unit> getSuccsOf(Unit u) {
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
		return unitGraph.getSuccsOf(u);
	}

	@Override
	public DirectedGraph<Unit> getOrCreateUnitGraph(SootMethod m) {
		return getOrCreateUnitGraph(m.getActiveBody());
	}

	public DirectedGraph<Unit> getOrCreateUnitGraph(Body body) {
		return bodyToUnitGraph.getUnchecked(body);
	}

	protected DirectedGraph<Unit> makeGraph(Body body) {
		return new ExceptionalUnitGraph(body, UnitThrowAnalysis.v() ,true);
	}

	@Override
	public boolean isExitStmt(Unit u) {
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
		return unitGraph.getTails().contains(u);
	}

	@Override
	public boolean isStartPoint(Unit u) {
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);		
		return unitGraph.getHeads().contains(u);
	}

	@Override
	public boolean isFallThroughSuccessor(Unit u, Unit succ) {
		assert getSuccsOf(u).contains(succ);
		if(!u.fallsThrough()) return false;
		Body body = unitToOwner.get(u);
		return body.getUnits().getSuccOf(u) == succ;
	}

	@Override
	public boolean isBranchTarget(Unit u, Unit succ) {
		assert getSuccsOf(u).contains(succ);
		if(!u.branches()) return false;
		for (UnitBox ub : succ.getUnitBoxes()) {
			if(ub.getUnit()==succ) return true;
		}
		return false;
	}

	public List<Value> getParameterRefs(SootMethod m) {
		return methodToParameterRefs.getUnchecked(m);
	}

	@Override
	public Collection<Unit> getStartPointsOf(SootMethod m) {
		if(m.hasActiveBody()) {
			Body body = m.getActiveBody();
			DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
			return unitGraph.getHeads();
		}
		return Collections.emptySet();
	}

	@Override
	public boolean isCallStmt(Unit u) {
		return ((Stmt)u).containsInvokeExpr();
	}

	@Override
	public Set<Unit> allNonCallStartNodes() {
		Set<Unit> res = new LinkedHashSet<Unit>(unitToOwner.keySet());
		for (Iterator<Unit> iter = res.iterator(); iter.hasNext();) {
			Unit u = iter.next();
			if(isStartPoint(u) || isCallStmt(u)) iter.remove();
		}
		return res;
	}
	
	@Override
	public Set<Unit> allNonCallEndNodes() {
		Set<Unit> res = new LinkedHashSet<Unit>(unitToOwner.keySet());
		for (Iterator<Unit> iter = res.iterator(); iter.hasNext();) {
			Unit u = iter.next();
			if(isExitStmt(u) || isCallStmt(u)) iter.remove();
		}
		return res;
	}
	
	@Override
	public Set<Unit> allNodes()
	{
		return new LinkedHashSet<Unit>(unitToOwner.keySet());
	}
	
	@Override
	public Iterable<Unit> allNonStartNodes()
	{
		return Iterables.<Unit>filter(unitToOwner.keySet(), new Predicate<Unit>() {
			@Override
			public boolean apply(Unit u) {
				return !isStartPoint(u);
			}
		});
	}
	

	@Override
	public Collection<Unit> getReturnSitesOfCallAt(Unit u) {
		return getSuccsOf(u);
	}

	@Override
	public Set<Unit> getCallsFromWithin(SootMethod m) {
		return methodToCallsFromWithin.getUnchecked(m);		
	}
	
	@Override
	public List<Unit> getPredsOf(Unit u) {
		assert u != null;
		Body body = unitToOwner.get(u);
		DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
		return unitGraph.getPredsOf(u);
	}

	@Override
	public Collection<Unit> getEndPointsOf(SootMethod m) {
		if(m.hasActiveBody()) {
			Body body = m.getActiveBody();
			DirectedGraph<Unit> unitGraph = getOrCreateUnitGraph(body);
			return unitGraph.getTails();
		}
		return Collections.emptySet();
	}
	
	@Override
	public List<Unit> getPredsOfCallAt(Unit u) {
		return getPredsOf(u);
	}
	
	@Override
	public boolean isReturnSite(Unit n) {
		for (Unit pred : getPredsOf(n))
			if (isCallStmt(pred))
				return true;
		return false;
	}
	
}