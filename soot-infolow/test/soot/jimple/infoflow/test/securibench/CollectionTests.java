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

public class CollectionTests extends JUnitTests {

	@Test
	public void collections1() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections1: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections2() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections2: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections3() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections3: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 2);
	}

	@Test
	public void collections4() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections4: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections5() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections5: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections6() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections6: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections7() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections7: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections8() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections8: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections9() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections9: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		negativeCheckInfoflow(infoflow);
		// this test fails because we taint whole list instead of single items..
	}

	@Test
	public void collections10() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections10: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections11() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections11: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections12() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections12: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections13() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections13: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

	@Test
	public void collections14() {
		Infoflow infoflow = initInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<securibench.micro.collections.Collections14: void doGet(javax.servlet.http.HttpServletRequest,javax.servlet.http.HttpServletResponse)>");
		infoflow.computeInfoflow(appPath, libPath, entryPointCreator, epoints, sources, sinks);
		checkInfoflow(infoflow, 1);
	}

}
