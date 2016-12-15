package soot.jimple.infoflow.loadtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;

public class CompleteICFG implements DirectedGraph<Unit> {
	
	Set<Unit> units = new HashSet<Unit>();
	Multimap<Unit, Unit> forward = HashMultimap.create();
	Multimap<Unit, Unit> backward = HashMultimap.create();
	
	// Graph should only be used to determine ordner
	// Selection of edges may be additionally supported by points-to analysis
	public CompleteICFG(BiDiInterproceduralCFG<Unit, SootMethod> icfg, Unit start) {
		Stack<Unit> todo = new Stack<Unit>();
		
		// map method to return sites
		Stack<Collection<Unit>> returnSites = new Stack<Collection<Unit>>();
		
		// init
		todo.add(start);
		
		while(!todo.isEmpty())
		{
			Unit unit = todo.pop();
			if(!units.contains(unit)) {
				units.add(unit);
				
				// call edges
				if(icfg.isCallStmt(unit)) {
					boolean returnSitesExist = false;
					
					Set<Unit> rs = new HashSet<Unit>();
					
					Collection<SootMethod> methods = icfg.getCalleesOfCallAt(unit);
					for(SootMethod method : methods) {
						for(Unit startPointsOf : icfg.getStartPointsOf(method)) {
							if(startPointsOf != unit) {
								forward.put(unit, startPointsOf);
								backward.put(startPointsOf, unit);
								todo.push(startPointsOf);
								rs.addAll(icfg.getReturnSitesOfCallAt(unit));
							}
						}
					}
					
					if(!rs.isEmpty()) {
						returnSitesExist = true;
						returnSites.push(rs);
					}
					
					
					if(!returnSitesExist)
					{
						// handle as normal edge
						for(Unit successor : icfg.getSuccsOf(unit))
						{
							forward.put(unit, successor);
							backward.put(successor, unit);
							todo.push(successor);
						}
					}
					
				} else if(icfg.isExitStmt(unit)) {
					// return (exit) edges (from call to return sites in same method OR )
					
					// lookup return sites
					Collection<Unit> rs = returnSites.pop();
					
					for(Unit returnSite : rs)
					{
						forward.put(unit, returnSite);
						backward.put(returnSite, unit);
						todo.push(returnSite);
					}
				} else {
					// normal edges
					for(Unit successor : icfg.getSuccsOf(unit))
					{
						forward.put(unit, successor);
						backward.put(successor, unit);
						todo.push(successor);
					}
				}
				
			} else {
				// duplicate
				System.out.println("Duplicate " + unit);
			}
		}
		
	}

	@Override
	public List<Unit> getHeads() {
		// All units which are not keys in backward
		List<Unit> heads = new ArrayList<Unit>(units);
		heads.removeAll(backward.keySet());
		
		return heads;
	}

	@Override
	public List<Unit> getTails() {
		// All units which are not keys in forward
		// TODO Auto-generated method stub
		List<Unit> tails = new ArrayList<Unit>(units);
		tails.removeAll(forward.keySet());
		
		return tails;
	}

	@Override
	public List<Unit> getPredsOf(Unit s) {
		return new ArrayList<Unit>(backward.get(s));
	}

	@Override
	public List<Unit> getSuccsOf(Unit s) {
		return new ArrayList<Unit>(forward.get(s));
	}

	@Override
	public int size() {
		return units.size();
	}

	@Override
	public Iterator<Unit> iterator() {
		return units.iterator();
	}

}
