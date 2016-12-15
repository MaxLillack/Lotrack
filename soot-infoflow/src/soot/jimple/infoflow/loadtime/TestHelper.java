package soot.jimple.infoflow.loadtime;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Assert;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.Stmt;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.SourceContext;
import soot.jimple.infoflow.loadtime.MongoLoader;
import soot.jimple.infoflow.source.SourceInfo;
import soot.spl.ifds.IConstraint;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.JimpleLineNumberTag;
import soot.tagkit.Tag;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

public class TestHelper implements AutoCloseable {
	
	private MongoLoader mongo;
	
	public TestHelper() throws UnknownHostException {
		mongo = new MongoLoader();
	}
	
	public int getFeatureOffset(Config conf)
	{
		Config features = conf.getConfig("features");
	
		int offset = 0;
		for(Entry<String, ConfigValue> feature : features.root().entrySet())
		{
			String name = feature.getKey();
			int i = features.getConfig(name).getInt("index");
			offset = i > offset ? i : offset;
		}
		return offset;
	}
	
	public int checkResults(Config conf, String collectionName)
	{
		List<? extends Config> expectedResults = conf.getConfigList("expectedResults");
		
		int checkedResults = 0;
		
		for(Config resultForClass : expectedResults) {
			
			String className = resultForClass.getString("className");
			
			List<? extends Config> constraints = resultForClass.getConfigList("constraints");
			for(Config constraintElement : constraints)
			{
				int jimpleLine = constraintElement.getInt("jimpleLine");
				String expectedConstraint = constraintElement.getString("constraint");
				
				String constraint = mongo.getConstraint(collectionName, className, jimpleLine);
				Assert.assertEquals("Line " + jimpleLine, expectedConstraint, constraint);
				
				checkedResults++;
			}
		}
		
		return checkedResults;
	}
	
	public int getJimpleLineNumber(Unit unit)
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
	
	public static String getSourceInfo(Unit unit, LoadTimeSourceSinkManager sourceSinkManager, LoadTimeInfoflow infoflow, FeatureNames featureNames)
	{
		String option = null;
		if(unit instanceof Stmt) {
			Stmt stmt = (Stmt) unit;
			SourceInfo sourceInfo = sourceSinkManager.getSourceInfo(stmt, infoflow.getiCfg());
			if(sourceInfo != null)
			{
				int optionIndex = (int) sourceInfo.getUserData();
				if(featureNames != null)
				{
					option = featureNames.getMapping().get(optionIndex);
				} else {
					option = Integer.toString(optionIndex);
				}
			}
		}
		return option;
	}
	
	public void detailedDBLog(Table<Unit, Abstraction, IConstraint> results, String configName, LoadTimeInfoflow infoflow)
	{
		System.out.println("detailedDBLog");
		
		Aliasing aliasing = infoflow.getLoadtimeInfoflowProblem().getAliasing();
		
		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager(configName);
		
		try(MongoLoader loader = new MongoLoader()) {
			loader.clearDetailedLog(configName);
			
			loader.startBulkDetailedLog();
			for(Cell<Unit, Abstraction, IConstraint> cell : results.cellSet())
			{
				Unit unit = cell.getRowKey();
				
				SootMethod sootMethod = infoflow.getiCfg().getMethodOf(unit);
				String methodName = sootMethod.toString();
				SootClass sootClass = sootMethod.getDeclaringClass();
				
				String slicingInfo = "";
				// Check for data flow dependency
				for(ValueBox use : unit.getUseBoxes())
				{
					if(aliasing.mayAlias(use.getValue(), cell.getColumnKey().getAccessPath().getPlainValue())) {
						SourceContext sourceContext = cell.getColumnKey().getSourceContext();
						if(sourceContext != null && sourceContext.getUserData() != null)
						{
							FeatureInfo featureInfo = (FeatureInfo) sourceContext.getUserData();
							slicingInfo = "In Slice of feature " + featureInfo.getIndex() + " Data-Dep";
						}
					}
				}
				
				// No data dependency check for control flow dependency
				if(slicingInfo.equals(""))
				{
					if(cell.getColumnKey().toString().startsWith("zero"))
					{
						if(!cell.getValue().toString().equals("true"))
						{
							slicingInfo = "In Slice of feature " + cell.getValue().toString() + " CFlow-Dep";
						}
					}
				}
				
				Collection<Integer> bytecodeIndexes = getBytecodeIndex(unit);
				
				// Check if unit is source i.e. slice criteria
				String sourceInfo = getSourceInfo(unit, sourceSinkManager, infoflow, null);
				boolean isSource = sourceInfo != null;
				
				String methodByteCodeJoanaStyle = getMethodByteCodeJoanaStyle(sootMethod);
				
				int jimpleLineNumber = getJimpleLineNumber(cell.getRowKey());

				loader.saveDetailedLog(configName, cell.getRowKey().toString(), 
											   cell.getColumnKey().toString(), 
											   cell.getValue().toString(), 
											   sootClass.getName(),
											   jimpleLineNumber,
											   unit.getJavaSourceStartLineNumber(),
											   slicingInfo,
											   bytecodeIndexes,
											   methodName,
											   methodByteCodeJoanaStyle,
											   isSource);			
			}
			
			loader.executeBulkDetailedLog();
		}
		System.out.println("detailedDBLog done");
	}

	public static String getMethodByteCodeJoanaStyle(SootMethod sootMethod) {
		String methodByteCodeJoanaStyle = sootMethod.getBytecodeSignature();
		// Adjust format of byMethodName to format used by Joana
		// 1. Remove < and >
		methodByteCodeJoanaStyle = methodByteCodeJoanaStyle.substring(1, methodByteCodeJoanaStyle.length() - 1);
		// 2. Replace ": " (separating class name and method name) by "."
		methodByteCodeJoanaStyle = methodByteCodeJoanaStyle.replace(": ", ".");
		return methodByteCodeJoanaStyle;
	}

	public static Collection<Integer> getBytecodeIndex(Unit unit) {
		Collection<Integer> bytecodeIndexes = new ArrayList<>();
		
		for(Tag tag : unit.getTags())
		{
			if(tag instanceof BytecodeOffsetTag)
			{
				int bytecodeIndex = Integer.parseInt(tag.toString());
				bytecodeIndexes.add(bytecodeIndex);
			}
		}
		
		if(bytecodeIndexes.isEmpty())
		{
			bytecodeIndexes.add(-1);
		}
		
		return bytecodeIndexes;
	}
	
	
	public MongoLoader getMongoLoader() {
		return mongo;
	}

	public void close()
	{
		mongo.close();
		mongo = null;
	}
	
	
}
