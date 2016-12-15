package soot.spl.ifds;

import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.IFDSTabulationProblem;
import heros.JoinLattice;
import heros.solver.IDESolver;
import heros.solver.JumpFnSingleton;
import heros.solver.JumpFunctions;
import heros.solver.PathEdge;
import heros.solver.TopologicalSorter;
import heros.template.DefaultIDETabulationProblem;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.tagkit.Host;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;

public class SPLIFDSSolver<D,AccessPath> extends IDESolver<Unit,D,SootMethod,IConstraint,ExtendedInterproceduralCFG> {
	
	private LoadTimeHelper<D> helper;
//	private BDDPairing replacement;
	private IFDSTabulationProblem<Unit,D,SootMethod,BiDiInterproceduralCFG<Unit,SootMethod>> ifdsProblem;
	/**
	 * Creates a solver for the given problem. The solver must then be started by calling
	 * {@link #solve()}.
	 * @param fullFmConstraint 
	 * @param alloyFilePath 
	 * @param numFeaturesPresent 
	 */
	public SPLIFDSSolver(final IFDSTabulationProblem<Unit,D,SootMethod,BiDiInterproceduralCFG<Unit,SootMethod>> ifdsProblem,
						 final LoadTimeHelper<D> helper) {
		super(new DefaultIDETabulationProblem<Unit,D,SootMethod,IConstraint,ExtendedInterproceduralCFG>(new ExtendedInterproceduralCFG(ifdsProblem.interproceduralCFG())) {

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

			public EdgeFunctions<Unit,D,SootMethod,IConstraint> createEdgeFunctionsFactory() {
				return new IFDSEdgeFunctions<D,Unit,AccessPath>(ifdsProblem, interproceduralCFG(), helper);
			}

			public JoinLattice<IConstraint> createJoinLattice() {
				return new JoinLattice<IConstraint>() {

					public IConstraint topElement() {
						return Constraint.falseValue();
					}

					public IConstraint bottomElement() {
						return Constraint.trueValue();
					}

					public IConstraint join(IConstraint left, IConstraint right) {
						return left.or(right);
					}
				};
			}
			
			public EdgeFunction<IConstraint> createAllTopFunction() {
				return new SPLFeatureFunction(Constraint.falseValue());
			}	
		});
		
        JumpFnSingleton.init(jumpFn);

		this.helper = helper;
		
//		if(Constraint.FACTORY != null) {
//			Constraint.FACTORY.done();
//		}
//		
//		if(Constraint.FACTORY == null || !Constraint.FACTORY.isInitialized()) {
//			Constraint.FACTORY = JFactory.init(10000, 10000);
//			Constraint.FACTORY.setIncreaseFactor(1.5);
//			Constraint.FACTORY.setMaxIncrease(1000000);
//			Constraint.FACTORY.setVarNum(1000); //some number high enough to accommodate the max. number of features; ideally we should compute this number 
//		}
		
		if(Constraint.trackPrecise == null)
		{
			Constraint.trackPrecise = new HashSet<Integer>(helper.trackPrecise());
		}
		
		CachedZ3Solver.featureNames = helper.getFeatureNames();

		this.ifdsProblem = ifdsProblem;
		
//		replacement = Constraint.FACTORY.makePair();
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

	public IConstraint resultAt(Unit stmt, D value) {
		return super.resultAt(stmt, value);	
	}
	
	@Override
	public Map<D, IConstraint> resultsAt(Unit stmt) {
		Map<D, IConstraint> resultsAt = super.resultsAt(stmt);
		Map<D, IConstraint> res = new HashMap<D, IConstraint>();
		for(Entry<D,IConstraint> entry: resultsAt.entrySet()) {
			res.put(entry.getKey(), entry.getValue());
		}
		return res;
	}

	// @TODO not orResult, uses zeroValue
	public IConstraint orResult(Unit stmt) {
	
		Map<D, IConstraint> resultsAt = super.resultsAt(stmt);
		IConstraint res = null;
		
		if(resultsAt.isEmpty()) {
			res = Constraint.trueValue();
		} else {
			res = Constraint.falseValue();
//			Map<D, EdgeFunction<Constraint<String>>> tempResults = jumpFn.reverseLookup(stmt, zeroValue);
			if(val.contains(stmt, zeroValue) && val.get(stmt, zeroValue) != null) {
				res = val.get(stmt, zeroValue);
			} else {
//				System.err.println("No value for stmt " + stmt.toString());
			}
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
	protected boolean vetoNewFunction(D sourceVal, Unit target, D targetVal, EdgeFunction<IConstraint> f) {
		return helper.vetoNewFunction(sourceVal, target, targetVal, f, zeroValue);
	}
	
	@Override
	protected Collection<Entry<PathEdge<Unit, D>, EdgeFunction<IConstraint>>> findMatchingAbstractions(Unit target)
	{
		return helper.findMatchingAbstractions(target);
	}
	
	@Override
	protected D deriveAntiAbstraction(D abstraction)
	{
		return helper.deriveAntiAbstraction(abstraction);
	}
	
	@Override
	protected boolean isAntiAbstraction(D abstraction) {
		return helper.isAntiAbstraction(abstraction);
	}

	@Override
	protected void cleanEdgeList(Collection<PathEdge<Unit, D>> edges)
	{
		// To be overwritten		
		helper.cleanEdgeList(edges);
	}

	// DEBUG
	public JumpFunctions<Unit, D, IConstraint> getJumpFn() {
		return this.jumpFn;
	}

}
