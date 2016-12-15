package soot.jimple.infoflow.loadtime;

import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.PseudoTopologicalOrderer;
import heros.solver.TopologicalSorter;

public class SootTopologicalSorter implements TopologicalSorter<Unit, SootMethod> {

	private BiDiInterproceduralCFG<Unit, SootMethod> icfg;

	public SootTopologicalSorter(BiDiInterproceduralCFG<Unit, SootMethod> icfg) {
		this.icfg = icfg;
	}
	
	@Override
	public List<Unit> getPseudoTopologicalOrder(SootMethod method) {
		PseudoTopologicalOrderer<Unit> pseudoTopologicalOrderer = new PseudoTopologicalOrderer<Unit>();
		
//		DirectedGraph<Unit> unitGraph = icfg.getOrCreateUnitGraph(method);^qyed
		
		// assume single start unit
		Unit start = icfg.getStartPointsOf(method).iterator().next();
		CompleteICFG completeICFG = new CompleteICFG(icfg, start);
		
		List<Unit> order = pseudoTopologicalOrderer.newList(completeICFG, false);
		
		return null;
	}
}
