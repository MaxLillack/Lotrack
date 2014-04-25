package soot.spl.ifds;

import heros.EdgeFunction;
import heros.EdgeFunctions;
import heros.FlowFunction;
import heros.FlowFunctions;
import heros.IFDSTabulationProblem;
import heros.InterproceduralCFG;
import heros.ZeroedFlowFunctions;
import heros.edgefunc.EdgeIdentity;
import heros.solver.IDESolver;
import heros.solver.JumpFunctions;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

import soot.Scene;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.BinopExpr;
import soot.jimple.IfStmt;
import soot.jimple.IntConstant;
import soot.jimple.NeExpr;
import soot.jimple.NullConstant;
import soot.jimple.NumericConstant;
import soot.jimple.Stmt;
import soot.jimple.internal.AbstractBinopExpr;
import soot.jimple.internal.JIfStmt;
import soot.jimple.internal.JNeExpr;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.jimple.toolkits.pointer.RWSet;
import soot.jimple.toolkits.pointer.SideEffectAnalysis;
import soot.tagkit.Host;
import soot.tagkit.Tag;

public class IFDSEdgeFunctions<D,N,SootValue> implements EdgeFunctions<Unit,D,SootMethod,Constraint<String>> {
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private final FlowFunctions<Unit, D, SootMethod> zeroedFlowFunctions;
	private IFDSTabulationProblem<Unit,D,SootMethod,BiDiInterproceduralCFG<Unit,SootMethod>> ifdsProblem;
	private final BiDiInterproceduralCFG<Unit,SootMethod> icfg;
	private LoadTimeHelper<D,SootValue> helper;
	
	public IFDSEdgeFunctions(IFDSTabulationProblem<Unit,D,SootMethod,BiDiInterproceduralCFG<Unit,SootMethod>> ifdsProblem,
							 BiDiInterproceduralCFG<Unit,SootMethod> icfg,
							 LoadTimeHelper<D,SootValue> helper) {
		this.ifdsProblem = ifdsProblem;
		this.icfg = icfg;
		this.helper = helper;

		zeroedFlowFunctions = new ZeroedFlowFunctions<Unit, D, SootMethod>(ifdsProblem.flowFunctions(),ifdsProblem.zeroValue());
	}
	
	public EdgeFunction<Constraint<String>> getNormalEdgeFunction(Unit currStmt, D currNode, Unit succStmt, D succNode) {
		return buildFlowFunction(currStmt, succStmt, currNode, succNode, zeroedFlowFunctions.getNormalFlowFunction(currStmt, succStmt), false);
	}

	public EdgeFunction<Constraint<String>> getCallEdgeFunction(Unit callStmt, D srcNode, SootMethod destinationMethod,D destNode) {
		return buildFlowFunction(callStmt, destinationMethod, srcNode, destNode, zeroedFlowFunctions.getCallFlowFunction(callStmt, destinationMethod), true);
	}

	public EdgeFunction<Constraint<String>> getReturnEdgeFunction(Unit callSite, SootMethod calleeMethod, Unit exitStmt,D exitNode, Unit returnSite,D retNode) {
		return buildFlowFunction(exitStmt, returnSite, exitNode, retNode, zeroedFlowFunctions.getReturnFlowFunction(callSite, calleeMethod, exitStmt, returnSite), false);
	}

	public EdgeFunction<Constraint<String>> getCallToReturnEdgeFunction(Unit callSite, D callNode, Unit returnSite, D returnSideNode) {
		return buildFlowFunction(callSite, returnSite, callNode, returnSideNode, zeroedFlowFunctions.getCallToReturnFlowFunction(callSite, returnSite), false);
	}
	
	private EdgeFunction<Constraint<String>> buildFlowFunction(Unit src, 
															   Host successor, 
															   D srcNode, 
															   D tgtNode, 
															   FlowFunction<D> originalFlowFunction, 
															   boolean isCall) {
		
		Constraint<String> srcFeatures = helper.getFeaturesForFlow(src, successor, srcNode, tgtNode, icfg.getMethodOf(src), ifdsProblem.zeroValue());		
		
		//logger.info("buildFlowFunction:: src {}, sucessor {} [{}, {}]: {}", src, successor, srcNode, tgtNode, srcFeatures);

		if(srcFeatures == null) {
			return EdgeIdentity.v();
		}
		
		boolean isFallThroughEdge = false;
		if(successor instanceof Unit) {
			Unit succUnit = (Unit) successor;
			isFallThroughEdge = icfg.isFallThroughSuccessor(src,succUnit);
		}		
		
		boolean canFallThrough = !isCall && src.fallsThrough();
		
		boolean isTarget = originalFlowFunction.computeTargets(srcNode).contains(tgtNode);
		boolean createPosFromFeatures = isTarget && (isFallThroughEdge || !canFallThrough);
		
		//boolean originalCase = isTarget && !(isFallThroughEdge && !canFallThrough);
		
		boolean inprecise = false;
		
		boolean positive = true;
		if(src instanceof IfStmt && !srcFeatures.equals(Constraint.<String>trueValue()) && helper.isPrecise(src)) {
			IfStmt ifstmt = (IfStmt) src;
			Value condition = ifstmt.getConditionBox().getValue();
			
			if(condition instanceof NeExpr) {
				Value op2 = ((AbstractBinopExpr)condition).getOp2();
				
				if(op2 instanceof NullConstant) {
//					positive = false;
				} else if(op2 instanceof NumericConstant && ((IntConstant)op2).value == 0) {
					positive = false;
				}

			}
			if(condition instanceof BinopExpr) {
				Value op2 = ((AbstractBinopExpr)condition).getOp2();
				if(op2 instanceof IntConstant && ((IntConstant)op2).value > 1) {
					inprecise = true;
				}
			}
		}
		
		// For imprecisly tracked variables, always use positive form in branch edge and negative form in fall through edge
		if(!positive) {
			synchronized (Constraint.FACTORY) {
				for(int index : srcFeatures.getBDD().scanSet()) {
					if(!helper.isPrecise(index)) {
						positive = true;
					}
				}
			}

		}
		
		
		if(!positive) {
			srcFeatures = Constraint.<String>negate(srcFeatures);
		}
		
		Constraint<String> pos = createPosFromFeatures ?
				srcFeatures :
				Constraint.<String>falseValue();
				
		/*Constraint<String> neg = srcNode==tgtNode && isFallThroughEdge ?
				Constraint.<String>make(features,false):
				Constraint.<String>falseValue();*/

		Constraint<String> neg = !(isFallThroughEdge || !canFallThrough) ?
				Constraint.<String>negate(srcFeatures):
				Constraint.<String>falseValue();
				
//		if(inprecise) {
//			neg = srcFeatures;
//		}
					
		Constraint<String> lifted = pos.or(neg);
		
		if(srcFeatures != null) {
			//logger.info("Src: {}, Successor {}, srcNode {}, lifted {}", src, successor, srcNode, lifted);
		}
		
		/*
		logger.info("buildFlowFunction:: src {}, sucessor {} [{}, {}]", src, successor, srcNode, tgtNode);
		logger.info("Is identity edge? {}", srcNode==tgtNode);
		logger.info("isFallThroughEdge? {}", isFallThroughEdge);
		logger.info("pos {} || neg {} => {}", pos, neg, lifted);
		*/
		
		return new SPLFeatureFunction(lifted);
	}
	
}