package soot.jimple.infoflow.taintWrappers;

import java.util.concurrent.atomic.AtomicInteger;

import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;

/**
 * Abstract base class for all taint propagation wrappers
 * 
 * @author Steven Arzt
 */
public abstract class AbstractTaintWrapper implements ITaintPropagationWrapper {
	
	private final AtomicInteger wrapperHits = new AtomicInteger(0);
	private final AtomicInteger wrapperMisses = new AtomicInteger(0);

	/**
	 * Gets whether the taints produced by this taint wrapper are exclusive, i.e. there are
	 * no other taints than those produced by the wrapper. In effect, this tells the analysis
	 * not to propagate inside the callee.
	 * @param stmt The call statement to check
	 * @param taintedPath The tainted field or value to propagate
	 * @param icfg The interprocedural cfg 
	 * @return True if this taint wrapper is exclusive, otherwise false. 
	 */
	protected abstract boolean isExclusiveInternal(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg);

	@Override
	public boolean isExclusive(Stmt stmt, AccessPath taintedPath, IInfoflowCFG icfg) {
		if (isExclusiveInternal(stmt, taintedPath, icfg)) {
			wrapperHits.incrementAndGet();
			return true;
		}
		else {
			wrapperMisses.incrementAndGet();
			return false;
		}
	}

	@Override
	public int getWrapperHits() {
		return wrapperHits.get();
	}

	@Override
	public int getWrapperMisses() {
		return wrapperMisses.get();
	}

}
