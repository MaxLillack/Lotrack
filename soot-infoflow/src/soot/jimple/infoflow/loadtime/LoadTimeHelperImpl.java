package soot.jimple.infoflow.loadtime;

import heros.EdgeFunction;
import heros.solver.JumpFnSingleton;
import heros.solver.JumpFunctions;
import heros.solver.Pair;
import heros.solver.PathEdge;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.jms.IllegalStateException;
import javax.management.RuntimeErrorException;

import com.sun.media.jfxmedia.logging.Logger;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import soot.Local;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.EqExpr;
import soot.jimple.GeExpr;
import soot.jimple.GtExpr;
import soot.jimple.IfStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.IntConstant;
import soot.jimple.LeExpr;
import soot.jimple.LtExpr;
import soot.jimple.NeExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.NullConstant;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.SourceContext;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.spl.ifds.CachedZ3Solver;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.ConstraintSet;
import soot.spl.ifds.IConstraint;
import soot.spl.ifds.LoadTimeHelper;
import soot.spl.ifds.SPLFeatureFunction;
import soot.spl.ifds.SolverOperation;
import soot.spl.ifds.SolverOperation.Operator;
import soot.tagkit.Host;

public class LoadTimeHelperImpl implements LoadTimeHelper<Abstraction> {

//	private final Logger logger = LoggerFactory.getLogger(getClass());
	public static int knownFeaturesOffset = 10;
	
	private Map<AbstractionAtStatement, IConstraint> knownConstraints = new ConcurrentHashMap<AbstractionAtStatement, IConstraint>();
	private Map<Unit, IConstraint> srcToConstraint = new ConcurrentHashMap<Unit, IConstraint>();
	
	private Map<Unit, Boolean> isPrecise = new ConcurrentHashMap<Unit, Boolean>();
	
	private JumpFunctions<Unit, Abstraction, IConstraint> jumpFn = null;
	SootMethod mainMethod = null;
	
//	private ForkJoinPool pool = new ForkJoinPool();
	
	private Set<Integer> preciseFeatures = new HashSet<Integer>();
	private Aliasing aliasing;
	private Map<Integer, String> featureNames = new HashMap<>();
	private Map<GreekLetterKey, String> greekLetter = new HashMap<>();
	private Map<String, Integer> timesUsed = new HashMap<>();
	private BiDiInterproceduralCFG<Unit, SootMethod> icfg;
	
	// Not working yet, check unit tests first
	private boolean vetoTaintsBasedOnConstraintOrder = false;
	
	public LoadTimeHelperImpl(Config featureConfig, Aliasing aliasing, BiDiInterproceduralCFG<Unit, SootMethod> icfg)
	{
		this.aliasing = aliasing;
		for(Entry<String, ConfigValue> entry : featureConfig.root().entrySet())
		{
			Config conf = featureConfig.getConfig(entry.getKey());
			if(conf.getBoolean("precise")) {
				preciseFeatures.add(conf.getInt("index"));
			}
			featureNames.put(conf.getInt("index"), entry.getKey());
		}
		
		CachedZ3Solver.setMaxFeatureIndex(featureConfig.root().entrySet().size());
//		CachedZ3Solver.setImpliesMap(implies);
		
		this.icfg = icfg;
	}
	
	private class AbstractionAtStatement {
		public Abstraction abstraction;
		public Unit unit;
		
		public AbstractionAtStatement(Abstraction abstraction, Unit unit) {
			this.abstraction = abstraction;
			this.unit = unit;
		}

		@Override
		public String toString() {
			return "AbstractionAtStatement [abstraction=" + abstraction
					+ ", unit=" + unit + "]";
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + getOuterType().hashCode();
			result = prime * result
					+ ((abstraction == null) ? 0 : abstraction.hashCode());
			result = prime * result + ((unit == null) ? 0 : unit.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			AbstractionAtStatement other = (AbstractionAtStatement) obj;
			if (!getOuterType().equals(other.getOuterType()))
				return false;
			if (abstraction == null) {
				if (other.abstraction != null)
					return false;
			} else if (!abstraction.equals(other.abstraction))
				return false;
			if (unit == null) {
				if (other.unit != null)
					return false;
			} else if (!unit.equals(other.unit))
				return false;
			return true;
		}

		private LoadTimeHelperImpl getOuterType() {
			return LoadTimeHelperImpl.this;
		}
	}
	
//	private class GetFeatureTask extends RecursiveAction
//	{
//		private Abstraction abs;
//		private PathEdge<Unit, Abstraction> edge;
//		private Value condition;
//		private Value op1;
//		private Value op2;
//		private Unit src;
//		private Abstraction srcNode;
//		private Aliasing aliasing;
//		
//		public GetFeatureTask(PathEdge<Unit, Abstraction> edge, Value op1, Value op2, Value condition, Unit src, Abstraction srcNode, Aliasing aliasing)
//		{
//			this.condition = condition;
//			this.op1 = op1;
//			this.op2 = op2;
//			this.edge = edge;
//			this.abs = edge.factAtTarget();
//			this.srcNode = srcNode;
//			this.src = src;
//			this.aliasing = aliasing;
//		}
//
//		@Override
//		public void compute() {
//			Abstraction abs = edge.factAtTarget();
//			FeatureInfo featureInfo = abs.getSourceContext() != null 
//					? (FeatureInfo) abs.getSourceContext().getUserData()
//					: null;
//			boolean match = false;
//			if(featureInfo != null)
//			{
//				if(!featureInfo.getVariable().isStaticFieldRef() && aliasing.mayAlias(op1, featureInfo.getVariable().getPlainLocal())) {
//					match = true;
//				}
//			
//			}
//							
//			if(match) {
//				IConstraint subResult = null;
//				
//				if(featureInfo.getValue() != null /* && featureInfo.getValue() */ && (!found || featureInfo.getValue())) {
//					EdgeFunction<IConstraint> existingFunction = jumpFn.getFunction(edge);
//					
//					found = true;
//					
//					if(featureInfo.getIndex() != -1) {
//						BitSet features = new BitSet();
//						features.set(featureInfo.getIndex());
//						if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
//							subResult = ((SPLFeatureFunction) existingFunction).getFeatures();
//						} else {
//							subResult = Constraint.trueValue();
//						}
//						
//						if(preciseFeatures.contains(featureInfo.getIndex())) {
//							isPrecise.put(src, true);
//						}
//					} else {
//						subResult = Constraint.trueValue();
//						
//						if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
//							subResult = ((SPLFeatureFunction) existingFunction).getFeatures();
//						}          		
//					}
//					
//					if(srcToConstraint.containsKey(src)) {
//						synchronized (srcToConstraint) {
//							srcToConstraint.put(src, subResult.or(srcToConstraint.get(src)));
//						}
//					} else {
//						srcToConstraint.put(src, subResult);
//					}
//					
//					knownConstraints.put(new AbstractionAtStatement(srcNode, src), subResult);           	
//				}
//			}
//
//			
//		}
//		
//	}
	
