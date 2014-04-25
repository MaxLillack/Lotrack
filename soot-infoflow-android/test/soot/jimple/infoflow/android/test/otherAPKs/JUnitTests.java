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
package soot.jimple.infoflow.android.test.otherAPKs;

import java.io.IOException;

import junit.framework.Assert;

import org.junit.Test;

import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;

public class JUnitTests {
	
	/**
	 * Analyzes the given APK file for data flows
	 * @param fileName The full path and file name of the APK file to analyze
	 * @return The data leaks found in the given APK file
	 * @throws IOException Thrown if the given APK file or any other required
	 * file could not be found
	 */
	public InfoflowResults analyzeAPKFile(String fileName) throws IOException {
		return analyzeAPKFile(fileName, false, true, false);
	}
	
	/**
	 * Analyzes the given APK file for data flows
	 * @param fileName The full path and file name of the APK file to analyze
	 * @param enableImplicitFlows True if implicit flows shall be tracked,
	 * otherwise false
	 * @param enableStaticFields True if taints in static fields shall be tracked,
	 * otherwise false
	 * @param flowSensitiveAliasing True if a flow-sensitive alias analysis
	 * shall be used, otherwise false
	 * @return The data leaks found in the given APK file
	 * @throws IOException Thrown if the given APK file or any other required
	 * file could not be found
	 */
	public InfoflowResults analyzeAPKFile(String fileName, boolean enableImplicitFlows,
			boolean enableStaticFields, boolean flowSensitiveAliasing) throws IOException {
		String androidJars = System.getenv("ANDROID_JARS");
		if (androidJars == null)
			throw new RuntimeException("Android JAR dir not set");
		System.out.println("Loading Android.jar files from " + androidJars);
		
		SetupApplication setupApplication = new SetupApplication(androidJars, fileName);
		setupApplication.setTaintWrapper(new EasyTaintWrapper("EasyTaintWrapperSource.txt"));
		setupApplication.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
		setupApplication.setEnableImplicitFlows(enableImplicitFlows);
		setupApplication.setEnableStaticFieldTracking(enableStaticFields);
		setupApplication.setFlowSensitiveAliasing(flowSensitiveAliasing);
		return setupApplication.runInfoflow();
	}

	@Test
	public void runTest1() throws IOException {
		InfoflowResults res = analyzeAPKFile
				("testAPKs/9458cfb51c90130938abcef7173c3f6d44a02720.apk", false, false, false);
		Assert.assertTrue(res.size() > 0);
	}

}
