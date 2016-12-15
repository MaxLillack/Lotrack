package soot.spl.ifds;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Z3Exception;

import soot.spl.ifds.SolverOperation.Operator;
import soot.util.NumberedString;

public class Constraint implements Cloneable, IConstraint {
	
	public static Set<Integer> trackPrecise;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private final static IConstraint FALSE = new ConstraintFalse();
	private final static IConstraint TRUE = new ConstraintTrue();
	
	private String Z3Constraint;
	
	private Constraint(BitSet elems, int value) {
//		synchronized (FACTORY) {
//			BDD curr = FACTORY.one();
//			if(!elems.isEmpty()) {
//				for(int i=elems.nextSetBit(0); i>=0; i=elems.nextSetBit(i+1)) {
//					BDD ithVar = FACTORY.ithVar(i);
//					curr = curr.andWith(ithVar);
//				}
//			} else
//				curr = curr.not(); //no elements provided; assume the "FALSE" constraint
//			if(positive) 
//				bdd = curr;
//			else
//				bdd = curr.not();
//			if(bdd.getFactory() != FACTORY) {
//				logger.error("BDDFactory mismatch");
//			}
//		}
//		

		Z3Constraint = CachedZ3Solver.initializeConstraint(elems, value, trackPrecise);		
	}
//	
//	private Constraint(BitSet elems, Set<NumberedString> featureDomain) {
//		synchronized (FACTORY) {
//			BDD curr = FACTORY.one();
//			for(NumberedString feature: featureDomain) {
//				int i = feature.getNumber();
//				BDD ithVar = FACTORY.ithVar(i);
//				if(!elems.get(i)) {
//					ithVar = ithVar.not();
//				}
//				curr = curr.andWith(ithVar);
//			}
//			bdd = curr;
//			if(bdd.getFactory() != FACTORY) {
//				logger.error("BDDFactory mismatch");
//			}
//		}
//	}
	
	
	/**
	 * Constructs a <i>full</i> constraint in the sense that all variables mentioned in
	 * featureDomain but not mentioned in elems will be automatically negated.
	 * If the domain is {a,b,c} and elems is {b} then this will construct the
	 * constraint !a && b && !c.
	 */
//	public static Constraint make(BitSet elems, Set<NumberedString> featureDomain) {
//		return new Constraint(elems,featureDomain);
//	}

	/**
	 * If positive is true then for elems={a,b} this constructs a constraint
	 * a && b. Otherwise, this constructs a constraint !(a && b).
	 * A constraint of the form a && b does not say anything at all about variables
	 * not mentioned. In particular, a && b is not the same as a && b && !c.
	 */
	public static Constraint make(BitSet elems, int value) {
		if(elems.isEmpty()) throw new RuntimeException("empty constraint!");
		return new Constraint(elems, value);
	}
	
//	public synchronized static IConstraint make(BDD bdd) {
////		synchronized (FACTORY) {
//			if(bdd.isOne())
//				return Constraint.trueValue();
//			else if(bdd.isZero())
//				return Constraint.falseValue();
//			else {
//				return new Constraint(bdd, null);
//			}
////		}
//	}
	
	public Constraint(String Z3Constraint) {
//		if(bdd != null && bdd.getFactory() != FACTORY) {
//			logger.error("BDDFactory mismatch");
//			this.bdd = null;
//		} else {
//			this.bdd = bdd;
			if(Z3Constraint.startsWith("(null)"))
			{
				throw new IllegalArgumentException("Invalid constraint " + Z3Constraint);
			}
			this.Z3Constraint = Z3Constraint;
//		}
			
	}
	
