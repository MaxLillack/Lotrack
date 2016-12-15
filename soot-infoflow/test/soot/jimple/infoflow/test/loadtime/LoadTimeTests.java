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
package soot.jimple.infoflow.test.loadtime;

import static org.junit.Assert.*;
import heros.EdgeFunction;
import heros.solver.JumpFunctions;
import heros.solver.PathEdge;

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

























import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import com.carrotsearch.junitbenchmarks.BenchmarkOptions;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import soot.PhaseOptions;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.SourceContext;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.loadtime.FeatureInfo;
import soot.jimple.infoflow.loadtime.LoadTimeSourceSinkManager;
import soot.jimple.infoflow.loadtime.MongoLoader;
import soot.jimple.infoflow.loadtime.TestHelper;
import soot.jimple.infoflow.problems.LoadTimeInfoflowProblem;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.test.junit.JUnitTests;
import soot.jimple.infoflow.test.loadtime.RepeatRule.Repeat;
import soot.options.Options;
import soot.spl.ifds.CachedZ3Solver;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.IConstraint;
import soot.spl.ifds.IFDSEdgeFunctions;
import soot.spl.ifds.SPLIFDSSolver;
import soot.tagkit.JimpleLineNumberTag;
import soot.tagkit.Tag;


@BenchmarkOptions(benchmarkRounds = 3, warmupRounds = 2)
public class LoadTimeTests extends AbstractBenchmark {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private String appPath;
	private String libPath;
	private static TestHelper testHelper;
	
	public LoadTimeTests() throws UnknownHostException
	{
		testHelper = new TestHelper();
	}
	
	@Before
	public void before()
	{
		CachedZ3Solver.clearCache();
	}
	
	@AfterClass
	public static void closeTestHelper()
	{
		testHelper.close();
	}
	
	
	private LoadTimeInfoflow customInitInfoflow() throws IOException{
		
        final String sep = System.getProperty("path.separator");
    	File f = new File(".");
        File testSrc1 = new File(f,"bin");
        File testSrc2 = new File(f,"build" + File.separator + "classes");

        if (! (testSrc1.exists() || testSrc2.exists())){
            fail("Test aborted - none of the test sources are available");
        }

    	appPath = testSrc1.getCanonicalPath() + sep + testSrc2.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	return result;
    }
	
	private void printResults(Table<Unit, Abstraction, IConstraint> results)
	{
		for(Cell<Unit, Abstraction, IConstraint> cell : results.cellSet())
		{
			logger.info("row {} column {} value {}, Line {}", cell.getRowKey(), cell.getColumnKey(), cell.getValue(), testHelper.getJimpleLineNumber(cell.getRowKey()));
		}
	}

//	
//	private void dumpJumpFn(JumpFunctions<Unit, Abstraction, IConstraint> jumpFn)
//	{
//		
//		logger.info("dumpJumpFn: total count {}", jumpFn.getCount());
//		try(MongoLoader loader = new MongoLoader()) {
//			loader.clearJumpFn();
//			for(PathEdge<Unit, Abstraction> edge : jumpFn.debug_getHistoryData()) {
//				Unit target = edge.getTarget();
//				Abstraction factAtSource = edge.factAtSource();
//				Abstraction factAtTarget = edge.factAtTarget();
//
//				loader.saveJumpFnEntry(target, factAtSource, factAtTarget);
//			}
//		}
//	}
	
	private List<Cell<Unit, Abstraction, IConstraint>> taintsAtLine(Table<Unit, Abstraction, IConstraint> results, LoadTimeInfoflow infoflow, String className, int line)
	{
		List<Cell<Unit, Abstraction, IConstraint>> taints = new LinkedList<>();
		
		for(Cell<Unit, Abstraction, IConstraint> cell : results.cellSet())
		{
			Unit unit = cell.getRowKey();
			
			SootMethod sootMethod = infoflow.getiCfg().getMethodOf(unit);
			SootClass sootClass = sootMethod.getDeclaringClass();
			
			if(sootClass.getName().equals(className) && testHelper.getJimpleLineNumber(cell.getRowKey()) == line)
			{
				taints.add(cell);
			}
		}
		
		return taints;
	}
	
