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

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


/**
 * The IDE algorithm uses a list of jump functions. Instead of a list, we use a set of three
 * maps that are kept in sync. This allows for efficient indexing: the algorithm accesses
 * elements from the list through three different indices.
 */
@ThreadSafe
public class JumpFunctions<N,D,L> {
	@DontSynchronize("immutable")	
	private final EdgeFunction<L> allTop;
	
	// TODO: Memory is never freed. We need to delete from data and update indices.
//	private Map<PathEdge<N,D>, EdgeFunction<L>> data = new ConcurrentHashMap<PathEdge<N,D>, EdgeFunction<L>>(100000, 0.8f, 1);
//	private Map<N, Set<PathEdge<N,D>>> index = new ConcurrentHashMap<N, Set<PathEdge<N,D>>>(100000, 0.8f, 1);
	
//	private Map<N, Map<PathEdge<N,D>, EdgeFunction<L>>> index2 = new ConcurrentHashMap<N, Map<PathEdge<N,D>, EdgeFunction<L>>>(100000, 0.75f, 1);

	private Map<N, Map<PathEdge<N,D>, EdgeFunction<L>>> newdata = new ConcurrentHashMap<>();
	
	//DEBUG
//	private Collection<PathEdge<N,D>> history = new LinkedList<>();
	
//	private Map<N, ReadWriteLock> locks = new ConcurrentHashMap<>();
	
//	private LoadingCache<N, Set<PathEdge<N,D>>> indexCache;
	
	public JumpFunctions(EdgeFunction<L> allTop) {
		this.allTop = allTop;
		
//		indexCache = CacheBuilder.newBuilder()
//				.build(
//						new CacheLoader<N, Set<PathEdge<N,D>>>() {
//
//							@Override
//							public Set<PathEdge<N, D>> load(N key) throws Exception {
//								Set<PathEdge<N, D>> pathEdges = new HashSet<PathEdge<N, D>>();
//								for(PathEdge<N, D> pathEdge : data.keySet())
//								{
//									if(pathEdge.getTarget().equals(key)) {
//										pathEdges.add(pathEdge);
//									}
//								}
//								return pathEdges;
//							}
//							
//						});
		
	}

	private int count = 0;

