package models;


import java.io.File;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import play.Logger;

import com.mongodb.AggregationOutput;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;



public class MongoLoader {
	
	MongoClient mongoClient;
	DB db;
	
	public MongoLoader() throws UnknownHostException
	{
		mongoClient = new MongoClient();
		db = mongoClient.getDB("loadtime");
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
		Map<String, String> paths = new HashMap<String, String>();
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
		AggregationOutput output = collection.aggregate(match, group);
		
		Map<String, Integer> result = new HashMap<String, Integer>();
		for(DBObject entry : output.results())
		{
			String javaPath = (String) entry.get("_id");
			int count = (int) entry.get("count");
			result.put(javaPath, count);
		}
		return result;
	}
}
