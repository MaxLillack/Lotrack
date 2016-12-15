/*******************************************************************************
 * Copyright (c) 2012 Eric Bodden.
 * Copyright (c) 2013 Tata Consultancy Services & Ecole Polytechnique de Montreal
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 *     Marc-Andre Laverdiere-Papineau - Fixed race condition
 ******************************************************************************/
package heros.solver;


import heros.DontSynchronize;
import heros.EdgeFunction;
import heros.EdgeFunctionCache;
import heros.EdgeFunctions;
import heros.FlowFunction;
import heros.FlowFunctionCache;
import heros.FlowFunctions;
import heros.IDETabulationProblem;
import heros.InterproceduralCFG;
import heros.JoinLattice;
import heros.Neo4JConnector;
import heros.PGSQLConnector;
import heros.SynchronizedBy;
import heros.ZeroedFlowFunctions;
import heros.edgefunc.EdgeIdentity;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.CacheBuilder;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.common.collect.Table.Cell;

/**
 * Solves the given {@link IDETabulationProblem} as described in the 1996 paper by Sagiv,
 * Horwitz and Reps. To solve the problem, call {@link #solve()}. Results can then be
 * queried by using {@link #resultAt(Object, Object)} and {@link #resultsAt(Object)}.
 * 
 * Note that this solver and its data structures internally use mostly {@link java.util.LinkedHashSet}s
 * instead of normal {@link HashSet}s to fix the iteration order as much as possible. This
 * is to produce, as much as possible, reproducible benchmarking results. We have found
 * that the iteration order can matter a lot in terms of speed.
 *
 * @param <N> The type of nodes in the interprocedural control-flow graph. 
 * @param <D> The type of data-flow facts to be computed by the tabulation problem.
 * @param <M> The type of objects used to represent methods.
 * @param <V> The type of values to be computed along flow edges.
 * @param <I> The type of inter-procedural control-flow graph being used.
 */
public class IDESolver<N,D,M,V,I extends InterproceduralCFG<N, M>> {
	
	public static CacheBuilder<Object, Object> DEFAULT_CACHE_BUILDER = CacheBuilder.newBuilder().concurrencyLevel(Runtime.getRuntime().availableProcessors()).initialCapacity(10000).softValues();
	
    protected static final Logger logger = LoggerFactory.getLogger(IDESolver.class);

    //enable with -Dorg.slf4j.simpleLogger.defaultLogLevel=trace
    public static final boolean DEBUG = logger.isDebugEnabled();

	protected CountingThreadPoolExecutor executor;
	
	@DontSynchronize("only used by single thread")
	protected int numThreads;
	
	@SynchronizedBy("thread safe data structure, consistent locking when used")
	protected final JumpFunctions<N,D,V> jumpFn;
	
	@SynchronizedBy("thread safe data structure, only modified internally")
	protected final I icfg;
	
	//stores summaries that were queried before they were computed
	//see CC 2010 paper by Naeem, Lhotak and Rodriguez
	@SynchronizedBy("consistent lock on 'incoming'")
	protected final Table<N,D,Table<N,D,EdgeFunction<V>>> endSummary = HashBasedTable.create();

	//edges going along calls
	//see CC 2010 paper by Naeem, Lhotak and Rodriguez
	@SynchronizedBy("consistent lock on field")
	protected final Table<N,D,Map<N,Set<D>>> incoming = HashBasedTable.create();
	
	@DontSynchronize("stateless")
	protected final FlowFunctions<N, D, M> flowFunctions;

	@DontSynchronize("stateless")
	protected final EdgeFunctions<N,D,M,V> edgeFunctions;

	@DontSynchronize("only used by single thread")
	protected final Map<N,Set<D>> initialSeeds;

	@DontSynchronize("stateless")
	protected final JoinLattice<V> valueLattice;
	
	@DontSynchronize("stateless")
	protected final EdgeFunction<V> allTop;

	@SynchronizedBy("consistent lock on field")
	public final Table<N,D,V> val = HashBasedTable.create();	
	
	@DontSynchronize("benign races")
	public long flowFunctionApplicationCount;

	@DontSynchronize("benign races")
	public long flowFunctionConstructionCount;
	
	@DontSynchronize("benign races")
	public long propagationCount;
	
	@DontSynchronize("benign races")
	public long durationFlowFunctionConstruction;
	
	@DontSynchronize("benign races")
	public long durationFlowFunctionApplication;

	@DontSynchronize("stateless")
	protected final D zeroValue;
	
	@DontSynchronize("readOnly")
	protected final FlowFunctionCache<N,D,M> ffCache; 

	@DontSynchronize("readOnly")
	protected final EdgeFunctionCache<N,D,M,V> efCache;

	@DontSynchronize("readOnly")
	protected final boolean followReturnsPastSeeds;

	@DontSynchronize("readOnly")
	protected final boolean computeValues;
	
	private ForkJoinPool forkJoinPool = new ForkJoinPool();
	
	private boolean enableNeo4Jlogging = false;
	private boolean enablePGSQLlogging = false;
	private boolean enableAntiAbstraction = true;
	private Neo4JConnector<N,D> neo4j;
	private PGSQLConnector<N,D,I,M> pgsql;
	
	/**
	 * Creates a solver for the given problem, which caches flow functions and edge functions.
	 * The solver must then be started by calling {@link #solve()}.
	 */
	public IDESolver(IDETabulationProblem<N,D,M,V,I> tabulationProblem) {
		//this(tabulationProblem, DEFAULT_CACHE_BUILDER, DEFAULT_CACHE_BUILDER);
		this(tabulationProblem, (CacheBuilder) null, null);
	}

	/**
	 * Creates a solver for the given problem, constructing caches with the given {@link CacheBuilder}. The solver must then be started by calling
	 * {@link #solve()}.
	 * @param flowFunctionCacheBuilder A valid {@link CacheBuilder} or <code>null</code> if no caching is to be used for flow functions.
	 * @param edgeFunctionCacheBuilder A valid {@link CacheBuilder} or <code>null</code> if no caching is to be used for edge functions.
	 */
	public IDESolver(IDETabulationProblem<N,D,M,V,I> tabulationProblem, @SuppressWarnings("rawtypes") CacheBuilder flowFunctionCacheBuilder, @SuppressWarnings("rawtypes") CacheBuilder edgeFunctionCacheBuilder) {
		if(logger.isDebugEnabled()) {
			if(flowFunctionCacheBuilder != null) {
				flowFunctionCacheBuilder = flowFunctionCacheBuilder.recordStats();
			}
			if(edgeFunctionCacheBuilder != null) {
				edgeFunctionCacheBuilder = edgeFunctionCacheBuilder.recordStats();
			}
		}
		this.zeroValue = tabulationProblem.zeroValue();
		this.icfg = tabulationProblem.interproceduralCFG();		
		FlowFunctions<N, D, M> flowFunctions = tabulationProblem.autoAddZero() ?
				new ZeroedFlowFunctions<N,D,M>(tabulationProblem.flowFunctions(), tabulationProblem.zeroValue()) : tabulationProblem.flowFunctions(); 
		EdgeFunctions<N, D, M, V> edgeFunctions = tabulationProblem.edgeFunctions();
		if(flowFunctionCacheBuilder!=null) {
			ffCache = new FlowFunctionCache<N,D,M>(flowFunctions, flowFunctionCacheBuilder);
			flowFunctions = ffCache;
		} else {
			ffCache = null;
		}
		if(edgeFunctionCacheBuilder!=null) {
			efCache = new EdgeFunctionCache<N,D,M,V>(edgeFunctions, edgeFunctionCacheBuilder);
			edgeFunctions = efCache;
		} else {
			efCache = null;
		}
		
		this.flowFunctions = flowFunctions;
		this.edgeFunctions = edgeFunctions;
		this.initialSeeds = tabulationProblem.initialSeeds();
		this.valueLattice = tabulationProblem.joinLattice();
		this.allTop = tabulationProblem.allTopFunction();
		this.jumpFn = new JumpFunctions<N,D,V>(allTop);
		this.followReturnsPastSeeds = tabulationProblem.followReturnsPastSeeds();
		this.numThreads = Math.max(1,tabulationProblem.numThreads());
		this.computeValues = tabulationProblem.computeValues();
		this.executor = getExecutor();
	}

