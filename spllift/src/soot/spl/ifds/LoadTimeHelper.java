package soot.spl.ifds;

import heros.EdgeFunction;
import heros.solver.PathEdge;

import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.IfStmt;
import soot.tagkit.Host;

public interface LoadTimeHelper<D> {
	public Collection<ConstraintSet> getFeaturesForFlow(Unit src, Host successor, D srcNode, D tgtNode, SootMethod method, D zeroValue, Collection<Entry<PathEdge<Unit, D>, EdgeFunction<IConstraint>>> matchingAbstractions);
	public boolean hasFeatures(Unit src, Host successor, D srcNode, D tgtNode);
	public boolean vetoNewFunction(D sourceVal, Unit target, D targetVal, EdgeFunction<IConstraint> f, D zeroValue);
	public boolean isPrecise(Unit src);
	boolean isPrecise(int index);
	public Set<Integer> trackPrecise();
	public Map<Integer, String> getFeatureNames();
	Collection<Entry<PathEdge<Unit, D>, EdgeFunction<IConstraint>>> findMatchingAbstractions(Unit ifStmt);
	public D deriveAntiAbstraction(D abstraction);
	public boolean isAntiAbstraction(D abstraction);
	public void cleanEdgeList(Collection<PathEdge<Unit, D>> edges);
}
