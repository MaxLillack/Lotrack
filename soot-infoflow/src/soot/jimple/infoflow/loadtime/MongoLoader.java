package soot.jimple.infoflow.loadtime;

import heros.EdgeFunction;

import java.net.UnknownHostException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FilenameUtils;
import org.bson.Document;
import org.bson.types.ObjectId;

import net.sf.javabdd.BDD;

import com.google.common.collect.Table;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.Block;
import com.mongodb.BulkWriteOperation;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import static com.mongodb.client.model.Filters.*;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import soot.SootClass;
import soot.Unit;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.IConstraint;
import soot.spl.ifds.SPLIFDSSolver;

public class MongoLoader implements AutoCloseable {

	MongoClient mongoClient;
	DB db;

	Map<SootClass, String> javaSourceCode = new ConcurrentHashMap<SootClass, String>();

	Map<IConstraint, String> prettyConstraints = new ConcurrentHashMap<IConstraint, String>();
	FeatureNames featureNames;

	DBCollection jimpleCollection;
	
	Pattern imprecise = Pattern.compile("(\\w+)_(\\w+)");
	Pattern precise = Pattern.compile("(?<![a-zA-Z_])[a-zA-Z]+(?![a-zA-Z_])");

	public MongoLoader() {
		
		mongoClient = new MongoClient();
		db = mongoClient.getDB("loadtime");
		jimpleCollection = db.getCollection("JimpleFiles");
		
	}

	public void saveResults(LoadTimeInfoflow infoflow, String collectionName,
			String basePath) {
		featureNames = new FeatureNames(collectionName);

		DBCollection collection = db.getCollection(collectionName);
		DBCollection log = db.getCollection("log");
		// clear collection
		collection.remove(new BasicDBObject());

		Table<Unit, Abstraction, IConstraint> res = infoflow.getSplResults();

		if(res == null) {
			throw new RuntimeException("Missing results.");
		}
		
		SPLIFDSSolver<Abstraction, AccessPath> splSolver = infoflow.getSPLSolver();

		List<DBObject> objects = Collections.synchronizedList(new LinkedList<DBObject>());
		List<DBObject> logObjects = Collections.synchronizedList(new LinkedList<DBObject>());

		LoadTimeSourceSinkManager sourceSinkManager = new LoadTimeSourceSinkManager(collectionName);
		
		ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 60,
				TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
				new ThreadPoolExecutor.CallerRunsPolicy());

		
		Map<String, String> jimpleSources = new ConcurrentHashMap<String, String>();
		for (Unit unit : res.rowKeySet()) {

			SaveToMongoTask task = new SaveToMongoTask(infoflow, basePath,
					splSolver, prettyConstraints,
					featureNames, unit, this, logObjects, objects, jimpleSources,
					sourceSinkManager);

			executor.execute(task);
		}

