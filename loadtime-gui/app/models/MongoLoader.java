package models;


import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import play.Logger;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;



public class MongoLoader implements AutoCloseable {
	
	MongoClient mongoClient;
	DB db;
	
	public MongoLoader() throws UnknownHostException
	{
		
		Config conf = ConfigFactory.load();
		String host = conf.getString("mongodb_host");
		int port = conf.getInt("mongodb_port");
		String mongodb_db_name = conf.getString("mongodb_db_name");
		
		ServerAddress mongodbAddress = new ServerAddress(host, port);
		
		
		if(conf.getBoolean("mongodb_use_auth"))
		{
			String user = conf.getString("mongodb_user");
			String password = conf.getString("mongodb_password");
			MongoCredential credential = MongoCredential.createCredential(user, mongodb_db_name, password.toCharArray());
			mongoClient = new MongoClient(mongodbAddress, Arrays.asList(credential));
		} else {
			mongoClient = new MongoClient(mongodbAddress);
		}
		
		
		db = mongoClient.getDB(mongodb_db_name);
	}
	
	public List<DBObject> getResults(String path, String collectionName)
	{

		DBCollection collection = db.getCollection(collectionName);
		
		
		BasicDBObject query = new BasicDBObject("JavaPath", path);

		DBCursor cursor = collection.find(query);
		List<DBObject> results = new ArrayList<DBObject>();
		
		if(!cursor.hasNext()) {
			cursor.close();
			query = new BasicDBObject("JimplePath", path);
			cursor = collection.find(query);
		}
		
		try {
		   while(cursor.hasNext()) {
			   results.add(cursor.next());
		   }
		} finally {
		   cursor.close();
		}
		
		return results;
	}
	
	public List<DBObject> getAllResults(String collectionName)
	{
		DBCollection collection = db.getCollection(collectionName);
		List<DBObject> results = new ArrayList<DBObject>();
		
		DBCursor cursor = collection.find();
		try {
		   while(cursor.hasNext()) {
			   results.add(cursor.next());
		   }
		} finally {
		   cursor.close();
		}
		return results;
	}
	
	public Set<String> getAllCollections()
	{
		return db.getCollectionNames();
	}
	
	public List<LoadTimeResult> getAllLoadTimeResults()
	{
		List<LoadTimeResult> loadTimeResults = new LinkedList<LoadTimeResult>();
		
		for(String collectionName : getAllCollections())
		{
			Map<String, Integer> countConstraints = new HashMap<String, Integer>();
			
			DBCollection collection = db.getCollection(collectionName);
			DBCursor cursor = collection.find();
			try {
			   while(cursor.hasNext()) {
				   DBObject entry = cursor.next();
				   String constraint = (String) entry.get("ConstraintPretty");
					if(!countConstraints.containsKey(constraint)) {
						countConstraints.put(constraint, 1);
					} else {
						countConstraints.put(constraint, countConstraints.get(constraint) + 1);
					}
			   }
			} finally {
			   cursor.close();
			}
			
			for(Entry<String, Integer> entry : countConstraints.entrySet())
			{
				loadTimeResults.add(new LoadTimeResult(collectionName, entry.getKey(), entry.getValue()));
			}
		}
		
		return loadTimeResults;
	}

	public Map<String, String> getJimplePaths(String collectionName) {
		DBCollection collection = db.getCollection(collectionName);
		DBCursor cursor = collection.find();
		Map<String, String> paths = new TreeMap<String, String>();
		try {
		   while(cursor.hasNext()) {
			   DBObject entry = cursor.next();
			   String jimplePath = (String) entry.get("JimplePath");
			   File f = new File(jimplePath);
			   if(!paths.containsKey(jimplePath)) {
				   paths.put(jimplePath, f.getName());
			   }
		   }
		} finally {
		   cursor.close();
		}
		return paths;
	}

	public String getJimpleSource(String className)
	{
		DBCollection jimpleCollection = db.getCollection("JimpleFiles");
		BasicDBObject query = new BasicDBObject("Name", className);
		DBObject result = jimpleCollection.findOne(query);
		String content = null;
		if(result == null) {
			Logger.info("No jimple file for " + className + " found.");
		} else {
			content = (String)result.get("Content");
		}
		return content;
	}
	
	public Map<String, Integer> getResultCount(String collectionName)
	{
		DBCollection collection = db.getCollection(collectionName);
		
		DBObject matchField = new BasicDBObject("Constraint", new BasicDBObject("$ne", "true"));
		DBObject match = new BasicDBObject("$match", matchField);
		
		DBObject groupFields = new BasicDBObject("_id", "$JavaPath");
		groupFields.put("count", new BasicDBObject( "$sum", 1));
		DBObject group = new BasicDBObject("$group", groupFields);
		AggregationOutput output = collection.aggregate(Arrays.asList(match, group));
		
		Map<String, Integer> result = new HashMap<String, Integer>();
		for(DBObject entry : output.results())
		{
			String javaPath = (String) entry.get("_id");
			int count = (int) entry.get("count");
			result.put(javaPath, count);
		}
		return result;
	}
	
	public List<String> getDetailedLog(String app, String className, int lineNumber)
	{
		DBCollection collection = db.getCollection("detailedLog");
		BasicDBObject criteria = new BasicDBObject("className", className).append("line", lineNumber).append("app", app);
		DBCursor cursor = collection.find(criteria);
		
		List<String> result = new ArrayList<String>();
		
		try {
		   while(cursor.hasNext()) {
			   DBObject entry = cursor.next();
			   String column = (String) entry.get("column");
			   String value = (String) entry.get("value");
			   String text = column + " (" + value + ")";
			   text += " SlicingInfo " + (String) entry.get("slicingInfo"); 
			   result.add(text);
		   }
		} finally {
		   cursor.close();
		}
		
		return result;
	}

	public boolean isInSlice(String className, int lineNumber) {
		DBCollection collection = db.getCollection("detailedLog");
		BasicDBObject criteria = new BasicDBObject("className", className).append("line", lineNumber);
		DBCursor cursor = collection.find(criteria);
		
		boolean isInSlice = false;
		
		try {
		   while(cursor.hasNext()) {
			   DBObject entry = cursor.next();
			   if(!((String) entry.get("slicingInfo")).isEmpty())
			   {
				   isInSlice = true;
			   }
		   }
		} finally {
		   cursor.close();
		}
		
		return isInSlice;
	}

	public Set<String> joanaSlice(String projectName, String bcMethodName, BasicDBList bytecodeIndexes) {
		

		DBCollection joanaResult = db.getCollection("joanaResult");

		Set<String> result = new HashSet<>();
			   
	    for(Object bytecodeIndexObj : bytecodeIndexes)
	    {
		   int bytecodeIndex = (int) bytecodeIndexObj;
		   if(bytecodeIndex > -1) {
			   BasicDBObject query = new BasicDBObject("ProjectName", projectName)
												   .append("bcMethod", bcMethodName)
												   .append("BCIndex", bytecodeIndex);
			   
			   DBCursor joanacursor = joanaResult.find(query);
			   try {
				   // TODO - Currently ignores inner classes and method overloading
				   while(joanacursor.hasNext())
				   {
					   DBObject row = joanacursor.next();
					   String option = (String) row.get("Option");
					   result.add(option);
				   }
			   } finally {
				   joanacursor.close();
			   }	
			   
		   }
	    }

		
		// query joana results
		
		return result;
	}

	@Override
	public void close() throws Exception {
		if(mongoClient != null)
		{
			mongoClient.close();
			mongoClient = null;
		}
	}

}
