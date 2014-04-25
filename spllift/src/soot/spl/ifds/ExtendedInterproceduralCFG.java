package soot.spl.ifds;

import heros.InterproceduralCFG;
import heros.solver.IDESolver;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import soot.SootMethod;
import soot.Trap;
import soot.Unit;
import soot.Value;
import soot.dava.internal.javaRep.DLengthExpr;
import soot.jimple.IfStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.ThrowStmt;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.tagkit.Host;
import soot.tagkit.TryCatchTag;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.MHGPostDominatorsFinder;

public class ExtendedInterproceduralCFG implements BiDiInterproceduralCFG<Unit,SootMethod> {
	
	protected BiDiInterproceduralCFG<Unit,SootMethod> delegate;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private Map<SootMethod,MHGPostDominatorsFinder<Unit>> savedMHGPostDominatorsFinders = new ConcurrentHashMap<SootMethod,MHGPostDominatorsFinder<Unit>>();
	
	public ExtendedInterproceduralCFG(BiDiInterproceduralCFG<Unit, SootMethod> delegate) {
		this.delegate = delegate;
	}
	

	public Unit getPostDominator(Unit unit) {
		SootMethod method = getMethodOf(unit);
		if(!savedMHGPostDominatorsFinders.containsKey(method)) {
//			logger.info("Create MHGPostDominatorsFinder for Method {}", method);
			DirectedGraph<Unit> graph = delegate.getOrCreateUnitGraph(method);
			savedMHGPostDominatorsFinders.put(method, new MHGPostDominatorsFinder<Unit>(graph));
		}
		
		Unit postdom = savedMHGPostDominatorsFinders.get(method).getImmediateDominator(unit);
		if (postdom == null)
			return null;
		else
			return postdom;
	}

	@Override
	public boolean isSpecialStatementType(Unit stmt) {
		return (stmt instanceof IfStmt);
	}

	public List<Unit> getSuccsOf(Unit n) {
		List<Unit> succsOf = delegate.getSuccsOf(n);
		if(n instanceof ThrowStmt) {
			TryCatchTag tryCatchTag = (TryCatchTag) getMethodOf(n).getActiveBody().getTag(TryCatchTag.NAME);
			if(tryCatchTag!=null) {
				for(Unit possibleHandlerUnit: succsOf) {
					Unit fallThroughUnit = tryCatchTag.getFallThroughUnitOf(possibleHandlerUnit);
					if(fallThroughUnit!=null) {
						List<Unit> res = new LinkedList<Unit>(succsOf);
						res.add(fallThroughUnit);
						return res;
					}
				}
			} 
		}
		return succsOf;
	}
	
	public boolean isFallThroughSuccessor(Unit stmt, Unit succ) {
		if(delegate.isFallThroughSuccessor(stmt, succ))
			return true;
		if(stmt instanceof ThrowStmt) {
			TryCatchTag tryCatchTag = (TryCatchTag) getMethodOf(stmt).getActiveBody().getTag(TryCatchTag.NAME);
			if(tryCatchTag!=null) {
				for(Unit possibleHandlerUnit: delegate.getSuccsOf(stmt)) {
					Unit fallThroughUnit = tryCatchTag.getFallThroughUnitOf(possibleHandlerUnit);
					if(fallThroughUnit==succ) {
						return true;
					}
				}
			}
		}
		return false;		
	}

	public boolean isBranchTarget(Unit stmt, Unit succ) {
		if(delegate.isBranchTarget(stmt, succ))
			return true;
		if(stmt instanceof ThrowStmt) {
			for(Trap trap: delegate.getMethodOf(stmt).getActiveBody().getTraps()) {
				if(trap.getHandlerUnit()==succ) {
					return true;
				}
			}
		} 
		return false;		
	}
	
	public SootMethod getMethodOf(Unit n) {
		return delegate.getMethodOf(n);
	}

	public Set<SootMethod> getCalleesOfCallAt(Unit n) {
		return delegate.getCalleesOfCallAt(n);
	}

	public Set<Unit> getCallersOf(SootMethod m) {
		return delegate.getCallersOf(m);
	}

	public Set<Unit> getCallsFromWithin(SootMethod m) {
		return delegate.getCallsFromWithin(m);
	}

	public Collection<Unit> getStartPointsOf(SootMethod m) {
		return delegate.getStartPointsOf(m);
	}

	public Collection<Unit> getReturnSitesOfCallAt(Unit n) {
		return delegate.getReturnSitesOfCallAt(n);
	}

	public boolean isCallStmt(Unit stmt) {
		return delegate.isCallStmt(stmt);
	}

	public boolean isExitStmt(Unit stmt) {
		return delegate.isExitStmt(stmt);
	}

	public boolean isStartPoint(Unit stmt) {
		return delegate.isStartPoint(stmt);
	}

	public Set<Unit> allNonCallStartNodes() {
		return delegate.allNonCallStartNodes();
	}

	@Override
	public List<Unit> getPredsOf(Unit u) {
		return delegate.getPredsOf(u);
	}

	@Override
	public Collection<Unit> getEndPointsOf(SootMethod m) {
		return delegate.getEndPointsOf(m);
	}

	@Override
	public List<Unit> getPredsOfCallAt(Unit u) {
		return delegate.getPredsOfCallAt(u);
	}

	@Override
	public Set<Unit> allNonCallEndNodes() {
		return delegate.allNonCallEndNodes();
	}
	
	@Override
	public Set<Unit> allNodes() {
		return delegate.allNodes();
	}

	@Override
	public Iterable<Unit> allNonStartNodes() {
		return delegate.allNonStartNodes();
	}
	
	@Override
	public DirectedGraph<Unit> getOrCreateUnitGraph(SootMethod body) {
		return delegate.getOrCreateUnitGraph(body);
	}

	@Override
	public List<Value> getParameterRefs(SootMethod m) {
		return delegate.getParameterRefs(m);
	}

	@Override
	public boolean isReturnSite(Unit n) {
		return delegate.isReturnSite(n);
	}
	
}
