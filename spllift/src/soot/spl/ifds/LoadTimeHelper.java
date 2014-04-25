package soot.spl.ifds;

import heros.EdgeFunction;
import heros.solver.DefinedVariable;

import java.util.BitSet;
import java.util.Map;
import java.util.Set;

import net.sf.javabdd.BDD;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.tagkit.Host;

public interface LoadTimeHelper<D, SootValue> extends DefinedVariable<D,SootValue> {
	public Constraint<String> getFeaturesForFlow(Unit src, Host successor, D srcNode, D tgtNode, SootMethod method, D zeroValue);
	public boolean hasFeatures(Unit src, Host successor, D srcNode, D tgtNode);
	public boolean vetoNewFunction(D sourceVal, Unit target, D targetVal, EdgeFunction<Constraint<String>> f, D zeroValue);
	public boolean isPrecise(Unit src);
	boolean isPrecise(int index);
	public Set<Integer> trackPrecise();
}
