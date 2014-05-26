package soot.jimple.infoflow.loadtime;

import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;

import net.sf.javabdd.BDD;

import com.google.common.collect.Table;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.WriteConcern;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import soot.SootClass;
import soot.Unit;
import soot.jimple.infoflow.LoadTimeInfoflow;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.SPLIFDSSolver;

public class MongoLoader implements AutoCloseable {

	MongoClient mongoClient;
	DB db;

	Map<SootClass, String> javaSourceCode = new ConcurrentHashMap<SootClass, String>();

	Map<Constraint<String>, String> prettyConstraints = new ConcurrentHashMap<Constraint<String>, String>();
	FeatureNames featureNames;

	DBCollection jimpleCollection;

	public MongoLoader() {
		try {
			mongoClient = new MongoClient();
			db = mongoClient.getDB("loadtime");
			jimpleCollection = db.getCollection("JimpleFiles");
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void saveResults(LoadTimeInfoflow infoflow, String collectionName,
			String basePath, int knownFeaturesOffset) {
		featureNames = new FeatureNames(collectionName);

		DBCollection collection = db.getCollection(collectionName);
		DBCollection log = db.getCollection("log");
		// clear collection
		collection.remove(new BasicDBObject());

		Table<Unit, Abstraction, Constraint<String>> res = infoflow
				.getSplResults();

		SPLIFDSSolver<Abstraction, AccessPath> splSolver = infoflow
				.getSPLSolver();

		List<DBObject> objects = Collections
				.synchronizedList(new LinkedList<DBObject>());
		List<DBObject> logObjects = Collections
				.synchronizedList(new LinkedList<DBObject>());

		ThreadPoolExecutor executor = new ThreadPoolExecutor(4, 4, 60,
				TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),
				new ThreadPoolExecutor.CallerRunsPolicy());

		for (Unit unit : res.rowKeySet()) {

			SaveToMongoTask task = new SaveToMongoTask(infoflow, basePath,
					splSolver, knownFeaturesOffset, prettyConstraints,
					featureNames, unit, this, logObjects, objects);

			executor.execute(task);
		}

		executor.shutdown();

		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		collection.insert(objects);
		log.insert(logObjects);
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

	public String prettyPrintConstraint(Constraint<String> constraint) {
		if (constraint == Constraint.<String> trueValue()) {
			return "true";
		}
		if (constraint == Constraint.<String> falseValue()) {
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
					.append("appPath", FilenameUtils.getName(appPath));
			jimpleCollection.update(query, doc, true, false,
					WriteConcern.NORMAL);
		}
	}

	public String getJimpleSource(String className) {
		BasicDBObject query = new BasicDBObject("Name", className);
		String content = (String) jimpleCollection.findOne(query)
				.get("Content");
		return content;
	}

	public void close() {
		mongoClient.close();
		mongoClient = null;
	}

	public boolean isDone(String configName) {
		return db.collectionExists(configName);
	}

	public void skip(String configName) {
		logProgress(configName, "skip");

	}

	public ConstraintShare getConstraintShare(String collectionName) {
		CommandResult cmdResult = db.doEval("constraintShare('"
				+ collectionName + "')");

		DBCollection constraintShares = db.getCollection("constraintShares");
		BasicDBObject query = new BasicDBObject("_id", collectionName);
		DBObject result = constraintShares.findOne(query);
		if (result == null) {
			return null;
		}
		double total = (double) ((DBObject) result.get("value")).get("total");
		double constraint = (double) ((DBObject) result.get("value")).get("constraint");
		
		int totalLines = getJimpleLineCount(collectionName);
		double share = constraint / totalLines;

		return new ConstraintShare(totalLines, share);

	}

	public void clearResult(String collectionName) {
		if (db.collectionExists(collectionName)) {
			db.getCollection(collectionName).drop();
		}

	}

	public int getJimpleLineCount(String collectionName) {
		List<String> classes = null;

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
}
