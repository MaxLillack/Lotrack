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
package soot.jimple.infoflow.test.junit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;

import soot.RefType;
import soot.Scene;
import soot.SootField;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.IInfoflow.AliasingAlgorithm;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.taintWrappers.AbstractTaintWrapper;
import soot.jimple.infoflow.test.utilclasses.TestWrapper;

/**
 * tests aliasing of heap references
 */
public class MaxTests extends JUnitTests {

	@Test(timeout = 300000)
	@Ignore
	public void testForEarlyTermination() {
		Infoflow infoflow = initInfoflow();
		infoflow.setAliasingAlgorithm(AliasingAlgorithm.PtsBased);
		infoflow.setTaintWrapper(new TestWrapper());
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.MaxTest: void test1a()>");
		infoflow.computeInfoflow(appPath, libPath, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}
	
	@Test(timeout = 300000)
	@Ignore
	public void test1c() {
		Infoflow infoflow = initInfoflow();
		infoflow.setAliasingAlgorithm(AliasingAlgorithm.PtsBased);
		infoflow.setTaintWrapper(new TestWrapper());
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.MaxTest: void test1c()>");
		infoflow.computeInfoflow(appPath, libPath, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}
	
	@Test(timeout = 300000)
	public void test2() {
		Infoflow infoflow = initInfoflow();
		infoflow.setAliasingAlgorithm(AliasingAlgorithm.PtsBased);
		infoflow.setTaintWrapper(new TestWrapper());
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.MaxTest: void test2a()>");
		infoflow.computeInfoflow(appPath, libPath, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}
	
	@Test(timeout = 300000)
	public void test3() {
		Infoflow infoflow = initInfoflow();
		infoflow.setAliasingAlgorithm(AliasingAlgorithm.PtsBased);
		infoflow.setTaintWrapper(new TestWrapper());
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.MaxTest: void test3()>");
		infoflow.computeInfoflow(appPath, libPath, epoints, sources, sinks);
		
		
		
		checkInfoflow(infoflow, 1);
	}
}
