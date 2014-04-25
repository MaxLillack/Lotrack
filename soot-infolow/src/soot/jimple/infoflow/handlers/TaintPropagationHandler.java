package soot.jimple.infoflow.handlers;

import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

/**
 * Handler interface for callbacks during taint propagation
 * 
 * @author Steven Arzt
 * @author Malte Viering
 */
public interface TaintPropagationHandler {
	
	/**
	 * Enumeration containing the supported types of data flow edges
	 */
	public enum FlowFunctionType {
		NormalFlowFunction,
		CallFlowFunction,
		CallToReturnFlowFunction,
		ReturnFlowFunction
	}

	/**
	 * Handler function that is invoked when a taint is proagated in the data
	 * flow engine
	 * @param stmt The statement over which the taint is propagated
	 * @param taints The set of taints being propagated
	 * @param cfg The interprocedural control flow graph containing the current
	 * method
	 * @param type The type of data flow edge being processed
	 */
	public void notifyFlowIn
			(Unit stmt,
			Set<Abstraction> taints,
			BiDiInterproceduralCFG<Unit, SootMethod> cfg,
			FlowFunctionType type);

	/**
	 * Handler function that is invoked when a new taint is generated in the data
	 * flow engine
	 * @param stmt The statement over which the taint is propagated
	 * @param taints The set of taints being propagated
	 * @param cfg The interprocedural control flow graph containing the current
	 * method
	 * @param type The type of data flow edge being processed
	 */
	public void notifyFlowOut
			(Unit stmt,
			Set<Abstraction> taints,
			BiDiInterproceduralCFG<Unit, SootMethod> cfg,
			FlowFunctionType type);

	
}
