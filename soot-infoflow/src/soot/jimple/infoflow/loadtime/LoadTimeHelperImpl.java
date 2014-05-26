package soot.jimple.infoflow.loadtime;

import heros.EdgeFunction;
import heros.solver.JumpFnSingleton;
import heros.solver.JumpFunctions;
import heros.solver.PathEdge;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.RecursiveAction;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDPairing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table.Cell;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import soot.Local;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.EqExpr;
import soot.jimple.IfStmt;
import soot.jimple.NeExpr;
import soot.jimple.ConditionExpr;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.aliasing.IAliasingStrategy;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.LoadTimeHelper;
import soot.spl.ifds.SPLFeatureFunction;
import soot.tagkit.Host;

public class LoadTimeHelperImpl implements LoadTimeHelper<Abstraction, AccessPath> {

	private final Logger logger = LoggerFactory.getLogger(getClass());
	public static int knownFeaturesOffset = 10;
	
	private Map<Unit, Integer> localToFeatureIndex = new ConcurrentHashMap<Unit,Integer>();

	private Map<AbstractionAtStatement, Constraint<String>> knownConstraints = new ConcurrentHashMap<AbstractionAtStatement, Constraint<String>>();
	private Map<Unit, Constraint<String>> srcToConstraint = new ConcurrentHashMap<Unit, Constraint<String>>();
	
	private Map<Unit, Boolean> isPrecise = new ConcurrentHashMap<Unit, Boolean>();
	
	private JumpFunctions<Unit, Abstraction, Constraint<String>, Value> jumpFn = null;
	SootMethod mainMethod = null;
	
	private ForkJoinPool pool = new ForkJoinPool();
	
	private BDDPairing replacement;
	
	private Set<Integer> preciseFeatures = new HashSet<Integer>();
	private Aliasing aliasing;
	
