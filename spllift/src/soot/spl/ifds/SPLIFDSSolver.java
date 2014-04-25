package soot.spl.ifds;

import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.IFDSTabulationProblem;
import heros.JoinLattice;
import heros.solver.IDESolver;
import heros.solver.JumpFnSingleton;
import heros.template.DefaultIDETabulationProblem;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import net.sf.javabdd.BDD;
import net.sf.javabdd.BDDPairing;
import net.sf.javabdd.JFactory;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.tagkit.Host;

public class SPLIFDSSolver<D,AccessPath> extends IDESolver<Unit,D,SootMethod,Constraint<String>,ExtendedInterproceduralCFG, AccessPath> {
	
	private LoadTimeHelper<D,AccessPath> helper;
	private BDDPairing replacement;
	private IFDSTabulationProblem<Unit,D,SootMethod,BiDiInterproceduralCFG<Unit,SootMethod>> ifdsProblem;
	/**
	 * Creates a solver for the given problem. The solver must then be started by calling
	 * {@link #solve()}.
	 * @param fullFmConstraint 
	 * @param alloyFilePath 
	 * @param numFeaturesPresent 
	 */
	public SPLIFDSSolver(final IFDSTabulationProblem<Unit,D,SootMethod,BiDiInterproceduralCFG<Unit,SootMethod>> ifdsProblem,
						 final LoadTimeHelper<D,AccessPath> helper) {
		super(new DefaultIDETabulationProblem<Unit,D,SootMethod,Constraint<String>,ExtendedInterproceduralCFG>(new ExtendedInterproceduralCFG(ifdsProblem.interproceduralCFG())) {

			public FlowFunctions<Unit,D,SootMethod> createFlowFunctionsFactory() {
				return new FlowFunctions<Unit,D,SootMethod>() {

					@Override
					public FlowFunction<D> getNormalFlowFunction(Unit curr, Unit succ) {
						FlowFunction<D> original = ifdsProblem.flowFunctions().getNormalFlowFunction(curr, succ);
						
						boolean hasFeatures = helper.hasFeatures(curr, succ, null, null);
						
						if(hasFeatures && interproceduralCFG().isFallThroughSuccessor(curr, succ)) {
							return new WrappedFlowFunction<D>(original);
						} else {
							return original;
						}
					}

					@Override
					public FlowFunction<D> getCallFlowFunction(Unit callStmt, SootMethod destinationMethod) {
						return ifdsProblem.flowFunctions().getCallFlowFunction(callStmt, destinationMethod);
					}

					@Override
					public FlowFunction<D> getReturnFlowFunction(Unit callSite, SootMethod calleeMethod, Unit exitStmt, Unit returnSite) {
						return ifdsProblem.flowFunctions().getReturnFlowFunction(callSite, calleeMethod, exitStmt, returnSite);
					}

					@Override
					public FlowFunction<D> getCallToReturnFlowFunction(Unit callSite, Unit returnSite) {
						FlowFunction<D> original = ifdsProblem.flowFunctions().getCallToReturnFlowFunction(callSite, returnSite);
						// @TODO
						if(hasFeatureAnnotation(callSite)) {
							return new WrappedFlowFunction<D>(original);
						} else {
							return original;
						}
					}
				};
			}
			
			public Map<Unit,Set<D>> initialSeeds() {
				return ifdsProblem.initialSeeds();
			}

			public D createZeroValue() {
				return ifdsProblem.zeroValue();
			}

			public EdgeFunctions<Unit,D,SootMethod,Constraint<String>> createEdgeFunctionsFactory() {
				return new IFDSEdgeFunctions<D,Unit,AccessPath>(ifdsProblem, interproceduralCFG(), helper);
			}

			public JoinLattice<Constraint<String>> createJoinLattice() {
				return new JoinLattice<Constraint<String>>() {

					public Constraint<String> topElement() {
						return Constraint.falseValue();
					}

					public Constraint<String> bottomElement() {
						return Constraint.trueValue();
					}

					public Constraint<String> join(Constraint<String> left, Constraint<String> right) {
						return left.or(right);
					}
				};
			}
			
			public EdgeFunction<Constraint<String>> createAllTopFunction() {
				return new SPLFeatureFunction(Constraint.<String>falseValue());
			}	
		}, helper);
		
        JumpFnSingleton.init(jumpFn);

		this.helper = helper;
		
		if(Constraint.FACTORY != null) {
			Constraint.FACTORY.done();
		}
		
		if(Constraint.FACTORY == null || !Constraint.FACTORY.isInitialized()) {
			Constraint.FACTORY = JFactory.init(10000, 10000);
			Constraint.FACTORY.setIncreaseFactor(1.5);
			Constraint.FACTORY.setMaxIncrease(1000000);
			Constraint.FACTORY.setVarNum(1000); //some number high enough to accommodate the max. number of features; ideally we should compute this number 
		}
		
		if(Constraint.trackPrecise == null)
		{
			Constraint.trackPrecise = new HashSet<Integer>(helper.trackPrecise());
		}

		this.ifdsProblem = ifdsProblem;
		replacement = Constraint.FACTORY.makePair();
	}