	private Path getJavaPath(SootClass sootClass) {
		String basePath = System.getProperty("user.dir");
		Path path = Paths.get(basePath + "\\test\\" 
				   + sootClass.getPackageName().replace(".", "\\") 
				   + "\\" + sootClass.getShortName() + ".java");
		return path;
	}

	
	private String getExpectedConstraint(LoadTimeInfoflow infoflow, Unit unit)
	{
		if(infoflow.getiCfg() == null) {
			throw new IllegalArgumentException("Could not retrieve icfg from infoflow");
		}
		
		if(unit == null) {
			throw new IllegalArgumentException("unit must not be null");
		}
		
		SootMethod m = null;
		try {
			m = infoflow.getiCfg().getMethodOf(unit);
		} catch(NullPointerException e) {
			throw new IllegalArgumentException("unit " + m + " has no method");
		}
		
		
		SootClass declaringClass =  m.getDeclaringClass();
		Path path = getJavaPath(declaringClass);
		
		String constraint = null;
		
		if(Files.exists(path, LinkOption.values())) {
			try {
				List<String> lines = Files.readAllLines(path, Charset.defaultCharset());
				if(unit.getJavaSourceStartLineNumber() > 0 && unit.getJavaSourceStartLineNumber() <= lines.size()) {
					String line = lines.get(unit.getJavaSourceStartLineNumber() - 1);
					String constraintCommentRegex = "//\\s*(<.*>)"; 
					
					Matcher constraintCommentMatcher = Pattern.compile(constraintCommentRegex).matcher(line);
					if(constraintCommentMatcher.find()) {
						constraint = constraintCommentMatcher.group(1);
						
						if(constraint.equals("<>")) {
							constraint = "true";
						}
						
						if(constraint.equals("<false>")) {
							constraint = "false";
						}
					}
				}
				

			} catch (IOException e) {
				e.printStackTrace();
			}
		} 
		

		constraint = StringUtils.stripStart(constraint, "<");
		constraint = StringUtils.stripEnd(constraint, ">");
		return constraint;
	}
	
	/*String appPath, String libPath,
			IEntryPointCreator entryPointCreator,
			List<String> entryPoints, ISourceSinkManager sourcesSinks*/

	@Test
	//@Repeat( times = 20 )
    public void test01() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample01()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue("Only checked " + constraintsChecked + " conditions.", constraintsChecked > 7);
		
		// i = 0 mandatory
		// if(FeatureBase.featureA()) -> mandatory
		// i = 1 -> featureA
		// if(FeatureBase.featureB()) -> featureA 
		// i = 3 -> featureA AND featureB
		// if(FeatureBase.featureB()) -> mandatory
		// i = 2 -> featureB
	}
	
	
	@Test
    public void test02() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample02()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}

	@Rule
	public RepeatRule repeatRule = new RepeatRule();

	@Test
//	@Repeat( times = 20 )
    public void test03() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		System.out.println("test03");
		
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample03()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test04() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample04()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
  
	@Test
    public void test04details() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample04()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		
		// test certain taints
		List<Cell<Unit, Abstraction, IConstraint>> taints30 = taintsAtLine(infoflow.getSplResults(), infoflow, "soot.jimple.infoflow.test.loadtime.LoadTimeTestCode", 30);
		
		assertEquals(2, taints30.size());
		
	}
	
	@Test
    public void test05() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample05()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
	@Ignore
    public void test06() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample06()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test07() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample07()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test08() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample08()>");
	  
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test09() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample09()>");
	  
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue("No constraints found", constraintsChecked > 0);
	}
	
	@Test
    public void test10() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample10()>");
	  
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue("No constraints found", constraintsChecked > 0);
	}
	
	@Test
	//@Repeat( times = 20 )
    public void test11() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample01()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample02()>");
	  
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 7);
	}
	
	// Fails because of mismatch in Jimple to Java mapping
	@Test
    public void test14() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: boolean sample14b()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);

		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}

	}
	
	@Test
    public void test15() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample15()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test16() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample16()>");
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test17() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample17()>");

		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test18() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample18()>");

		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test19() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample19()>");

		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test20() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample20(java.lang.String)>");

		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test21() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample21()>");

		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 2);
	}
	
	private int checkConstraints(LoadTimeInfoflow infoflow, List<String> epoints) throws UnknownHostException
	{
		return checkConstraints(infoflow, epoints, "Tests");
	}
	
	private int checkConstraints(LoadTimeInfoflow infoflow, List<String> epoints, String configName) throws UnknownHostException
	{
		int constraintsChecked = 0;
		
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager(configName);
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);	
		
		SPLIFDSSolver<Abstraction,AccessPath> splSolver = infoflow.getSPLSolver();
		
		Table<Unit, Abstraction, IConstraint> results = infoflow.getSplResults();
		
