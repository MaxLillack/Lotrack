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
package soot.jimple.infoflow.taintWrappers;

import java.util.HashSet;
import java.util.Set;

import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;

/**
 * Set of taint wrappers. It supports taint wrapping for a class if at least one
 * of the contained wrappers supports it. The resulting taints are the union of
 * all taints produced by the contained wrappers.
 * 
 * @author Steven Arzt
 */
public class TaintWrapperSet extends AbstractTaintWrapper {

	private Set<ITaintPropagationWrapper> wrappers = new HashSet<ITaintPropagationWrapper>();
	
	/**
	 * Adds the given wrapper to the chain of wrappers.
	 * @param wrapper The wrapper to add to the chain.
	 */
	public void addWrapper(ITaintPropagationWrapper wrapper) {
		this.wrappers.add(wrapper);
	}

	@Override
	public Set<AccessPath> getTaintsForMethod(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg) {
		Set<AccessPath> resList = new HashSet<AccessPath>();
		for (ITaintPropagationWrapper w : this.wrappers)
			resList.addAll(w.getTaintsForMethod(stmt, taintedPath, icfg));
		return new HashSet<AccessPath>(resList);
	}

	@Override
	public boolean isExclusiveInternal(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg) {
		for (ITaintPropagationWrapper w : this.wrappers)
			if (w.isExclusive(stmt, taintedPath, icfg))
				return true;
		return false;
	}
}
