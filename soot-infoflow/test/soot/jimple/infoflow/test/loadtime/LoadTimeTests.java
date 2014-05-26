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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.loadtime.LoadTimeSourceSinkManager;
import soot.jimple.infoflow.loadtime.MongoLoader;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.jimple.infoflow.test.junit.JUnitTests;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.IFDSEdgeFunctions;
import soot.spl.ifds.SPLIFDSSolver;
import soot.tagkit.JimpleLineNumberTag;
import soot.tagkit.Tag;

public class LoadTimeTests extends JUnitTests {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private String appPath;
	private String libPath;
	
	@Rule
	public RepeatRule repeatRule = new RepeatRule();


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
	
	private void printResults(Table<Unit, Abstraction, Constraint<String>> results)
	{
		for(Cell<Unit, Abstraction, Constraint<String>> cell : results.cellSet())
		{
			logger.info("row {} column {} value {}, Line {}", cell.getRowKey(), cell.getColumnKey(), cell.getValue(), getJimpleLineNumber(cell.getRowKey()));
		}
	}
	
	
	private Path getJavaPath(SootClass sootClass) {
		String basePath = System.getProperty("user.dir");
		Path path = Paths.get(basePath + "\\test\\" 
				   + sootClass.getPackageName().replace(".", "\\") 
				   + "\\" + sootClass.getShortName() + ".java");
		return path;
	}

	
	private int getJimpleLineNumber(Unit unit)
	{
		int lineNumber = -1;
		List<Tag> tags = unit.getTags();
		for(Tag tag : tags) {
			if(tag instanceof JimpleLineNumberTag) {
				int line = ((JimpleLineNumberTag) tag).getLineNumber();
				if(line > 0) {
					lineNumber = line;
				}
			}
		}
		return lineNumber;
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
		assertTrue(constraintsChecked > 7);
		
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
	
	@Test
    public void test03() throws IOException{
		LoadTimeInfoflow infoflow = customInitInfoflow();
	    
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
	@Ignore
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
	
	@Test
    public void test14() throws IOException {
	    LoadTimeInfoflow infoflow = customInitInfoflow();
		List<String> epoints = new ArrayList<String>();
		epoints.add("<soot.jimple.infoflow.test.loadtime.LoadTimeTestCode: boolean sample14b()>");

		int constraintsChecked = checkConstraints(infoflow, epoints);
		assertTrue(constraintsChecked > 0);
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
		assertTrue(constraintsChecked > 0);
	}
	
	private int checkConstraints(LoadTimeInfoflow infoflow, List<String> epoints)
	{
		int constraintsChecked = 0;
		
		List<String> sources = new ArrayList<String>();
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager(sources, "Tests");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);	
		
		SPLIFDSSolver<Abstraction,AccessPath> splSolver = infoflow.getSPLSolver();
		
		Table<Unit, Abstraction, Constraint<String>> results = infoflow.getSplResults();
		  
		printResults(results);
		
		for(Unit unit : results.rowKeySet()) {
			String expectedConstraint = getExpectedConstraint(infoflow, unit);
			
			if(expectedConstraint != null) {
				logger.info("Check Constraint for {}", unit);
				constraintsChecked++;
				String result = splSolver.orResult(unit).toString();
				assertEquals(expectedConstraint, result);
			} else {
				logger.info("No Constraint for {}", unit);
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
		
		List<String> sources = new ArrayList<String>();
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager(sources, "Tests");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);
		
		
		Config conf = ConfigFactory.load().getConfig("Tests");
		Config features = conf.getConfig("features");
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.saveResults(infoflow, "Tests", "C:\\Users\\Max\\workspace\\soot-infoflow\\test\\", features.entrySet().size());
			
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
	
}