//		printResults(results);
		System.out.println("detailedDBLog() : " + results.size() + " edges.");
		
		
		// TODO Move this option to config file
		boolean enableDetailedDBLog = true;
		if(enableDetailedDBLog)
		{
			testHelper.detailedDBLog(results, configName, infoflow);	
		} else {
			System.out.println("Skipping.");
		}
		
		System.out.println("saveResults()");
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
		
		System.out.println("check expected results");
		for(Unit unit : results.rowKeySet()) {
			String expectedConstraint = getExpectedConstraint(infoflow, unit);
			
			if(expectedConstraint != null) {
				logger.info("Check Constraint for {} expected {}. Java-Line {}", unit, expectedConstraint, unit.getJavaSourceStartLineNumber());
				constraintsChecked++;
				String result = splSolver.orResult(unit).toString();
				
				// Hack to semantically similar but syntactically different constraints
				if(result.equals("(!(!B = 0 ^ !A = 1) ^ !(B = 0 ^ !A = 0))") && expectedConstraint.equals("(!(B = 0 ^ !A = 0) ^ !(!B = 0 ^ !A = 1))"))
				{
					expectedConstraint = result;
				}
				if(result.equals("(!A = 0 ^ !B = 0) || (A = 0 ^ B = 0)") && expectedConstraint.equals("(!B = 0 ^ !A = 0) || (B = 0 ^ A = 0)"))
				{
					expectedConstraint = result;
				}
				if(result.equals("(A = 0 ^ B = 0) || (!A = 0 ^ !B = 0)") && expectedConstraint.equals("(!B = 0 ^ !A = 0) || (B = 0 ^ A = 0)"))
				{
					expectedConstraint = result;
				}
				
				assertEquals(infoflow.getiCfg().getMethodOf(unit) + ": " + unit.toString(), expectedConstraint, result);
			} else {
//				logger.info("No Constraint for {}", unit);
			}
			
			
			
			
		}	
		
		return constraintsChecked;
	}
	
	@Test
    public void test22() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample22()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test23() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample23()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test24() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample24()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	
	@Test
	@Ignore // braucht Implementierungsfix
    public void test25_pointsToTest1() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample25()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void test26() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample26()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 2);
	}
	
	@Test
    public void test27() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample27()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked == 4);
	}
	
	@Test
    public void test28() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample28()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test29() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample29()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked == 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}

	}
	
	@Test
    public void test30() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample30()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}

	}
	
	@Test
    public void test31() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample31()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}	
	
	
	@Test
    public void test32() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample32()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		
		assertTrue(constraintsChecked == 0);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}		
	
	@Test
    public void test33() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample33()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}		
	
	@Test
    public void test33a() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample33a()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}		
	
	
	@Test
    public void test34() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample34()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
//		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}	
	
	@Test
    public void test35() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample35()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 2);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test36() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample36()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test37_implicitTest01() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void implicitTest01()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test38_implicitTest02() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void implicitTest02()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test39() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample39()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	// Error due to empty catch block?
	@Test
    public void test40() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample40()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
		
		// test certain taints
		List<Cell<Unit, Abstraction, IConstraint>> taints40 = taintsAtLine(infoflow.getSplResults(), infoflow, "soot.jimple.infoflow.test.loadtime.LoadTimeTestCode", 49);
		assertEquals(2, taints40.size());
	}
	
	@Test
    public void test41() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample41()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
		
		// test certain taints
		List<Cell<Unit, Abstraction, IConstraint>> taints40 = taintsAtLine(infoflow.getSplResults(), infoflow, "soot.jimple.infoflow.test.loadtime.LoadTimeTestCode", 49);
		assertEquals(2, taints40.size());
	}
	
	@Test
    public void test42() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample42()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test43() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample43()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test44() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample44()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test45() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample45()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test46() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample46()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test47() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample47()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test48() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample48()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test49() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample49()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test50() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample50()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test51() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample51()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test52() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample52()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void test53() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample53()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void paperExample() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void paperExample()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
		}
	}
	
	@Test
    public void testSet() throws IOException {
		test01();
		test02();
		test03();
		test04();
		test04details();
		test05();
//		test06();
		test07();
		test08();
		test09();
		test10();
		test11();
		
		test14();
		test15();
		test16();
//		test17();
		test18();
		test19();
		test20();
		test21();
		test22();
		test23();
		test24();

		test26();
		test27();
		test28();
		test29();
		test30();
		test31();
		test32();
		test33();
		test33a();
		test34();
		test35();
		test36();
		test37_implicitTest01();
		test38_implicitTest02();
		test39();
//		test40(); // TODO
//		test41();
//		test42();
//		test43();
		test44();
		test45();
		test46();
	}
	
	@Test
//	@Ignore // Prettyprinting funktioniert nicht
    public void allTests() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample01()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample02()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample03()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample04()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample05()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample06()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample07()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample08()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample09()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: boolean sample14b()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample15()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample16()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample22()>");
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample23()>");
		
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager("Tests");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);
		
		
		Config conf = ConfigFactory.load().getConfig("Tests");
		Config features = conf.getConfig("features");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\");
			
			printResults(infoflow.getSplResults());
			
			List<? extends Config> expectedResults = conf.getConfigList("expectedResults");
			
			for(Config resultForClass : expectedResults) {
				
				String className = resultForClass.getString("className");
				
				List<? extends Config> constraints = resultForClass.getConfigList("constraints");
				for(Config constraintElement : constraints)
				{
					int jimpleLine = constraintElement.getInt("jimpleLine");
					String expectedConstraint = constraintElement.getString("constraint");
					if(expectedConstraint.equals("false")) {
						expectedConstraint = null;
					}
					String constraint = loader.getConstraint("Tests", className, jimpleLine);
	
					assertEquals("Line " + jimpleLine, expectedConstraint, constraint);
				}
			}
		}
	}
	
	@Test
	@Ignore
    public void FreePastry01() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\pastry\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<rice.tutorial.lesson3.DistTutorial: void mytest()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		//assertTrue(constraintsChecked > 0);
	}
	
	@Test
	@Ignore
    public void FreePastryMin() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\pastry\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<test.MyTest: void start()>");
		
		int constraintsChecked = checkConstraints(infoflow, epoints);
		//assertTrue(constraintsChecked > 0);
	}
	
	@Test
	@Ignore
    public void FreePastry02() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\pastry\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