	/**
	 * Records a jump function. The source statement is implicit.
	 * @see PathEdge
	 */
	public synchronized void addFunction(D sourceVal, N target, D targetVal, EdgeFunction<L> function) {
		assert sourceVal!=null;
		assert target!=null;
		assert targetVal!=null;
		assert function!=null;
		
		//we do not store the default function (all-top)
		if(function.equalTo(allTop)) return;
		
		PathEdge<N,D> key = new PathEdge<N,D>(sourceVal, target, targetVal);

		Map<PathEdge<N,D>, EdgeFunction<L>> edges = newdata.get(target);
		
		
		if(edges == null) {
			edges = new HashMap<PathEdge<N,D>, EdgeFunction<L>>();
			
			// Initialize with empty hash map
			newdata.put(target, edges);
//			locks.putIfAbsent(target, new ReentrantReadWriteLock());
		}
		
//		Lock lock = locks.get(target).writeLock();
//		lock.lock();
		
//		data.put(key, function);
//		
//		
//		if(!index.containsKey(target)) {
//			index.put(target, Collections.newSetFromMap(new ConcurrentHashMap<PathEdge<N,D>, Boolean>()));
//		}
//		index.get(target).add(key);
			
		
//		if(!index2.containsKey(target)) {
//			index2.put(target, new ConcurrentHashMap<PathEdge<N,D>, EdgeFunction<L>>());
//		}
//		index2.get(target).put(key, function);
		
		// Add function
		EdgeFunction<L> oldValue = edges.put(key, function);
		if(oldValue == null) {
			count++;
		}
		
//		history.add(key);
		
//	
//		if(!CollectionUtils.isEqualCollection(index.get(target), newdata.get(target).keySet())) {
//			throw new RuntimeException();
//		}
//		
//		lock.unlock();
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
	public Collection<Map.Entry<D,EdgeFunction<L>>> reverseLookup(N target, D targetVal) {
		assert target!=null;
		assert targetVal!=null;
	
//		ReadWriteLock lock = locks.get(target);
//		if(lock == null) {
//			locks.putIfAbsent(target, new ReentrantReadWriteLock());
//			lock = locks.get(target);
//		}
//		lock.readLock().lock();
		
//		Map<D,EdgeFunction<L>> res2 = new HashMap<D,EdgeFunction<L>>();
//		
//		Set<PathEdge<N, D>> edges = index.get(target);
//		if(edges != null) {
//			for(PathEdge<N,D> pathEdge : edges) {
//				if(pathEdge.dTarget == targetVal) {
//					res2.put(pathEdge.dSource, data.get(pathEdge));
//				}
//			}
//		}
		
		
		// Working (but slow hash-map based implementation)
//		Map<D,EdgeFunction<L>> res3 = new HashMap<D,EdgeFunction<L>>();
//		
//		for(Entry<PathEdge<N, D>, EdgeFunction<L>> entry : newdata.get(target).entrySet())
//		{
//			if(entry.getKey().factAtTarget() == targetVal) {
//				res3.put(entry.getKey().factAtSource(), entry.getValue());
//			}
//		}
		
		// New list-based implementation
		Collection<Map.Entry<D,EdgeFunction<L>>> res3 = new LinkedList<>();
		
		for(Entry<PathEdge<N, D>, EdgeFunction<L>> entry : newdata.get(target).entrySet())
		{
			if(entry.getKey().factAtTarget() == targetVal) {
				res3.add(new AbstractMap.SimpleEntry<D,EdgeFunction<L>>(entry.getKey().factAtSource(), entry.getValue()));
			}
		}
		
//		lock.readLock().unlock();
		
//		if(!CollectionUtils.isEqualCollection(res2.entrySet(), res3.entrySet())) {
//			throw new RuntimeException("Collection not equal");
//		}
		
//		Set<PathEdge<N, D>> pathEdges = data.entrySet().stream().parallel()
//		.filter((entry) -> { return entry.getKey().getTarget().equals(target); })
//		.map(Entry<PathEdge<N,D>, EdgeFunction<L>>::getKey)
//		.collect(Collectors.toSet());
		
		return res3;
	
	}
	
	/**
	 * Returns, for a given source value and target statement all
	 * associated target values, and for each the associated edge function. 
     * The return value is a mapping from target value to function.
	 */
	public Map<D,EdgeFunction<L>> forwardLookup(D sourceVal, N target) {
		assert sourceVal!=null;
		assert target!=null;

//		ReadWriteLock lock = locks.get(target);
//		if(lock == null) {
//			locks.putIfAbsent(target, new ReentrantReadWriteLock());
//			lock = locks.get(target);
//		}
//		lock.readLock().lock();
		
//		Map<D, EdgeFunction<L>> res2 = new HashMap<D,EdgeFunction<L>>();
//		
//		Set<PathEdge<N, D>> edges = index.get(target);
//		if(edges != null) {
//			for(PathEdge<N,D> pathEdge : edges) {
//				if(pathEdge.dSource.equals(sourceVal)) {
//					res2.put(pathEdge.dTarget, data.get(pathEdge));
//				}
//			}
//		}
		
		Map<D,EdgeFunction<L>> res3 = new HashMap<D,EdgeFunction<L>>();
		Map<PathEdge<N, D>, EdgeFunction<L>> edges = newdata.get(target);
		if(edges != null) {
			for(Entry<PathEdge<N, D>, EdgeFunction<L>> entry : edges.entrySet())
			{
				if(entry.getKey().factAtSource().equals(sourceVal)) {
					res3.put(entry.getKey().factAtTarget(), entry.getValue());
				}
			}
		}
//		lock.readLock().unlock();
		
//		if(!CollectionUtils.isEqualCollection(res2.entrySet(), res3.entrySet())) {
//			throw new RuntimeException("forwardLookup: Collection not equal");
//		}
//		
		return res3;

	}
	
	/**
	 * Returns for a given target statement all jump function records with this target.
	 * The return value is a set of records of the form (sourceVal,targetVal,edgeFunction).
	 */
	public Map<PathEdge<N, D>, EdgeFunction<L>> lookupByTarget(N target) {
//
//		ReadWriteLock lock = locks.get(target);
//		if(lock == null) {
//			locks.putIfAbsent(target, new ReentrantReadWriteLock());
//			lock = locks.get(target);
//		}
//		lock.readLock().lock();
		
		Map<PathEdge<N, D>, EdgeFunction<L>> pathEdges2 = newdata.get(target);
		if(pathEdges2 == null) {
			pathEdges2 = Collections.emptyMap();
		}
		
//		lock.readLock().unlock();

		return pathEdges2;
	}
	
	public boolean edgeExists(PathEdge<N,D> edge)
	{
//		return data.containsKey(edge);
		Map<PathEdge<N, D>, EdgeFunction<L>> edges = newdata.get(edge.getTarget());
		return edges != null && edges.containsKey(edge);
	}
	
	public EdgeFunction<L> getFunction(PathEdge<N, D> edge)
	{
		Map<PathEdge<N, D>, EdgeFunction<L>> edges = newdata.get(edge.getTarget());
		if(edges != null) {		
//			Optional<Entry<PathEdge<N, D>, EdgeFunction<L>>> result = edges.entrySet().stream().filter(entry -> entry.getKey().equals(edge)).findFirst();
//			
//			if(result.isPresent()) {
//				return result.get().getValue();
//			} else {
//				return null;
//			}
			return edges.get(edge);
		} else {
			return null;
		}
	}

	/**
	 * Removes all jump functions
	 */
	public synchronized void clear() {

	}

	public int getTargetCount() {
		return newdata.keySet().size();
	}
	
	//temp
//	public Collection<PathEdge<N, D>> debug_getHistoryData()
//	{
//		return history;
//	}
}
