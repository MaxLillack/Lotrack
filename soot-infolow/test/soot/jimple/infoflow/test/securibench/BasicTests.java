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
package soot.jimple.infoflow.test.securibench;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import soot.jimple.infoflow.Infoflow;

public class BasicTests extends JUnitTests {

	/**
	 * custom test to check whether there is an edge to string methods or not on
	 * different configurations
	 */
	@Test(timeout = 300000)
	public void basic0() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic0: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic1() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic1: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic2() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic2: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic3() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic3: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic4() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic4: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic5() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic5: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 3);
	}

	@Test(timeout = 300000)
	public void basic6() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic6: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic7() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic7: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic8() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic8: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic9() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic9: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic10() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic10: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic11() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic11: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test(timeout = 300000)
	public void basic12() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic12: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test(timeout = 300000)
	public void basic13() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic13: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic14() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic14: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic15() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic15: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic16() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic16: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic17() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic17: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic18() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic18: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic19() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic19: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic20() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic20: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic21() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic21: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 4);
	}

	@Test(timeout = 300000)
	public void basic22() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic22: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic23() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic23: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 3);
	}

	@Test(timeout = 300000)
	public void basic24() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic24: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic25() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic25: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic26() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic26: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic27() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic27: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic28() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic28: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test(timeout = 300000)
	public void basic29() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic29: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test(timeout = 300000)
	public void basic30() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic30: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic31() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic31: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 3);
	}

	@Test(timeout = 300000)
	public void basic32() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic32: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic33() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic33: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic34() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic34: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test(timeout = 300000)
	public void basic35() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic35: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 6);
	}

	@Test(timeout = 300000)
	public void basic36() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic36: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic37() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic37: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic38() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic38: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic39() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic39: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic40() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic40: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
		// TODO: this test fails at the moment!
	}

	@Test(timeout = 300000)
	public void basic41() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic41: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test(timeout = 300000)
	public void basic42() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.basic.Basic42: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.setEnableStaticFieldTracking(false);
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

}
