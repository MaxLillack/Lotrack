package soot.spl.ifds;

import java.util.HashMap;

public class SPLFeatureFunctionRepository {
	private HashMap<IConstraint, SPLFeatureFunction> functions = new HashMap<IConstraint, SPLFeatureFunction>();
	
	public SPLFeatureFunction make(IConstraint source)
	{
		if(!functions.containsKey(source)) {
			functions.put(source, new SPLFeatureFunction(source));
		}
		return functions.get(source);
	}
}