	private boolean greekLetterLimitReached = false;
	
	private String greekLetter(int number)
	{
		// TODO - Testing limit
		int limit = 1000;
		if(number > limit)
		{
			if(!greekLetterLimitReached)
			{
				greekLetterLimitReached = true;
				System.err.println("greek letter limit reached!");
			}
			number = limit;
		}
		
		switch (number) {
		case 1:		return "Alpha";
		case 2:		return "Beta";
		case 3:		return "Gamma";
		case 4:		return "Delta";
		case 5:		return "Epsilon";
		case 6:		return "Zeta";
		case 7:		return "Eta";
		case 8:		return "Theta";
		case 9:		return "Iota";
		case 10:	return "Kappa";
		case 11:	return "Lambda";
		case 12:	return "Mu";
		case 13:	return "Nu";
		case 14:	return "Xi";
		case 15:	return "Omicron";
		case 16:	return "Pi";
		case 17:	return "Rho";
		case 18:	return "Sigma";
		case 19:	return "Tau";
		case 20:	return "Upsilon";
		case 21:	return "Phi";
		case 22:	return "Chi";
		case 23:	return "Psi";
		case 24:	return "Omega";
		default:	return "UGL" + number;
		}
	}
	
	@Override
	public Collection<ConstraintSet> getFeaturesForFlow(Unit src, Host successor,
									 Abstraction srcNode, Abstraction tgtNode, SootMethod method, Abstraction zeroValue, Collection<Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>>> matchingAbstractions) {

		
		Collection<ConstraintSet> results = new LinkedHashSet<>();
		IConstraint result = null;
		IConstraint baseConstraint = null;
		
		String methodName = method.getName();
		if(mainMethod == null && methodName.equals("dummyMainMethod")) {
			mainMethod = method;
		}
		// We will not create constraint in the (generated) main method
		if(method == mainMethod)
		{
			return null;
		}
		
		if(jumpFn == null) {
			JumpFnSingleton jumpFnSingleton = JumpFnSingleton.getInstance();
        	jumpFn = jumpFnSingleton.getJumpFn();
		}
		
		if(src instanceof IfStmt) {
			
			// if the if statement is empty, i.e. branch and fall-through edges are the same, we will not create a constraint
			if(icfg.getSuccsOf(src).size() == 1)
			{
				return null;
			}
			
			IfStmt ifStmt = (IfStmt) src;
			Value condition = ifStmt.getCondition();
			
			ConditionExpr conditionExpr = ((ConditionExpr)condition);
			Value op1 = conditionExpr.getOp1();
			Value op2 = conditionExpr.getOp2();
			
			boolean found = false;
			
			if(matchingAbstractions == null)
			{
				throw new RuntimeException(" matchingAbstractions must not be null for if statements");
			}
			
			// existing 
//			Map<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> existing = jumpFn.lookupByTarget((Unit)successor);
			
			String operator = null;
			boolean negate = false;
			if(conditionExpr instanceof EqExpr) {
				operator = "=";
			} else if(conditionExpr instanceof NeExpr) {
				operator = "=";
				negate = true;
			} else if(conditionExpr instanceof GeExpr) {
				operator = ">=";
			} else if(conditionExpr instanceof GtExpr) {
				operator = ">";
			} else if(conditionExpr instanceof LeExpr) {
				operator = "<=";
			} else if(conditionExpr instanceof LtExpr) {
				operator = "<";
			} else {
				throw new RuntimeException("Unknown condition type " + conditionExpr.toString());
			}
			
			TreeMap<Abstraction, EdgeFunction<IConstraint>> op1matchingConcrete = new TreeMap<>(Comparator.comparing(Abstraction::hashCode));
			TreeMap<Abstraction, EdgeFunction<IConstraint>> op1matchingAbstract = new TreeMap<>(Comparator.comparing(Abstraction::hashCode));	
			TreeMap<Abstraction, EdgeFunction<IConstraint>> op2matchingConcrete = new TreeMap<>(Comparator.comparing(Abstraction::hashCode));
			TreeMap<Abstraction, EdgeFunction<IConstraint>> op2matchingAbstract = new TreeMap<>(Comparator.comparing(Abstraction::hashCode));
			
			TreeMap<Abstraction, EdgeFunction<IConstraint>> op1matchingImpreciseAbstract = new TreeMap<>(Comparator.comparing(Abstraction::hashCode));
			TreeMap<Abstraction, EdgeFunction<IConstraint>> op2matchingImpreciseAbstract = new TreeMap<>(Comparator.comparing(Abstraction::hashCode));
			
			boolean op2IsContant = op2 instanceof IntConstant;
			
			for(Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> entry : matchingAbstractions)
			{		
				Abstraction abs = entry.getKey().factAtTarget();
				FeatureInfo featureInfo = abs.getSourceContext() != null 
						? (FeatureInfo) abs.getSourceContext().getUserData()
						: null;
				
				if(featureInfo.getValue() != null)
				{
					// Use aliasing?
					// Op1
					if(!featureInfo.getVariable().isEmpty() && op1.toString().equals(featureInfo.getVariable().getPlainLocal().toString()))
					{
						if(featureInfo.getValue() > -1) {
							op1matchingConcrete.put(abs, entry.getValue());
						} else if(featureInfo.getIndex() > -1) {
							op1matchingAbstract.put(abs, entry.getValue());
						} else if(featureInfo.getImpreciseIndex() != null && featureInfo.getImpreciseIndex() > -1) {
							op1matchingImpreciseAbstract.put(abs, entry.getValue());
						}
					}
					// Op1
					if(op2 instanceof Local && !featureInfo.getVariable().isEmpty() && op2.toString().equals(featureInfo.getVariable().getPlainLocal().toString()))
					{
						if(featureInfo.getValue() > -1) {
							op2matchingConcrete.put(abs, entry.getValue());
						} else if(featureInfo.getIndex() > -1) {
							op2matchingAbstract.put(abs, entry.getValue());
						} else if(featureInfo.getImpreciseIndex() != null && featureInfo.getImpreciseIndex() > -1) {
							op2matchingImpreciseAbstract.put(abs, entry.getValue());
						}
					}
				}
			}
			
			/*
			 * Multiple possible casese
			 * There could be multiple matching and concrete taints for op1
			 * There could be a single abstract matching taint for op2
			 * Same for op2
			 * op2 could also be a constant
			 * 
			 * abstract and abstract => A = B
			 * concrete (0 if A) and abstract (B) => A ^ B = 0
			 * multiple concretes: concrete (0 if A) and abstract (B); concrete (1 if !A) and abstract (B) => (A ^ B = 0) || (!A ^ B = 1)
			 * 
			 */
			
			// We assume that either only abstract taints match or only concrete ones
			assert op1matchingAbstract.isEmpty() || op1matchingConcrete.isEmpty();
			assert op2matchingAbstract.isEmpty() || op2matchingConcrete.isEmpty();
			// We assume at most one abstract taint can match
			assert op1matchingAbstract.isEmpty() || op1matchingAbstract.size() == 1;
			assert op2matchingAbstract.isEmpty() || op2matchingAbstract.size() == 1;
			
			// Abstract - Abstract
			if(!op1matchingAbstract.isEmpty() && !op2matchingAbstract.isEmpty())
			{
				FeatureInfo featureInfo1 = (FeatureInfo) op1matchingAbstract.firstKey().getSourceContext().getUserData();
				FeatureInfo featureInfo2 = (FeatureInfo) op2matchingAbstract.firstKey().getSourceContext().getUserData();
				
				result = Constraint.intConstraint("|" + featureInfo1.getIndex() + "|", "|" + featureInfo2.getIndex() + "|", operator);

				if(negate) {
					result = Constraint.negate(result);
				}
			}
			
			// Concrete - Abstract
			if(!op1matchingConcrete.isEmpty() && !op2matchingAbstract.isEmpty())
			{
				List<IConstraint> parts = new ArrayList<>();
				
				// check if matching are complete
				IConstraint completeCheck = Constraint.falseValue();
				for(Entry<Abstraction, EdgeFunction<IConstraint>> matchingOp1 : op1matchingConcrete.entrySet())
				{
					if(op1matchingConcrete.firstEntry().getValue() instanceof SPLFeatureFunction) {
						SPLFeatureFunction c = (SPLFeatureFunction) matchingOp1.getValue();
						completeCheck = completeCheck.or(c.getFeatures());
					} 
				}
				
				boolean isComplete = completeCheck.equals(Constraint.trueValue());
				
				// If we only have incomplete concrete values, we create an artficial negation of these taints
				if(!isComplete)
				{
					IConstraint resultPart = Constraint.trueValue();
					if(negate) {
						resultPart = Constraint.negate(resultPart);
					}

					IConstraint baseConstraintPart = null;
					if(op1matchingConcrete.firstEntry().getValue() instanceof SPLFeatureFunction)
					{
						SPLFeatureFunction constraint = (SPLFeatureFunction) op1matchingConcrete.firstEntry().getValue();
						if(constraint != null)
						{
							GreekLetterKey gkKey = new GreekLetterKey(src, 0);
							if(greekLetter.get(gkKey) == null) 
							{
								int used = timesUsed.merge("IncompleteSymb", 1, (oldValue, value) -> oldValue + 1);
								String letter = "IncompleteSymb" + "_" + greekLetter(used);
								greekLetter.put(gkKey, letter);
							}					
							
							result = Constraint.impreciseConstraint(greekLetter.get(gkKey));
							baseConstraintPart = Constraint.negate(Constraint.negate(completeCheck).and(result));
						}
					} else {
						baseConstraintPart = Constraint.negate(resultPart);
					}
					
					results.add(new ConstraintSet(baseConstraintPart, resultPart, true));
				}
				
				// for each concrete taint, select value and connect with matching op2. If there are multiple concretes, connect with OR
				for(Entry<Abstraction, EdgeFunction<IConstraint>> matchingOp1 : op1matchingConcrete.entrySet())
				{
					FeatureInfo featureInfoOp1 = (FeatureInfo) matchingOp1.getKey().getSourceContext().getUserData();
					FeatureInfo featureInfoOp2 = (FeatureInfo) op2matchingAbstract.firstKey().getSourceContext().getUserData();
					
					String featureName = "|" + featureInfoOp2.getIndex() + "|";
					SPLFeatureFunction constraint = (SPLFeatureFunction) matchingOp1.getValue();					
					// TODO - adjust for new system with result set
					IConstraint resultPart = constraint.getFeatures().and(Constraint.intConstraint(featureName, featureInfoOp1.getValue(), operator));
					if(negate) {
						resultPart = Constraint.negate(resultPart);
					}
					parts.add(resultPart);
				}
				
				result = parts.get(0);
				// combine parts using OR if there is more than one
				for(int i = 1; i < parts.size(); i++)
				{
					result = result.or(parts.get(i));
				}
			}
			
			// Abstract - Concrete
			if(!op1matchingAbstract.isEmpty() && !op2matchingConcrete.isEmpty())
			{
				List<IConstraint> parts = new ArrayList<>();
				
				// for each concrete taint, select value and connect with matching op2. If there are multiple concretes, connect with OR
				for(Entry<Abstraction, EdgeFunction<IConstraint>> matchingOp2 : op2matchingConcrete.entrySet())
				{
					FeatureInfo featureInfoOp1 = (FeatureInfo) op1matchingAbstract.firstKey().getSourceContext().getUserData();
					FeatureInfo featureInfoOp2 = (FeatureInfo) matchingOp2.getKey().getSourceContext().getUserData();
			
					String featureName = "|" + featureInfoOp1.getIndex() + "|";
					SPLFeatureFunction constraint = (SPLFeatureFunction) matchingOp2.getValue();	
					
					IConstraint c = Constraint.intConstraint(featureName, featureInfoOp2.getValue(), operator);
					if(negate)
					{
						c = Constraint.negate(c);
					}
					IConstraint resultPart = constraint.getFeatures().and(c);
					parts.add(resultPart);
				}
				
				result = parts.get(0);
				// combine parts using OR if there is more than one
				for(int i = 1; i < parts.size(); i++)
				{
					result = result.or(parts.get(i));
				}
			}
			
			// Concrete - Constant
			if(!op1matchingConcrete.isEmpty() && op2IsContant)
			{
				IntConstant op2IntConstant = (IntConstant) op2;
				List<IConstraint> parts = new ArrayList<>();
				
				EdgeFunction<IConstraint> zeroConstraint = jumpFn.getFunction(new PathEdge<Unit, Abstraction>(zeroValue, src, zeroValue));
				SPLFeatureFunction zeroSplFeatureFunction = null;
				if(zeroConstraint instanceof SPLFeatureFunction)
				{
					zeroSplFeatureFunction = (SPLFeatureFunction) zeroConstraint;
				}
				
				// check if matching are complete
				IConstraint completeCheck = Constraint.falseValue();
				for(Entry<Abstraction, EdgeFunction<IConstraint>> matchingOp1 : op1matchingConcrete.entrySet())
				{
					if(matchingOp1.getValue() instanceof SPLFeatureFunction) {
						SPLFeatureFunction c = (SPLFeatureFunction) matchingOp1.getValue();
						completeCheck = completeCheck.or(c.getFeatures());
					} 
				}
				
				boolean isComplete = completeCheck.equals(Constraint.trueValue()) || (zeroSplFeatureFunction != null && completeCheck.equals(zeroSplFeatureFunction.getFeatures()));
				
				// If we only have incomplete concrete values, we create an artficial negation of these taints
				if(!isComplete)
				{
					FeatureInfo featureInfoOp1 = (FeatureInfo) op1matchingConcrete.firstKey().getSourceContext().getUserData();
					IConstraint resultPart ;
					if(featureInfoOp1.getValue() > -1) {
						resultPart = Constraint.intConstraint(featureInfoOp1.getValue(), op2IntConstant.value, operator);	
					} else {
						resultPart = Constraint.trueValue();
					}
					// Overwritten - Check logic above
//					IConstraint resultPart = Constraint.trueValue();
					if(negate) {
						resultPart = Constraint.negate(resultPart);
					}

					IConstraint baseConstraintPart = null;
					if(op1matchingConcrete.firstEntry().getValue() instanceof SPLFeatureFunction)
					{
						SPLFeatureFunction constraint = (SPLFeatureFunction) op1matchingConcrete.firstEntry().getValue();
						if(constraint != null)
						{
							GreekLetterKey gkKey = new GreekLetterKey(src, 0);
							if(greekLetter.get(gkKey) == null) 
							{
								int used = timesUsed.merge("IncompleteSymb", 1, (oldValue, value) -> oldValue + 1);
								String letter = "IncompleteSymb" + "_" + greekLetter(used);
								greekLetter.put(gkKey, letter);
							}					
							
							result = Constraint.impreciseConstraint(greekLetter.get(gkKey));
							baseConstraintPart = Constraint.negate(Constraint.negate(completeCheck).and(result));
						}
					} else {
						baseConstraintPart = Constraint.negate(resultPart);
					}
					
					results.add(new ConstraintSet(baseConstraintPart, resultPart, true));
				}
				
				
				for(Entry<Abstraction, EdgeFunction<IConstraint>> matchingOp1 : op1matchingConcrete.entrySet())
				{
					FeatureInfo featureInfoOp1 = (FeatureInfo) matchingOp1.getKey().getSourceContext().getUserData();
					IConstraint resultPart = Constraint.intConstraint(featureInfoOp1.getValue(), op2IntConstant.value, operator);	
					if(!negate) {
						resultPart = Constraint.negate(resultPart);
					}
					
					IConstraint baseConstraintPart = null;
					if(matchingOp1.getValue() instanceof SPLFeatureFunction)
					{
						SPLFeatureFunction constraint = (SPLFeatureFunction) matchingOp1.getValue();
						baseConstraintPart  = constraint.getFeatures();
					}
					parts.add(resultPart);
					
					results.add(new ConstraintSet(baseConstraintPart, resultPart));
				}
				
			}
			
			// Abstract - Constant
			if(!op1matchingAbstract.isEmpty() && op2IsContant)
			{
				IntConstant op2IntConstant = (IntConstant) op2;
				Abstraction abs = op1matchingAbstract.firstKey();
				
				// check for anti abstractions
				boolean hasAntiAbstraction = false;
				for(Abstraction a : op1matchingAbstract.keySet())
				{
					FeatureInfo featureInfo = (FeatureInfo) a.getSourceContext().getUserData();
					if(featureInfo.getIsAnti())
					{
						hasAntiAbstraction = true;
					}
				}
				
				if(hasAntiAbstraction)
				{
					// TODO temp - no constraint if there are anti abstractions
					return null;
				}
				
				FeatureInfo featureInfoOp1 = (FeatureInfo) abs.getSourceContext().getUserData();
				String featureName = "|" + featureInfoOp1.getIndex() + "|";
				result = Constraint.intConstraint(featureName, op2IntConstant.value, operator);	

				SPLFeatureFunction constraint = null;
				if(op1matchingAbstract.firstEntry().getValue() instanceof SPLFeatureFunction) {
					constraint = (SPLFeatureFunction) op1matchingAbstract.firstEntry().getValue();
				}
				if(constraint != null && constraint.getFeatures() != null) {
					baseConstraint = constraint.getFeatures();
				}
				
				if(negate) {
					result = Constraint.negate(result);
				}
				

			}
			
			// Concrete - Concrete	
			if(!op1matchingConcrete.isEmpty() && !op2matchingConcrete.isEmpty())
			{
				IConstraint constraint = Constraint.falseValue();
				
				for(Entry<Abstraction, EdgeFunction<IConstraint>> taintOp1Entry : op1matchingConcrete.entrySet())
				{
					FeatureInfo featureInfoOp1 = (FeatureInfo) taintOp1Entry.getKey().getSourceContext().getUserData();
					for(Entry<Abstraction, EdgeFunction<IConstraint>> taintOp2Entry : op2matchingConcrete.entrySet())
					{
						FeatureInfo featureInfoOp2 = (FeatureInfo) taintOp2Entry.getKey().getSourceContext().getUserData();
						// valueConstraint should be true or false after simplification (1>1 -> false, 3=3 -> true, ...)
						IConstraint valueConstraint = Constraint.intConstraint(featureInfoOp1.getValue(), featureInfoOp2.getValue(), operator);
						

						
						// TODO check conversion
						if(taintOp1Entry.getValue() instanceof SPLFeatureFunction && taintOp2Entry.getValue() instanceof SPLFeatureFunction)
						{
							SPLFeatureFunction splFeatureFunction1 = (SPLFeatureFunction) taintOp1Entry.getValue();
							SPLFeatureFunction splFeatureFunction2 = (SPLFeatureFunction) taintOp2Entry.getValue();
							
							
							IConstraint base = splFeatureFunction1.getFeatures().and(splFeatureFunction2.getFeatures());
							IConstraint extension = valueConstraint;
							ConstraintSet constraintSet = new ConstraintSet(base, extension);
							results.add(constraintSet);
							
//							IConstraint temp = valueConstraint.and(splFeatureFunction1.getFeatures()).and(splFeatureFunction2.getFeatures());
//							constraint = constraint.or(temp);
						}
		
					}
				}
				
//				result = constraint;
			}
			
			
			// Imprecise: Abstract -- ??
			if(result == null && results.isEmpty() && !op1matchingAbstract.isEmpty())
			{
				Abstraction abs = op1matchingAbstract.firstKey();
				FeatureInfo featureInfoOp1 = (FeatureInfo) abs.getSourceContext().getUserData();
				
				String Z3featureName = "|" + featureInfoOp1.getIndex() + "|";
				
				GreekLetterKey gkKey = new GreekLetterKey(src, featureInfoOp1.getIndex());
				if(greekLetter.get(gkKey) == null) 
				{
					int used = timesUsed.merge(Z3featureName, 1, (oldValue, value) -> oldValue + 1);
					String letter = featureNames.get(featureInfoOp1.getIndex()) + "_" + greekLetter(used);
					greekLetter.put(gkKey, letter);
				}					
				
				result = Constraint.impreciseConstraint(greekLetter.get(gkKey));
				
				if(negate) {
					result = Constraint.negate(result);
				}
				
				if(op1matchingAbstract.firstEntry().getValue() instanceof SPLFeatureFunction) {
					SPLFeatureFunction constraint = (SPLFeatureFunction) op1matchingAbstract.firstEntry().getValue();
					if(constraint != null && constraint.getFeatures() != null)
					{
						baseConstraint = constraint.getFeatures();
					}
				}
			}
			
			// Imprecise: ?? -- Abstract
			if(result == null && results.isEmpty() && !op2matchingAbstract.isEmpty())
			{
				Abstraction abs = op2matchingAbstract.firstKey();
				FeatureInfo featureInfoOp2 = (FeatureInfo) abs.getSourceContext().getUserData();
				SPLFeatureFunction constraint = null;
				if(op2matchingAbstract.firstEntry().getValue() instanceof SPLFeatureFunction) {
					constraint = (SPLFeatureFunction) op2matchingAbstract.firstEntry().getValue();
				}
				if(!preciseFeatures.contains(featureInfoOp2.getIndex()))
				{
					String featureName = "|" + featureInfoOp2.getIndex() + "|";
					result = Constraint.intConstraint(featureName, -1, operator);	
					if(negate) {
						result = Constraint.negate(result);
					}
					if(constraint != null) {
						baseConstraint = constraint.getFeatures();
					}
				}
			}
			
			boolean builtImpreciseConstraints = false;
			
			// Imprecise op1
			if(result == null && results.isEmpty() && !op1matchingImpreciseAbstract.isEmpty())
			{
				
				// For multiple matching abstraction, prefer abstraction with value -1 (unknown but relevant) to values -2
				
				List<Entry<Abstraction, EdgeFunction<IConstraint>>> entries = new ArrayList<>();
				boolean addedOnlyMinus2 = false;
				for(Entry<Abstraction, EdgeFunction<IConstraint>> e : op1matchingImpreciseAbstract.entrySet())
				{
					Abstraction a = e.getKey();
					SourceContext sourceContext = a.getSourceContext();
					if(sourceContext != null && sourceContext.getUserData() != null)
					{
						FeatureInfo featureInfo = (FeatureInfo) sourceContext.getUserData();
						if(featureInfo.getValue() != -2 || entries.isEmpty()) {

							// If until now we only added -2 elements we clear entries, may be empty already
							if(addedOnlyMinus2)
							{
								entries.clear();
								addedOnlyMinus2 = false;
							}

							entries.add(e);
							
							if(featureInfo.getValue() == -2) {
								addedOnlyMinus2 = true;
							}
						}
					}
				}
				
				for(Entry<Abstraction, EdgeFunction<IConstraint>> entry : entries)
				{
					Abstraction abs = entry.getKey();
					FeatureInfo featureInfoOp1 = (FeatureInfo) abs.getSourceContext().getUserData();
					SPLFeatureFunction constraint = null;
					if(entry.getValue() instanceof SPLFeatureFunction) {
						constraint = (SPLFeatureFunction) entry.getValue();
					}
					String Z3featureName = "|" + featureInfoOp1.getImpreciseIndex() + "|";
									
					GreekLetterKey gkKey = new GreekLetterKey(src, featureInfoOp1.getImpreciseIndex());
					if(greekLetter.get(gkKey) == null) 
					{
						int used = timesUsed.merge(Z3featureName, 1, (oldValue, value) -> oldValue + 1);
						String letter = featureNames.get(featureInfoOp1.getImpreciseIndex()) + "_" + greekLetter(used);
						greekLetter.put(gkKey, letter);
					}
					
					result = Constraint.impreciseConstraint(greekLetter.get(gkKey));
					
	//				if(constraint != null) {
	//					addImplies(result, constraint.getFeatures());
	//				}
					
					if(negate) {
						result = Constraint.negate(result);
					}
					if(constraint != null) {
						result = constraint.getFeatures().and(result);
						// for imprecise taints
						if(result.isConstFalse())
						{
							result = constraint.getFeatures().and(Constraint.negate(result));
						}
					}
					
					results.add(new ConstraintSet(baseConstraint, result));
				}
				builtImpreciseConstraints = true;
			}
			
			
			// Imprecise op2
			if(result == null && results.isEmpty() && !op2matchingImpreciseAbstract.isEmpty())
			{
				Abstraction abs = op2matchingImpreciseAbstract.firstKey();
				FeatureInfo featureInfoOp2 = (FeatureInfo) abs.getSourceContext().getUserData();
				SPLFeatureFunction constraint = null;
				if(op2matchingImpreciseAbstract.firstEntry().getValue() instanceof SPLFeatureFunction) {
					constraint = (SPLFeatureFunction) op2matchingImpreciseAbstract.firstEntry().getValue();
				}
				String Z3featureName = "|" + featureInfoOp2.getImpreciseIndex() + "|";

				GreekLetterKey gkKey = new GreekLetterKey(src, featureInfoOp2.getImpreciseIndex());
				if(greekLetter.get(gkKey) == null) 
				{
					int used = timesUsed.merge(Z3featureName, 1, (oldValue, value) -> oldValue + 1);
					
					String letter = featureNames.get(featureInfoOp2.getImpreciseIndex()) + "_" + greekLetter(used);
					greekLetter.put(gkKey, letter);
				}

				result = Constraint.impreciseConstraint(greekLetter.get(gkKey));
				
				if(negate) {
					result = Constraint.negate(result);
				}
				if(constraint != null) {
					result = constraint.getFeatures().and(result);
					
					// for imprecise taints
					if(result.isConstFalse())
					{
						result = constraint.getFeatures().and(Constraint.negate(result));
					}
				}
				builtImpreciseConstraints = true;
			}
			
			// We negate the result (we original analyses the condition for the goto to happen but need to return the condition for branching)
			// We will not negate an condition of imprecise constraints 
			if(result != null && !builtImpreciseConstraints)
			{
				result = Constraint.negate(result);
			}

			
			
//			for(Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> entry : matchingAbstractions)
//			{
//
//				Abstraction abs = entry.getKey().factAtTarget();
//				FeatureInfo featureInfo = abs.getSourceContext() != null 
//						? (FeatureInfo) abs.getSourceContext().getUserData()
//						: null;
//				
//				
//				IConstraint subResult = null;
//				
//				if(featureInfo.getValue() != null && !found) {
//					EdgeFunction<IConstraint> existingFunction = entry.getValue();
//					
//					found = true;
//					
//					//TODO - op2 could be NullConstant, we should fall back to a non-precise version
//					if(featureInfo.getIndex() != -1 && op2 instanceof IntConstant) {
//						if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
//							subResult = ((SPLFeatureFunction) existingFunction).getFeatures();
//						} else {
//							subResult = Constraint.trueValue();
//						}
//						
//						
//						assert operator != null;
//						String featureName = "|" + featureInfo.getIndex() + "|";
//						subResult = Constraint.intConstraint(featureName, ((IntConstant)op2).value, operator);
//						
//						// Look wrong, but for some reason negation is default and we only skip if condition is negation expr
//						if(!negate) {
//							subResult = Constraint.negate(subResult);	
//						}
//						
//						if(preciseFeatures.contains(featureInfo.getIndex())) {
//							isPrecise.put(src, true);
//						}
//					} else {
//
//						
//						if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
//							subResult = ((SPLFeatureFunction) existingFunction).getFeatures();
//						}        
//						
//						Integer op2value = null;
//						if(op2 instanceof IntConstant)
//						{
//							op2value = ((IntConstant)op2).value;
//						} else if(op2 instanceof Local)
//						{
//							// Look for taint for this variable
//							for(Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> entry2 : matchingAbstractions)
//							{
//								Abstraction abs2 = entry2.getKey().factAtTarget();
//								EdgeFunction<IConstraint> existingFunction2 = entry2.getValue();
//								FeatureInfo featureInfo2 = abs2.getSourceContext() != null 
//										? (FeatureInfo) abs2.getSourceContext().getUserData()
//										: null;
//								if(featureInfo2 != null) {
//									if(featureInfo2.getValue() != -1 && op2.toString().equals(featureInfo2.getVariable().getPlainLocal().toString()))
//									{
//										// If op2 is a tainted variable we must also ensure that the constraint of op2's taint is added
//										// As a shortcut, we can ensure that op1's condition AND op2's condition are SAT
//										if(existingFunction != null && existingFunction instanceof SPLFeatureFunction
//											&& existingFunction2 != null && existingFunction2 instanceof SPLFeatureFunction) {
//											
//											SPLFeatureFunction c1 = (SPLFeatureFunction) existingFunction;
//											SPLFeatureFunction c2 = (SPLFeatureFunction) existingFunction2;
//											
//											IConstraint conjunction = c1.getFeatures().and(c2.getFeatures());
//											if(!conjunction.isConstFalse()) {
//												op2value = featureInfo2.getValue();
//											}
//					
//										}
//										
//										
//									}
//								}
//							}
//						}
//						
//						
//						// We only consider condition where the second operand is a constant, we should be able
//						// to extend this to variable operands if we have a taint for them
//						if(op1.toString().equals(featureInfo.getVariable().getPlainLocal().toString()) && featureInfo.getValue() != -1 && op2value != null)
//						{
//							assert featureInfo.getValue() > -1;
//							assert featureInfo.getValue() != null;
//							subResult = Constraint.intConstraint(featureInfo.getValue(), op2value, operator);
//							String temp = subResult.getZ3Constraint();
//							
//							// Check constraint for SAT
//							// DEBUG
//							if(temp.equals("(= 0 1)") || temp.equals("(= 1 0)") )
//							{
//								found = false;
//								subResult = Constraint.falseValue();
//							} else {
//								// There are still check for equalivalence to true which must be changed
//								// Temp fix
//								subResult = ((SPLFeatureFunction) existingFunction).getFeatures(); 
//								subResult = Constraint.negate(subResult);
//							}
//							
//							if(negate)
//							{
//								subResult = Constraint.negate(subResult);
//							}
//							
//							
//						} else {
//						
//							subResult = Constraint.trueValue();
//						}
//					}
//					
//			
//					IConstraint oldConstraint = srcToConstraint.get(src);
//					if(oldConstraint != null) {
//							srcToConstraint.put(src, subResult.or(oldConstraint));
//					} else {
//						srcToConstraint.put(src, subResult);
//					}
//					
//				}
//				
//
//			}
//			if(srcToConstraint.containsKey(src)) {
//				result = srcToConstraint.get(src);
//			}
		}
		else if(srcNode == zeroValue && tgtNode != zeroValue)
		{
			// Create constraint for new abstraction
//			FeatureInfo featureInfo = tgtNode.getSourceContext() != null 
//										? (FeatureInfo) tgtNode.getSourceContext().getUserData()
//										: null;
//			
//            if(featureInfo != null ) {
//            	if(featureInfo.getIndex() != -1) {
//            		BitSet features = new BitSet();
//            		features.set(featureInfo.getIndex());	
//					result = Constraint.make(features, featureInfo.getValue());
//	           	}
//            }
		}
		
		
		if(results.isEmpty()) {
			results.add(new ConstraintSet(baseConstraint, result));
		}
		
		return results;
	}

//	private void addImplies(IConstraint result, IConstraint constraint) {
//		CachedZ3Solver.addPrettyprinted(result.getZ3Constraint(), result.getZ3Constraint());
//		implies.merge(result, constraint, (oldValue, value) -> oldValue.or(value));
//	}

