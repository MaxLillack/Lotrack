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
package soot.jimple.infoflow.test.utilclasses;

import java.util.HashSet;
import java.util.Set;

import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.taintWrappers.AbstractTaintWrapper;

public class TestWrapper extends AbstractTaintWrapper {

	@Override
	public Set<AccessPath> getTaintsForMethod(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg) {
		return new HashSet<AccessPath>();
	}

	@Override
	public boolean isExclusiveInternal(Stmt stmt, AccessPath taintedPath,
			IInfoflowCFG icfg) {
		return false;
	}

}