		executor.shutdown();

		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		if(!objects.isEmpty())
		{
			collection.insert(objects);
		}
		if(!logObjects.isEmpty())
		{
			log.insert(logObjects);
		}
	}

	public void logStart(String collectionName) {
		logProgress(collectionName, "start");
	}

	private void logProgress(String collectionName, String action) {
		DBCollection progress = db.getCollection("progress");

		BasicDBObject row = new BasicDBObject();
		row.append("collectionName", collectionName);

		row.append("action", action);

		SimpleDateFormat datetime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		row.append("time", datetime.format(new Date()));

		progress.insert(row);
	}

	public void logEnd(String collectionName) {
		logProgress(collectionName, "end");
	}
	
	public long getRuntime(String collectionName) throws ParseException
	{
		SimpleDateFormat dateformat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");		
		DBCollection progress = db.getCollection("progress");
		
		BasicDBObject query = new BasicDBObject("collectionName", collectionName).append("action", "end");
		DBCursor endCursor = progress.find(query).sort(new BasicDBObject("time", -1));

		
		BasicDBObject startQuery = new BasicDBObject("collectionName", collectionName).append("action", "start");
		DBCursor startCursor = progress.find(startQuery).sort(new BasicDBObject("time", -1));
		
		long seconds = -1;
		
		try {
			if (endCursor.hasNext()) {
				String timeString = (String) endCursor.next().get("time");
				Date end = dateformat.parse(timeString);
				timeString = (String) startCursor.next().get("time");
				Date start = dateformat.parse(timeString);

				long difference = end.getTime() - start.getTime();
				seconds = TimeUnit.MILLISECONDS.toSeconds(difference);
			}
		} finally {
			endCursor.close();
			startCursor.close();
		}
		
		return seconds;
	}

	public String getConstraint(String collectionName, String className,
			int jimpleLine) {
		DBCollection collection = db.getCollection(collectionName);

		BasicDBObject query = new BasicDBObject("Class", className).append(
				"JimpleLineNo", jimpleLine);
		DBCursor cursor = collection.find(query);

		String constraint = null;

		try {
			while (cursor.hasNext()) {
				constraint = (String) cursor.next().get("ConstraintPretty");
			}
		} finally {
			cursor.close();
		}

		return constraint;
	}
	
	public void clearDetailedLog(String app)
	{
		DBCollection collection = db.getCollection("detailedLog");
		// clear collection
		collection.remove(new BasicDBObject("app", app));
	}
	
	private BulkWriteOperation bulkInserter = null;
	private long bulkInserterCounter = 0;
	
	public void startBulkDetailedLog()
	{
		DBCollection collection = db.getCollection("detailedLog");
		bulkInserter = collection.initializeUnorderedBulkOperation();
	}
	
	public void executeBulkDetailedLog()
	{
		if(bulkInserter == null)
		{
			throw new IllegalArgumentException("No bulk insert is active."); 
		}
		// Execute with default write concern
		// Must not commit empty batch, this would cause an exception
		if(bulkInserterCounter > 0)
		{
			bulkInserter.execute();
			bulkInserterCounter = 0;
		}
	}
	
	public void saveDetailedLog(String app, 
								String row, 
								String column, 
								String value, 
								String className, 
								int line, 
								int javaLine, 
								String slicingInfo, 
								Collection<Integer> bytecodeIndexes, 
								String method,
								String methodBytecodeSignature,
								boolean isSource)
	{
		
		if(bulkInserter == null)
		{
			throw new IllegalArgumentException("startBulkDetailedLog() must be called before."); 
		}
		
		BasicDBObject rowLog = new BasicDBObject();
		rowLog.append("app", app);

		rowLog.append("row", row);
		rowLog.append("column", column);
		rowLog.append("value", value);
		rowLog.append("className", className);
		rowLog.append("line", line);
		rowLog.append("javaLine", javaLine);
		rowLog.append("slicingInfo", slicingInfo);
		
		BasicDBList indexList = new BasicDBList();
		indexList.addAll(bytecodeIndexes);
		rowLog.append("bytecodeIndexes", indexList);
		
		rowLog.append("method", method);
		rowLog.append("methodBytecodeSignatureJoanaStyle", methodBytecodeSignature);
		rowLog.append("isSource", isSource);
		
		bulkInserter.insert(rowLog);
		bulkInserterCounter++;
		
		if(bulkInserterCounter > 1000)
		{
			// Execute first batch and recreate bulk inserter
			executeBulkDetailedLog();
			startBulkDetailedLog();
		}
		
		
	}

	public int getJavaLine(String collectionName, String className, String unit) {
		DBCollection collection = db.getCollection(collectionName);

		BasicDBObject query = new BasicDBObject("Class", className).append(
				"Unit", unit);
		DBCursor cursor = collection.find(query);

		int javaLine = 0;

		try {
			while (cursor.hasNext()) {
				javaLine = (int) cursor.next().get("JavaLineNo");
			}
		} finally {
			cursor.close();
		}

		return javaLine;
	}

	public int getJimpleLine(String collectionName, String className,
			String unit) {
		DBCollection collection = db.getCollection(collectionName);

		BasicDBObject query = new BasicDBObject("Class", className).append(
				"Unit", unit);
		DBCursor cursor = collection.find(query);

		int jimpleLine = 0;

		try {
			while (cursor.hasNext()) {
				jimpleLine = (int) cursor.next().get("JimpleLineNo");
			}
		} finally {
			cursor.close();
		}

		return jimpleLine;
	}

	public String prettyPrintConstraint(IConstraint constraint) {
		if (constraint == Constraint.trueValue()) {
			return "true";
		}
		if (constraint == Constraint.falseValue()) {
			return "false";
		}

		if (!prettyConstraints.containsKey(constraint)) {
			Map<Integer, String> nameMapping = featureNames.getMapping();
			String pretty = constraint.prettyString(nameMapping);
			if (pretty == null) {
				pretty = "Unknown";
			}
			// if(constraint.isUnknown()) {
			// synchronized (Constraint.FACTORY) {
			// BDD bdd = constraint.getBDD();
			// prettyConstraints.put(constraint, "UnknownConstraint (" + (bdd !=
			// null ? bdd.toString() : "") + ")");
			// }
			// } else {
			prettyConstraints.put(constraint, pretty);
			// }
		}
		return prettyConstraints.get(constraint);
	}

	public void saveJimpleFiles(Map<String, StringBuilder> classes, String appPath) {
		for (Entry<String, StringBuilder> jimpleFile : classes.entrySet()) {
			BasicDBObject query = new BasicDBObject("Name", jimpleFile.getKey());
			
			BasicDBObject doc = new BasicDBObject("Name", jimpleFile.getKey())
					.append("Content", jimpleFile.getValue().toString())
					.append("appPath", appPath);
			jimpleCollection.update(query, doc, true, false,
					WriteConcern.NORMAL);
		}
	}

	public String getJimpleSource(String className) {
		BasicDBObject query = new BasicDBObject("Name", className);
		DBObject document = jimpleCollection.findOne(query);
		String content = null;
		if(document != null)
		{
			content = (String) document.get("Content");
		}
		return content;
	}

	public void close() {
		mongoClient.close();
		mongoClient = null;
	}

	public boolean isDone(String configName) {
		if(db.collectionExists(configName))
		{
			DBCollection col = db.getCollection(configName);
			DBCursor cursor = col.find();
			while (cursor.hasNext()) {		
				DBObject row = cursor.next();
				Integer version = (Integer) row.get("version");
				if(version != null && version > 1) {
					return true;
				} else {
					return false;
				}
			}
		}
		return false;
	}

	public void skip(String configName) {
		logProgress(configName, "skip");

	}

	public ConstraintShare getConstraintShare(String collectionName) {
		DBCollection col = db.getCollection(collectionName);
	
		double lines = 0;
		double withConstraint = 0;
		
		DBCursor cursor = col.find();
		while (cursor.hasNext()) {		
			DBObject row = cursor.next();
			String constraint = (String) row.get("Constraint");
			
			if(!constraint.equals("true")) {
				withConstraint++;
			}
			lines++;
		}

		return new ConstraintShare(lines, withConstraint/lines);
	}
	
	public List<String> usedOptions(String collectionName)
	{
		DBCollection collection = db.getCollection(collectionName);
		List<String> options = (List<String>) collection.distinct("Option");
		return options;
	}

	public void clearResult(String collectionName) {
		if (db.collectionExists(collectionName)) {
			db.getCollection(collectionName).drop();
		}

	}

	public int getJimpleLineCount(String collectionName) {
		int count = 0;
	
		Config conf = ConfigFactory.load().getConfig(collectionName);
		
		BasicDBObject query = new BasicDBObject("appPath", conf.getString("apk"));
		DBCursor cursor = db.getCollection("JimpleFiles").find(query);
		
		try {
			while (cursor.hasNext()) {
				String content = (String) cursor.next().get("Content");
				for(String line : content.split("\n")) {
					if(!line.equals("")) {
						count++;
					}
				}
			}
		} finally {
			cursor.close();
		}
		

		return count;
	}

	public Map<String, Long> getConstraintCount(String collectionName) {
		List<String> constraints = null;
		
		Map<String, Long> result = new HashMap<String, Long>();
		
		if (db.collectionExists(collectionName)) {
			DBCollection col = db.getCollection(collectionName);
			constraints = (List<String>) col.distinct("ConstraintPretty");
			
			for(String constraint : constraints)
			{
				BasicDBObject query = new BasicDBObject("ConstraintPretty", constraint);
				result.put(constraint, col.count(query));
				
			}
			
		}
		
		return result;
	}

	public Map<String, Integer> getConstraintsPerApp(String collectionName) {
		List<String> constraints = null;
		
		Map<String, Integer> result = new HashMap<String, Integer>();
		
		if (db.collectionExists(collectionName)) {
			DBCollection col = db.getCollection(collectionName);
			constraints = (List<String>) col.distinct("ConstraintPretty");
			
			for(String constraint : constraints)
			{
				result.put(constraint, 1);
			}
		}
		
		return result;
	}
	
	public Map<String,Integer> constraintAnalysis(String collectionName)
	{
		if(!db.collectionExists(collectionName))
		{
			throw new IllegalArgumentException("Collection " + collectionName + " not found.");
		}
		DBCollection col = db.getCollection(collectionName);
		DBCursor cursor = col.find(new BasicDBObject("Constraint", new BasicDBObject("$ne", "true")));
		
		int total = 0;
		int preciseOnly = 0;
		int impreciseOnly = 0;
		int mix = 0;
		
		Map<String,Integer> results = new HashMap<>();
		
		/*
		 * The database currently only keeps the first 100 characters of the constraint.
		 * Therefore, our results may not be correct regarding "contains only one kind of terms" and 
		 * we must not count the terms
		 */
		while (cursor.hasNext()) {		
			DBObject row = cursor.next();
			String constraint = (String) row.get("Constraint");
			
			boolean containsPreciseTerms = precise.matcher(constraint).find();
			boolean containsImpreciseTerms = imprecise.matcher(constraint).find();
			
			if(containsPreciseTerms && !containsImpreciseTerms)
			{
				preciseOnly++;
			}
			if(containsImpreciseTerms && !containsPreciseTerms)
			{
				impreciseOnly++;
			}
			if(containsPreciseTerms && containsImpreciseTerms) {
				mix++;
			}
			total++;
		}
		
		results.put("preciseOnly", preciseOnly);
		results.put("mix", mix);
		results.put("impreciseOnly", impreciseOnly);
		results.put("total", total);
		
		System.out.println("ConstraintAnalysis: preicise only " + preciseOnly + ", mix " + mix + ", imprecise only " + impreciseOnly + ", total " + total);
		
		return results;
	}
	
	public Set<String> getTerms(String constraint)
	{
		Set<String> optionsUsed = new HashSet<>();

		Matcher m = precise.matcher(constraint);
		while(m.find())
		{
			String option = m.group();
			optionsUsed.add(option);
		}
		m = imprecise.matcher(constraint);
		while(m.find())
		{
			String option = m.group(1);
			if(!option.equals("IncompleteSymb")) {
				optionsUsed.add(option);
			}
		}
		return optionsUsed;
	}
	
	public Set<Set<String>> constraintAnalysisTerms(String collectionName)
	{
		if(!db.collectionExists(collectionName))
		{
			throw new IllegalArgumentException("Collection " + collectionName + " not found.");
		}
		DBCollection col = db.getCollection(collectionName);
		DBCursor cursor = col.find(new BasicDBObject("Constraint", new BasicDBObject("$ne", "true")));
	
		Set<Set<String>> termCombinations = new HashSet<>();
		while (cursor.hasNext()) {		
			DBObject row = cursor.next();
			
			BasicDBList usedTerms = (BasicDBList) row.get("usedTerms");
			if(usedTerms != null)
			{
				// Since analysis version 2
				Set<String> optionsUsed = new HashSet<>();
				for(int i = 0; i < usedTerms.size(); i++)
				{
					optionsUsed.add((String)usedTerms.get(i));
				}
				termCombinations.add(optionsUsed);
			} else {
				String constraint = (String) row.get("Constraint");
				Set<String> optionsUsed = getTerms(constraint);
				termCombinations.add(optionsUsed);
			}
		}
		return termCombinations;
	}
	
	public Map<Integer,Integer> constraintAnalysisInteractions(String collectionName)
	{
		if(!db.collectionExists(collectionName))
		{
			throw new IllegalArgumentException("Collection " + collectionName + " not found.");
		}
		DBCollection col = db.getCollection(collectionName);
		DBCursor cursor = col.find(new BasicDBObject("Constraint", new BasicDBObject("$ne", "true")));
	
		Map<Integer,Integer> interactionLevelToCount = new HashMap<>();
		while (cursor.hasNext()) {		
			DBObject row = cursor.next();
			
			BasicDBList usedTerms = (BasicDBList) row.get("usedTerms");
			Set<String> optionsUsed = new HashSet<>();
			for(int i = 0; i < usedTerms.size(); i++)
			{
				optionsUsed.add((String)usedTerms.get(i));
			}
			interactionLevelToCount.merge(optionsUsed.size(), 1, (oldValue, newValue) -> oldValue + newValue);
		}
		return interactionLevelToCount;
	}
	
	
	public void clearLogCall()
	{
		DBCollection collection = db.getCollection("callLog");
		// clear collection
		collection.remove(new BasicDBObject());
	}
	
	public void logCall(String src, String method) {
		DBCollection collection = db.getCollection("callLog");

		BasicDBObject rowLog = new BasicDBObject();
		rowLog.append("src", src);
		rowLog.append("method", method);

		collection.insert(rowLog);
	}

	public void clearJumpFn() {
		DBCollection collection = db.getCollection("jumpFn");
		// clear collection
		collection.remove(new BasicDBObject());
		
	}

	public void saveJumpFnEntry(Unit target, Abstraction factAtSource,
			Abstraction factAtTarget) {
		DBCollection collection = db.getCollection("jumpFn");

		BasicDBObject rowLog = new BasicDBObject();
		rowLog.append("target", target.toString());
		rowLog.append("factAtSource", factAtSource.toString());
		rowLog.append("factAtTarget", factAtTarget.toString());
		
		collection.insert(rowLog);
		
	}

	public void saveUsedFeatures(String appPath, String appName, List<String> usedFeatures) {
		DBCollection collection = db.getCollection("usedFeatures");
		
		// Remove old entries
		collection.remove(new BasicDBObject().append("app", appPath));
				
		BasicDBObject rowLog = new BasicDBObject();
		rowLog.append("app", appPath);
		rowLog.append("appName", appName);
		rowLog.append("usedFeatures", usedFeatures.toArray(new String[0]));
		
		collection.insert(rowLog);
	}

	public Collection<String> getExistingUsedFeatureApps() {
		DBCollection collection = db.getCollection("usedFeatures");
		return (List<String>) collection.distinct("app");
	}

	public void compareJoana() {
		runCompare("UnitTest1");
		runCompare("UnitTest2");
		runCompare("UnitTest3");
		runCompare("UnitTest4");
		runCompare("UnitTest5");
		runCompare("UnitTest6");
		runCompare("UnitTest7");
		runCompare("UnitTest8");
		runCompare("UnitTest9");
		runCompare("UnitTest10");
		runCompare("UnitTest11");
	}

	private void runCompare(String app) {
		// Seach detailedLog for "app" = app, bytecodeIndex != -1. Get Line, slicingInfo, methodBytecodeSignatureJoanaStyle, bytecodeIndex and value.
		DBCollection detailedLog = db.getCollection("detailedLog");
		DBCollection joanaResult = db.getCollection("joanaResult");
		
		BasicDBObject query = new BasicDBObject("app", app);
		DBCursor cursor = detailedLog.find(query);
		
		int inConfigMapCount = 0;
		int inLotrackSliceCount = 0;
		int inJoanaSliceCount = 0;
		
		try {
			while (cursor.hasNext()) {
				
				DBObject row = cursor.next();
				int bytecodeIndex = (int) row.get("bytecodeIndex");
				String methodBytecode = (String) row.get("methodBytecodeSignatureJoanaStyle");
				
				if(bytecodeIndex > -1 && methodBytecode.contains("MaxUnit")) {
					
					int line = (int) row.get("line");
					boolean inLotrackSlice = ((String) row.get("slicingInfo")).isEmpty();
					boolean inConfigMap = ((String) row.get("value")).equals("true");
					
					boolean inJoanaSlice = false;
					
					// In joanaResult, search for "ProjectName"=app, BCIndex = bytecodeIndex. Note if there is a match or not
					BasicDBObject queryJoana = new BasicDBObject("ProjectName", app).append("BCIndex", bytecodeIndex).append("bcMethod", methodBytecode);
					DBCursor cursorJoana = joanaResult.find(queryJoana);
					try {
						while (cursorJoana.hasNext()) {
							DBObject rowJoana = cursorJoana.next();
							inJoanaSlice = true;
						}
					} finally {
						cursorJoana.close();
					}
					
					if(inConfigMap)
					{
						inConfigMapCount++;
					}
					if(inLotrackSlice)
					{
						inLotrackSliceCount++;
					}
					if(inJoanaSlice)
					{
						inJoanaSliceCount++;
					}
				}
				
			}
		} finally {
			cursor.close();
		}
		
		System.out.println("App " + app + " inConfigMapCount " + inConfigMapCount + " inLotrackSliceCount " + inLotrackSliceCount + " inJoanaSliceCount " + inJoanaSliceCount);
	}
	
	public void slicingEvaluation(String projectName)
	{
		// Lotrack collection
		MongoDatabase database = mongoClient.getDatabase("loadtime");
		MongoCollection<Document> lotrackCollection = database.getCollection(projectName);
		// joana results collection
		MongoCollection<Document> joana = database.getCollection("joanaResult");
		
		// config map size
//		long configMapSize = lotrackCollection.count(ne("ConstraintPretty", "true"));
//		System.out.println("config map size = " + configMapSize);
		
		// slice size
//		long sliceSize = joana.count(eq("ProjectName", projectName));
//		System.out.println("Slice size = " + sliceSize);
		
		// Try mapping
		
		AtomicInteger successfulMapping = new AtomicInteger();
		AtomicInteger failedMapping = new AtomicInteger();
		
		AtomicInteger hasConstraintCount = new AtomicInteger();
		AtomicInteger hasNoConstraintCount = new AtomicInteger();
		
		
		HashSet<ObjectId> mongoDocumentsSeen = new HashSet<>();
		HashSet<BytecodeID> slicesSeen = new HashSet<>();
		
		FindIterable<Document> iterable = joana.find(eq("ProjectName", projectName));
		iterable.forEach(new Block<Document>() {
			
			@Override
			public void apply(Document document) {
				String bcMethod = document.getString("bcMethod");
				int bcIndex = document.getInteger("BCIndex");
				
				// We count each instruction once, even if it is contained in multiple slices (for different options)
				BytecodeID bytecodeID = new BytecodeID(bcMethod, bcIndex);
				
				if(bcIndex > -1 && slicesSeen.add(bytecodeID)) {
					// Lookup in config map
					FindIterable<Document> lotrackIterable = lotrackCollection.find(and(in("bytecodeIndexes", bcIndex), eq("methodBytecodeSignatureJoanaStyle", bcMethod)));
					Document d = lotrackIterable.first();
					if(d == null)
					{
						// No mapping
						int i = failedMapping.incrementAndGet();
						if(i < 10)
						{
//							System.out.println("Could not match method " + bcMethod + " index " + bcIndex);
						}
						
					} else {
						// mapping
						successfulMapping.incrementAndGet();
						boolean hasConstraint = !d.getString("ConstraintPretty").equals("true");
						
						ObjectId id = (ObjectId)d.get("_id"); 
						if(mongoDocumentsSeen.add(id))
						{
							if(hasConstraint)
							{
								hasConstraintCount.incrementAndGet();
							} else {
								hasNoConstraintCount.incrementAndGet();
							}
						}
					}
				}
				
			}
		});
		
		System.out.println("mapping nodes " + successfulMapping.get() + " successful " + failedMapping.get() + " failed.");
		System.out.println("Of successfully mapped nodes " + hasConstraintCount.get() + " have a constraint and " + hasNoConstraintCount + " have the trivial constraint.");
		
	}
	
}