	public LoadTimeHelperImpl(Config featureConfig, Aliasing aliasing)
	{
		this.aliasing = aliasing;
		for(Entry<String, ConfigValue> entry : featureConfig.root().entrySet())
		{
			Config conf = featureConfig.getConfig(entry.getKey());
			if(conf.getBoolean("precise")) {
				preciseFeatures.add(conf.getInt("index"));
			}
		}
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
	
	private class GetFeatureTask extends RecursiveAction
	{
		private Abstraction abs;
		private PathEdge<Unit, Abstraction> edge;
		private Value condition;
		private Value op1;
		private Value op2;
		private Unit src;
		private Abstraction srcNode;
		private Aliasing aliasing;
		
		public GetFeatureTask(PathEdge<Unit, Abstraction> edge, Value op1, Value op2, Value condition, Unit src, Abstraction srcNode, Aliasing aliasing)
		{
			this.condition = condition;
			this.op1 = op1;
			this.op2 = op2;
			this.edge = edge;
			this.abs = edge.factAtTarget();
			this.srcNode = srcNode;
			this.src = src;
			this.aliasing = aliasing;
		}

		@Override
		public void compute() {
			FeatureInfo featureInfo = abs.getSourceContext() != null 
										? (FeatureInfo) abs.getSourceContext().getUserData()
										: null;
			boolean match = false;
			if(featureInfo != null)
			{
				if(!featureInfo.getVariable().isStaticFieldRef() && aliasing.mayAlias(op1, featureInfo.getVariable().getPlainLocal())) {
					match = true;
				}
				
			}
								
            if(match) {
            	Constraint<String> result = null;
            	
            	if(featureInfo.getValue() != null && featureInfo.getValue()) {
            		EdgeFunction<Constraint<String>> existingFunction = jumpFn.getFunction(edge);
            		
	            	if(featureInfo.getIndex() != -1) {
	            		BitSet features = new BitSet();
	            		features.set(featureInfo.getIndex());
	            		if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
	            			result = ((SPLFeatureFunction) existingFunction).getFeatures();
	            		} else {
	            			result = Constraint.<String>trueValue();
	            		}
	            		
	            		if(preciseFeatures.contains(featureInfo.getIndex())) {
	            			isPrecise.put(src, true);
	            		}
	            	} else {
	            		result = Constraint.<String>trueValue();
	            		
	            		if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
	            			result = ((SPLFeatureFunction) existingFunction).getFeatures();
	            		}          		
	            	}
	            	
	            	if(srcToConstraint.containsKey(src)) {
	            		synchronized (srcToConstraint) {
	            			srcToConstraint.put(src, result.or(srcToConstraint.get(src)));
						}
	            	} else {
	            		srcToConstraint.put(src, result);
	            	}
	            	
	            	AbstractionAtStatement absAtStmt = new AbstractionAtStatement(srcNode, src);
	            	knownConstraints.put(absAtStmt, result);           	
            	}
			}
			
		}
		
	}
	
	@Override
	public Constraint<String> getFeaturesForFlow(Unit src, Host successor,
									 Abstraction srcNode, Abstraction tgtNode, SootMethod method, Abstraction zeroValue) {

		Constraint<String> result = null;
		
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
			IfStmt ifStmt = (IfStmt) src;
			Value condition = ifStmt.getCondition();
			
			assert condition instanceof EqExpr || condition instanceof NeExpr;
			
			AbstractionAtStatement absAtStmt = new AbstractionAtStatement(srcNode, src);
			Value op1 = ((ConditionExpr)condition).getOp1();
			Value op2 = ((ConditionExpr)condition).getOp2();
			
			Map<PathEdge<Unit, Abstraction>, EdgeFunction<Constraint<String>>> incoming = jumpFn.lookupByTarget(src);
						
			List<ForkJoinTask<Void>> toJoin = new LinkedList<ForkJoinTask<Void>>();
			
			boolean found = false;
			
			for(Entry<PathEdge<Unit, Abstraction>, EdgeFunction<Constraint<String>>> entry : incoming.entrySet())
			{
//				GetFeatureTask task = new GetFeatureTask(entry.getKey(), op1, op2, condition, src, tgtNode, aliasing);
//				toJoin.add(pool.submit(task));
				
//				task.compute();
				
				Abstraction abs = entry.getKey().factAtTarget();
				FeatureInfo featureInfo = abs.getSourceContext() != null 
						? (FeatureInfo) abs.getSourceContext().getUserData()
						: null;
				boolean match = false;
				if(featureInfo != null)
				{
					if(!featureInfo.getVariable().isStaticFieldRef() && aliasing.mayAlias(op1, featureInfo.getVariable().getPlainLocal())) {
						match = true;
					}
				
				}
								
				if(match) {
					Constraint<String> subResult = null;
					
					if(featureInfo.getValue() != null /* && featureInfo.getValue() */ && (!found || featureInfo.getValue())) {
						EdgeFunction<Constraint<String>> existingFunction = jumpFn.getFunction(entry.getKey());
						
						found = true;
						
						if(featureInfo.getIndex() != -1) {
							BitSet features = new BitSet();
							features.set(featureInfo.getIndex());
							if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
								subResult = ((SPLFeatureFunction) existingFunction).getFeatures();
							} else {
								subResult = Constraint.<String>trueValue();
							}
							
							if(preciseFeatures.contains(featureInfo.getIndex())) {
								isPrecise.put(src, true);
							}
						} else {
							subResult = Constraint.<String>trueValue();
							
							if(existingFunction != null && existingFunction instanceof SPLFeatureFunction) {
								subResult = ((SPLFeatureFunction) existingFunction).getFeatures();
							}          		
						}
						
						if(srcToConstraint.containsKey(src)) {
							synchronized (srcToConstraint) {
								srcToConstraint.put(src, subResult.or(srcToConstraint.get(src)));
							}
						} else {
							srcToConstraint.put(src, subResult);
						}
						
						knownConstraints.put(new AbstractionAtStatement(srcNode, src), subResult);           	
					}
				}

			}
			
			for(ForkJoinTask<Void> task : toJoin)
			{
				task.join();
			}
			
			if(knownConstraints.containsKey(absAtStmt)) {
				result = knownConstraints.get(absAtStmt);
			} else if(srcToConstraint.containsKey(src)) {
				result = srcToConstraint.get(src);
			} else  {
//				if(!jumpFn.valueIsDefined(op1)) {
//					result = Constraint.<String>trueValue();
//				} else {
//					result = Constraint.<String>falseValue();
//				}
			}
		} else if(srcNode == zeroValue && tgtNode != zeroValue)
		{
			// Create constraint for new abstraction
			FeatureInfo featureInfo = tgtNode.getSourceContext() != null 
										? (FeatureInfo) tgtNode.getSourceContext().getUserData()
										: null;
			
            if(featureInfo != null ) {
            	if(featureInfo.getValue() != null) {
	            	if(featureInfo.getIndex() != -1) {
	            		BitSet features = new BitSet();
	            		features.set(featureInfo.getIndex());	
						result = Constraint.<String>make(features,featureInfo.getValue());
	            	}
            	}
            }
		}
		
		return result;
	}
	
	@Override
	public boolean hasFeatures(Unit src, Host successor,
									 Abstraction srcNode, Abstraction tgtNode) {

		return src instanceof IfStmt;
	}
	
	@Override
	public AccessPath getDefinedVariable(Abstraction abs) {
		if(abs == null) {
			return null;
		}
		FeatureInfo featureInfo = abs.getSourceContext() != null 
				? (FeatureInfo) abs.getSourceContext().getUserData()
				: null;
		
		if(featureInfo != null && featureInfo.getValue() != null) {

			return featureInfo.getVariable();
		} else {
			return null;
		}
	}

	@Override
	public boolean vetoNewFunction(Abstraction sourceVal, Unit target,	Abstraction targetVal, EdgeFunction<Constraint<String>> f, Abstraction zeroValue) {
		boolean result = true;
		
		if(jumpFn == null) {
			JumpFnSingleton jumpFnSingleton = JumpFnSingleton.getInstance();
        	jumpFn = jumpFnSingleton.getJumpFn();
		}
		
		if(targetVal != zeroValue && targetVal.getSourceContext() != null) {
			if(targetVal.getSourceContext().getUserData() == null) {
				// No news? Check if for the same target, there is already a jumpFn entry with a similiar/better function
				Map<PathEdge<Unit, Abstraction>, EdgeFunction<Constraint<String>>> targetEntries = jumpFn.lookupByTarget(target);
				if (targetEntries != null) {
					for(EdgeFunction<Constraint<String>> entry : targetEntries.values()) {
						if(entry.equalTo(f)) {
							result = false;
							break;
						}
					}
				}
			}
		}
		
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

}
