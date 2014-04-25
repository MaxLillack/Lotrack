/*******************************************************************************
 * Copyright (c) 2013 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package heros.solver;

import heros.EdgeFunction;
import heros.IFDSTabulationProblem;
import heros.InterproceduralCFG;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ForkJoinTask;

import com.google.common.collect.Maps;

/**
 * An {@link IFDSSolver} that tracks paths for reporting. To do so, it requires that data-flow abstractions implement the LinkedNode interface.
 * The solver implements a cache of data-flow facts for each statement and source value. If for the same statement and source value the same
 * target value is seen again (as determined through a cache hit), then the solver propagates the cached value but at the same time links
 * both target values with one another.
 *  
 * @author Eric Bodden
 */
public class PathTrackingIFDSSolver<N, D extends LinkedNode<D>, M, I extends InterproceduralCFG<N, M>, SootValue> extends IFDSSolver<N, D, M, I, SootValue> {

	public PathTrackingIFDSSolver(IFDSTabulationProblem<N, D, M, I> ifdsProblem) {
		super(ifdsProblem);
	}

	protected final Map<CacheEntry, LinkedNode<D>> cache = Maps.newHashMap();
	
	@Override
	protected NewPathEdgeProcessingTask propagate(D sourceVal, N target, D targetVal, EdgeFunction<IFDSSolver.BinaryDomain> f, N relatedCallSite, boolean isUnbalancedReturn, Set<N> joinPoints) {
		CacheEntry currentCacheEntry = new CacheEntry(target, sourceVal, targetVal);

		//logger.info("SourceVal {}, target {}, targetVal{}", sourceVal, target, targetVal);
		
		boolean propagate = false;
		synchronized (this) {
			if (cache.containsKey(currentCacheEntry)) {
				LinkedNode<D> existingTargetVal = cache.get(currentCacheEntry);
				if (existingTargetVal != targetVal)
					existingTargetVal.addNeighbor(targetVal);
			} else {
				cache.put(currentCacheEntry, targetVal);
				propagate = true;
			}
		}

		if (propagate) {
			NewPathEdgeProcessingTask task = super.propagate(sourceVal, target, targetVal, f, relatedCallSite, isUnbalancedReturn, joinPoints);
			if(task != null) {
				task.invoke();
			}
		}
		return null;
	};
	
	
	private class CacheEntry {
		private N n;
		private D sourceVal;
		private D targetVal;

		public CacheEntry(N n, D sourceVal, D targetVal) {
			super();
			this.n = n;
			this.sourceVal = sourceVal;
			this.targetVal = targetVal;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((sourceVal == null) ? 0 : sourceVal.hashCode());
			result = prime * result + ((targetVal == null) ? 0 : targetVal.hashCode());
			result = prime * result + ((n == null) ? 0 : n.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			@SuppressWarnings({ "unchecked" })
			CacheEntry other = (CacheEntry) obj;
			if (sourceVal == null) {
				if (other.sourceVal != null)
					return false;
			} else if (!sourceVal.equals(other.sourceVal))
				return false;
			if (targetVal == null) {
				if (other.targetVal != null)
					return false;
			} else if (!targetVal.equals(other.targetVal))
				return false;
			if (n == null) {
				if (other.n != null)
					return false;
			} else if (!n.equals(other.n))
				return false;
			return true;
		}
	}	
	


}