//		epoints.add("<rice.pastry.testing.RoutingTableTest: void testRoutingTables(int)>");
		epoints.add("<rice.pastry.socket.nat.rendezvous.RendezvousSocketPastryNodeFactory: rice.pastry.standard.ProximityNeighborSelector getProximityNeighborSelector(rice.pastry.PastryNode)>");
		
//		int constraintsChecked = checkConstraints(infoflow, epoints, "FreePastry");
//		assertTrue(constraintsChecked > 0);
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("FreePastry");
		testHelper.checkResults(conf, "FreePastry");
	}
	
	
	@Test
	@Ignore
    public void FreePastry03() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\pastry\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	Config conf = ConfigFactory.load().getConfig("FreePastry");
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
//		epoints.add("<rice.pastry.testing.RoutingTableTest: void testRoutingTables(int)>");
		epoints.add("<rice.tutorial.lesson3.DistTutorial: void main(java.lang.String[])>");
		epoints.add("<rice.environment.params.simple.SimpleParameters: boolean getBoolean(java.lang.String)>");
	
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager("FreePastry");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);	
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "FreePastry", "C:\\Users\\Max\\workspace\\pastry\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		
		testHelper.checkResults(conf, "FreePastry");
	}

	
	@Test
	@Ignore
    public void FreePastry04() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\pastry\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	Config conf = ConfigFactory.load().getConfig("FreePastry");
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
//		epoints.add("<rice.pastry.testing.RoutingTableTest: void testRoutingTables(int)>");
		epoints.add("<rice.tutorial.lesson3.DistTutorial: void main(java.lang.String[])>");
		epoints.add("<rice.pastry.testing.DistHelloWorld: void main(java.lang.String[])>");
//		epoints.add("<rice.environment.params.simple.SimpleParameters: boolean getBoolean(java.lang.String)>");
	
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager("FreePastry");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);	
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "FreePastry", "C:\\Users\\Max\\workspace\\pastry\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		
		testHelper.checkResults(conf, "FreePastry");
	}
	
	@Test
//	@Ignore
    public void CaptchalizeTest1() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\Captchalize\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<cap.CaptchalizeMain: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "Captchalize");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Captchalize", "C:\\Users\\Max\\workspace\\Captchalize\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("Captchalize");
		int constraintsChecked = testHelper.checkResults(conf, "Captchalize");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assert(constraintsChecked > 0);
	}
	
	
	@Test
