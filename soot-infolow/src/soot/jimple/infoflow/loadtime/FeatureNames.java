package soot.jimple.infoflow.loadtime;

import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

public class FeatureNames {

	Map<Integer, String> featureNames = new ConcurrentHashMap<Integer,String>();
	
	public FeatureNames(String configName) {
		Config conf = ConfigFactory.load().getConfig(configName);
		Config features = conf.getConfig("features");
		
		for(Entry<String, ConfigValue> featureConfig : features.root().entrySet())
		{
			String name = featureConfig.getKey();
			int index = features.getConfig(name).getInt("index");
			featureNames.put(index, name);
		}
	}

	public Map<Integer, String> getMapping() {
		return featureNames;
	}
}