	/**
	 * Computes the constraint representing this OR other.
	 * The constraint is automatically reduced such that
	 * a || !a results in true.
	 * @see Constraint#trueValue()
	 */
	@Override
	public synchronized IConstraint or(IConstraint other) {		
		
		// Z3 based implementation
		// new Context, import both boolExpr, combine with or
//		Context ctxResult = null;
//		Solver solverResult = null;
//		try {
//			ctxResult = new Context();
//			solverResult = ctxResult.mkSolver();
//			
//			if(solver.getNumAssertions() == 0)
//			{
//				throw new RuntimeException("Empty current constraint");
//			}
//			
//			if(solver.getNumAssertions() > 1) {
//				throw new RuntimeException("Unexpected number of assertion in current solver");
//			}
//			
//			// import first
//			BoolExpr first = (BoolExpr) solver.getAssertions()[0].translate(ctxResult);
//			
//			// import second
//			BoolExpr second;
//			
//			if(other == trueValue()) {
//				second = ctxResult.mkBool(true);
//			} else if(other == falseValue())
//			{
//				second = ctxResult.mkBool(false);
//			} else {
//				
//				if(other.getSolver() == null)
//				{
//					throw new RuntimeException("Other constraint is null");
//				}
//			
//				if(other.getSolver().getNumAssertions() == 0)
//				{
//					throw new RuntimeException("Empty other constraint");
//				}
//				
//				if(other.getSolver().getNumAssertions() > 1) {
//					throw new RuntimeException("Unexpected number of assertion in other solver");
//				}
//				
//				second = (BoolExpr) other.getSolver().getAssertions()[0].translate(ctxResult);
//			}
//			
//			solverResult.add((BoolExpr)ctxResult.mkOr(first, second).simplify());
//			
//			int a = 0;
//			
//
//		} catch (Z3Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		String z3Constraint = null;
		// Optimization: A || A <=> A
		if(Z3Constraint.equals(other.getZ3Constraint()))
		{
			z3Constraint = Z3Constraint;
		} else {
			z3Constraint = CachedZ3Solver.solve(new SolverOperation(Z3Constraint, other.getZ3Constraint(), Operator.OR));
		}
//		synchronized (FACTORY) {
//			if(other==trueValue()) return other;
//			if(other==falseValue()) return this;
//			
//			BDD disjunction = bdd.or(other.getBDD());
//			if(disjunction.isOne()) {				
//				return trueValue();
//			}
//			else {
//				return new Constraint(disjunction, z3Constraint);
		return new Constraint(z3Constraint);
//			}
//		}
		

		
	}
	
//	public Solver getSolver() {
//		return solver;
//	}

	public synchronized static IConstraint negate(IConstraint other)
	{		
//		synchronized (FACTORY) {
			if(other==trueValue()) return falseValue();
			if(other==falseValue()) return trueValue();
//			
//			BDD negation = other.getBDD().not();
//			if(negation.isOne()) 
//				return trueValue();
//			else if(negation.isZero()) 
//				return falseValue();
//			else {

				
				String z3Constraint = CachedZ3Solver.solve(new SolverOperation(other.getZ3Constraint(), Operator.NEGATE));
				
//				return new Constraint(negation, z3Constraint);
				return new Constraint(z3Constraint);
//			}
//		}
	}
	
	/**
	 * Computes the constraint representing this AND other.
	 * The constraint is automatically reduced such that
	 * a && !a results in false.
	 * @see Constraint#falseValue()
	 */
	@Override
	public synchronized IConstraint and(IConstraint other) {		
//		synchronized (FACTORY) {
			if(other==trueValue()) return this;
			if(other==falseValue()) return other;
			// A && A <=> A
//			if(this.equals(other)) return this;
			
//
//					
//			BDD conjunction = bdd.and(other.getBDD());
//			if(conjunction.isZero()) {
				// In case of non-value tracking (imprecise tracking) we apply the rule A && !A => !A
				// This is important in case of nested conditions:
				/*
				 * if(A)
				 *   --> A
				 *   if(A)
				 *     --> A
				 *   else
				 *     --> A && !A => !A --> Would otherwise be false i.e., not reachable
				 * else
				 *   --> !A
				 */
//				for(int b : bdd.scanSet())
//				{
//					if(!trackPrecise.contains(b))
//					{
//						boolean inOther = false;
//						for(int o : other.bdd.scanSet()) {
//							if(o == b) inOther = true;
//						}
//						if(inOther) {
//							return new Constraint<T>(other.bdd);
//						}
//					}
//				}
				
//				return falseValue();
//			} else {
//	
				String z3Constraint = CachedZ3Solver.solve(new SolverOperation(Z3Constraint, other.getZ3Constraint(), Operator.AND));
				
				IConstraint result = null;
				if(z3Constraint.equals("false")) {
					result = falseValue();
				} else if (z3Constraint.equals("true")) {
					result = trueValue();
				} else {
					result = new Constraint(z3Constraint);
				}
				
				return result;
				
//			}
//		}
	}
	
