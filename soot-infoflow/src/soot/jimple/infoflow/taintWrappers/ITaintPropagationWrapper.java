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

import java.util.Set;

import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;

/**
 * This interface declares methods to define classes and methods which should not be analyzed directly.
 * Instead the outcome of the analysis is summarized (which improves performance and helps if the sources are not available)
 * @author Christian
 *
 */
public interface ITaintPropagationWrapper {
	
	/**
	 * Checks an invocation statement for black-box taint propagation. This allows
	 * the wrapper to artificially propagate taints over method invocations without
	 * requiring the analysis to look inside the method.
	 * @param stmt The invocation statement which to check for black-box taint propagation
	 * @param taintedPath The tainted field or value to propagate
	 * @param icfg The interprocedural control flow graph 
	 * @return The list of tainted values after the invocation statement referenced in {@link Stmt}
	 * has been executed
	 */
	public Set<AccessPath> getTaintsForMethod(Stmt stmt, AccessPath taintedPath, IInfoflowCFG icfg);

	/**
	 * Gets whether the taints produced by this taint wrapper are exclusive, i.e. there are
	 * no other taints than those produced by the wrapper. In effect, this tells the analysis
	 * not to propagate inside the callee.
	 * @param stmt The call statement to check
	 * @param taintedPath The tainted field or value to propagate 
	 * @param icfg The interprocedural control flow graph 
	 * @return True if this taint wrapper is exclusive, otherwise false. 
	 */
	public boolean isExclusive(Stmt stmt, AccessPath taintedPath, IInfoflowCFG icfg);

	/**
	 * Gets the number of times in which the taint wrapper was able to
	 * exclusively model a method call. This is equal to the number of times
	 * isExclusive() returned true.
	 * @return The number of method model requests that succeeded
	 */
	public int getWrapperHits();
	

	/**
	 * Gets the number of times in which the taint wrapper was NOT able to
	 * exclusively model a method call. This is equal to the number of times
	 * isExclusive() returned false.
	 * @return The number of method model requests that failed
	 */
	public int getWrapperMisses();

}
