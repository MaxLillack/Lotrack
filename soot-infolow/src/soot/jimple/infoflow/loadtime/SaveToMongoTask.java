package soot.jimple.infoflow.loadtime;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.javabdd.BDD;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.SPLIFDSSolver;
import soot.tagkit.JimpleLineNumberTag;
import soot.tagkit.SourceFileTag;
import soot.tagkit.Tag;

import com.google.common.base.Splitter;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;

public class SaveToMongoTask implements Runnable {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private Unit unit;
	private LoadTimeInfoflow infoflow;
	private String basePath;
	private SPLIFDSSolver<Abstraction,AccessPath> splSolver;
	private int knownFeaturesOffset;
	private Map<Constraint<String>, String> prettyConstraints;
	private FeatureNames featureNames;
	private MongoLoader mongoLoader;
	private List<DBObject> logObjects;
	private List<DBObject> objects;
	
	public SaveToMongoTask(LoadTimeInfoflow infoflow, 
						   String basePath, 
						   SPLIFDSSolver<Abstraction,AccessPath> splSolver, 
						   int knownFeaturesOffset,
						   Map<Constraint<String>, String> prettyConstraints,
						   FeatureNames featureNames, 
						   Unit unit,
						   MongoLoader mongoLoader,
						   List<DBObject> logObjects,
						   List<DBObject> objects) {
		this.infoflow = infoflow;
		this.unit = unit;
		this.basePath = basePath;
		this.splSolver = splSolver;
		this.knownFeaturesOffset = knownFeaturesOffset;
		this.prettyConstraints = prettyConstraints;
		this.featureNames = featureNames;
		this.mongoLoader = mongoLoader;
		this.logObjects = logObjects;
		this.objects = objects;
	}
	
	private String getJavaPath(SootClass sootClass, String basePath) {
		SourceFileTag sourceFileTag = (SourceFileTag) sootClass.getTag("SourceFileTag");
		if(sourceFileTag != null) {
			return basePath 
				   + sootClass.getPackageName().replace(".", "\\") 
				   + "\\" + sourceFileTag.getSourceFile();
		} else {
			return null;
		}
	}
	
	private String getJimplePath(SootClass sootClass)
	{
		String basePath = System.getProperty("user.dir");
		String path = basePath + "\\JimpleFiles\\" 
				   + sootClass.getName()
				   + ".jimple";
		return path;
	}
	
	public int getJimpleLineNumber(final Unit unit, String className)
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
		
		
		if(lineNumber == -1 && className != null) {
			String jimpleSource = mongoLoader.getJimpleSource(className);
			
			if(jimpleSource != null) {
				// Fallback - Search in source
				Iterable<String> lines = Splitter.on("\n").split(jimpleSource);
				
				int i = 1;
				for(String line : lines)
				{
					if(line.contains(unit.toString())) {
						lineNumber = i;
						break;
					}
					i++;
				}
			}
		}
		
		return lineNumber;
	}
	
	public int getJavaLineNumer(final Unit unit)
	{
		return unit.getJavaSourceStartLineNumber();
	}
	
	private void createLogObject(SootClass sootClass)
	{
		
		Map<Abstraction, Constraint<String>> results = splSolver.resultsAt(unit);
		for(Entry<Abstraction, Constraint<String>> cell : results.entrySet())
		{
			BasicDBObject row = new BasicDBObject();
			row.append("class", sootClass.getName());
			row.append("row", unit.toString());
			row.append("column", cell.getKey().toString());
			row.append("value", cell.getValue().toString());
			row.append("Line", getJimpleLineNumber(unit, null));
			logObjects.add(row);
		}
	}
	
	public String prettyPrintConstraint(Constraint<String> constraint)
	{
		if(constraint == Constraint.<String>trueValue()) {
			return "true";
		}
		if(constraint == Constraint.<String>falseValue()) {
			return "false";
		}

		if(!prettyConstraints.containsKey(constraint)) {
			Map<Integer, String> nameMapping = featureNames.getMapping();
			String pretty = constraint.prettyString(nameMapping);
			if(pretty == null) {
				pretty = "Unknown";
			}
//			if(constraint.isUnknown()) {
//				synchronized (Constraint.FACTORY) {
//					BDD bdd = constraint.getBDD();
//					prettyConstraints.put(constraint, "UnknownConstraint (" + (bdd != null ? bdd.toString() : "")  + ")");
//				}
//			} else {
				prettyConstraints.put(constraint, pretty);
//			}
		}
		return prettyConstraints.get(constraint);
	}

	@Override
	public void run() {
		
		SootMethod sootMethod = infoflow.getiCfg().getMethodOf(unit);
		SootClass sootClass = sootMethod.getDeclaringClass();
		
		boolean skipClass = false;

		
		if(sootClass.getName().startsWith("java.")
		  || sootClass.getName().startsWith("sun.")) {
			
			skipClass =true;
		}

		if(!skipClass) {
			
			String javaPath = getJavaPath(sootClass, basePath);
			String jimplePath = getJimplePath(sootClass);

			Constraint<String> orResult;
			synchronized (Constraint.FACTORY) {
				orResult = splSolver.orResult(unit);
			}
			
			int jimpleLineNumber = getJimpleLineNumber(unit, sootMethod.getDeclaringClass().getName());
			int javaLineNumber = getJavaLineNumer(unit);
			
			BasicDBObject row = new BasicDBObject();
			row.append("Package", sootClass.getJavaPackageName());
			row.append("Class", sootClass.getName());
			row.append("Method", sootMethod.getName());
			
			row.append("JavaLineNo", javaLineNumber);
			row.append("JimpleLineNo", jimpleLineNumber);
			
			String orResultString = orResult.toString();
			row.append("Constraint", orResultString.length() > 100 ? orResultString.substring(0, 99) + "..." : orResultString);
			String prettyConstraint = prettyPrintConstraint(orResult);
			row.append("ConstraintPretty", prettyConstraint.length() > 100 ? prettyConstraint.substring(0, 99) + "..." : prettyConstraint);
			
			row.append("JavaPath", javaPath);
			row.append("JimplePath", jimplePath);
			
			row.append("Unit", unit.toString());
			objects.add(row);

//			if(sootMethod.getName().contains("pickFileSimple")) {
//				createLogObject(sootClass);
//			}
		}
	}

}