	private static boolean hasFeatureAnnotation(Host host) {
		return host.hasTag("LoadTimeFeature");
	}
	
	/**
	 * Returns the set of facts that hold at the given statement.
	 */
	public Set<D> ifdsResultsAt(Unit statement) {
		return resultsAt(statement).keySet();
	}

	public Constraint<String> resultAt(Unit stmt, D value) {
		return super.resultAt(stmt, value);	
	}
	
	@Override
	public Map<D, Constraint<String>> resultsAt(Unit stmt) {
		Map<D, Constraint<String>> resultsAt = super.resultsAt(stmt);
		Map<D, Constraint<String>> res = new HashMap<D, Constraint<String>>();
		for(Entry<D,Constraint<String>> entry: resultsAt.entrySet()) {
			res.put(entry.getKey(), entry.getValue());
		}
		return res;
	}

	public Constraint<String> orResult(Unit stmt) {
	
		Map<D, Constraint<String>> resultsAt = super.resultsAt(stmt);
		Constraint<String> res = null;
		
		if(resultsAt.isEmpty()) {
			res = Constraint.<String>trueValue();
		} else {
			res = Constraint.<String>falseValue();
//			Map<D, EdgeFunction<Constraint<String>>> tempResults = jumpFn.reverseLookup(stmt, zeroValue);
			res = val.get(stmt, zeroValue);
		}
		

//		for(Entry<D,Constraint<String>> entry: resultsAt.entrySet()) {
//			
//			if(entry.getValue().bdd != null) {
//				
//				BDD bdd = entry.getValue().bdd;
//
//				// Why this?
//				if(!entry.getValue().bdd.isOne() && !bdd.isOne()) {
//					res = res.or(Constraint.<String>make(bdd));
//				}
//			} else {
//				res = res.or(entry.getValue());
//			}				
//		}
		
		
		
		// Pseudovar still in result -> Unknown
//		if(res.bdd != null) {
//			for(int var : res.bdd.scanSet()) {
//				if(var > knownFeaturesOffset) {
//					res.setUnknown(true);
//				}
//			}
//		}
		
		return res;
	}

	static class WrappedFlowFunction<D> implements FlowFunction<D> {
		
		private FlowFunction<D> del;

		private WrappedFlowFunction(FlowFunction<D> del) {
			this.del = del;
		}

		@Override
		public Set<D> computeTargets(D source) {
			Set<D> targets = new HashSet<D>(del.computeTargets(source));
			targets.add(source);
			return targets;
		}
	}

	public ThreadPoolExecutor getExistingExecutor() {
		return executor;
	}
	
	@Override
	protected boolean vetoNewFunction(D sourceVal, Unit target, D targetVal, EdgeFunction<Constraint<String>> f) {
		return helper.vetoNewFunction(sourceVal, target, targetVal, f, zeroValue);
	}

}