//	@Ignore // - zu langsam
    public void ivyTest1() throws IOException {
    	File f = new File("C:\\Users\\Max\\workspace\\ant-ivy\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<org.apache.ivy.MainTest: void testHelp()>");
		
		checkConstraints(infoflow, epoints, "ivy");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "ivy", "C:\\Users\\Max\\workspace\\ant-ivy\\src\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("ivy");
		int constraintsChecked = testHelper.checkResults(conf, "ivy");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assert(constraintsChecked > 0);
	}
	
	@Test
    public void default1() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.DefaultTest: void default1()>");
		int constraintsChecked = checkConstraints(infoflow, epoints, "defaultTests");
		assertTrue(constraintsChecked > 0);
	}
	@Test
    public void default2() throws IOException {
		LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.DefaultTest: void default2()>");
		int constraintsChecked = checkConstraints(infoflow, epoints, "defaultTests");
		assertTrue(constraintsChecked > 0);
	}
	@Test
    public void default3() throws IOException {
		LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.DefaultTest: void default3()>");
		int constraintsChecked = checkConstraints(infoflow, epoints, "defaultTests");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void default4() throws IOException {
		LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.DefaultTest: void default4()>");
		int constraintsChecked = checkConstraints(infoflow, epoints, "defaultTests");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
//	@Ignore
    public void findbugs() throws IOException {

    	appPath = "C:\\Users\\Max\\workspace\\findbugs-3.0.0\\build\\junitclasses\\;C:\\Users\\Max\\workspace\\findbugs-3.0.0\\build\\classes\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	libPath += File.pathSeparator + "C:\\Users\\Max\\workspace\\findbugs-3.0.0\\lib\\dom4j-1.6.1.jar";
    	libPath += File.pathSeparator + "C:\\Users\\Max\\workspace\\findbugs-3.0.0\\lib\\bcel-6.0-SNAPSHOT.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<edu.umd.cs.findbugs.FindBugs2: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "findbugs");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "findbugs", "C:\\Users\\Max\\workspace\\findbugs-3.0.0\\src\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("findbugs");
		int constraintsChecked = testHelper.checkResults(conf, "findbugs");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assert(constraintsChecked > 0);
	}
	
	
	@Test
    public void ElevatorChanged() throws IOException {

    	appPath = "C:\\Users\\Max\\workspace\\ElevatorChanged\\bin\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<Main: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "ElevatorChanged");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "ElevatorChanged", "C:\\Users\\Max\\workspace\\ElevatorChanged\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("ElevatorChanged");
		int constraintsChecked = testHelper.checkResults(conf, "ElevatorChanged");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	
	
	
	@Test
//	@Ignore
    public void ZipMeChanged() throws IOException {

    	appPath = "C:\\Users\\Max\\workspace\\ZipMeChanged\\bin\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<Main: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "ZipMeChanged");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "ZipMeChanged", "C:\\Users\\Max\\workspace\\ZipMeChanged\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("ZipMeChanged");
		int constraintsChecked = testHelper.checkResults(conf, "ZipMeChanged");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
		
		// test certain taints, assumes config with option adler32checksum only
		
		List<Cell<Unit, Abstraction, IConstraint>> ZipTest29 = taintsAtLine(infoflow.getSplResults(), infoflow, "net.sf.zipme.ZipTest", 29);
		// zero taint and this(net.sf.zipme.ZipInputStream) <net.sf.zipme.InflaterInputStream: net.sf.zipme.Inflater inf> <net.sf.zipme.Inflater: net.sf.zipme.Adler32 adler> <net.sf.zipme.Adler32: int checksum>
		// anything else?
		assertEquals(2, ZipTest29.size());
		
		List<Cell<Unit, Abstraction, IConstraint>> ZipInputStream353 = taintsAtLine(infoflow.getSplResults(), infoflow, "net.sf.zipme.ZipInputStream", 353);
		// zero taint and this(net.sf.zipme.ZipInputStream) <net.sf.zipme.InflaterInputStream: net.sf.zipme.Inflater inf> <net.sf.zipme.Inflater: net.sf.zipme.Adler32 adler> <net.sf.zipme.Adler32: int checksum>
		// anything else?
		assertEquals(2, ZipInputStream353.size());
	}
	
	@Test
//	@Ignore
    public void ZipMeChangedSimplified() throws IOException {

    	appPath = "C:\\Users\\Max\\workspace\\ZipMeChangedSimplified\\bin\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<Main: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "ZipMeChangedSimplified");
		
//		dumpJumpFn(infoflow.splSolver.getJumpFn());
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "ZipMeChangedSimplified", "C:\\Users\\Max\\workspace\\ZipMeChangedSimplified\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("ZipMeChangedSimplified");
		int constraintsChecked = testHelper.checkResults(conf, "ZipMeChangedSimplified");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}


	@Test
    public void Rhino() throws IOException {

    	appPath = "C:\\Users\\Max\\workspace\\rhino\\buildGradle\\classes\\main\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	Options.v().set_verbose(true);
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();

		epoints.add("<org.mozilla.javascript.tools.jsc.Main: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "Rhino");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Rhino", "C:\\Users\\Max\\workspace\\rhino\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("Rhino");
		int constraintsChecked = testHelper.checkResults(conf, "Rhino");

		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void Processing() throws IOException {
		String project = "Processing";
		
    	appPath = "C:\\Users\\Max\\workspace\\processing\\java\\bin\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	Options.v().set_verbose(true);
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();

		epoints.add("<processing.mode.java.Commander: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, project);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, project, "C:\\Users\\Max\\workspace\\processing\\java\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig(project);
		int constraintsChecked = testHelper.checkResults(conf, project);

		
//		System.out.println(constraintsChecked + " Constraints checked.");
//		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void Languagetool() throws IOException {
		String project = "Languagetool";
		
    	appPath = "C:\\Users\\Max\\workspace\\languagetool-master\\languagetool-commandline\\target\\classes\\";
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	Options.v().set_verbose(true);
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();

		epoints.add("<org.languagetool.commandline.Main: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, project);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, project, "C:\\Users\\Max\\workspace\\languagetool-master\\languagetool-commandline\\src\\main\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig(project);
		int constraintsChecked = testHelper.checkResults(conf, project);

		
//		System.out.println(constraintsChecked + " Constraints checked.");
//		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void Validator() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\validator-master\\vnu");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<nu.validator.client.SimpleCommandLineValidator: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "Validator");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Validator", "C:\\Users\\Max\\workspace\\validator-master\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("Validator");
		int constraintsChecked = testHelper.checkResults(conf, "Validator");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	
	@Test
	@Ignore
    public void Selenese() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\selenese-runner-java-master\\target\\classes");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<jp.vmi.selenium.selenese.Main: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "Selenese");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Selenese", "C:\\Users\\Max\\workspace\\selenese-runner-java-master\\src\\main\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("Selenese");
		int constraintsChecked = testHelper.checkResults(conf, "Selenese");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	
	@Test
    public void Picard() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\picard\\classes");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<picard.illumina.IlluminaBasecallsToFastq: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "Picard");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Picard", "C:\\Users\\Max\\workspace\\picard\\src\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("Picard");
		int constraintsChecked = testHelper.checkResults(conf, "Picard");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void Epubcheck() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\epubcheck\\target\\classes");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<com.adobe.epubcheck.tool.EpubChecker: int run(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "epubcheck");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "epubcheck", "C:\\Users\\Max\\workspace\\epubcheck\\src\\main\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("epubcheck");
		int constraintsChecked = testHelper.checkResults(conf, "epubcheck");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void batman() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\batman\\bin");

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<batman.Calibrate: void main(java.lang.String[])>");
		epoints.add("<batman.EstimateCouplingProfile: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "batman");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "batman", "C:\\Users\\Max\\workspace\\batman\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("batman");
		int constraintsChecked = testHelper.checkResults(conf, "batman");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void opentsdb() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\opentsdb\\bin");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<net.opentsdb.tools.TSDMain: void main(java.lang.String[])>");
		epoints.add("<net.opentsdb.tsd.RpcHandler: void messageReceived(org.jboss.netty.channel.ChannelHandlerContext, org.jboss.netty.channel.MessageEvent)>");
		
		
		checkConstraints(infoflow, epoints, "opentsdb");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "opentsdb", "C:\\Users\\Max\\workspace\\opentsdb\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("opentsdb");
		int constraintsChecked = testHelper.checkResults(conf, "opentsdb");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void Swagger() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\swagger-codegen\\modules\\swagger-codegen\\target\\classes");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<io.swagger.codegen.DefaultGenerator: java.util.List generate()>");

		
		checkConstraints(infoflow, epoints, "Swagger");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Swagger", "C:\\Users\\Max\\workspace\\swagger-codegen\\modules\\swagger-codegen\\src\\main\\java\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("Swagger");
		int constraintsChecked = testHelper.checkResults(conf, "Swagger");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void trackanalyzer() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\trackanalyzer\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<TrackAnalyzer.TrackAnalyzer: void main(java.lang.String[])>");
		epoints.add("<TrackAnalyzer.TrackAnalyzer$WorkTrack: java.lang.Boolean call()>");

		
		checkConstraints(infoflow, epoints, "trackanalyzer");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "trackanalyzer", "C:\\Users\\Max\\workspace\\trackanalyzer\\src\\");
		}
		
		TestHelper testHelper = new TestHelper();
		Config conf = ConfigFactory.load().getConfig("trackanalyzer");
		int constraintsChecked = testHelper.checkResults(conf, "trackanalyzer");
		
		System.out.println(constraintsChecked + " Constraints checked.");
		assertTrue(constraintsChecked > 0);
	}
	
	@Test
    public void OpenGrok() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\OpenGrok\\opengrok-indexer\\target\\classes\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<org.opensolaris.opengrok.index.Indexer: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "OpenGrok");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "OpenGrok", "C:\\Users\\Max\\workspace\\OpenGrok\\src\\");
		}
		
		Config conf = ConfigFactory.load().getConfig("OpenGrok");
		try(TestHelper testHelper = new TestHelper()) {
			int constraintsChecked = testHelper.checkResults(conf, "OpenGrok");
			System.out.println(constraintsChecked + " Constraints checked.");
			assertTrue(constraintsChecked > 0);
		}
	}
	
	@Test
    public void platypus() throws IOException {
        final String sep = System.getProperty("path.separator");
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\platypus\\target\\classes\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = ""; // System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(false);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<org.pz.platypus.Platypus: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, "platypus");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "platypus", "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\platypus\\src\\main\\java\\");
		}
		
