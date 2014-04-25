package soot.spl.ifds;

import static soot.spl.ifds.Constraint.FeatureModelMode.NO_SINGLETON;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDD.BDDIterator;
import net.sf.javabdd.BDD.BDDToString;
import net.sf.javabdd.BDDDomain;
import net.sf.javabdd.BDDFactory;
import net.sf.javabdd.BDDPairing;
import soot.util.NumberedString;
import soot.util.StringNumberer;

public class Constraint<T> implements Cloneable {
	
	public static BDDFactory FACTORY;
	public static Set<Integer> trackPrecise;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
//	private boolean isUnknown = false;

	
//	public boolean isUnknown() {
//		return isUnknown;
//	}

//	public void setUnknown(boolean isUnknown) {
//		this.isUnknown = isUnknown;
//	}

	public enum FeatureModelMode{
		NONE, 			//do not consider the feature model at all
		ALL,			//consider all feature constraints
		NO_SINGLETON	//consider all feature constraints but singleton constraints of the form "A" or "!A"
	};
	
	public static FeatureModelMode fmMode = NO_SINGLETON;

	@SuppressWarnings({ "rawtypes" })
	private final static Constraint FALSE = new Constraint(null) {
		public Constraint and(Constraint other) {
			//false && other = false
			return this;
		}		

		public Constraint or(Constraint other) {
			//false || other == other
			return other;
		}		

		public String toString() {
			return "false";
		}
		
		public String toString(StringNumberer featureNumberer) {
			return toString();
		}

		public int hashCode() {
			return -436534;
		}
		
		public boolean equals(Object obj) {
			return obj==this;
		}
		
		protected Constraint exists(NumberedString varToQuantify) {
			return this;
		}
		
		public Constraint simplify(Iterable allFeatures, Collection usedFeatures) {
			return this;
		}
		
		public int size() {
			return 0;
		}
	};
	
	@SuppressWarnings({ "rawtypes" })
	private final static Constraint TRUE = new Constraint(null) {
		public Constraint and(Constraint other) {
			//true && other == other
			return other; 
		}
		
		public Constraint or(Constraint other) {
			//true || other == true
			return this;
		}

		public String toString() {
			return "true";
		}
		
		public String toString(StringNumberer featureNumberer) {
			return toString();
		}

		public int hashCode() {
			return -23214;
		}
		
		public boolean equals(Object obj) {
			return obj==this;
		}
		
		protected Constraint exists(NumberedString varToQuantify) {
			return this;
		}
		
		public Constraint simplify(Iterable allFeatures, Collection usedFeatures) {
			return this;
		}

		public int size() {
			return 0;
		}
	};

	protected final BDD bdd;
	
	public BDD getBDD()
	{
		synchronized (FACTORY) {
			return bdd;
		}
	}
	
//	@Override
//	protected void finalize() throws Throwable {
//		synchronized (FACTORY) {
//			bdd.free();
//		}
//	}

	private Constraint(BitSet elems, boolean positive) {
		synchronized (FACTORY) {
			BDD curr = FACTORY.one();
			if(!elems.isEmpty()) {
				for(int i=elems.nextSetBit(0); i>=0; i=elems.nextSetBit(i+1)) {
					BDD ithVar = FACTORY.ithVar(i);
					curr = curr.andWith(ithVar);
				}
			} else
				curr = curr.not(); //no elements provided; assume the "FALSE" constraint
			if(positive) 
				bdd = curr;
			else
				bdd = curr.not();
			if(bdd.getFactory() != FACTORY) {
				logger.error("BDDFactory mismatch");
			}
		}
	}
	
	private Constraint(BitSet elems, Set<NumberedString> featureDomain) {
		synchronized (FACTORY) {
			BDD curr = FACTORY.one();
			for(NumberedString feature: featureDomain) {
				int i = feature.getNumber();
				BDD ithVar = FACTORY.ithVar(i);
				if(!elems.get(i)) {
					ithVar = ithVar.not();
				}
				curr = curr.andWith(ithVar);
			}
			bdd = curr;
			if(bdd.getFactory() != FACTORY) {
				logger.error("BDDFactory mismatch");
			}
		}
	}
	
	
	/**
	 * Constructs a <i>full</i> constraint in the sense that all variables mentioned in
	 * featureDomain but not mentioned in elems will be automatically negated.
	 * If the domain is {a,b,c} and elems is {b} then this will construct the
	 * constraint !a && b && !c.
	 */
	public static <T> Constraint<T> make(BitSet elems, Set<NumberedString> featureDomain) {
		return new Constraint<T>(elems,featureDomain);
	}

	/**
	 * If positive is true then for elems={a,b} this constructs a constraint
	 * a && b. Otherwise, this constructs a constraint !(a && b).
	 * A constraint of the form a && b does not say anything at all about variables
	 * not mentioned. In particular, a && b is not the same as a && b && !c.
	 */
	public static <T> Constraint<T> make(BitSet elems, boolean positive) {
		if(elems.isEmpty()) throw new RuntimeException("empty constraint!");
		return new Constraint<T>(elems,positive);
	}
	
	public synchronized static <T> Constraint<T> make(BDD bdd) {
		synchronized (FACTORY) {
			if(bdd.isOne())
				return Constraint.trueValue();
			else if(bdd.isZero())
				return Constraint.falseValue();
			else return new Constraint<T>(bdd);
		}
	}
	
	protected Constraint(BDD bdd) {
		if(bdd != null && bdd.getFactory() != FACTORY) {
			logger.error("BDDFactory mismatch");
			this.bdd = null;
		} else {
			this.bdd = bdd;		
		}
	}
	
