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

public class InterTests extends JUnitTests {
	@Test
	public void inter1() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter1: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter2() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter2: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test
	public void inter3() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter3: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter4() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter4: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter5() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter5: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter6() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter6: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter7() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter7: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter8() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter8: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter9() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter9: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test
	public void inter10() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter10: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter11() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter11: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter12() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter12: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter13() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter13: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void inter14() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.inter.Inter14: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

}