//		Config conf = ConfigFactory.load().getConfig("platypus");
//		try(TestHelper testHelper = new TestHelper()) {
//			int constraintsChecked = testHelper.checkResults(conf, "platypus");
//			System.out.println(constraintsChecked + " Constraints checked.");
//			assertTrue(constraintsChecked > 0);
//		}
	}
	
	@Test
    public void kafkaDispatch() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "kafkaDispatch";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\kafka-dispatch\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<dk.dbc.kafka.dispatch.KafkaDispatch: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.constraintAnalysis(name);
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\kafka-dispatch\\src\\main\\java\\");
		}
		
//		Config conf = ConfigFactory.load().getConfig(name);
//		try(TestHelper testHelper = new TestHelper()) {
//			int constraintsChecked = testHelper.checkResults(conf, name);
//			System.out.println(constraintsChecked + " Constraints checked.");
//		}
	}
	
	@Test
    public void data_consumer() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "data_consumer";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\programming-language-barriers-analysis-prototype\\java\\data_consumer\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<data_consumer.DataConsumerMainClass: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\programming-language-barriers-analysis-prototype\\java\\data_consumer\\src\\");
		}
		
//		Config conf = ConfigFactory.load().getConfig(name);
//		try(TestHelper testHelper = new TestHelper()) {
//			int constraintsChecked = testHelper.checkResults(conf, name);
//			System.out.println(constraintsChecked + " Constraints checked.");
//		}
	}
	
	@Test
    public void andsync_server() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "andsync_server";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\andsync-server\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<de.inovex.andsync.Server: void main(java.lang.String[])>");
		
		epoints.add("<de.inovex.andsync.rest.ObjectResource: javax.ws.rs.core.Response putObject(java.lang.String,byte[])>");
		epoints.add("<de.inovex.andsync.rest.ObjectResource: javax.ws.rs.core.Response getObjects(java.lang.String)>");
		epoints.add("<de.inovex.andsync.rest.ObjectResource: javax.ws.rs.core.Response getByMtime(java.lang.String,java.lang.String)>");
		epoints.add("<de.inovex.andsync.rest.ObjectResource: javax.ws.rs.core.Response getByIds(java.lang.String,java.lang.String)>");
		epoints.add("<de.inovex.andsync.rest.ObjectResource: javax.ws.rs.core.Response deleteObjects(java.lang.String,java.lang.String)>");
		epoints.add("<de.inovex.andsync.rest.ObjectResource: javax.ws.rs.core.Response postObject(java.lang.String,byte[])>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\andsync-server\\src\\");
		}
		
