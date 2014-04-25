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
package soot.jimple.infoflow.android.test.loadtime;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;


import soot.Unit;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.android.MaxSetupApplication;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.loadtime.MongoLoader;
import soot.spl.ifds.Constraint;

public class CustomApps {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private static TestHelper helper;
	private static MongoLoader mongo;
	
	public LoadTimeInfoflow analyzeAPKFile(String fileName, boolean enableImplicitFlows, String configName) throws IOException {
		String androidJars = System.getenv("ANDROID_JARS");
		if (androidJars == null)
			throw new RuntimeException("Android JAR dir not set");
		System.out.println("Loading Android.jar files from " + androidJars);

		String droidBenchDir = System.getenv("DROIDBENCH");
		if (droidBenchDir == null)
			droidBenchDir = System.getProperty("DROIDBENCH");
		if (droidBenchDir == null)
			throw new RuntimeException("DroidBench dir not set");		
		System.out.println("Loading DroidBench from " + droidBenchDir);
		
		MaxSetupApplication setupApplication = new MaxSetupApplication(androidJars,
				droidBenchDir + File.separator + fileName);
		setupApplication.setTaintWrapperFile("EasyTaintWrapperSource.txt");
		setupApplication.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
		setupApplication.setEnableImplicitFlows(enableImplicitFlows);
		return setupApplication.runInfoflow(configName);
	}
	
	@BeforeClass
	public static void setup() throws UnknownHostException
	{
		helper = new TestHelper();
		mongo = helper.getMongoLoader();
	}
	
	@AfterClass
	public static void closeMongo()
	{
		helper.close();
	}
	
	
	@Test
	public void MaxUnitTest1() throws IOException {
		String collectionName = "UnitTest1";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom1.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest2() throws IOException {
		String collectionName = "UnitTest2";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom2.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest3() throws IOException {
		String collectionName = "UnitTest3";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom3.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest4() throws IOException {
		String collectionName = "UnitTest4";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom4.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	private void printResults(Table<Unit, Abstraction, Constraint<String>> results)
	{
		for(Cell<Unit, Abstraction, Constraint<String>> cell : results.cellSet())
		{
			logger.info("row {} column {} value {}", cell.getRowKey(), cell.getColumnKey(), cell.getValue());
		}
	}
	
	@Test
	public void MaxUnitTest5() throws IOException {
		String collectionName = "UnitTest5";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom5.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
		printResults(infoflow.getSplResults());
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void MaxUnitTest6() throws IOException {
		String collectionName = "UnitTest6";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom6.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void MaxUnitTest7() throws IOException {
		String collectionName = "UnitTest7";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom7.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void MaxUnitTest8() throws IOException {
		String collectionName = "UnitTest8";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom8.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest9() throws IOException {
		String collectionName = "UnitTest9";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom9.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest10() throws IOException {
		String collectionName = "UnitTest10";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom10.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
	
	
	@Test
	public void MaxUnitTest11() throws IOException {
		String collectionName = "UnitTest11";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom11.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
		int offset = helper.getFeatureOffset(conf);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\connectbot\\src\\", offset);
		helper.checkResults(conf, collectionName);
	}
}