	private class PerformanceMeter implements Runnable
	{
		int oldValue = 0;
		DecimalFormat formatter = new DecimalFormat("0.00");
		
		@Override
		public void run() {
			int currentValue = jumpFn.getCount();
			try {
				logger.info("{}*10^4 edges per 60 Seconds ({}*10^6 total) {} targets", (currentValue-oldValue)/10000, StringUtils.leftPad(formatter.format(currentValue/1000000f), 8), jumpFn.getTargetCount());
			} catch(Exception e) {
				logger.info("Exception performance meter " + e.getMessage());
			}
			oldValue = currentValue;
	
		}	
	}
	
	private ScheduledExecutorService logTimeoutExecutor = Executors.newScheduledThreadPool(1);
	private class LogTimeout implements Runnable
	{
		private Date startTime = new Date();
		private String name;
		
		public LogTimeout(String name)
		{
			this.name = name;
		}
		
		@Override
		public void run() {
			Date now = new Date();
			long secondsSinceStart = (now.getTime() - startTime.getTime()) / 1000;
			
			logger.info("{} - {} Seconds since start", name, secondsSinceStart);
		}	
	}

	
	/**
	 * Runs the solver on the configured problem. This can take some time.
	 */
	public void solve() {		
		//submitInitialSeeds();
		//awaitCompletionComputeValuesAndShutdown();
		//precomputePostdominators();
		
		ScheduledExecutorService performanceMonitorExecutor = Executors.newScheduledThreadPool(1);
		logger.info("add PerformanceMeter");
		performanceMonitorExecutor.scheduleAtFixedRate(new PerformanceMeter(), 60, 60, TimeUnit.SECONDS);
		

		// Test topo sort
//		topologicalSorter.getPseudoTopologicalOrder(icfg.getMethodOf(initialSeeds.keySet().iterator().next()));
		
		
		if(enableNeo4Jlogging) neo4j = new Neo4JConnector<N,D>();
		if(enablePGSQLlogging) pgsql = new PGSQLConnector<N,D,I,M>();
		
		forkJoinPool.invoke(new InitialSeeds());
		awaitCompletionComputeValuesAndShutdown();
		
		if(enableNeo4Jlogging) neo4j.writeQueue();
		if(enablePGSQLlogging) pgsql.writeQueue();
		
		forkJoinPool.shutdown();
		performanceMonitorExecutor.shutdown();
	}
	
	private class InitialSeeds extends RecursiveAction
	{
		private static final long serialVersionUID = -4139751440350761394L;

		@Override
		protected void compute() {
			
			Queue<NewPathEdgeProcessingTask> tasks = new LinkedList<NewPathEdgeProcessingTask>();
			
			HashSet<N> joinPoints = new LinkedHashSet<N>();
			
			if(initialSeeds.size() > 1) {
				throw new IllegalStateException("Only thinking about a single initial seed at the moment");
			}
			
			for(Entry<N, Set<D>> seed: initialSeeds.entrySet()) {
				N startPoint = seed.getKey();
				for(D val: seed.getValue()) {
					NewPathEdgeProcessingTask task = propagate(zeroValue, startPoint, val, EdgeIdentity.<V>v(), null, false, joinPoints);
					if(task != null) {
						tasks.add(task);
					}
				}
				//jumpFn.addFunction(zeroValue, startPoint, zeroValue, EdgeIdentity.<V>v());
			}
			
			do {
				LinkedList<ForkJoinTask<List<NewPathEdgeProcessingTask>>> toJoin = new LinkedList<ForkJoinTask<List<NewPathEdgeProcessingTask>>>();
				
				while(!tasks.isEmpty()) {
					NewPathEdgeProcessingTask task = tasks.poll();
					toJoin.add(task.fork());
				}
				
				while(!toJoin.isEmpty())
				{
					List<NewPathEdgeProcessingTask> newTasks = toJoin.poll().join();
					for(NewPathEdgeProcessingTask newTask : newTasks) {
						if(newTask != null) {
							tasks.add(newTask);
						}
					}
				}				
			} while(!tasks.isEmpty());
			
		}
	}

	/**
	 * Schedules the processing of initial seeds, initiating the analysis.
	 * Clients should only call this methods if performing synchronization on
	 * their own. Normally, {@link #solve()} should be called instead.
	 */
	protected void submitInitialSeeds() {
		for(Entry<N, Set<D>> seed: initialSeeds.entrySet()) {
			N startPoint = seed.getKey();
			for(D val: seed.getValue()) {
				propagate(zeroValue, startPoint, val, EdgeIdentity.<V>v(), null, false, null);
			}
			jumpFn.addFunction(zeroValue, startPoint, zeroValue, EdgeIdentity.<V>v());
		}
	}

	/**
	 * Awaits the completion of the exploded super graph. When complete, computes result values,
	 * shuts down the executor and returns.
	 */
	protected void awaitCompletionComputeValuesAndShutdown() {
		{
			final long before = System.currentTimeMillis();
			//run executor and await termination of tasks
			runExecutorAndAwaitCompletion();
			durationFlowFunctionConstruction = System.currentTimeMillis() - before;
		}
		if(computeValues) {
			final long before = System.currentTimeMillis();
			computeValues();
			durationFlowFunctionApplication = System.currentTimeMillis() - before;
		}
		if(logger.isDebugEnabled())
			printStats();
		
		//ask executor to shut down;
		//this will cause new submissions to the executor to be rejected,
		//but at this point all tasks should have completed anyway
		executor.shutdown();
		//similarly here: we await termination, but this should happen instantaneously,
		//as all tasks should have completed
		runExecutorAndAwaitCompletion();
	}