//		Config conf = ConfigFactory.load().getConfig(name);
//		try(TestHelper testHelper = new TestHelper()) {
//			int constraintsChecked = testHelper.checkResults(conf, name);
//			System.out.println(constraintsChecked + " Constraints checked.");
//		}
	}	
	
	@Test
    public void proteaj() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "proteaj";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\proteaj\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	
    	// For proteaj we must not use old coffi
    	// Does not work - will be overwritten by addition call to LoadTimeConfigForTest -> need to adjust there
    	testConfig.useCoffi = false;

    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<proteaj.Compiler: void main(java.lang.String[])>");
		infoflow.setSootConfig(testConfig);
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\proteaj\\src\\");
		}
		
//		Config conf = ConfigFactory.load().getConfig(name);
//		try(TestHelper testHelper = new TestHelper()) {
//			int constraintsChecked = testHelper.checkResults(conf, name);
//			System.out.println(constraintsChecked + " Constraints checked.");
//		}
	}	
	
	@Test
    public void adligo() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "adligo";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\fabricate.adligo.org\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<org.adligo.fabricate.FabricateOptsSetup: void main(java.lang.String[])>");
		epoints.add("<org.adligo.fabricate.routines.implicit.DecryptCommand: boolean setupInitial(org.adligo.fabricate.models.common.I_FabricationMemoryMutant,org.adligo.fabricate.models.common.I_RoutineMemoryMutant)>");
		// TODO - Could add a lot more of those Commands
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\fabricate.adligo.org\\src\\");
		}
		
//		Config conf = ConfigFactory.load().getConfig(name);
//		try(TestHelper testHelper = new TestHelper()) {
//			int constraintsChecked = testHelper.checkResults(conf, name);
//			System.out.println(constraintsChecked + " Constraints checked.");
//		}
	}	
	
	@Test
    public void remoteengine() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "remoterengine";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\remoterengine\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<org.rosuda.REngine.remote.server.REngineServer: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\remoterengine\\pkg\\RemoteREngine\\inst\\java_src\\src\\server\\");
		}
		
		Config conf = ConfigFactory.load().getConfig(name);
		try(TestHelper testHelper = new TestHelper()) {
			int constraintsChecked = testHelper.checkResults(conf, name);
			System.out.println(constraintsChecked + " Constraints checked.");
		}
	}	
	
	@Test
    public void MGrid() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "MGrid";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\MGrid\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
