package soot.spl.ifds;

import soot.Unit;
import soot.tagkit.Host;
import heros.EdgeFunction;
import heros.edgefunc.AllTop;
import heros.edgefunc.EdgeIdentity;

public class SPLFeatureFunction implements EdgeFunction<Constraint<String>> {
	
	protected final Constraint<String> features;
	
	public Constraint<String> getFeatures() {
		return features;
	}

	public SPLFeatureFunction(Constraint<String> features){
		this.features = features;
	} 
	
	public Constraint<String> computeTarget(Constraint<String> source) {
		Constraint<String> conjunction = source.and(features);
		return conjunction;
		//return conjunction.considerFeatureModel(fmContext);
	}

	public EdgeFunction<Constraint<String>> composeWith(EdgeFunction<Constraint<String>> secondFunction) {
		if(secondFunction instanceof EdgeIdentity || secondFunction instanceof AllTop) return this;
		
		SPLFeatureFunction other = (SPLFeatureFunction)secondFunction;
		
		return new SPLFeatureFunction(features.and(other.features));
	}

	public EdgeFunction<Constraint<String>> joinWith(EdgeFunction<Constraint<String>> otherFunction) {
		//here we implement union/"or" semantics
		if(otherFunction instanceof AllTop) return this;
		if(otherFunction instanceof EdgeIdentity) return otherFunction;

		SPLFeatureFunction other = (SPLFeatureFunction)otherFunction;
		return new SPLFeatureFunction(features.or(other.features));
	}
	
	public boolean equalTo(EdgeFunction<Constraint<String>> other) {
		if(other instanceof SPLFeatureFunction) {
			SPLFeatureFunction function = (SPLFeatureFunction) other;
			return function.features.equals(features);
		}
		return false;
	}

	public String toString() {
		return features.toString();
	}


}
