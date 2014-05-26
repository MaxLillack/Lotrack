package soot.jimple.infoflow.aliasing;

import heros.solver.PathEdge;

import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.solver.IInfoflowSolver;

/**
 * A fully flow-sensitive aliasing strategy
 * 
 * @author Steven Arzt
 */
public class FlowSensitiveAliasStrategy extends AbstractBulkAliasStrategy {
	
	private final IInfoflowSolver bSolver;
	
	public FlowSensitiveAliasStrategy(IInfoflowCFG cfg, IInfoflowSolver backwardsSolver) {
		super(cfg);
		
		if(backwardsSolver == null) {
			throw new IllegalArgumentException("backwardsSolver must not be null.");
		}
		
		this.bSolver = backwardsSolver;
	}

	@Override
	public void computeAliasTaints
			(final Abstraction d1, final Stmt src,
			final Value targetValue, Set<Abstraction> taintSet,
			SootMethod method, Abstraction newAbs) {
		if (!newAbs.isAbstractionActive())
			return;
		
		// Start the backwards solver
		Abstraction bwAbs = newAbs.deriveInactiveAbstraction(src);
		for (Unit predUnit : interproceduralCFG().getPredsOf(src))
			bSolver.processEdge(new PathEdge<Unit, Abstraction>(d1,
					predUnit, bwAbs));
	}
	
	@Override
	public void injectCallingContext(Abstraction d3, IInfoflowSolver fSolver,
			SootMethod callee, Unit callSite, Abstraction source, Abstraction d1) {
		bSolver.injectContext(fSolver, callee, d3, callSite, source, d1);
	}

	@Override
	public boolean isFlowSensitive() {
		return true;
	}

	@Override
	public boolean requiresAnalysisOnReturn() {
		return false;
	}

}