	@Override
	public Collection<Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>>> findMatchingAbstractions(Unit src) {
		
		if(!(src instanceof IfStmt))
		{
			return Collections.emptyList();
		}
		
		IfStmt ifStmt = (IfStmt) src;
		
		if(jumpFn == null) {
			JumpFnSingleton jumpFnSingleton = JumpFnSingleton.getInstance();
        	jumpFn = jumpFnSingleton.getJumpFn();
		}
		
		Map<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> incoming = jumpFn.lookupByTarget(ifStmt);
		
		Value condition = ifStmt.getCondition();
		ConditionExpr conditionExpr = ((ConditionExpr)condition);
		Value op1 = conditionExpr.getOp1();
		Value op2 = conditionExpr.getOp2();
		
		Collection<Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>>> results = new ArrayList<>();
		
		for(Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> entry : incoming.entrySet())
		{
			Abstraction abs = entry.getKey().factAtTarget();
		
			FeatureInfo featureInfo = abs.getSourceContext() != null 
					? (FeatureInfo) abs.getSourceContext().getUserData()
					: null;
			
			boolean match = false;
			if(featureInfo != null)
			{
				if(abs.getAccessPath().isInstanceFieldRef()) {
					// @TODO -> recheck handling of instance fields e.g. test27
					
	//						if(aliasing.mayAlias(op1, featureInfo.getVariable().getPlainLocal())) {
	//							
	//						}
	//						
	//						if(featureInfo.getVariable().getBaseType() != null) {
	//							AccessPath mappedAP = aliasing.mayAlias(featureInfo.getVariable(), new AccessPath(op1, false));
	//							if(mappedAP != null)
	//							{
	//								AccessPath op1AP = new AccessPath(op1, false);
	//								
	//								boolean entails = op1AP.entails(featureInfo.getVariable());
	//								match = entails;
	//							}
	//							
	//							
	//						}
				} else {
					if(!featureInfo.getVariable().isEmpty() && !featureInfo.getVariable().isStaticFieldRef() && (aliasing.mayAlias(op1, featureInfo.getVariable().getPlainLocal())
																		|| aliasing.mayAlias(op2, featureInfo.getVariable().getPlainLocal()))) {
						match = true;
					}
				}
				
				if(match)
				{
					results.add(entry);
				}
			
			}
		}
		return results;
	}
	
