package soot.jimple.infoflow.aliasing;

import java.util.HashSet;
import java.util.Set;

import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.solver.IInfoflowSolver;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

/**
 * Aliasing strategy to be used for conditionally-called methods when analyzing
 * implicit flows
 * 
 * @author Steven Arzt
 */
public class ImplicitFlowAliasStrategy extends AbstractBulkAliasStrategy {

    public ImplicitFlowAliasStrategy(IInfoflowCFG cfg) {
		super(cfg);
	}

	private final Table<SootMethod, AccessPath, Set<AccessPath>> globalAliases = HashBasedTable.create();

	/**
	 * Computes the global non-flow-sensitive alias information for the given
	 * method
	 * @param method The method for which to compute the alias information
	 */
	private void computeGlobalAliases(SootMethod method) {
		synchronized (globalAliases) {
			// If we already know the aliases for the given method, there is nothing
			// left to be done
			if (globalAliases.containsRow(method))
				return;
	
			// Find the aliases
			for (Unit u : method.getActiveBody().getUnits()) {
				if (!(u instanceof AssignStmt))
					continue;
				final AssignStmt assign = (AssignStmt) u;
				
				// Aliases can only be generated on the heap
				if (!(assign.getLeftOp() instanceof FieldRef
						&& (assign.getRightOp() instanceof FieldRef
								|| assign.getRightOp() instanceof Local)))
					if (!(assign.getRightOp() instanceof FieldRef
							&& (assign.getLeftOp() instanceof FieldRef
									|| assign.getLeftOp() instanceof Local)))
						continue;
				
				final AccessPath apLeft = new AccessPath(assign.getLeftOp(), true);
				final AccessPath apRight = new AccessPath(assign.getRightOp(), true);

				Set<AccessPath> mapLeft = globalAliases.get(method, apLeft);
				if (mapLeft == null) {
					mapLeft = new HashSet<AccessPath>();
					globalAliases.put(method, apLeft, mapLeft);
				}
				mapLeft.add(apRight);

				Set<AccessPath> mapRight = globalAliases.get(method, apRight);
				if (mapRight == null) {
					mapRight = new HashSet<AccessPath>();
					globalAliases.put(method, apRight, mapRight);
				}
				mapLeft.add(apLeft);
			}
		}
	}

	@Override
	public void computeAliasTaints(Abstraction d1, Stmt src, Value targetValue,
			Set<Abstraction> taintSet, SootMethod method, Abstraction newAbs) {	
		// If we don't have an alias set for this method yet, we compute it
		if (!globalAliases.containsRow(method))
			computeGlobalAliases(method);
		
		// Use global aliasing
		Value baseValue = ((InstanceFieldRef) targetValue).getBase();
		Set<AccessPath> aliases = globalAliases.get(method, new AccessPath
				(baseValue, true));
		if (aliases != null)
			for (AccessPath ap : aliases) {
				Abstraction aliasAbs = newAbs.deriveNewAbstraction(
						ap.merge(newAbs.getAccessPath()), src);
				taintSet.add(aliasAbs);
			}
	}

	@Override
	public void injectCallingContext(Abstraction abs, IInfoflowSolver fSolver,
			SootMethod callee, Unit callSite, Abstraction source, Abstraction d1) {
	}

	@Override
	public boolean isFlowSensitive() {
		return false;
	}

	@Override
	public boolean requiresAnalysisOnReturn() {
		return true;
	}
	
}
