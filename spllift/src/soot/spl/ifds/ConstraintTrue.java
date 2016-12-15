package soot.spl.ifds;

import java.util.Map;

import com.microsoft.z3.Solver;


public class ConstraintTrue implements IConstraint {

	@Override
	public boolean isConstFalse() {
		return false;
	}
	
	@Override
	public IConstraint and(IConstraint other) {
		//true && other = other
		return other;
	}		

	@Override
	public IConstraint or(IConstraint other) {
		//true || other == true
		return this;
	}		

	@Override
	public String toString() {
		return "true";
	}
	
	@Override
	public int hashCode() {
		return -23214;
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj==this;
	}
	
	public int size() {
		return 0;
	}

//	@Override
//	public BDD getBDD() {
//		// TODO Auto-generated method stub
//		return null;
//	}

//	@Override
//	public String toString(Map<Integer, String> featureNumberer) {
//		return toString();
//	}
//
	@Override
	public String prettyString(Map<Integer, String> bddToString) {
		return toString();
	}

	@Override
	public boolean isConstTrue() {
		return true;
	}

	@Override
	public String getZ3Constraint() {
		return "true";
	}

}