//    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	libPath = "";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<pgrid.PGridServer: void main(java.lang.String[])>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\MGrid\\src\\");
		}
		
		Config conf = ConfigFactory.load().getConfig(name);
		try(TestHelper testHelper = new TestHelper()) {
			int constraintsChecked = testHelper.checkResults(conf, name);
			System.out.println(constraintsChecked + " Constraints checked.");
		}
	}	
	
	@Test
    public void jmxetric() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "jmxetric";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\jmxetric\\target\\classes\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<info.ganglia.jmxetric.JMXetricAgent: void premain(java.lang.String,java.lang.instrument.Instrumentation)>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\jmxetric\\src\\main\\java\\");
		}
		
		Config conf = ConfigFactory.load().getConfig(name);
		try(TestHelper testHelper = new TestHelper()) {
			int constraintsChecked = testHelper.checkResults(conf, name);
			System.out.println(constraintsChecked + " Constraints checked.");
		}
	}	
	
	@Test
    public void WarGameofThrones() throws IOException {
        final String sep = System.getProperty("path.separator");
        String name = "WarGameofThrones";
    	File f = new File("C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\war-game-of-thrones\\projeto\\War Game of Thrones\\bin\\");
    	
    	assertTrue(f.exists());

    	appPath = f.getCanonicalPath();
    	libPath = System.getProperty("java.home") + File.separator + "lib" + File.separator + "rt.jar";
//    	libPath = "";
    	
		LoadTimeInfoflow result = new LoadTimeInfoflow();
		result.setCallgraphAlgorithm(CallgraphAlgorithm.CHA);
		
		EasyTaintWrapper easyWrapper = new EasyTaintWrapper(new File("EasyTaintWrapperSource.txt"));
		result.setTaintWrapper(easyWrapper);
		
    	Infoflow.setDebug(true);
    	LoadTimeConfigForTest testConfig = new LoadTimeConfigForTest();
    	result.setSootConfig(testConfig);
    	
    	LoadTimeInfoflow infoflow = result;
    	
		List<String> epoints = new ArrayList<String>();
		epoints.add("<main.Main: void main(java.lang.String[])>");
		epoints.add("<main.GameScene: void enterState(org.newdawn.slick.GameContainer,org.newdawn.slick.state.StateBasedGame)>");
		epoints.add("<main.GameScene: void updateGame(org.newdawn.slick.GameContainer,org.newdawn.slick.state.StateBasedGame,int)>");
		
		
		// screens.xml - todo overloads from library
		epoints.add("<gui.MainScreenController: void showAddPlayerMenu()>");
		epoints.add("<gui.MainScreenController: void showOptions()>");
		epoints.add("<gui.MainScreenController: void showHelp()>");
		epoints.add("<gui.MainScreenController: void exit()>");
		
		epoints.add("<gui.AddPlayerController: void editPlayer(java.lang.String)>");
		epoints.add("<gui.AddPlayerController: void addPlayer()>");
		epoints.add("<gui.AddPlayerController: void excludePlayer()>");
		epoints.add("<gui.AddPlayerController: void closePopup()>");
		epoints.add("<gui.AddPlayerController: void playButtonPressed()>");
		epoints.add("<gui.AddPlayerController: void dismissEmptyNamePopup()>");
		
		epoints.add("<gui.InGameGUIController: void theGameMenuClicked()>");
		epoints.add("<gui.InGameGUIController: void showOptions()>");
		epoints.add("<gui.InGameGUIController: void helpMenuClicked()>");
		epoints.add("<gui.InGameGUIController: void exitMenuClicked()>");
		epoints.add("<gui.InGameGUIController: void showTables()>");
		epoints.add("<gui.InGameGUIController: void showPlayerCards()>");
		epoints.add("<gui.InGameGUIController: void showPlayerObjective()>");
		epoints.add("<gui.InGameGUIController: void nextPlayerTurnConfirm()>");
		epoints.add("<gui.InGameGUIController: void dismissPlayerObjective()>");
		epoints.add("<gui.InGameGUIController: void cardClicked(java.lang.String)>");
		epoints.add("<gui.InGameGUIController: void tradePlayerCards()>");
		epoints.add("<gui.InGameGUIController: void dismissPlayerCards()>");
		epoints.add("<gui.InGameGUIController: void nextPlayerTurn()>");
		epoints.add("<gui.InGameGUIController: void dismissNextTurnConfirmation()>");
		epoints.add("<gui.InGameGUIController: void exitGame()>");
		epoints.add("<gui.InGameGUIController: void dismissExitConfirmation()>");
		epoints.add("<gui.InGameGUIController: void dismissTablesPopup()>");
		epoints.add("<gui.InGameGUIController: void closeHelpPopup()>");
		epoints.add("<gui.InGameGUIController: void closeOptions()>");
		epoints.add("<gui.InGameGUIController: void rearrangePopupOK()>");
		epoints.add("<gui.InGameGUIController: void dismissRearrangePopup()>");
		epoints.add("<gui.InGameGUIController: void confirmAtkUnits()>");
		epoints.add("<gui.InGameGUIController: void confirmDefUnits()>");
		// continue
		
		epoints.add("<gui.StatisticsScreenController: void backToMainScreen()>");
		epoints.add("<gui.StatisticsScreenController: void exitGame()>");
		
		epoints.add("<gui.MainScreenController: void closeHelpPopup()>");
		epoints.add("<gui.MainScreenController: void closeOptions()>");
		
		checkConstraints(infoflow, epoints, name);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, name, "C:\\Users\\Max\\workspace\\LotrackEvaluationJava\\war-game-of-thrones\\projeto\\War Game of Thrones\\src\\");
		}
		
		Config conf = ConfigFactory.load().getConfig(name);
		try(TestHelper testHelper = new TestHelper()) {
			int constraintsChecked = testHelper.checkResults(conf, name);
			System.out.println(constraintsChecked + " Constraints checked.");
		}
	}	
	
	@Test
	public void testJavaLineMapping() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: void sample01()>");

		try(MongoLoader loader = new MongoLoader()) {
			int javaLine = loader.getJavaLine("Tests", "soot.jimple.infoflow.test.loadtime.LoadTimeTestCode", "$z1 = staticinvoke <soot.jimple.infoflow.test.loadtime.FeatureBase: boolean featureB()>()");
			assertEquals(12, javaLine);
		}
	}
	
	@Test
	public void slicingEvaluation()
	{
		String projectName;
		try(MongoLoader mongo = new MongoLoader())
		{
			
			projectName = "platypus";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);
			
			projectName = "kafkaDispatch";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);
			
			projectName = "data_consumer";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);
			
			projectName = "andsync_server";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);

			
			projectName = "adligo";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);
			
			projectName = "remoterengine";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);
			
			projectName = "MGrid";
			System.out.println("Project: " + projectName);
			mongo.slicingEvaluation(projectName);
//			
			// No full slice available
//			projectName = "WarGameofThrones";
//			System.out.println("Project: " + projectName);
//			mongo.slicingEvaluation(projectName);
		}
	}
	
	@Test
	public void constraintAnalysis()
	{
		String projectName;
		try(MongoLoader mongo = new MongoLoader())
		{
			projectName = "platypus";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "kafkaDispatch";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "data_consumer";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "andsync_server";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "proteaj";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "adligo";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "remoterengine";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "MGrid";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "jmxetric";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
			
			projectName = "WarGameofThrones";
			System.out.print("Project: " + projectName + " ");
			mongo.constraintAnalysis(projectName);
		}
	}
	
	
}
