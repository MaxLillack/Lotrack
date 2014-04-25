/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.solver;

import heros.solver.IDESolver;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.util.ConcurrentHashSet;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.jimple.toolkits.pointer.RWSet;
import soot.jimple.toolkits.pointer.SideEffectAnalysis;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.MHGPostDominatorsFinder;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

/**
 * Interprocedural control-flow graph for the infoflow solver
 * 
 * @author Steven Arzt
 * @author Eric Bodden
 */
public class InfoflowCFG implements IInfoflowCFG {

	protected final BiDiInterproceduralCFG<Unit, SootMethod> delegate; 
	
	protected final LoadingCache<Unit,UnitContainer> unitToPostdominator =
			IDESolver.DEFAULT_CACHE_BUILDER.build( new CacheLoader<Unit,UnitContainer>() {
				@Override
				public UnitContainer load(Unit unit) throws Exception {
					SootMethod method = getMethodOf(unit);
					DirectedGraph<Unit> graph = delegate.getOrCreateUnitGraph(method);
					MHGPostDominatorsFinder<Unit> postdominatorFinder = new MHGPostDominatorsFinder<Unit>(graph);
					Unit postdom = postdominatorFinder.getImmediateDominator(unit);
					if (postdom == null)
						return new UnitContainer(method);
					else
						return new UnitContainer(postdom);
				}
			});
	
	protected final SideEffectAnalysis sideEffectAnalysis;
	
	public InfoflowCFG() {
		this(new JimpleBasedInterproceduralCFG());
	}
	
	public InfoflowCFG(BiDiInterproceduralCFG<Unit,SootMethod> delegate) {
		this.delegate = delegate;
		if (Scene.v().hasCallGraph())
			this.sideEffectAnalysis = new SideEffectAnalysis
					(Scene.v().getPointsToAnalysis(), Scene.v().getCallGraph());
		else
			this.sideEffectAnalysis = null;
	}
	
	@Override
	public UnitContainer getPostdominatorOf(Unit u) {
		return unitToPostdominator.getUnchecked(u);
	}
	
	@Override
	public Set<SootField> getReadVariables(SootMethod caller, Stmt inv) {
		if (sideEffectAnalysis == null)
			return null;
		
		final RWSet rwSet;
		synchronized (sideEffectAnalysis) {
			rwSet = sideEffectAnalysis.readSet(caller, inv);
		}
		if (rwSet == null)
			return null;
		Set<SootField> objSet = new ConcurrentHashSet<SootField>();
		for (Object o : rwSet.getFields())
			if (o instanceof SootField)
				objSet.add((SootField) o);
		for (Object o : rwSet.getGlobals())
			if (o instanceof SootField)
				objSet.add((SootField) o);
		return objSet;
	}
	
	@Override
	public Set<SootField> getWriteVariables(SootMethod caller, Stmt inv) {
		if (sideEffectAnalysis == null)
			return null;

		final RWSet rwSet;
		synchronized (sideEffectAnalysis) {
			rwSet = sideEffectAnalysis.writeSet(caller, inv);
		}
		if (rwSet == null)
			return null;
		Set<SootField> objSet = new ConcurrentHashSet<SootField>();
		for (Object o : rwSet.getFields())
			if (o instanceof SootField)
				objSet.add((SootField) o);
		for (Object o : rwSet.getGlobals())
			if (o instanceof SootField)
				objSet.add((SootField) o);
		return objSet;
	}

	//delegate methods follow
	
	public SootMethod getMethodOf(Unit u) {
		return delegate.getMethodOf(u);
	}

	public List<Unit> getSuccsOf(Unit u) {
		return delegate.getSuccsOf(u);
	}

	public boolean isExitStmt(Unit u) {
		return delegate.isExitStmt(u);
	}

	public boolean isStartPoint(Unit u) {
		return delegate.isStartPoint(u);
	}

	public boolean isFallThroughSuccessor(Unit u, Unit succ) {
		return delegate.isFallThroughSuccessor(u, succ);
	}

	public boolean isBranchTarget(Unit u, Unit succ) {
		return delegate.isBranchTarget(u, succ);
	}

	public Collection<Unit> getStartPointsOf(SootMethod m) {
		return delegate.getStartPointsOf(m);
	}

	public boolean isCallStmt(Unit u) {
		return delegate.isCallStmt(u);
	}

	public Set<Unit> allNonCallStartNodes() {
		return delegate.allNonCallStartNodes();
	}
	
	public Set<Unit> allNodes()
	{
		return delegate.allNodes();
	}
	
	public Iterable<Unit> allNonStartNodes()
	{
		return delegate.allNonStartNodes();
	}

	public Set<SootMethod> getCalleesOfCallAt(Unit u) {
		return delegate.getCalleesOfCallAt(u);
	}

	public Set<Unit> getCallersOf(SootMethod m) {
		return delegate.getCallersOf(m);
	}

	public Collection<Unit> getReturnSitesOfCallAt(Unit u) {
		return delegate.getReturnSitesOfCallAt(u);
	}

	public Set<Unit> getCallsFromWithin(SootMethod m) {
		return delegate.getCallsFromWithin(m);
	}

	public List<Unit> getPredsOf(Unit u) {
		return delegate.getPredsOf(u);
	}

	public Collection<Unit> getEndPointsOf(SootMethod m) {
		return delegate.getEndPointsOf(m);
	}

	@Override
	public List<Unit> getPredsOfCallAt(Unit u) {
		return delegate.getPredsOf(u);
	}

	@Override
	public Set<Unit> allNonCallEndNodes() {
		return delegate.allNonCallEndNodes();
	}

	@Override
	public DirectedGraph<Unit> getOrCreateUnitGraph(SootMethod m) {
		return delegate.getOrCreateUnitGraph(m);
	}

	@Override
	public List<Value> getParameterRefs(SootMethod m) {
		return delegate.getParameterRefs(m);
	}

	@Override
	public boolean isReturnSite(Unit n) {
		return delegate.isReturnSite(n);
	}

	@Override
	public Unit getPostDominator(Unit stmt) {
		// TODO Temp - testing load time
		return null;
	}

	@Override
	public boolean isSpecialStatementType(Unit stmt) {
		return (stmt instanceof IfStmt || stmt instanceof InvokeExpr);
	}
}

