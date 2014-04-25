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

import heros.InterproceduralCFG;

import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;

import org.junit.Test;

import com.typesafe.config.Config;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.Infoflow;
import soot.jimple.infoflow.entryPointCreators.DefaultEntryPointCreator;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;

/**
 * Test cases for FlowDroid's interaction with non-standard source sink managers
 * 
 * @author Steven Arzt
 */
public class SourceSinkTests extends JUnitTests{
	
	private static final String sourceGetSecret =
			"<soot.jimple.infoflow.test.SourceSinkTestCode: soot.jimple.infoflow.test.SourceSinkTestCode$A getSecret()>";
	private static final String sourceGetSecret2 =
			"<soot.jimple.infoflow.test.SourceSinkTestCode: soot.jimple.infoflow.test.SourceSinkTestCode$B getSecret2()>";
	
	private abstract class BaseSourceSinkManager implements ISourceSinkManager {
		
		@Override
		public boolean isSink(Stmt sCallSite,
				InterproceduralCFG<Unit, SootMethod> cfg) {
			if (!sCallSite.containsInvokeExpr())
				return false;
			SootMethod target = sCallSite.getInvokeExpr().getMethod();
			return target.getSignature().equals(sink);
		}

		@Override
		public Config getFeatureConfig() {
			return null;
		}

		@Override
		public boolean trackPrecise(SourceInfo sourceInfo) {
			return false;
		}

	}

	@Test(timeout=300000)
    public void fieldTest(){
		ISourceSinkManager sourceSinkManager = new BaseSourceSinkManager() {
			
			@Override
			public SourceInfo getSourceInfo(Stmt sCallSite,
					InterproceduralCFG<Unit, SootMethod> cfg) {
				if (sCallSite.containsInvokeExpr()
						&& sCallSite.getInvokeExpr().getMethod().getName().equals("getSecret"))
					return new SourceInfo(true);
				return null;
			}
			
		};
		
    	Infoflow infoflow = initInfoflow();
    	List<String> epoints = new ArrayList<String>();
    	epoints.add("<soot.jimple.infoflow.test.SourceSinkTestCode: void testDataObject()>");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);
		Assert.assertTrue(infoflow.isResultAvailable());
		Assert.assertEquals(1, infoflow.getResults().size());
		Assert.assertTrue(infoflow.getResults().isPathBetweenMethods(sink, sourceGetSecret));
    }
	
	@Test(timeout=300000)
    public void negativeFieldTest(){
		ISourceSinkManager sourceSinkManager = new BaseSourceSinkManager() {
			
			@Override
			public SourceInfo getSourceInfo(Stmt sCallSite,
					InterproceduralCFG<Unit, SootMethod> cfg) {
				if (sCallSite.containsInvokeExpr()
						&& sCallSite.getInvokeExpr().getMethod().getName().equals("getSecret"))
					return new SourceInfo(false);
				return null;
			}
			
		};
		
    	Infoflow infoflow = initInfoflow();
    	List<String> epoints = new ArrayList<String>();
    	epoints.add("<soot.jimple.infoflow.test.SourceSinkTestCode: void testDataObject()>");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);
		negativeCheckInfoflow(infoflow);
    }

	@Test(timeout=300000)
    public void accessPathTypesTest(){
		ISourceSinkManager sourceSinkManager = new BaseSourceSinkManager() {
			
			@Override
			public SourceInfo getSourceInfo(Stmt sCallSite,
					InterproceduralCFG<Unit, SootMethod> cfg) {
				if (sCallSite.containsInvokeExpr()
						&& (sCallSite.getInvokeExpr().getMethod().getName().equals("getSecret")
								||(sCallSite.getInvokeExpr().getMethod().getName().equals("getSecret2"))))
					return new SourceInfo(true);
				return null;
			}
			
		};
		
    	Infoflow infoflow = initInfoflow();
    	List<String> epoints = new ArrayList<String>();
    	epoints.add("<soot.jimple.infoflow.test.SourceSinkTestCode: void testAccessPathTypes()>");
		infoflow.computeInfoflow(appPath, libPath, new DefaultEntryPointCreator(), epoints, sourceSinkManager);
		Assert.assertTrue(infoflow.isResultAvailable());
		Assert.assertEquals(1, infoflow.getResults().size());
		Assert.assertTrue(infoflow.getResults().isPathBetweenMethods(sink, sourceGetSecret));
		Assert.assertTrue(infoflow.getResults().isPathBetweenMethods(sink, sourceGetSecret2));
    }

}
