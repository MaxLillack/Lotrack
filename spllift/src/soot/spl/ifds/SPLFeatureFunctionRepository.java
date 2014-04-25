package soot.spl.ifds;

import java.util.HashMap;

public class SPLFeatureFunctionRepository {
	private HashMap<Constraint<String>, SPLFeatureFunction> functions = new HashMap<Constraint<String>, SPLFeatureFunction>();
	
	public SPLFeatureFunction make(Constraint<String> source)
	{
		if(!functions.containsKey(source)) {
			functions.put(source, new SPLFeatureFunction(source));
		}
		return functions.get(source);
	}
}
