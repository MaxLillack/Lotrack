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

import java.util.Collections;
import java.util.Set;

import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.internal.JAssignStmt;

/**
 * Internal taint wrapper for the use in some test cases
 */
public class ListExampleWrapper extends AbstractTaintWrapper {

	@Override
	public Set<AccessPath> getTaintsForMethod(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg) {
		// method add + added element is tainted -> whole list is tainted
		if(stmt.getInvokeExpr().getMethod().getSubSignature().equals("boolean add(java.lang.Object)"))
			if (taintedPath.getPlainValue().equals(stmt.getInvokeExpr().getArg(0)))
				return Collections.singleton(new AccessPath(((InstanceInvokeExpr) stmt.getInvokeExprBox().getValue()).getBase(),
						false));

		// method get + whole list is tainted -> returned element is tainted
		if(stmt.getInvokeExpr().getMethod().getSubSignature().equals("java.lang.Object get(int)"))
			if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
				InstanceInvokeExpr iiExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
				if (taintedPath.getPlainValue().equals(iiExpr.getBase()))
					if(stmt instanceof JAssignStmt)
						return Collections.singleton(new AccessPath(((JAssignStmt)stmt).getLeftOp(), true));
			}

		// For the moment, we don't implement static taints on wrappers. Pass it on
		// not to break anything
		if(taintedPath.isStaticFieldRef())
			return Collections.singleton(taintedPath);

		return Collections.emptySet();
	}

	@Override
	public boolean isExclusiveInternal(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg) {
		return false;
	}
}