	/**
	 * Runs execution, re-throwing exceptions that might be thrown during its execution.
	 */
	private void runExecutorAndAwaitCompletion() {
		try {
			executor.awaitCompletion();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		Throwable exception = executor.getException();
		if(exception!=null) {
			throw new RuntimeException("There were exceptions during IDE analysis. Exiting.",exception);
		}
	}

    /**
     * Dispatch the processing of a given edge. It may be executed in a different thread.
     * @param edge the edge to process
     */
    protected void scheduleEdgeProcessing(PathEdge<N,D> edge){
    	// If the executor has been killed, there is little point
    	// in submitting new tasks
    	if (executor.isTerminating())
    		return;

    	executor.execute(new PathEdgeProcessingTask(edge));
    	propagationCount++;
    }
	
    /**
     * Dispatch the processing of a given value. It may be executed in a different thread.
     * @param vpt
     */
    private void scheduleValueProcessing(ValuePropagationTask vpt){
    	// If the executor has been killed, there is little point
    	// in submitting new tasks
    	if (executor.isTerminating())
    		return;
    	executor.execute(vpt);
    }
  
    /**
     * Dispatch the computation of a given value. It may be executed in a different thread.
     * @param task
     */
	private void scheduleValueComputationTask(ValueComputationTask task) {
    	// If the executor has been killed, there is little point
    	// in submitting new tasks
    	if (executor.isTerminating())
    		return;
		executor.execute(task);
	}
	
	/**
	 * Lines 13-20 of the algorithm; processing a call site in the caller's context.
	 * 
	 * For each possible callee, registers incoming call edges.
	 * Also propagates call-to-return flows and summarized callee flows within the caller. 
	 * 
	 * @param edge an edge whose target node resembles a method call
	 */
	private List<NewPathEdgeProcessingTask> processCall(PathEdge<N,D> edge, Collection<N> joinPoints) {
		final D d1 = edge.factAtSource();
		final N n = edge.getTarget(); // a call node; line 14...

		final D d2 = edge.factAtTarget();
		EdgeFunction<V> f = jumpFunction(edge);
		Collection<N> returnSiteNs = icfg.getReturnSitesOfCallAt(n);
		
		NewPathEdgeProcessingTask task = null;
		List<NewPathEdgeProcessingTask> tasks = new LinkedList<NewPathEdgeProcessingTask>();
		
		//for each possible callee
		Collection<M> callees = icfg.getCalleesOfCallAt(n);
		for(M sCalledProcN: callees) { //still line 14
			
			//compute the call-flow function
			FlowFunction<D> function = flowFunctions.getCallFlowFunction(n, sCalledProcN);
			flowFunctionConstructionCount++;
			Set<D> res = computeCallFlowFunction(function, d1, d2);			
			
			//for each callee's start point(s)
			Collection<N> startPointsOf = icfg.getStartPointsOf(sCalledProcN);
			for(N sP: startPointsOf) {
				
				if(enableNeo4Jlogging) neo4j.logEdge(sP, edge, "processCall");
				if(enablePGSQLlogging) pgsql.logEdge(sP, edge, "processCall", icfg);
				
				//for each result node of the call-flow function
				for(D d3: res) {
					//create initial self-loop
					// @TODO Changes fourth parameter -> check again
					task = propagate(d3, sP, d3, f, n, false, joinPoints); //line 15
					if(task != null) {
						tasks.add(task);
					}
					
					//register the fact that <sp,d3> has an incoming edge from <n,d2>
					Set<Cell<N, D, EdgeFunction<V>>> endSumm;
					synchronized (incoming) {
						//line 15.1 of Naeem/Lhotak/Rodriguez
						addIncoming(sP,d3,n,d2);
						//line 15.2, copy to avoid concurrent modification exceptions by other threads
						endSumm = new HashSet<Table.Cell<N,D,EdgeFunction<V>>>(endSummary(sP, d3));
					}
					
					//still line 15.2 of Naeem/Lhotak/Rodriguez
					//for each already-queried exit value <eP,d4> reachable from <sP,d3>,
					//create new caller-side jump functions to the return sites
					//because we have observed a potentially new incoming edge into <sP,d3>
					for(Cell<N, D, EdgeFunction<V>> entry: endSumm) {
						N eP = entry.getRowKey();
						D d4 = entry.getColumnKey();
						EdgeFunction<V> fCalleeSummary = entry.getValue();
						//for each return site
						for(N retSiteN: returnSiteNs) {					
							//compute return-flow function
							FlowFunction<D> retFunction = flowFunctions.getReturnFlowFunction(n, sCalledProcN, eP, retSiteN);
							flowFunctionConstructionCount++;
							//for each target value of the function
							for(D d5: computeReturnFlowFunction(retFunction, d4, n, Collections.singleton(d2))) {
								//update the caller-side summary function
								EdgeFunction<V> f4 = edgeFunctions.getCallEdgeFunction(n, d2, sCalledProcN, d3);
								EdgeFunction<V> f5 = edgeFunctions.getReturnEdgeFunction(n, sCalledProcN, eP, d4, retSiteN, d5);
								EdgeFunction<V> fPrime = f4.composeWith(fCalleeSummary).composeWith(f5);							
								task = propagate(d1, retSiteN, d5, f.composeWith(fPrime), n, false, joinPoints);
								if(task != null) {
									tasks.add(task);
								}
							}
						}
					}
				}		
			}
		}
		//line 17-19 of Naeem/Lhotak/Rodriguez		
		//process intra-procedural flows along call-to-return flow functions
		for (N returnSiteN : returnSiteNs) {
			if(enablePGSQLlogging) pgsql.logEdge(returnSiteN, edge, "returnSite", icfg);
			
			FlowFunction<D> callToReturnFlowFunction = flowFunctions.getCallToReturnFlowFunction(n, returnSiteN);
			flowFunctionConstructionCount++;
			for(D d3: computeCallToReturnFlowFunction(callToReturnFlowFunction, d1, d2)) {
				EdgeFunction<V> edgeFnE = edgeFunctions.getCallToReturnEdgeFunction(n, d2, returnSiteN, d3);
				task = propagate(d1, returnSiteN, d3, f.composeWith(edgeFnE), n, false, joinPoints);
				if(task != null) {
					tasks.add(task);
				}
			}
		}
		
		return tasks;
	}

	/**
	 * Computes the call flow function for the given call-site abstraction
	 * @param callFlowFunction The call flow function to compute
	 * @param d1 The abstraction at the current method's start node.
	 * @param d2 The abstraction at the call site
	 * @return The set of caller-side abstractions at the callee's start node
	 */
	protected Set<D> computeCallFlowFunction
			(FlowFunction<D> callFlowFunction, D d1, D d2) {
		return callFlowFunction.computeTargets(d2);
	}

	/**
	 * Computes the call-to-return flow function for the given call-site
	 * abstraction
	 * @param callToReturnFlowFunction The call-to-return flow function to
	 * compute
	 * @param d1 The abstraction at the current method's start node.
	 * @param d2 The abstraction at the call site
	 * @return The set of caller-side abstractions at the return site
	 */
	protected Set<D> computeCallToReturnFlowFunction
			(FlowFunction<D> callToReturnFlowFunction, D d1, D d2) {
		return callToReturnFlowFunction.computeTargets(d2);
	}
	
	/**
	 * Lines 21-32 of the algorithm.
	 * 
	 * Stores callee-side summaries.
	 * Also, at the side of the caller, propagates intra-procedural flows to return sites
	 * using those newly computed summaries.
	 * 
	 * @param edge an edge whose target node resembles a method exits
	 */
	protected List<NewPathEdgeProcessingTask> processExit(PathEdge<N,D> edge, Collection<N> joinPoints) {
		final N n = edge.getTarget(); // an exit node; line 21...
		EdgeFunction<V> f = jumpFunction(edge);
		M methodThatNeedsSummary = icfg.getMethodOf(n);
		
		//logger.info("Process exit of {}", methodThatNeedsSummary.toString());
		
		final D d1 = edge.factAtSource();
		final D d2 = edge.factAtTarget();
		
		List<NewPathEdgeProcessingTask> tasks = new LinkedList<NewPathEdgeProcessingTask>();
		
		//for each of the method's start points, determine incoming calls
		Collection<N> startPointsOf = icfg.getStartPointsOf(methodThatNeedsSummary);
		Map<N,Set<D>> inc = new HashMap<N,Set<D>>();
		for(N sP: startPointsOf) {
			//line 21.1 of Naeem/Lhotak/Rodriguez
			
			//register end-summary
			synchronized (incoming) {
				addEndSummary(sP, d1, n, d2, f);
				//copy to avoid concurrent modification exceptions by other threads
				for (Entry<N, Set<D>> entry : incoming(d1, sP).entrySet())
					inc.put(entry.getKey(), new HashSet<D>(entry.getValue()));
			}
		}
		
		//for each incoming call edge already processed
		//(see processCall(..))
		for (Entry<N,Set<D>> entry: inc.entrySet()) {
			//line 22
			N c = entry.getKey();
			//for each return site
			for(N retSiteC: icfg.getReturnSitesOfCallAt(c)) {
				
				if(enableNeo4Jlogging) neo4j.logEdge(retSiteC, edge, "processExit");
				if(enablePGSQLlogging) pgsql.logEdge(retSiteC, edge, "processExit", icfg);
				
				//compute return-flow function
				FlowFunction<D> retFunction = flowFunctions.getReturnFlowFunction(c, methodThatNeedsSummary,n,retSiteC);
				flowFunctionConstructionCount++;
				Set<D> targets = computeReturnFlowFunction(retFunction, d2, c, entry.getValue());
				//for each incoming-call value
				for(D d4: entry.getValue()) {
					
					Collection<Entry<D, EdgeFunction<V>>> valAndFuncs = jumpFn.reverseLookup(c,d4);
					
					//for each target value at the return site
					//line 23
					for(D d5: targets) {
						//compute composed function
						EdgeFunction<V> f4 = edgeFunctions.getCallEdgeFunction(c, d4, icfg.getMethodOf(n), d1);
						EdgeFunction<V> f5 = edgeFunctions.getReturnEdgeFunction(c, icfg.getMethodOf(n), n, d2, retSiteC, d5);
						EdgeFunction<V> fPrime = f4.composeWith(f).composeWith(f5);
						//for each jump function coming into the call, propagate to return site using the composed function
						//synchronized (jumpFn) { // some other thread might change jumpFn on the way
							for(Map.Entry<D,EdgeFunction<V>> valAndFunc: valAndFuncs) {
								EdgeFunction<V> f3 = valAndFunc.getValue();
								if(!f3.equalTo(allTop)) {
									D d3 = valAndFunc.getKey();
									NewPathEdgeProcessingTask task = propagate(d3, retSiteC, d5, f3.composeWith(fPrime), c, false, joinPoints);
									if(task != null) {
										tasks.add(task);
									}
								}
							}
						//}
					}
				}
			}
		}
		
		//handling for unbalanced problems where we return out of a method with a fact for which we have no incoming flow
		//note: we propagate that way only values that originate from ZERO, as conditionally generated values should only
		//be propagated into callers that have an incoming edge for this condition
		//logger.info("Checking {} for return past seed: {}", methodThatNeedsSummary.toString(), (followReturnsPastSeeds && inc.isEmpty() && d1.equals(zeroValue)));
		
		if(followReturnsPastSeeds && inc.isEmpty() && d1.equals(zeroValue)) {
			// only propagate up if we 
				Collection<N> callers = icfg.getCallersOf(methodThatNeedsSummary);
				for(N c: callers) {
					//logger.info("CallerOf: Method {} is called by {}", methodThatNeedsSummary.toString(), c.toString());
					for(N retSiteC: icfg.getReturnSitesOfCallAt(c)) {
						FlowFunction<D> retFunction = flowFunctions.getReturnFlowFunction(c, methodThatNeedsSummary,n,retSiteC);
						flowFunctionConstructionCount++;
						
						//logger.info("computeReturnFlowFunction for method {}", methodThatNeedsSummary.toString());
						//logger.info("computeReturnFlowFunction for edge with dTarget {}", edge.dTarget.toString());
						//logger.info("computeReturnFlowFunction for edge with target {}", edge.target.toString());
						//logger.info("computeReturnFlowFunction for at call site {}", c.toString());
						
						Set<D> targets = computeReturnFlowFunction(retFunction, d2, c, Collections.singleton(zeroValue));
						
						if(!targets.isEmpty())
						{
							//logger.info("targets created based current edge {}", edge.toString());
							//for(D target : targets) {
							//	logger.info("Target: {}", target.toString());
							//}
						}
						for(D d5: targets) {
							EdgeFunction<V> f5 = edgeFunctions.getReturnEdgeFunction(c, icfg.getMethodOf(n), n, d2, retSiteC, d5);
							NewPathEdgeProcessingTask task = propagate(zeroValue, retSiteC, d5, f.composeWith(f5), c, true, joinPoints);
							if(task != null) {
								tasks.add(task);
							}
						}
					}
					
				}
				//in cases where there are no callers, the return statement would normally not be processed at all;
				//this might be undesirable if the flow function has a side effect such as registering a taint;
				//instead we thus call the return flow function will a null caller
				if(callers.isEmpty()) {
					FlowFunction<D> retFunction = flowFunctions.getReturnFlowFunction(null, methodThatNeedsSummary,n,null);
					flowFunctionConstructionCount++;
					retFunction.computeTargets(d2);
				}
			}
			return tasks;
		}
	
	/**
	 * Computes the return flow function for the given set of caller-side
	 * abstractions.
	 * @param retFunction The return flow function to compute
	 * @param d2 The abstraction at the exit node in the callee
	 * @param callSite The call site
	 * @param callerSideDs The abstractions at the call site
	 * @return The set of caller-side abstractions at the return site
	 */
	protected Set<D> computeReturnFlowFunction
			(FlowFunction<D> retFunction, D d2, N callSite, Set<D> callerSideDs) {
		return retFunction.computeTargets(d2);
	}
	
	/**
	 * Lines 33-37 of the algorithm.
	 * Simply propagate normal, intra-procedural flows.
	 * @param edge
	 * @throws  
	 */
	private List<NewPathEdgeProcessingTask> processNormalFlow(PathEdge<N,D> edge, Collection<N> joinPoints, Collection<Entry<PathEdge<N, D>, EdgeFunction<V>>> matchingAbstractions) {
		final D d1 = edge.factAtSource();
		final N n = edge.getTarget(); 
		final D d2 = edge.factAtTarget();
		
		EdgeFunction<V> f = jumpFunction(edge);

		List<N> successors = icfg.getSuccsOf(n);
		
		List<NewPathEdgeProcessingTask> tasks = new LinkedList<NewPathEdgeProcessingTask>();
		
		for (N m : successors) {
			
			if(enableNeo4Jlogging) neo4j.logEdge(m, edge, "processNormalFlow");
			if(enablePGSQLlogging) pgsql.logEdge(m, edge, "processNormalFlow", icfg);
			
			
//			logger.info("Flow from {} to {}", n, m);
			FlowFunction<D> flowFunction = flowFunctions.getNormalFlowFunction(n,m);
			flowFunctionConstructionCount++;
			Set<D> res = computeNormalFlowFunction(flowFunction, d1, d2);

			for (D d3 : res) {
				EdgeFunction<V> other = edgeFunctions.getNormalEdgeFunction(n, d2, m, d3, matchingAbstractions);
				EdgeFunction<V> fprime = f.composeWith(other);
			
//				logger.info("propagate: {} composeWith {} = {}", f, other, fprime);
				
				NewPathEdgeProcessingTask task = propagate(d1, m, d3, fprime, null, false, joinPoints); 
				if(task != null) {
					tasks.add(task);
				}
			}
			
		}
		return tasks;
	}
	
	/**
	 * Computes the normal flow function for the given set of start and end
	 * abstractions-
	 * @param flowFunction The normal flow function to compute
	 * @param d1 The abstraction at the method's start node
	 * @param d1 The abstraction at the current node
	 * @return The set of abstractions at the successor node
	 */
	protected Set<D> computeNormalFlowFunction
			(FlowFunction<D> flowFunction, D d1, D d2) {
		return flowFunction.computeTargets(d2);
	}
	
	private Set<N> skippedTargets = new HashSet<>();
	

	/**
	 * Propagates the flow further down the exploded super graph, merging any edge function that might
	 * already have been computed for targetVal at target. 
	 * @param sourceVal the source value of the propagated summary edge
	 * @param target the target statement
	 * @param targetVal the target value at the target statement
	 * @param f the new edge function computed from (s0,sourceVal) to (target,targetVal) 
	 * @param relatedCallSite for call and return flows the related call statement, <code>null</code> otherwise
	 *        (this value is not used within this implementation but may be useful for subclasses of {@link IDESolver}) 
	 * @param isUnbalancedReturn <code>true</code> if this edge is propagating an unbalanced return
	 *        (this value is not used within this implementation but may be useful for subclasses of {@link IDESolver}) 
	 */
	protected NewPathEdgeProcessingTask propagate(D sourceVal, N target, D targetVal, EdgeFunction<V> f,
		/* deliberately exposed to clients */ N relatedCallSite,
		/* deliberately exposed to clients */ boolean isUnbalancedReturn,
		Collection<N> joinPoints) {
		EdgeFunction<V> jumpFnE;
		EdgeFunction<V> fPrime;
		boolean newFunction;
		
		if(f == null)
		{
			throw new IllegalArgumentException("Passed edge function must not be null.");
		}
		
		PathEdge<N,D> edge = new PathEdge<N,D>(sourceVal, target, targetVal);		
		
		jumpFnE = jumpFn.getFunction(edge);
		if(jumpFnE==null) jumpFnE = allTop; //JumpFn is initialized to all-top (see line [2] in SRH96 paper)
		fPrime = jumpFnE.joinWith(f);
		newFunction = !fPrime.equalTo(jumpFnE);
		
//		logger.info("propage(sourceVal={}, target={}, targetVal={}, fPrime={})", sourceVal, target, targetVal, fPrime);
		
		if(newFunction) {
			newFunction = vetoNewFunction(sourceVal, target, targetVal, fPrime);
		}
		
		// Hack to test performance - stop propagation after fixed number of taints per edge
		long skipTargetLimit = 1000;
		if(jumpFn.lookupByTarget(target).size() > skipTargetLimit)
		{ 
			if(skippedTargets.add(target))
			{
				System.out.println("Skip target " + skippedTargets.size() + ": " + target.toString() + " in method " + icfg.getMethodOf(target));
			}
			newFunction = false;
		}
		
		if(newFunction) {
			//logger.info("jumpFn.addFunction(sourceVal={}, target={}, targetVal={}, fPrime={})", sourceVal, target, targetVal, fPrime);
			jumpFn.addFunction(sourceVal, target, targetVal, fPrime);
			
			NewPathEdgeProcessingTask task = new NewPathEdgeProcessingTask(edge, joinPoints, jumpFn.getCount());
			task.setJoinPoint(joinPoints);
			return task;
			// scheduleEdgeProcessing(edge);
		} else {
			return null;
		}
	}
	
	// To be overwritten
	protected boolean isAntiAbstraction(D abstraction)
	{
		return false;
	}
	
	protected boolean vetoNewFunction(D sourceVal, N target, D targetVal, EdgeFunction<V> f) {
		// To be overwritten
		return true;
	}
	
	protected Collection<Entry<PathEdge<N, D>, EdgeFunction<V>>> findMatchingAbstractions(N target) {
		// To be overwritten
		return null;
	}
	
	protected D deriveAntiAbstraction(D abstraction)
	{
		// To be overwritten
		return null;
	}
	
	protected void cleanEdgeList(Collection<PathEdge<N, D>> edges)
	{
		// To be overwritten
	}

	/**
	 * Computes the final values for edge functions.
	 */
	private void computeValues() {	
		//Phase II(i)
        logger.debug("Computing the final values for the edge functions");
		for(Entry<N, Set<D>> seed: initialSeeds.entrySet()) {
			N startPoint = seed.getKey();
			for(D val: seed.getValue()) {
				setVal(startPoint, val, valueLattice.bottomElement());
				Pair<N, D> superGraphNode = new Pair<N,D>(startPoint, val); 
				scheduleValueProcessing(new ValuePropagationTask(superGraphNode));
			}
		}
		logger.debug("Computed the final values of the edge functions");
		//await termination of tasks
		try {
			executor.awaitCompletion();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		//Phase II(ii)
		//we create an array of all nodes and then dispatch fractions of this array to multiple threads
		
//		Set<N> allNonCallStartNodes = icfg.allNonCallStartNodes();
//		Set<N> allNonCallStartNodes = icfg.allNodes();
		Iterable<N> allNonCallStartNodes = icfg.allNonCallStartNodes();
		
		for(N n : allNonCallStartNodes) {
			ValueComputationTask task = new ValueComputationTask(n);
			scheduleValueComputationTask(task);
		}
		
		//await termination of tasks
		try {
			executor.awaitCompletion();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void propagateValueAtStart(Pair<N, D> nAndD, N n) {
		D d = nAndD.getO2();		
		M p = icfg.getMethodOf(n);
		for(N c: icfg.getCallsFromWithin(p)) {					
			Set<Entry<D, EdgeFunction<V>>> entries; 
			entries = jumpFn.forwardLookup(d,c).entrySet();
			for(Map.Entry<D,EdgeFunction<V>> dPAndFP: entries) {
				D dPrime = dPAndFP.getKey();
				EdgeFunction<V> fPrime = dPAndFP.getValue();
				N sP = n;
				propagateValue(c,dPrime,fPrime.computeTarget(val(sP,d)));
				flowFunctionApplicationCount++;
			}
		}
	}
	
	private Set<Pair<N, D>> propagateValueHistory = new HashSet<>();
	
	private void propagateValueAtCall(Pair<N, D> nAndD, N n) {
		D d = nAndD.getO2();

		// Prevent unnecessary duplicate work
		if(propagateValueHistory.contains(nAndD))
		{
			return;
		}
		
		for(M q: icfg.getCalleesOfCallAt(n)) {
			FlowFunction<D> callFlowFunction = flowFunctions.getCallFlowFunction(n, q);
			flowFunctionConstructionCount++;
			for(D dPrime: callFlowFunction.computeTargets(d)) {
				EdgeFunction<V> edgeFn = edgeFunctions.getCallEdgeFunction(n, d, q, dPrime);
				for(N startPoint: icfg.getStartPointsOf(q)) {
					propagateValue(startPoint,dPrime, edgeFn.computeTarget(val(n,d)));
					flowFunctionApplicationCount++;
//					if(flowFunctionApplicationCount % 10000 == 0)
//					{
//						System.out.println("flowFunctionApplicationCount: " + flowFunctionApplicationCount);
//					}
				}
			}
			
			// propagate anti abstraction
			if(enableAntiAbstraction)
			{
				for(N startPoint: icfg.getStartPointsOf(q)) {
					for(Entry<PathEdge<N, D>, EdgeFunction<V>> entry : jumpFn.lookupByTarget(startPoint).entrySet())
					{
						D factAtTarget = entry.getKey().factAtTarget();
						// TODO - Do not use toString to check for anti-abstraction
						
						
						if(isAntiAbstraction(factAtTarget))
						{
							propagateValue(startPoint, factAtTarget, val(n,zeroValue));
						}
						
					}
				}
			}
		}
		
		propagateValueHistory.add(nAndD);
		
	}
	
	private void propagateValue(N nHashN, D nHashD, V v) {
		synchronized (val) {
			V valNHash = val(nHashN, nHashD);
			V vPrime = valueLattice.join(valNHash,v);
			if(!vPrime.equals(valNHash)) {
				setVal(nHashN, nHashD, vPrime);
				scheduleValueProcessing(new ValuePropagationTask(new Pair<N,D>(nHashN,nHashD)));
			}
		}
	}

	private V val(N nHashN, D nHashD){ 
		V l;
		synchronized (val) {
			l = val.get(nHashN, nHashD);
		}
		if(l==null) return valueLattice.topElement(); //implicitly initialized to top; see line [1] of Fig. 7 in SRH96 paper
		else return l;
	}
	
	private void setVal(N nHashN, D nHashD,V l){
		// TOP is the implicit default value which we do not need to store.
		synchronized (val) {
			if (l == valueLattice.topElement())  {   // do not store top values
				val.remove(nHashN, nHashD);
			}
			else {
				val.put(nHashN, nHashD,l);
			}
		}
	}

	private EdgeFunction<V> jumpFunction(PathEdge<N,D> edge) {
		//synchronized (jumpFn) {
			//EdgeFunction<V> function = jumpFn.forwardLookup(edge.factAtSource(), edge.getTarget()).get(edge.factAtTarget());
			EdgeFunction<V> function = jumpFn.getFunction(edge);
			if(function==null) return allTop; //JumpFn initialized to all-top, see line [2] in SRH96 paper
			return function;
		//}
	}

	private Set<Cell<N, D, EdgeFunction<V>>> endSummary(N sP, D d3) {
		Table<N, D, EdgeFunction<V>> map = endSummary.get(sP, d3);
		if(map==null) return Collections.emptySet();
		return map.cellSet();
	}

	private void addEndSummary(N sP, D d1, N eP, D d2, EdgeFunction<V> f) {
		Table<N, D, EdgeFunction<V>> summaries = endSummary.get(sP, d1);
		if(summaries==null) {
			summaries = HashBasedTable.create();
			endSummary.put(sP, d1, summaries);
		}
		//note: at this point we don't need to join with a potential previous f
		//because f is a jump function, which is already properly joined
		//within propagate(..)
		summaries.put(eP,d2,f);
	}	
	
	private Map<N, Set<D>> incoming(D d1, N sP) {
		synchronized (incoming) {
			Map<N, Set<D>> map = incoming.get(sP, d1);
			if(map==null) return Collections.emptyMap();
			return map;
		}
	}
	
	protected void addIncoming(N sP, D d3, N n, D d2) {
		synchronized (incoming) {
			Map<N, Set<D>> summaries = incoming.get(sP, d3);
			if(summaries==null) {
				summaries = new HashMap<N, Set<D>>();
				incoming.put(sP, d3, summaries);
			}
			Set<D> set = summaries.get(n);
			if(set==null) {
				set = new HashSet<D>();
				summaries.put(n,set);
			}
			set.add(d2);
		}
	}	
	
	/**
	 * Returns the V-type result for the given value at the given statement.
	 * TOP values are never returned.
	 */
	public V resultAt(N stmt, D value) {
		//no need to synchronize here as all threads are known to have terminated
		return val.get(stmt, value);
	}
	
	/**
	 * Returns the resulting environment for the given statement.
	 * The artificial zero value is automatically stripped. TOP values are
	 * never returned.
	 */
	public Map<D,V> resultsAt(N stmt) {
		//filter out the artificial zero-value
		//no need to synchronize here as all threads are known to have terminated
		return val.row(stmt);
	}
	
	/**
	 * Factory method for this solver's thread-pool executor.
	 */
	protected CountingThreadPoolExecutor getExecutor() {
		return new CountingThreadPoolExecutor(1, this.numThreads, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
	}
	
	/**
	 * Returns a String used to identify the output of this solver in debug mode.
	 * Subclasses can overwrite this string to distinguish the output from different solvers.
	 */
	protected String getDebugName() {
		return "";
	}

	public void printStats() {
		if(logger.isDebugEnabled()) {
			if(ffCache!=null)
				ffCache.printStats();
			if(efCache!=null)
				efCache.printStats();
		} else {
			logger.info("No statistics were collected, as DEBUG is disabled.");
		}
	}
	
	public class NewPathEdgeProcessingTask extends RecursiveTask<List<NewPathEdgeProcessingTask>>
	{
		@Override
		public String toString() {
			return "NewPathEdgeProcessingTask [target=" + target + "]";
		}


		private static final long serialVersionUID = -8272450092958976817L;
		
		private Collection<PathEdge<N,D>> edges;
		private Collection<N> joinPoints;
		private N target;
		private boolean computeDone = false;
		private int counter;
		
		public NewPathEdgeProcessingTask(PathEdge<N,D> edge, Collection<N> joinPoints, int counter)
		{
			this.edges = new LinkedHashSet<PathEdge<N,D>>();
			this.edges.add(edge);
			this.target = edge.getTarget();
//			this.joinPoints = new HashSet<N>(joinPoints);
			
			// remove current target if it exists
//			this.joinPoints.remove(target);
//			
//			if(this.joinPoints.isEmpty()) {
//				Set<N> joinPoint = getJoinPoint(target);
//				// joinPoint set may be empty (but never null)
//				this.joinPoints.addAll(joinPoint);
//			}
//			

			this.counter = counter;
		}
		
		private void addTask(NewPathEdgeProcessingTask task)
		{
			assert !computeDone;
			
			for(PathEdge<N,D> edge : task.getEdges())
			{
				if(edge.getTarget() != target) {
					throw new IllegalArgumentException("Mismatch in edge targets.");
				}
			}
			
			edges.addAll(task.getEdges().stream().filter(e -> !edges.contains(e)).collect(Collectors.toSet()));
		}
		
		private N getTarget()
		{
			return target;
		}
		
		public Collection<PathEdge<N, D>> getEdges() {
			return edges;
		}
		
		private void setJoinPoint(Collection<N> joinPoints)
		{
			assert !computeDone;
			
			this.joinPoints = joinPoints;
		}
		
		// merges two tasks with same target to the a single task
		private Collection<NewPathEdgeProcessingTask> groupResults(Collection<NewPathEdgeProcessingTask> tasks)
		{
			Map<N, NewPathEdgeProcessingTask> groupedTasks = new HashMap<>();
			
			for(NewPathEdgeProcessingTask task : tasks)
			{
				N target = task.getTarget();
				if(!groupedTasks.containsKey(target))
				{
					groupedTasks.put(target, task);
				} else {
					groupedTasks.get(target).addTask(task);
				}
			}
			// We create a new list with the elements so the underlying map can be garabage collected
			return new ArrayList<>(groupedTasks.values());
		}
		
		private Set<N> getJoinPoint(N target)
		{
			Set<N> result = new HashSet<N>();
			if(icfg.isCallStmt(target)) {
				Collection<N> returnSites = icfg.getReturnSitesOfCallAt(target);
				result.addAll(returnSites);
			} else if(icfg.isExitStmt(target)) {
				M methodThatNeedsSummary = icfg.getMethodOf(target);
				Set<N> inc = new HashSet<N>();
				Collection<N> startPointsOf = icfg.getStartPointsOf(methodThatNeedsSummary);
				for(N sP: startPointsOf) {
					synchronized (incoming) {
						for (Entry<D, Map<N, Set<D>>> entry : incoming.row(sP).entrySet()) {
							for(N n : entry.getValue().keySet()) {
								inc.add(n);
							}
						}
					}
				}
				for (N entry: inc) {
					//for each return site
					for(N retSiteC: icfg.getReturnSitesOfCallAt(entry)) {
						result.add(retSiteC);
					}
				}
			} else {
				List<N> successors = icfg.getSuccsOf(target);
				if(!successors.isEmpty()) {
					if(successors.size() == 1) {
						result.addAll(successors);
					} else {
						N postDominator = icfg.getPostDominator(target);
						if(postDominator != null) { 
							result.add(postDominator);
						}
					}
				}
			}
			
			return result;
		}
		
		
		// TODO - Better naming, temporary while refactoring
		private Collection<NewPathEdgeProcessingTask> computePart1()
		{	
//			logger.info("Target {}", getTarget());
			
			//logger.info("compute target {}, {} edges", target, edges.size());

		
			Collection<NewPathEdgeProcessingTask> tasks = new ArrayList<NewPathEdgeProcessingTask>();
			
			// Find matching abstractions in jumpFunctions
			// This way, a single calculation can be reused for all edges
			Collection<Entry<PathEdge<N, D>, EdgeFunction<V>>> matchingAbstraction = findMatchingAbstractions(target);
			
			// Test - Try to remove taints with value -2 if there are otherwise similiar taints with more information
			cleanEdgeList(edges);
			
			for(PathEdge<N, D> edge : edges) {
//				logger.info("Handling edge {} with joinPoints={}", edge, joinPoints);
//				if(icfg.isCallStmt(edge.getTarget())) {
//					List<NewPathEdgeProcessingTask> results = processCall(edge, joinPoints);
//					groupResults(results, groupedTasks);
//				} else {
//					//note that some statements, such as "throw" may be
//					//both an exit statement and a "normal" statement
//					if(icfg.isExitStmt(edge.getTarget())) {
//						List<NewPathEdgeProcessingTask> results = processExit(edge, joinPoints);
//						groupResults(results, groupedTasks);
//					}
//					if(!icfg.getSuccsOf(edge.getTarget()).isEmpty()) {
//						List<NewPathEdgeProcessingTask> results = processNormalFlow(edge, joinPoints);
//						groupResults(results, groupedTasks);
//					}
//				}
				
//				toJoin.add(new PathEdgeProcessingAction(edge, joinPoints).fork());
				List<NewPathEdgeProcessingTask> result = new PathEdgeProcessingAction(edge, joinPoints, matchingAbstraction).compute();
				if(result != null) {
					tasks.addAll(result);
				}
			}
			
			Collection<NewPathEdgeProcessingTask> results = groupResults(tasks);
			

				
			if(enableAntiAbstraction)
			{
				if(icfg.isCallStmt(target))
				{
					
					// @TODO do not use only first - use all possible callees
					for(M m : icfg.getCalleesOfCallAt(target) ) {
						addAntiAbstractions(tasks, results, m);
					}
				}
			}
			
			// re-group to consider additional tasks due to anti-abstractions
			results = groupResults(tasks);
			
			// edges are no longer needed and may be garbage collected. This should be allowed as soon as possible
			// because this object may be alive for a long time because of the recursion
			edges = null;
			
			propagationCount++;
			
			return results;
		}

		private void addAntiAbstractions(
				Collection<NewPathEdgeProcessingTask> tasks,
				Collection<NewPathEdgeProcessingTask> results, M m) {
			N firstStartPoint = null;
			if(m != null)
			{
				firstStartPoint = Iterables.getFirst(icfg.getStartPointsOf(m), null);
			}
			Map<D, Map<N, Set<D>>> temp;
			if(firstStartPoint != null) {
				temp = incoming.row(firstStartPoint);
			} else {
				temp = Collections.<D, Map<N, Set<D>>>emptyMap();
			}
			
			Set<D> abstractionsToHandle = new HashSet<>();
			
			for(Entry<D, Map<N, Set<D>>> entry : temp.entrySet())
			{
				if(entry.getKey() != zeroValue)
				{
					// look for target in results
					boolean found = false;
					for(NewPathEdgeProcessingTask t : results)
					{
						for(PathEdge<N,D> e : t.getEdges())
						{
							if(e.factAtSource().equals(entry.getKey())) {
								found = true;
							}
						}
					}
					if(!found)
					{
//						System.out.println("Need to add taint similar to abstraction " + entry.getKey());
						abstractionsToHandle.add(entry.getKey());
					}
					
				}
			}
			
			// derive "anti"-abstractions and process the same call again
			// As long as we are doing calls only, matchingAbstractions can be null
			for(D abstractionToHandle : abstractionsToHandle)
			{
				D antiAbstraction = deriveAntiAbstraction(abstractionToHandle);
				
				boolean alreadyExists = jumpFn.getFunction(new PathEdge<N, D>(antiAbstraction, firstStartPoint, antiAbstraction)) != null;
//						System.out.println("AntiAbstraction already exists: " + alreadyExists);
				
				// If the implicit taint was created for the current call site, we will not create an anti abstraction for this call site
				Map<N, Set<D>> existingCallSites = incoming.get(firstStartPoint, abstractionToHandle);
				if(existingCallSites.keySet().contains(target))
				{
					alreadyExists = true;
				}
				
				
				if(!alreadyExists)
				{
					EdgeFunction<V> f = jumpFn.forwardLookup(zeroValue, target).get(zeroValue);
					
					if(f == null)
					{
						f = EdgeIdentity.<V>v();
					}
//							System.out.println("Propagate AntiAbstraction " + antiAbstraction + " to " + firstStartPoint);
					NewPathEdgeProcessingTask task = propagate(antiAbstraction, firstStartPoint, antiAbstraction, f, null, false, joinPoints);
					if(task != null)
					{
						tasks.add(task);
					}
				}
			}
		}
		
		
		private void addOrExtend(Deque<NewPathEdgeProcessingTask> tasks, NewPathEdgeProcessingTask task)
		{
			boolean found = false;
			for(NewPathEdgeProcessingTask t : tasks)
			{
				if(t.getTarget() == task.getTarget()) {
					found = true;
					t.addTask(task);
				}
			}
			if(!found) {
				tasks.addFirst(task);
			}
		}
		
		@Override
		protected List<NewPathEdgeProcessingTask> compute() {
			
			assert joinPoints != null : "joinPoint not set";

			long propagationCountLimit = 150000;
			
			Collection<NewPathEdgeProcessingTask> groupedTasks = computePart1();
			
			// groupedTasks contains all pathEdges to be done grouped by their target
			// We use topological order as the ordner in which to process these edges

			// Get the overall join point from current target, accesses incoming, keep after join()
			Collection<N> joinPoint = getJoinPoint(getTarget());
			joinPoint.addAll(joinPoints);
			
			// allow joinPoints to be garbage collected (c.f. comment on edges = null)
			joinPoints = null;
			
			List<NewPathEdgeProcessingTask> tasks = new LinkedList<NewPathEdgeProcessingTask>();
			
			boolean nonRecursiveVersion = true;
			
			if(nonRecursiveVersion) {
				
				Deque<NewPathEdgeProcessingTask> outerWorklist = new LinkedList<>();
				Deque<NewPathEdgeProcessingTask> innerWorklist = new LinkedList<>();
				
				for(NewPathEdgeProcessingTask task : groupedTasks)
				{
					N target = task.getTarget();
					
					if(joinPoint.contains(target))
					{
						addOrExtend(outerWorklist, task);
					} else {										
						addOrExtend(innerWorklist, task);
					}
				}
				
				// disable recursive handling
				groupedTasks.clear();
				
				do {
					
					while(!innerWorklist.isEmpty() && propagationCount < propagationCountLimit)
					{
						NewPathEdgeProcessingTask task = innerWorklist.pop();
						
						Collection<N> subJoinPoints = getJoinPoint(task.getTarget());
						
						if(subJoinPoints.isEmpty()) {
							subJoinPoints = joinPoint;
						}
						
						// Start sub-tasks
						task.setJoinPoint(subJoinPoints);
						
						// If task target has multiple successors, update joinPoint
						if((icfg.getSuccsOf(task.getTarget()).size() > 1 || icfg.isCallStmt(task.getTarget()) ) && subJoinPoints != joinPoint)
						{
							joinPoint.addAll(subJoinPoints);
						}
						
						// Implement some kind of timeout for task
						Collection<NewPathEdgeProcessingTask> subGroupedTasks = task.computePart1();
					
						for(NewPathEdgeProcessingTask subTask : subGroupedTasks) {
							
							if(joinPoint.contains(subTask.getTarget()))
							{
								addOrExtend(outerWorklist, subTask);
							} else {	
								// Search if there is already a corresponding task in outer
								boolean foundInOuter = false;
								for(NewPathEdgeProcessingTask t : outerWorklist)
								{
									if(t.getTarget().equals(subTask.getTarget())) {
										foundInOuter = true;
									}
								}
								
								if(foundInOuter) {
									addOrExtend(outerWorklist, subTask);
								} else {
									addOrExtend(innerWorklist, subTask);
								}
							}
						}
						
//						if(propagationCount % 1000 == 0) {
//							logger.info("propagationCount " + propagationCount);
//						}
					}
					
					// update joinPoint, pop outerWorklist
					if(!outerWorklist.isEmpty())
					{
						innerWorklist.push(outerWorklist.pop());
						Collection<N> newJoinPoint = getJoinPoint(innerWorklist.peek().getTarget());
//						newJoinPoint.addAll(joinPoint);
//						joinPoint = newJoinPoint;
						joinPoint.addAll(newJoinPoint);
					}

				} while(!innerWorklist.isEmpty() && propagationCount < propagationCountLimit);
				
				if(propagationCount >= propagationCountLimit)
				{
					System.err.println("Stopped because of limit of propagationCount (IDESolver)");
				}
				
				logger.info("propagationCount " + propagationCount);
			
			} else {
				while(!groupedTasks.isEmpty()) {
					List<NewPathEdgeProcessingTask> results = new LinkedList<NewPathEdgeProcessingTask>();
					
					for(NewPathEdgeProcessingTask task : groupedTasks)
					{
						N target = task.getTarget();
	
						if(!joinPoint.contains(target))
						{
							Collection<N> subJoinPoints = getJoinPoint(target);
							
							if(subJoinPoints.isEmpty()) {
								subJoinPoints = joinPoint;
							}
							
							// Start sub-tasks
							task.setJoinPoint(subJoinPoints);
							
	//						toJoin.add(task.fork());
							
							// no async
							results.addAll(task.compute());
							
	//						Collection<NewPathEdgeProcessingTask> subGroupedTasks = task.computePart1();
							// filter tasks by joinPoint -> add on top of outer worklist
							// for remaining tasks: set joinPoints, add on top of inner worklist
							
							
						} else {
							// search for existing task to extend
							NewPathEdgeProcessingTask existingTask = null;
							for(NewPathEdgeProcessingTask t : tasks)
							{
								if(t.getTarget() == task.getTarget()) {
									existingTask = t;
								}
							}
							if(existingTask != null) {
								existingTask.addTask(task);
							} else {
								tasks.add(task);
							}
						}
					}
	//								
	//				for(ForkJoinTask<List<NewPathEdgeProcessingTask>> j : toJoin)
	//				{
	//					List<NewPathEdgeProcessingTask> result = j.join();
	//					if(result != null) {
	//						results.addAll(result);
	//					}
	//				}
					
					groupedTasks = groupResults(results);
				}
			}
			// set flag to indicate input may no longer be changed
			computeDone = true;
			
			return tasks;
		}
	}
	
	
	private class PathEdgeProcessingTask implements Runnable {
		private final PathEdge<N,D> edge;

		public PathEdgeProcessingTask(PathEdge<N,D> edge) {
			this.edge = edge;
		}

		public void run() {
			if(icfg.isCallStmt(edge.getTarget())) {
				processCall(edge, null);
			} else {
				//note that some statements, such as "throw" may be
				//both an exit statement and a "normal" statement
				if(icfg.isExitStmt(edge.getTarget())) {
					processExit(edge, null);
				}
				if(!icfg.getSuccsOf(edge.getTarget()).isEmpty()) {
					processNormalFlow(edge, null, null);
				}
			}
		}
	}
	
	private class PathEdgeProcessingAction extends RecursiveTask<List<NewPathEdgeProcessingTask>> {
		private static final long serialVersionUID = 4008575786417547193L;
		private final PathEdge<N,D> edge;
		private Collection<N> joinPoints;
		private Collection<Entry<PathEdge<N, D>, EdgeFunction<V>>> matchingAbstractions;
		
		public PathEdgeProcessingAction(PathEdge<N,D> edge, Collection<N> joinPoints, Collection<Entry<PathEdge<N, D>, EdgeFunction<V>>> matchingAbstractions) {
			this.edge = edge;
			this.joinPoints = joinPoints;
			this.matchingAbstractions = matchingAbstractions;
		}

		@Override
		public List<NewPathEdgeProcessingTask> compute() {
			List<NewPathEdgeProcessingTask> results = new ArrayList<NewPathEdgeProcessingTask>();
			
			if(icfg.isCallStmt(edge.getTarget())) {				
				results.addAll(processCall(edge, joinPoints));
			} else {
				//note that some statements, such as "throw" may be
				//both an exit statement and a "normal" statement
				if(icfg.isExitStmt(edge.getTarget())) {
					results.addAll(processExit(edge, joinPoints));
				}
				if(!icfg.getSuccsOf(edge.getTarget()).isEmpty()) {
					results.addAll(processNormalFlow(edge, joinPoints, matchingAbstractions));
				}
			}
			
			return results;
		}
	}
	
	private class ValuePropagationTask implements Runnable, Comparable<ValuePropagationTask> {
		private final Pair<N, D> nAndD;

		public ValuePropagationTask(Pair<N,D> nAndD) {
			this.nAndD = nAndD;
		}

		public void run() {
			N n = nAndD.getO1();
			if(icfg.isStartPoint(n) ||
				initialSeeds.containsKey(n)) { 		//our initial seeds are not necessarily method-start points but here they should be treated as such
				propagateValueAtStart(nAndD, n);
			}
			if(icfg.isCallStmt(n)) {
				propagateValueAtCall(nAndD, n);
			}
		}

		@Override
		public int compareTo(ValuePropagationTask o) {
			return 0;
		}
	}
	
	private class ValueComputationTask implements Runnable, Comparable<ValueComputationTask> {

		private N n;
		
		public ValueComputationTask(N value) {
			this.n = value;
		}

		public void run() {
			for(N sP: icfg.getStartPointsOf(icfg.getMethodOf(n))) {					
				Map<PathEdge<N, D>, EdgeFunction<V>> lookupByTarget = jumpFn.lookupByTarget(n);
				for(Entry<PathEdge<N, D>, EdgeFunction<V>> sourceValTargetValAndFunction : lookupByTarget.entrySet()) {
					PathEdge<N,D> edge = sourceValTargetValAndFunction.getKey();
					D dPrime = edge.factAtSource();
					D d = edge.factAtTarget();
					EdgeFunction<V> fPrime = sourceValTargetValAndFunction.getValue();
					synchronized (val) {
						//logger.info("setVal {}, {}, {}", n, d, valueLattice.join(val(n,d),fPrime.computeTarget(val(sP,dPrime))));
						setVal(n,d,valueLattice.join(val(n,d),fPrime.computeTarget(val(sP,dPrime))));
					}
					flowFunctionApplicationCount++;
				}
				
			}
		}

		@Override
		public int compareTo(ValueComputationTask o) {
			return 0;
		}
	}



}
