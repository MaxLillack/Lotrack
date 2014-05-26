package soot.jimple.infoflow.aliasing;

import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.solver.IInfoflowSolver;

/**
 * Common base class for alias strategies
 * 
 * @author Steven Arzt
 */
public abstract class AbstractAliasStrategy implements IAliasingStrategy {

	private final IInfoflowCFG cfg;
	private IInfoflowSolver fSolver;
	
	public AbstractAliasStrategy(IInfoflowCFG cfg) {
		this.cfg = cfg;
	}
	
	public IInfoflowCFG interproceduralCFG() {
		return this.cfg;
	}
	
	@Override
	public void setForwardSolver(IInfoflowSolver fSolver) {
		this.fSolver = fSolver;
	}
	
	protected IInfoflowSolver getForwardSolver() {
		return this.fSolver;
	}
	
}
