package soot.spl.ifds;

import java.util.Map;

import com.microsoft.z3.Solver;

public class ConstraintFalse implements IConstraint {

	public ConstraintFalse()
	{
	}
	
	@Override
	public boolean isConstFalse() {
		return true;
	}
	
	@Override
	public IConstraint and(IConstraint other) {
		//false && other = false
		return this;
	}		

	@Override
	public IConstraint or(IConstraint other) {
		//false || other == other
		return other;
	}		

	@Override
	public String toString() {
		return "false";
	}
	
	@Override
	public int hashCode() {
		return -436534;
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

	@Override
	public String prettyString(Map<Integer, String> bddToString) {
		return toString();
	}

	@Override
	public boolean isConstTrue() {
		return false;
	}

	@Override
	public String getZ3Constraint() {
		return "false";
	}

}