	@Override
	public boolean hasFeatures(Unit src, Host successor,
									 Abstraction srcNode, Abstraction tgtNode) {

		return src instanceof IfStmt;
	}
	
//	@Override
//	public AccessPath getDefinedVariable(Abstraction abs) {
//		if(abs == null) {
//			return null;
//		}
//		FeatureInfo featureInfo = abs.getSourceContext() != null 
//				? (FeatureInfo) abs.getSourceContext().getUserData()
//				: null;
//		
//		if(featureInfo != null && featureInfo.getValue() != null) {
//
//			return featureInfo.getVariable();
//		} else {
//			return null;
//		}
//	}

	@Override
	public boolean vetoNewFunction(Abstraction sourceVal, Unit target,	Abstraction targetVal, EdgeFunction<IConstraint> f, Abstraction zeroValue) {
		boolean result = true;
		
		if(jumpFn == null) {
			JumpFnSingleton jumpFnSingleton = JumpFnSingleton.getInstance();
        	jumpFn = jumpFnSingleton.getJumpFn();
		}
		
		// Exclude list defined in options is no effective. We enforce exclusion here
		String className = icfg.getMethodOf(target).getDeclaringClass().getName();
		if(className.startsWith("org.joda.") 
				|| className.startsWith("android.") 
				|| className.startsWith("java.")
				|| className.startsWith("com.google.")
				|| className.startsWith("org.apache."))
		{
			result = false;
		}
		
		// Test: We try to exclude targetVals with an ap with cycles
		if(targetVal.getAccessPath().getFields() != null)
		{
			Set<SootField> uniqueFields = new HashSet<SootField>(Arrays.asList(targetVal.getAccessPath().getFields()));
			if(uniqueFields.size() != targetVal.getAccessPath().getFields().length)
			{
				result = false;
			}
		}
		
		if(vetoTaintsBasedOnConstraintOrder)
		{
			EdgeFunction<IConstraint> existing = jumpFn.getFunction(new PathEdge<Unit, Abstraction>(sourceVal, target, targetVal));
			if(existing != null && existing instanceof SPLFeatureFunction && f instanceof SPLFeatureFunction) {
				SPLFeatureFunction existingConstraint = (SPLFeatureFunction) existing;
				SPLFeatureFunction newConstraint = (SPLFeatureFunction) f;
				
				// CNF
				String cnfExisting = CachedZ3Solver.solve(new SolverOperation(existingConstraint.getFeatures().getZ3Constraint(), Operator.CNF));
				String cnfNew = CachedZ3Solver.solve(new SolverOperation(newConstraint.getFeatures().getZ3Constraint(), Operator.CNF));
				
				// Substitute Existing
				Pattern p = Pattern.compile("(\\w+)_\\w+");
				Matcher matcher = p.matcher(cnfExisting);
				Map<String,String> replaceMap = new HashMap<>();
				while(matcher.find())
				{
					String base = matcher.group(1);
					replaceMap.put(matcher.group(0), base + "_subs");
				}
				String subsitutedExisting = cnfExisting;
				for(Entry<String, String> entry : replaceMap.entrySet())
				{
					subsitutedExisting = subsitutedExisting.replaceAll(entry.getKey(), entry.getValue());
				}
				
				// Substitute New
				matcher = p.matcher(cnfNew);
				replaceMap = new HashMap<>();
				while(matcher.find())
				{
					String base = matcher.group(1);
					replaceMap.put(matcher.group(0), base + "_subs");
				}
				String subsitutedNew = cnfNew;
				for(Entry<String, String> entry : replaceMap.entrySet())
				{
					subsitutedNew = subsitutedNew.replaceAll(entry.getKey(), entry.getValue());
				}
				
				// Check implies
				String implies = CachedZ3Solver.solve(new SolverOperation(subsitutedExisting, subsitutedNew, Operator.IMPLIES));
				if(implies.equals("true")) {
					result = false;
				}
			}
		}
		
		// Test 2: Can we exclude taints if there is already an taint for an alias?
//		Map<Abstraction, EdgeFunction<IConstraint>> existingTaints = jumpFn.forwardLookup(sourceVal, target);
//		for(Map.Entry<Abstraction, EdgeFunction<IConstraint>> entry : existingTaints.entrySet())
//		{	
//			if(entry.getKey().getSourceContext() != null && entry.getKey().getSourceContext().getUserData() != null && 
//					targetVal.getSourceContext() != null && targetVal.getSourceContext().getUserData() != null &&
//					entry.getKey().getSourceContext().getUserData().equals(targetVal.getSourceContext().getUserData()) && entry.getValue().equals(f)) {				
//				if(entry.getKey().getAccessPath() != null && targetVal.getAccessPath() != null &&
//						entry.getKey().getAccessPath().getBaseType() != null &&
//						aliasing.mayAlias(entry.getKey().getAccessPath(), targetVal.getAccessPath()) != null)
//				{
//					result = false;	
//				}
//			}
//		}
		
		// Test 3: Exclude taints to field, which are already tainted through another ap
		// breaks test27
//		Map<Abstraction, EdgeFunction<IConstraint>> existingTaints = jumpFn.forwardLookup(sourceVal, target);
//		for(Map.Entry<Abstraction, EdgeFunction<IConstraint>> entry : existingTaints.entrySet())
//		{	
//			if(entry.getKey().getAccessPath() != null && targetVal.getAccessPath() != null &&
//					entry.getKey().getAccessPath().getBaseType() != null &&
//					entry.getKey().getAccessPath() != null &&
//					targetVal.getAccessPath().getLastField() != null &&
//					entry.getKey().getAccessPath().getLastField() == targetVal.getAccessPath().getLastField())
//			{
//				result = false;	
//			}
//		}
		
		// When to constraints to to become to large?
//		if(f instanceof SPLFeatureFunction)
//		{
//			SPLFeatureFunction featureFunction = (SPLFeatureFunction) f;
//			if(featureFunction.toString().length() > 300)
//			{
//				SootMethod m = icfg.getMethodOf(target);
//				String declaringClass = m.getDeclaringClass().getName();
//				if(!declaringClass.startsWith("java"))
//				{
//					int a = 0;
//				}
//			}
//		}
		
//		if(targetVal != zeroValue && targetVal.getSourceContext() != null) {
//			if(targetVal.getSourceContext().getUserData() == null) {
//				// No news? Check if for the same target, there is already a jumpFn entry with a similiar/better function
//				Map<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> targetEntries = jumpFn.lookupByTarget(target);
//				
//				for(Entry<PathEdge<Unit, Abstraction>, EdgeFunction<IConstraint>> entry : targetEntries.entrySet()) {
//					if(entry.getKey().factAtTarget().equals(targetVal))
//					{
//						EdgeFunction<IConstraint> function = entry.getValue();
//						if(function.equalTo(f)) {
//							result = false;
//							break;
//						}
//					}
//					
//				}
//				
//			}
//		}

		
		// Hack for performance test
//		String className = icfg.getMethodOf(target).getDeclaringClass().getName();
//		if(!className.equals("org.opensolaris.opengrok.index.Indexer") 
//				&& !className.equals("dummyMainClass")
//				&& !className.equals("java.lang.Object")
//				&& !className.equals("java.lang.Class")
//				&& !className.equals("org.opensolaris.opengrok.configuration.RuntimeEnvironment")
//				&& !className.equals("org.opensolaris.opengrok.configuration.Configuration")
//				&& !className.equals("org.opensolaris.opengrok.configuration.RuntimeEnvironment$3")
////				&& !className.equals("org.opensolaris.opengrok.util.Executor")
//				
////				&& !className.equals("org.opensolaris.opengrok.history.HistoryGuru")
////				&& !className.equals("org.opensolaris.opengrok.history.FileHistoryCache")
////				&& !className.equals("org.opensolaris.opengrok.history.JDBCHistoryCache")
//				&& !className.startsWith("org.opensolaris.opengrok.")
//		)
//		{
////			System.out.println("Rejected class " + className);
////			result = false;
//		}
		return result;
	}

