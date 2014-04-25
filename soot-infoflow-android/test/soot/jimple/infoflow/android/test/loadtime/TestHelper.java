package soot.jimple.infoflow.android.test.loadtime;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map.Entry;

import org.junit.Assert;

import soot.jimple.infoflow.loadtime.MongoLoader;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

public class TestHelper {
	
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
	
	public void checkResults(Config conf, String collectionName)
	{
		List<? extends Config> expectedResults = conf.getConfigList("expectedResults");
		
		for(Config resultForClass : expectedResults) {
			
			String className = resultForClass.getString("className");
			
			List<? extends Config> constraints = resultForClass.getConfigList("constraints");
			for(Config constraintElement : constraints)
			{
				int jimpleLine = constraintElement.getInt("jimpleLine");
				String expectedConstraint = constraintElement.getString("constraint");
				
				String constraint = mongo.getConstraint(collectionName, className, jimpleLine);
				Assert.assertEquals("Line " + jimpleLine, expectedConstraint, constraint);
			}
		}
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
