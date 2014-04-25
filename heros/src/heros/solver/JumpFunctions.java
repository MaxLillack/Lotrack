/*******************************************************************************
 * Copyright (c) 2012 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package heros.solver;

import heros.DontSynchronize;
import heros.EdgeFunction;
import heros.ThreadSafe;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import java.util.Set;


import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;
import com.sun.swing.internal.plaf.synth.resources.synth;


/**
 * The IDE algorithm uses a list of jump functions. Instead of a list, we use a set of three
 * maps that are kept in sync. This allows for efficient indexing: the algorithm accesses
 * elements from the list through three different indices.
 */
@ThreadSafe
public class JumpFunctions<N,D,L, SootValue> {
	@DontSynchronize("immutable")	
	private final EdgeFunction<L> allTop;
	
	private Map<PathEdge<N,D>, EdgeFunction<L>> data = new ConcurrentHashMap<PathEdge<N,D>, EdgeFunction<L>>(100000, 0.75f, 1);
	private Map<N, Set<PathEdge<N,D>>> index = new ConcurrentHashMap<N, Set<PathEdge<N,D>>>(100000, 0.75f, 1);
	private Map<N, Map<PathEdge<N,D>, EdgeFunction<L>>> index2 = new ConcurrentHashMap<N, Map<PathEdge<N,D>, EdgeFunction<L>>>(100000, 0.75f, 1);

	private Set<SootValue> definedValues = Collections.newSetFromMap(new ConcurrentHashMap<SootValue,Boolean>());
	
	public JumpFunctions(EdgeFunction<L> allTop) {
		this.allTop = allTop;
	}

	private int count = 0;
	private PathEdge<N,D> previous = null;
	
	/**
	 * Records a jump function. The source statement is implicit.
	 * @see PathEdge
	 */
	public synchronized void addFunction(D sourceVal, N target, D targetVal, EdgeFunction<L> function, SootValue definedValue) {
		assert sourceVal!=null;
		assert target!=null;
		assert targetVal!=null;
		assert function!=null;
		
		//we do not store the default function (all-top)
		if(function.equalTo(allTop)) return;
		
		PathEdge<N,D> key = new PathEdge<N,D>(sourceVal, target, targetVal);
		data.put(key, function);

		if(!index.containsKey(target)) {
			index.put(target, Collections.newSetFromMap(new ConcurrentHashMap<PathEdge<N,D>, Boolean>()));
		}
		index.get(target).add(key);
		
		if(!index2.containsKey(target)) {
			index2.put(target, new ConcurrentHashMap<PathEdge<N,D>, EdgeFunction<L>>());
		}
		index2.get(target).put(key, function);
		
		if(definedValue != null) {
			definedValues.add(definedValue);
		}
		
		count++;
	}
	
	public boolean valueIsDefined(SootValue v)
	{
		if(v == null) {
			return false;
		}
		return definedValues.contains(v);
	}
	
	public int getCount()
	{
		return count;
	}
	
	/**
     * Returns, for a given target statement and value all associated
     * source values, and for each the associated edge function.
     * The return value is a mapping from source value to function.
	 */
	public Map<D,EdgeFunction<L>> reverseLookup(N target, D targetVal) {
		assert target!=null;
		assert targetVal!=null;
		
		Map<D,EdgeFunction<L>> res2 = new HashMap<D,EdgeFunction<L>>();
		
		Set<PathEdge<N, D>> edges = index.get(target);
		if(edges != null) {
			for(PathEdge<N,D> pathEdge : edges) {
				if(pathEdge.dTarget == targetVal) {
					res2.put(pathEdge.dSource, data.get(pathEdge));
				}
			}
		}
		return res2;
	}
	
	/**
	 * Returns, for a given source value and target statement all
	 * associated target values, and for each the associated edge function. 
     * The return value is a mapping from target value to function.
	 */
	public Map<D,EdgeFunction<L>> forwardLookup(D sourceVal, N target) {
		assert sourceVal!=null;
		assert target!=null;

		Map<D, EdgeFunction<L>> res2 = new ConcurrentHashMap<D,EdgeFunction<L>>();
		
		Set<PathEdge<N, D>> edges = index.get(target);
		if(edges != null) {
			for(PathEdge<N,D> pathEdge : edges) {
				if(pathEdge.dSource == sourceVal) {
					res2.put(pathEdge.dTarget, data.get(pathEdge));
				}
			}
		}
		
		return res2;
	}
	
	/**
	 * Returns for a given target statement all jump function records with this target.
	 * The return value is a set of records of the form (sourceVal,targetVal,edgeFunction).
	 */
	public Map<PathEdge<N,D>, EdgeFunction<L>> lookupByTarget(N target) {
		return index2.get(target);
	}
	
	public boolean edgeExists(PathEdge<N,D> edge)
	{
		return data.containsKey(edge);
	}
	
	public EdgeFunction<L> getFunction(PathEdge<N, D> edge)
	{
		return data.get(edge);
	}
	
	public Set<PathEdge<N, D>> slowAllEdges()
	{
		return data.keySet();
	}

	/**
	 * Removes all jump functions
	 */
	public synchronized void clear() {

	}

}