	@Override
	public boolean isPrecise(Unit unit) {
		return true;
//		return isPrecise.containsKey(unit);
	}


	@Override
	public boolean isPrecise(int index) {
		return preciseFeatures.contains(index);
	}

	@Override
	public Set<Integer> trackPrecise() {
		return preciseFeatures;
	}

	@Override
	public Map<Integer, String> getFeatureNames() {
		return featureNames;
	}

	@Override
	public Abstraction deriveAntiAbstraction(Abstraction abstraction) {
		Abstraction antiAbstraction = abstraction.clone();
		SourceContext sourceContext = antiAbstraction.getSourceContext();
		if(sourceContext != null && sourceContext.getUserData() != null)
		{
			FeatureInfo featureInfo = new FeatureInfo((FeatureInfo) sourceContext.getUserData());
			featureInfo.setIsAnti(true);
			antiAbstraction.setSourceContext(new SourceContext(sourceContext.getAccessPath(), sourceContext.getStmt(), featureInfo));
		}
		
		return antiAbstraction;
	}

	@Override
	public boolean isAntiAbstraction(Abstraction abstraction) {
		SourceContext sourceContext = abstraction.getSourceContext();
		if(sourceContext != null && sourceContext.getUserData() != null)
		{
			FeatureInfo featureInfo = (FeatureInfo) sourceContext.getUserData();
			return featureInfo.getIsAnti();	
		}
		return false;
	}