	/**
	 * Computes the constraint representing this OR other.
	 * The constraint is automatically reduced such that
	 * a || !a results in true.
	 * @see Constraint#trueValue()
	 */
	public Constraint<T> or(Constraint<T> other) {		
		synchronized (FACTORY) {
			if(other==trueValue()) return other;
			if(other==falseValue()) return this;
			
			BDD disjunction = bdd.or(other.bdd);
			if(disjunction.isOne()) {
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
//				
				return trueValue();
			}
			else
				return new Constraint<T>(disjunction);
		}
	}
	
	public static <T> Constraint<T> negate(Constraint<T> other)
	{		
		synchronized (FACTORY) {
			if(other==trueValue()) return falseValue();
			if(other==falseValue()) return trueValue();
			
			BDD negation = other.bdd.not();
			if(negation.isOne()) 
				return trueValue();
			else if(negation.isZero()) 
				return falseValue();
			else
				return new Constraint<T>(negation);
		}
	}
	
	/**
	 * Computes the constraint representing this AND other.
	 * The constraint is automatically reduced such that
	 * a && !a results in false.
	 * @see Constraint#falseValue()
	 */
	public Constraint<T> and(Constraint<T> other) {		
		synchronized (FACTORY) {
			if(other==trueValue()) return this;
			if(other==falseValue()) return other;
			
			// Unknown constraints stay unknown
//			if(other instanceof UnknownConstraint<?>)
//			{
//				return other;
//			}
			
//			BDD ref = FACTORY.ithVar(2);
//			BDDPairing pair = FACTORY.makePair();
//			pair.set(2, 2);
//			BDD replaced = bdd.veccompose(pair);
//			
//			BDD support = bdd.support();
//			
//			
//			BDD test = FACTORY.ithVar(0);
//			test.andWith(FACTORY.ithVar(1));
//			test.orWith(FACTORY.nithVar(1));
//			
//			BDD testSupport = test.support();
//			
//			logger.info("Start");
//			for(BDDIterator iter = test.iterator(testSupport); iter.hasNext();)
//			{
//				BDD bdd = (BDD) iter.next();
//				logger.info("BDD {}", bdd);
//			}
			
			
			BDD conjunction = bdd.and(other.bdd);
			if(conjunction.isZero()) {
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
				
				return falseValue();
			} else
				return new Constraint<T>(conjunction);
		}
	}
	
	@Override
	public String toString() {
		synchronized (FACTORY) {
			return bdd.toString();
		}
	}

	public String toString(Map<Integer,String> featureNumberer) {
		synchronized (FACTORY) {
			StringBuilder sb = new StringBuilder();
	        int[] set = new int[FACTORY.varNum()];
			toStringRecurse(FACTORY, sb, bdd, set, featureNumberer);
			return sb.toString();
		}
	}
	
	private static void toStringRecurse(BDDFactory f, StringBuilder sb, BDD r, int[] set,
			Map<Integer,String> featureNumberer) {
		synchronized (FACTORY) {
			int n;
			boolean first;
	
			if (r.isZero())
				return;
			else if (r.isOne()) {
				sb.append("{");
				first = true;
	
				for (n = 0; n < set.length; n++) {
					if (set[n] > 0) {
						if (!first)
							sb.append(" ^ ");
						first = false;
						if (set[n] != 2) {
							sb.append("!");
						}
						sb.append(featureNumberer.get(f.level2Var(n)));
					}
				}
				sb.append("} ");
			} else {
				set[f.var2Level(r.var())] = 1;
				BDD rl = r.low();
				toStringRecurse(f, sb, rl, set, featureNumberer);
				rl.free();
	
				set[f.var2Level(r.var())] = 2;
				BDD rh = r.high();
				toStringRecurse(f, sb, rh, set, featureNumberer);
				rh.free();
	
				set[f.var2Level(r.var())] = 0;
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public static <T> Constraint<T> trueValue() {
		return TRUE;
	}

	@SuppressWarnings("unchecked")
	public static <T> Constraint<T> falseValue() {
		return FALSE;
	}

	@Override
	public int hashCode() {
		synchronized (FACTORY) {
			return bdd.hashCode();
		}
	}

	@Override
	public boolean equals(Object obj) {
		synchronized (FACTORY) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			@SuppressWarnings("rawtypes")
			Constraint other = (Constraint) obj;
			if (bdd == null) {
				if (other.bdd != null)
					return false;
			} else if (!bdd.equals(other.bdd))
				return false;
			return true;
		}
	}

	protected Constraint<T> exists(NumberedString varToQuantify) {
		synchronized (FACTORY) {
			return make(bdd.exist(FACTORY.one().andWith(FACTORY.ithVar(varToQuantify.getNumber()))));
		}
	}
	
	public Constraint<T> simplify(Iterable<NumberedString> allFeatures, Collection<NumberedString> usedFeatures) {
		Constraint<T> fmConstraint = this;
		for (NumberedString feature : allFeatures) {
			if(!usedFeatures.contains(feature)) {
				fmConstraint = fmConstraint.exists(feature);
			}
		}
		return fmConstraint;
	}
	
	public int size() {
		synchronized (FACTORY) {
			return bdd.nodeCount();
		}
	}
	
	public String prettyString(Map<Integer,String> bddToString)
	{
		if(this == trueValue())
			return "true";
		if(this == falseValue())
			return "false";
		if(bdd == null)
			return null;
		
		return toString(bddToString).trim();
	}
}
