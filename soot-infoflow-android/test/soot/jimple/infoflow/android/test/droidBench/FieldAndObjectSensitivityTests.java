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
package soot.jimple.infoflow.android.test.droidBench;

import java.io.IOException;

import junit.framework.Assert;

import org.junit.Test;

import soot.jimple.infoflow.InfoflowResults;

public class FieldAndObjectSensitivityTests extends JUnitTests {
	
	@Test
	public void runTestFieldSensitivity1() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_FieldSensitivity1.apk");
		if (res != null)
			Assert.assertEquals(0, res.size());
	}

	@Test
	public void runTestFieldSensitivity2() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_FieldSensitivity2.apk");
		if (res != null)
			Assert.assertEquals(0, res.size());
	}

	@Test
	public void runTestFieldSensitivity3() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_FieldSensitivity3.apk");
		Assert.assertEquals(1, res.size());
	}

	@Test
	public void runTestFieldSensitivity4() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_FieldSensitivity4.apk");
		if (res != null)
			Assert.assertEquals(0, res.size());
	}

	@Test
	public void runTestInheritedObjects1() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_InheritedObjects1.apk");
		Assert.assertEquals(1, res.size());
	}

	@Test
	public void runTestObjectSensitivity1() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_ObjectSensitivity1.apk");
		if (res != null)
			Assert.assertEquals(0, res.size());
	}

	@Test
	public void runTestObjectSensitivity2() throws IOException {
		InfoflowResults res = analyzeAPKFile("FieldAndObjectSensitivity_ObjectSensitivity2.apk");
		if (res != null)
			Assert.assertEquals(0, res.size());
	}

}