	@Override
	public void cleanEdgeList(Collection<PathEdge<Unit, Abstraction>> edges) {
		
		Collection<PathEdge<Unit, Abstraction>> toRemove = new ArrayList<>();
		
		for(PathEdge<Unit, Abstraction> pathEdge : edges)
		{
			Abstraction abstraction = pathEdge.factAtTarget();
			SourceContext sourceContext = abstraction.getSourceContext();
			if(sourceContext != null && sourceContext.getUserData() != null)
			{
				FeatureInfo featureInfo = (FeatureInfo) sourceContext.getUserData();
				if(featureInfo.getValue() == -2)
				{
					Abstraction copy = abstraction.clone();
					FeatureInfo userData = new FeatureInfo(featureInfo);
					userData.setValue(-1);
					copy.setSourceContext(new SourceContext(sourceContext.getAccessPath(), sourceContext.getStmt(), userData));
					
					
					// Search for similar but better taints
					for(PathEdge<Unit, Abstraction> pathEdge2 : edges)
					{	
						if(pathEdge2 != pathEdge && pathEdge.factAtSource() == pathEdge2.factAtSource()
								&& copy.equals(pathEdge2.factAtTarget()))
						{
							toRemove.add(pathEdge);
						}
					}
				}
				
				if(featureInfo.getVariable().isEmpty())
				{
					Abstraction copy = abstraction.clone();
					FeatureInfo userData = new FeatureInfo(featureInfo);
					userData.setValue(-1);
					copy.setSourceContext(new SourceContext(sourceContext.getAccessPath(), sourceContext.getStmt(), userData));
					
					// Search for similar but better taints
					for(PathEdge<Unit, Abstraction> pathEdge2 : edges)
					{	
						if(pathEdge2 != pathEdge && !toRemove.contains(pathEdge2) &&  pathEdge.factAtSource() == pathEdge2.factAtSource()
								&& copy.equals(pathEdge2.factAtTarget()))
						{
							toRemove.add(pathEdge);
						}
					}
					
					userData.setValue(-2);
					
					// Search for similar but better taints
					for(PathEdge<Unit, Abstraction> pathEdge2 : edges)
					{	
						if(pathEdge2 != pathEdge && !toRemove.contains(pathEdge2) && pathEdge.factAtSource() == pathEdge2.factAtSource()
								&& copy.equals(pathEdge2.factAtTarget()))
						{
							toRemove.add(pathEdge);
						}
					}
				}
				
			}
		}
		
		if(!toRemove.isEmpty())
		{
			edges.removeAll(toRemove);
		}
	}

}
