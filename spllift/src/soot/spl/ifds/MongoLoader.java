package soot.spl.ifds;

import org.bson.Document;

import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

public class MongoLoader implements AutoCloseable {
	private MongoClient mongoClient;
	private MongoCollection<Document> collection;
	
	public class CacheResult
	{
		private String result;
		private String pretty;
		
		public CacheResult(String result, String pretty) {
			this.result = result;
			this.pretty = pretty;
		}
		public String getResult() {
			return result;
		}
		public String getPretty() {
			return pretty;
		}
		
	}
	
	public MongoLoader()
	{
		mongoClient = new MongoClient();
		MongoDatabase database = mongoClient.getDatabase("loadtime");
		collection = database.getCollection("solverCache");
		
		if(collection.listIndexes().first() == null) {
			collection.createIndex(new Document("key", "hashed"));
		}
	}
	
	public void add(String key, String value, String pretty)
	{
		collection.insertOne(new Document("key", key).append("value", value).append("pretty", pretty));
	}
	
	public CacheResult get(String key)
	{
		FindIterable<Document> iterable = collection.find(new Document("key", key));
		Document first = iterable.first();
		
		if(first != null)
		{
			CacheResult cacheResult = new CacheResult(first.getString("value"), first.getString("pretty"));
			return cacheResult;
		} else {
			return null;
		}
	}
	
	@Override
	public void close() throws Exception {
		mongoClient.close();
	}
}
