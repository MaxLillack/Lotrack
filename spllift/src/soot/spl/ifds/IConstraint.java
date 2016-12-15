package soot.spl.ifds;

import java.util.Map;

import com.microsoft.z3.Solver;

public interface IConstraint {

//	public abstract BDD getBDD();

	/**
	 * Computes the constraint representing this OR other.
	 * The constraint is automatically reduced such that
	 * a || !a results in true.
	 * @see Constraint#trueValue()
	 */
	public abstract IConstraint or(IConstraint other);

	/**
	 * Computes the constraint representing this AND other.
	 * The constraint is automatically reduced such that
	 * a && !a results in false.
	 * @see Constraint#falseValue()
	 */
	public abstract IConstraint and(IConstraint other);

	public abstract String toString();

//	public abstract String toString(Map<Integer, String> featureNumberer);

	public abstract int hashCode();

	public abstract boolean equals(Object obj);

	public abstract String prettyString(Map<Integer, String> bddToString);

	// Shows wheater constraint is the fixed ConstraintTrue or ConstraintFalse
	// isConstFalse() and isConstTrue is overwritten by subclasses
	public abstract boolean isConstFalse();

	public abstract boolean isConstTrue();
	
	public abstract String getZ3Constraint();

}