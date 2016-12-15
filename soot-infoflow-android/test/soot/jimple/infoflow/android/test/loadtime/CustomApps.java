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
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import static org.junit.Assert.*;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.android.MaxSetupApplication;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.loadtime.LoadTimeSourceSinkManager;
import soot.jimple.infoflow.loadtime.MongoLoader;
import soot.jimple.infoflow.loadtime.TestHelper;
import soot.jimple.infoflow.source.SourceInfo;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.IConstraint;

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
	public void MaxUnitTest01() throws IOException {
		String collectionName = "UnitTest1";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom1.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);

		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom1\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);

		
		Table<Unit, Abstraction, IConstraint> results = infoflow.getSplResults();
		
		Optional<Unit> unit = results.cellSet().stream()
							.filter(c -> c.getRowKey().toString().equals("if $z0 == 0 goto $r3 = virtualinvoke $r0.<de.ecspride.MaxUnitTest1: android.content.ContentResolver getContentResolver()>()"))
							.map(Cell::getRowKey)
							.findFirst();
		
		assertTrue(unit.isPresent());
		assertTrue(unit.get().hasTag("BytecodeOffsetTag"));
		int bytecodeIndex = Integer.parseInt(unit.get().getTag("BytecodeOffsetTag").toString());
		assertNotEquals(-1, bytecodeIndex);
		
		assertEquals(13, unit.get().getJavaSourceStartLineNumber());
		helper.checkResults(conf, collectionName);
		

	}
	
	@Test
	public void MaxUnitTest02() throws IOException {
		String collectionName = "UnitTest2";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom2.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);

		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom2\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest03() throws IOException {
		String collectionName = "UnitTest3";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom3.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);

		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom3\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest04() throws IOException {
		String collectionName = "UnitTest4";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom4.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);

		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom4\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	private void printResults(Table<Unit, Abstraction, IConstraint> results)
	{
		for(Cell<Unit, Abstraction, IConstraint> cell : results.cellSet())
		{
			logger.info("row {} column {} value {}", cell.getRowKey(), cell.getColumnKey(), cell.getValue());
		}
	}
	
	@Test
	public void MaxUnitTest05() throws IOException {
		String collectionName = "UnitTest5";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom5.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);

		printResults(infoflow.getSplResults());
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom5\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void MaxUnitTest06() throws IOException {
		String collectionName = "UnitTest6";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom6.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom6\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void MaxUnitTest07() throws IOException {
		String collectionName = "UnitTest7";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom7.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom7\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void MaxUnitTest08() throws IOException {
		String collectionName = "UnitTest8";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom8.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom8\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest09() throws IOException {
		String collectionName = "UnitTest9";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom9.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom9\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest10() throws IOException {
		String collectionName = "UnitTest10";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom10.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom10\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	
	@Test
	public void MaxUnitTest11() throws IOException {
		String collectionName = "UnitTest11";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom11.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom11\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}
	
	@Test
	public void MaxUnitTest12() throws IOException {
		String collectionName = "UnitTest12";
		LoadTimeInfoflow infoflow = analyzeAPKFile("MaxCustom12.apk", true, collectionName);
		Config conf = ConfigFactory.load().getConfig(collectionName);
	
		mongo.saveResults(infoflow, collectionName, "C:\\Users\\Max\\workspace\\MaxCustom12\\src\\");
		helper.detailedDBLog(infoflow.getSplResults(), collectionName, infoflow);
		helper.checkResults(conf, collectionName);
	}	
	
	@Test
	public void CompareLotrackJoana() throws IOException {
		mongo.compareJoana();
	}
	
	@Test
	@Ignore
	public void CollectJimpleTest1() throws IOException {
		String collectionName = "GenericAndroid";

		
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

		Collection<File> APKs = FileUtils.listFiles(new File("F:/google/google/apks"), FileFilterUtils.suffixFileFilter("apk"), DirectoryFileFilter.INSTANCE);
		
//		int limit = 30;
		
		
		boolean overwrite = false;
		
		Collection<String> existingApps = null;
		
        // save in DB
		try (MongoLoader mongoLoder = new MongoLoader()) {	
			existingApps = mongoLoder.getExistingUsedFeatureApps();
		}
		
		for(File apk : APKs) {
			if(!overwrite && !existingApps.contains(apk.getAbsolutePath())) { 
				try {
					MaxSetupApplication setupApplication = new MaxSetupApplication(androidJars, apk.getAbsolutePath());
					setupApplication.setTaintWrapperFile("EasyTaintWrapperSource.txt");
					setupApplication.calculateSourcesSinksEntrypoints("SourcesAndSinks.txt");
					
					setupApplication.collectJimpleFiles(collectionName);
				} catch(RuntimeException e)
				{
					System.err.println(apk.getAbsolutePath());
					System.err.println(e.getMessage());
				}
			}
//			if(limit-- == 0) {
//				break;
//			}
		}

	}
}