	@Override
	public String toString() {
//		String result = null;
//		
//		synchronized (FACTORY) {
//			result =  bdd.toString();
//		}
//		
		String resultZ3 = CachedZ3Solver.getPrettyprinted(Z3Constraint);
		
//		if(!result.equals(resultZ3))
//		{
//			boolean ok = false;
//			
//			// check exceptions
//			if(result.equals("<0:0, 1:1><0:1>") && resultZ3.equals("<0:1><1:1>")) {
//				ok = true;
//			}
//			
//			if(!ok) {
//				throw new RuntimeException("orginal result was " + result + " Z3-based is " + resultZ3);
//			}
//		}
//		
		return resultZ3;
	}

//	@Override
//	public String toString(Map<Integer,String> featureNumberer) {
//		synchronized (FACTORY) {
//			StringBuilder sb = new StringBuilder();
//	        int[] set = new int[FACTORY.varNum()];
//			toStringRecurse(FACTORY, sb, bdd, set, featureNumberer);
//			return sb.toString();
//		}
//	}
	
//	private static void toStringRecurse(BDDFactory f, StringBuilder sb, BDD r, int[] set,
//			Map<Integer,String> featureNumberer) {
//		synchronized (FACTORY) {
//			int n;
//			boolean first;
//	
//			if (r.isZero())
//				return;
//			else if (r.isOne()) {
//				sb.append("{");
//				first = true;
//	
//				for (n = 0; n < set.length; n++) {
//					if (set[n] > 0) {
//						if (!first)
//							sb.append(" ^ ");
//						first = false;
//						if (set[n] != 2) {
//							sb.append("!");
//						}
//						sb.append(featureNumberer.get(f.level2Var(n)));
//					}
//				}
//				sb.append("} ");
//			} else {
//				set[f.var2Level(r.var())] = 1;
//				BDD rl = r.low();
//				toStringRecurse(f, sb, rl, set, featureNumberer);
//				rl.free();
//	
//				set[f.var2Level(r.var())] = 2;
//				BDD rh = r.high();
//				toStringRecurse(f, sb, rh, set, featureNumberer);
//				rh.free();
//	
//				set[f.var2Level(r.var())] = 0;
//			}
//		}
//	}
	
	public static IConstraint trueValue() {
		return TRUE;
	}

	public static IConstraint falseValue() {
		return FALSE;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((Z3Constraint == null) ? 0 : Z3Constraint.hashCode());
		return result;
	}

//	@Override
//	public boolean equals(Object obj) {
//		synchronized (FACTORY) {
//			if (this == obj)
//				return true;
//			if (obj == null)
//				return false;
//			if (getClass() != obj.getClass())
//				return false;
//			Constraint other = (Constraint) obj;
//			if (bdd == null) {
//				if (other.bdd != null)
//					return false;
//			} else if (!bdd.equals(other.bdd))
//				return false;
//			return true;
//		}
//	}

//	protected Constraint<T> exists(NumberedString varToQuantify) {
//		synchronized (FACTORY) {
//			return make(bdd.exist(FACTORY.one().andWith(FACTORY.ithVar(varToQuantify.getNumber()))));
//		}
//	}
//	
//	public Constraint<T> simplify(Iterable<NumberedString> allFeatures, Collection<NumberedString> usedFeatures) {
//		Constraint<T> fmConstraint = this;
//		for (NumberedString feature : allFeatures) {
//			if(!usedFeatures.contains(feature)) {
//				fmConstraint = fmConstraint.exists(feature);
//			}
//		}
//		return fmConstraint;
//	}
//	
//	public int size() {
//		synchronized (FACTORY) {
//			return bdd.nodeCount();
//		}
//	}
	
	@Override
	public String prettyString(Map<Integer,String> bddToString)
	{
		if(this == trueValue())
			return "true";
		if(this == falseValue())
			return "false";
////		if(bdd == null)
////			return null;
//		
		// @TODO
		return toString();
	}
//	
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if(obj instanceof ConstraintTrue) {
			return Z3Constraint.equals("true") || CachedZ3Solver.constraintEquals(Z3Constraint, "true");
		}
		if(obj instanceof ConstraintFalse) {
			return Z3Constraint.equals("false") || CachedZ3Solver.constraintEquals(Z3Constraint, "false");
		}
		if (getClass() != obj.getClass())
			return false;
		Constraint other = (Constraint) obj;
		if (Z3Constraint == null) {
			if (other.Z3Constraint != null)
				return false;
		} else if (!Z3Constraint.equals(other.Z3Constraint) && !CachedZ3Solver.constraintEquals(Z3Constraint, other.Z3Constraint))
			return false;
		return true;
	}


	// Shows wheater constraint is the fixed ConstraintTrue or ConstraintFalse
	// isConstFalse() and isConstTrue is overwritten by subclasses
	@Override
	public boolean isConstFalse()
	{
		return false;
	}
	
	@Override
	public boolean isConstTrue()
	{
		return false;
	}

	@Override
	public String getZ3Constraint() {
		return Z3Constraint;
	}

	public static IConstraint intConstraint(String op1, String op2, String operator) {
		return new Constraint("(" + operator + " " + op1 + " " + op2 + ")");
	}

	public static IConstraint intConstraint(String op1, int op2, String operator) {
		assert !operator.equals("|-1|");
		return new Constraint("(" + operator + " " + op1 + " " + op2 + ")");
	}

	public static IConstraint intConstraint(int op1, int op2, String operator) {
		return new Constraint("(" + operator + " " + op1 + " " + op2 + ")");
	}	
	
	public static IConstraint impreciseConstraint(String opName) {
		return new Constraint(opName);
	}

}
