package soot.jimple.infoflow;

import heros.solver.CountingThreadPoolExecutor;
import heros.solver.TopologicalSorter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Table;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import soot.MethodOrMethodContext;
import soot.PackManager;
import soot.PatchingChain;
import soot.Printer;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.Stmt;
import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.InfoflowResults.SinkInfo;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.aliasing.FlowSensitiveAliasStrategy;
import soot.jimple.infoflow.aliasing.IAliasingStrategy;
import soot.jimple.infoflow.aliasing.PtsBasedAliasStrategy;
import soot.jimple.infoflow.config.IInfoflowConfig;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.entryPointCreators.IEntryPointCreator;
import soot.jimple.infoflow.handlers.ResultsAvailableHandler;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.ipc.IIPCManager;
import soot.jimple.infoflow.loadtime.LoadTimeHelperImpl;
import soot.jimple.infoflow.loadtime.LoadTimeSourceSinkManager;
import soot.jimple.infoflow.loadtime.MongoLoader;
import soot.jimple.infoflow.loadtime.SootTopologicalSorter;
import soot.jimple.infoflow.problems.BackwardsInfoflowProblem;
import soot.jimple.infoflow.problems.InfoflowProblem;
import soot.jimple.infoflow.problems.LoadTimeInfoflowProblem;
import soot.jimple.infoflow.solver.BackwardsInfoflowCFG;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.solver.fastSolver.InfoflowSolver;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;
import soot.jimple.infoflow.util.SootMethodRepresentationParser;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.options.Options;
import soot.spl.ifds.Constraint;
import soot.spl.ifds.IConstraint;
import soot.spl.ifds.SPLIFDSSolver;



public class LoadTimeInfoflow extends Infoflow {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected boolean enableImplicitFlows = true;
	private static boolean debug = true;
	private IInfoflowConfig sootConfig;
	public Table<Unit, Abstraction, IConstraint> splResults;
	public SPLIFDSSolver<Abstraction,AccessPath> splSolver;
	private LoadTimeInfoflowProblem forwardProblem;

	/**
	 * Creates a new instance of the InfoFlow class for analyzing plain Java code without any references to APKs or the Android SDK.
	 */
	public LoadTimeInfoflow() {
		super();
	}

	/**
	 * Creates a new instance of the Infoflow class for analyzing Android APK files.
	 * @param androidPath If forceAndroidJar is false, this is the base directory
	 * of the platform files in the Android SDK. If forceAndroidJar is true, this
	 * is the full path of a single android.jar file.
	 * @param forceAndroidJar True if a single platform JAR file shall be forced,
	 * false if Soot shall pick the appropriate platform version 
	 */
	public LoadTimeInfoflow(String androidPath, boolean forceAndroidJar) {
		super(androidPath, forceAndroidJar);
	}

	/**
	 * Creates a new instance of the Infoflow class for analyzing Android APK files.
	 * @param androidPath If forceAndroidJar is false, this is the base directory
	 * of the platform files in the Android SDK. If forceAndroidJar is true, this
	 * is the full path of a single android.jar file.
	 * @param forceAndroidJar True if a single platform JAR file shall be forced,
	 * false if Soot shall pick the appropriate platform version
	 * @param icfgFactory The interprocedural CFG to be used by the InfoFlowProblem 
	 */
	public LoadTimeInfoflow(String androidPath, boolean forceAndroidJar, BiDirICFGFactory icfgFactory) {
		super(androidPath, forceAndroidJar, icfgFactory);
	}
	
	public BiDiInterproceduralCFG<Unit, SootMethod> getiCfg()
	{
		return iCfg;
	}
	
	@Override
	public void computeInfoflow(String appPath, String libPath,
			IEntryPointCreator entryPointCreator,
			List<String> entryPoints, ISourceSinkManager sourcesSinks) {
		if (sourcesSinks == null) {
			logger.error("Sources are empty!");
			return;
		}
		
		initializeSoot(appPath, libPath,
				SootMethodRepresentationParser.v().parseClassNames(entryPoints, false).keySet());

		// entryPoints are the entryPoints required by Soot to calculate Graph - if there is no main method,
		// we have to create a new main method and use it as entryPoint and store our real entryPoints
		Scene.v().setEntryPoints(Collections.singletonList(entryPointCreator.createDummyMain(entryPoints)));
		ipcManager.updateJimpleForICC();
		
		// We explicitly select the packs we want to run for performance reasons
		if (callgraphAlgorithm != CallgraphAlgorithm.OnDemand) {
	        PackManager.v().getPack("wjpp").apply();
	        PackManager.v().getPack("cg").apply();
		}
        runAnalysis(sourcesSinks, null, appPath);
		if (debug)
			PackManager.v().writeOutput();
	}


	@Override
	public void computeInfoflow(String appPath, String libPath, String entryPoint,
			ISourceSinkManager sourcesSinks) {
		if (sourcesSinks == null) {
			logger.error("Sources are empty!");
			return;
		}

		initializeSoot(appPath, libPath,
				SootMethodRepresentationParser.v().parseClassNames
					(Collections.singletonList(entryPoint), false).keySet(), entryPoint);

		if (!Scene.v().containsMethod(entryPoint)){
			logger.error("Entry point not found: " + entryPoint);
			return;
		}
		SootMethod ep = Scene.v().getMethod(entryPoint);
		if (ep.isConcrete())
			ep.retrieveActiveBody();
		else {
			logger.debug("Skipping non-concrete method " + ep);
			return;
		}
		Scene.v().setEntryPoints(Collections.singletonList(ep));
		Options.v().set_main_class(ep.getDeclaringClass().getName());
		
		// Compute the additional seeds if they are specified
		Set<String> seeds = Collections.emptySet();
		if (entryPoint != null && !entryPoint.isEmpty())
			seeds = Collections.singleton(entryPoint);

		ipcManager.updateJimpleForICC();
		// We explicitly select the packs we want to run for performance reasons
		if (callgraphAlgorithm != CallgraphAlgorithm.OnDemand) {
	        PackManager.v().getPack("wjpp").apply();
	        PackManager.v().getPack("cg").apply();
		}
        runAnalysis(sourcesSinks, seeds, appPath);
		if (debug)
			PackManager.v().writeOutput();
	}

	
	/**
	 * Initializes Soot.
	 * @param appPath The application path containing the analysis client
	 * @param libPath The Soot classpath containing the libraries
	 * @param classes The set of classes that shall be checked for data flow
	 * analysis seeds. All sources in these classes are used as seeds.
	 * @param sourcesSinks The manager object for identifying sources and sinks
	 */
	private void initializeSoot(String appPath, String libPath, Set<String> classes) {
		initializeSoot(appPath, libPath, classes,  "");
	}
	
	/**
	 * Initializes Soot.
	 * @param appPath The application path containing the analysis client
	 * @param libPath The Soot classpath containing the libraries
	 * @param classes The set of classes that shall be checked for data flow
	 * analysis seeds. All sources in these classes are used as seeds. If a
	 * non-empty extra seed is given, this one is used too.
	 */
	private void initializeSoot(String appPath, String libPath, Set<String> classes,
			String extraSeed) {
		// reset Soot:
		logger.info("Resetting Soot...");
		soot.G.reset();
				
		Options.v().set_no_bodies_for_excluded(true);
		Options.v().set_allow_phantom_refs(true);
		if (debug)
			Options.v().set_output_format(Options.output_format_jimple);
		else
			Options.v().set_output_format(Options.output_format_none);
		
		// We only need to distinguish between application and library classes
		// if we use the OnTheFly ICFG
		if (callgraphAlgorithm == CallgraphAlgorithm.OnDemand) {
			Options.v().set_soot_classpath(libPath);
			if (appPath != null) {
				List<String> processDirs = new LinkedList<String>();
				for (String ap : appPath.split(File.pathSeparator))
					processDirs.add(ap);
				Options.v().set_process_dir(processDirs);
			}
		}
		else
			Options.v().set_soot_classpath(appPath + File.pathSeparator + libPath);
		
		Printer.v().setOption(Printer.ADD_JIMPLE_LN);
		
		// Configure the callgraph algorithm
		switch (callgraphAlgorithm) {
			case AutomaticSelection:
				if (extraSeed == null || extraSeed.isEmpty())
					Options.v().setPhaseOption("cg.spark", "on");
				else
					Options.v().setPhaseOption("cg.spark", "vta:true");
				Options.v().setPhaseOption("cg.spark", "string-constants:true");
				break;
			case CHA:
				Options.v().setPhaseOption("cg.cha", "on");
				break;
			case RTA:
				Options.v().setPhaseOption("cg.spark", "on");
				Options.v().setPhaseOption("cg.spark", "rta:true");
				Options.v().setPhaseOption("cg.spark", "string-constants:true");
				break;
			case VTA:
				Options.v().setPhaseOption("cg.spark", "on");
				Options.v().setPhaseOption("cg.spark", "vta:true");
				Options.v().setPhaseOption("cg.spark", "string-constants:true");
				break;
			case OnDemand:
				// nothing to set here
				break;
			default:
				throw new RuntimeException("Invalid callgraph algorithm");
		}
		
		// Specify additional options required for the callgraph
		if (callgraphAlgorithm != CallgraphAlgorithm.OnDemand) {
			Options.v().set_whole_program(true);
			Options.v().setPhaseOption("cg", "trim-clinit:false");
		}

		// do not merge variables (causes problems with PointsToSets)
		Options.v().setPhaseOption("jb.ulp", "off");
		
		if (!this.androidPath.isEmpty()) {
			Options.v().set_src_prec(Options.src_prec_apk);
			if (this.forceAndroidJar)
				soot.options.Options.v().set_force_android_jar(this.androidPath);
			else
				soot.options.Options.v().set_android_jars(this.androidPath);
		} else
			Options.v().set_src_prec(Options.src_prec_java);
		
		Options.v().setPhaseOption("tag.ln", "on");
		
		Options.v().set_keep_offset(true);
		
		//at the end of setting: load user settings:
		if (sootConfig != null)
			sootConfig.setSootOptions(Options.v());
		
		// load all entryPoint classes with their bodies
		Scene.v().loadNecessaryClasses();
		logger.info("Basic class loading done.");
		boolean hasClasses = false;
		for (String className : classes) {
			SootClass c = Scene.v().forceResolve(className, SootClass.BODIES);
			if (c != null){
				c.setApplicationClass();
				if(!c.isPhantomClass() && !c.isPhantom())
					hasClasses = true;
			}
		}
		if (!hasClasses) {
			logger.error("Only phantom classes loaded, skipping analysis...");
			return;
		}
	}	
	
	public void onlySaveJimple(String appPath, String libPath, IEntryPointCreator entryPointCreator, List<String> entryPoints, LoadTimeSourceSinkManager sourceSinkManager) {
		// Run the preprocessors
        for (Transform tr : preProcessors)
            tr.apply();
   
		initializeSoot(appPath, libPath, SootMethodRepresentationParser.v().parseClassNames(entryPoints, false).keySet());
		Scene.v().setEntryPoints(Collections.singletonList(entryPointCreator.createDummyMain(entryPoints)));
		ipcManager.updateJimpleForICC();
		
		// We explicitly select the packs we want to run for performance reasons
		if (callgraphAlgorithm != CallgraphAlgorithm.OnDemand) {
	        PackManager.v().getPack("wjpp").apply();
	        PackManager.v().getPack("cg").apply();
		}
		
		iCfg = icfgFactory.buildBiDirICFG(callgraphAlgorithm);
	     
        if (callgraphAlgorithm != CallgraphAlgorithm.OnDemand)
        	logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());
        iCfg = icfgFactory.buildBiDirICFG(callgraphAlgorithm);
        
        List<SootMethod> methods = new ArrayList<SootMethod>(getMethodsForSeeds(iCfg));
        
//        saveJimpleFiles(appPath, methods);
        List<Integer> usedFeatures = collectSources(appPath, methods, sourceSinkManager);
      
        Config featureConfig = sourceSinkManager.getFeatureConfig();
        
        // Build generic map from index to feature name
        Map<Integer, String> featureNamesMap = new HashMap<>();
		for(Entry<String, ConfigValue> entry : featureConfig.root().entrySet())
		{
			Config conf = featureConfig.getConfig(entry.getKey());
			
			if(usedFeatures.contains(conf.getInt("index"))) {
				String featureName = entry.getKey();
				featureNamesMap.put(conf.getInt("index"), featureName);
			}
		}
		
		// Create new list of features names similar to list of used features by index
		List<String> featureNames = new ArrayList<String>(usedFeatures.size());
		for(int featureIndex : usedFeatures) {
			featureNames.add(featureNamesMap.get(featureIndex));
		}
        
        
        // save in DB
		try (MongoLoader mongoLoder = new MongoLoader()) {	
			mongoLoder.saveUsedFeatures(appPath, FilenameUtils.getName(appPath), featureNames);
		}
	}
	
	
	private void runAnalysis(final ISourceSinkManager sourcesSinks, final Set<String> additionalSeeds, String appPath) {
		// Run the preprocessors
        for (Transform tr : preProcessors)
            tr.apply();

        if (callgraphAlgorithm != CallgraphAlgorithm.OnDemand)
        	logger.info("Callgraph has {} edges", Scene.v().getCallGraph().size());
        iCfg = icfgFactory.buildBiDirICFG(callgraphAlgorithm);
        
        int numThreads = Runtime.getRuntime().availableProcessors();

		CountingThreadPoolExecutor executor = new CountingThreadPoolExecutor
				(maxThreadNum == -1 ? numThreads : Math.min(maxThreadNum, numThreads),
				Integer.MAX_VALUE, 30, TimeUnit.SECONDS,
				new LinkedBlockingQueue<Runnable>());
		
		BackwardsInfoflowProblem backProblem;
		InfoflowSolver backSolver;
		final IAliasingStrategy aliasingStrategy;
		aliasingAlgorithm = AliasingAlgorithm.PtsBased;
		switch (aliasingAlgorithm) {
			case FlowSensitive:
				backProblem = new BackwardsInfoflowProblem(new BackwardsInfoflowCFG(iCfg), sourcesSinks);
				backSolver = new InfoflowSolver(backProblem, executor);
				aliasingStrategy = new FlowSensitiveAliasStrategy(iCfg, backSolver);
				break;
			case PtsBased:
				backProblem = null;
				backSolver = null;
				aliasingStrategy = new PtsBasedAliasStrategy(iCfg);
				break;
			default:
				throw new RuntimeException("Unsupported aliasing algorithm");
		}
		
		//InfoflowProblem forwardProblem  = new InfoflowProblem(iCfg, sourcesSinks, aliasingStrategy);
		forwardProblem  = new LoadTimeInfoflowProblem(iCfg, sourcesSinks,aliasingStrategy);
		
        logger.info("Looking for sources.");

        List<SootMethod> methods = new ArrayList<SootMethod>(getMethodsForSeeds(iCfg));
        

		// In Debug mode, we collect the Jimple bodies for
		// writing them to disk later       
        if (debug) {
        	System.out.println("Start saving Jimple files.");
            saveJimpleFiles(appPath, methods);
            System.out.println("Finished saving Jimple files.");
        }
        
		for (SootMethod m : methods) {
			if (m.hasActiveBody()) {
				if(m.getName().equals("dummyMainMethod")) {
					PatchingChain<Unit> units = m.getActiveBody().getUnits();
					forwardProblem.addInitialSeeds(units.getFirst(), Collections.singleton(forwardProblem.zeroValue()));
//					logger.info("Initial seed: {} in method {}", units.getFirst(), iCfg.getMethodOf(units.getFirst()));
				}
    		}
    		//logger.info("Source lookup done, found {} sources.", forwardProblem.getInitialSeeds().size());
        }
		
		if (!forwardProblem.hasInitialSeeds()){
			logger.error("No sources found, aborting analysis");
			return;
		}
		

		// We optionally also allow additional seeds to be specified
		if (additionalSeeds != null)
			for (String meth : additionalSeeds) {
				SootMethod m = Scene.v().getMethod(meth);
				if (!m.hasActiveBody()) {
					logger.warn("Seed method {} has no active body", m);
					continue;
				}
				forwardProblem.addInitialSeeds(m.getActiveBody().getUnits().getFirst(),
						Collections.singleton(forwardProblem.zeroValue()));
			}
		
		if (!forwardProblem.hasInitialSeeds()){
			logger.error("No sources found, aborting analysis");
			return;
		}

		logger.info("Source lookup done, found {} sources.", forwardProblem.getInitialSeeds().size());
		
		InfoflowSolver forwardSolver = new InfoflowSolver(forwardProblem, executor);
		//aliasingStrategy.setForwardSolver(forwardSolver);
		
		forwardProblem.setInspectSources(inspectSources);
		forwardProblem.setInspectSinks(inspectSinks);
		forwardProblem.setEnableImplicitFlows(enableImplicitFlows);
		forwardProblem.setEnableStaticFieldTracking(enableStaticFields);
		forwardProblem.setEnableExceptionTracking(enableExceptions);
		for (TaintPropagationHandler tp : taintPropagationHandlers)
			forwardProblem.addTaintPropagationHandler(tp);
		forwardProblem.setFlowSensitiveAliasing(flowSensitiveAliasing);
		forwardProblem.setTaintWrapper(taintWrapper);
		forwardProblem.setStopAfterFirstFlow(stopAfterFirstFlow);
		
		if (backProblem != null) {
			backProblem.setForwardSolver((InfoflowSolver) forwardSolver);
			backProblem.setTaintWrapper(taintWrapper);
			backProblem.setZeroValue(forwardProblem.createZeroValue());
			backProblem.setEnableStaticFieldTracking(enableStaticFields);
			backProblem.setEnableExceptionTracking(enableExceptions);
			for (TaintPropagationHandler tp : taintPropagationHandlers)
				backProblem.addTaintPropagationHandler(tp);
			backProblem.setFlowSensitiveAliasing(flowSensitiveAliasing);
			backProblem.setTaintWrapper(taintWrapper);
			backProblem.setActivationUnitsToCallSites(forwardProblem);
		}
		
		if (!enableStaticFields)
			logger.warn("Static field tracking is disabled, results may be incomplete");
		if (!flowSensitiveAliasing || !aliasingStrategy.isFlowSensitive())
			logger.warn("Using flow-insensitive alias tracking, results may be imprecise");

		//forwardSolver.solve();

		forwardSolver.cleanup();
		if(backSolver != null) {
			backSolver.cleanup();
		}
		forwardSolver = null;
		backSolver = null;
		Runtime.getRuntime().gc();
		
		logger.info("Creating SPL solver...");
		
		LoadTimeHelperImpl helper = new LoadTimeHelperImpl(sourcesSinks.getFeatureConfig(), new Aliasing(aliasingStrategy), getiCfg());
		
		splSolver = new SPLIFDSSolver<Abstraction, AccessPath>(forwardProblem, helper);	
		
		logger.info("Starting SPL solver...");
		
		splSolver.solve();
		
		// Not really nice, but sometimes Heros returns before all
		// executor tasks are actually done. This way, we give it a
		// chance to terminate gracefully before moving on.
		
		int terminateTries = 0;
		while (terminateTries < 10) {
			if (splSolver.getExistingExecutor().getActiveCount() != 0 || !splSolver.getExistingExecutor().isTerminated()) {
				terminateTries++;
				try {
					Thread.sleep(500);
				}
				catch (InterruptedException e) {
					logger.error("Could not wait for executor termination", e);
				}
			}
			else
				break;
		}
		if (splSolver.getExistingExecutor().getActiveCount() != 0 || !splSolver.getExistingExecutor().isTerminated()) {
			logger.error("IDE Executor did not terminate gracefully");
		} else {
			logger.info("IDE Executor finished");
		}
		
		//logger.info(splSolver.val.toString());
		
		splResults = splSolver.val;
	}

	public List<Integer> collectSources(String appPath, List<SootMethod> methods, ISourceSinkManager sourceSinkManager)
	{
		
		List<Integer> foundFeatures = new ArrayList<Integer>();
		
		for (SootMethod m : methods) {
			if (m.hasActiveBody()) {
				for(Unit unit: m.getActiveBody().getUnits())
				{
					if(unit instanceof Stmt) {
						Stmt stmt = (Stmt) unit;
						SourceInfo sourceInfo = sourceSinkManager.getSourceInfo(stmt, iCfg);
						if(sourceInfo != null)
						{
							int feature = (int) sourceInfo.getUserData();
							foundFeatures.add(feature);
						}
					}
				}
			}
		}
		
		return foundFeatures;
	}
	
	
	// Collects all jimple files and saves them using MongoLoader
	private void saveJimpleFiles(String appPath, List<SootMethod> methods) {
		// Sort jimple methods, to get fixed line numbers
		Collections.sort(methods, new Comparator<SootMethod>() {
			@Override
			public int compare(SootMethod m1, SootMethod m2) {
				return m1.getName().compareTo(m2.getName());
			}
		});
		
		Map<String, StringBuilder> classes = new HashMap<String, StringBuilder>();
		HashMap<SootClass, Integer> currentJimpleLn = new HashMap<SootClass, Integer>();
		
		for (SootMethod m : methods) {
			if (m.hasActiveBody()) {

				//Printer.v().setJimpleLnNum(1);
				if (classes.containsKey(m.getDeclaringClass().getName())) {
					Printer.v().setJimpleLnNum(currentJimpleLn.get(m.getDeclaringClass()));
					classes.put(m.getDeclaringClass().getName(), classes.get(m.getDeclaringClass().getName()).append(
							m.getActiveBody().toString()));
					currentJimpleLn.put(m.getDeclaringClass(), Printer.v().getJimpleLnNum());
				} else {
					currentJimpleLn.put(m.getDeclaringClass(), 1);
					Printer.v().setJimpleLnNum(currentJimpleLn.get(m.getDeclaringClass()));
					classes.put(m.getDeclaringClass().getName(), new StringBuilder(m.getActiveBody().toString()));
				}
				
				currentJimpleLn.put(m.getDeclaringClass(), Printer.v().getJimpleLnNum());
			}
		}
		// In Debug mode, we write the Jimple files to the database
		try (MongoLoader mongoLoder = new MongoLoader()) {	
			mongoLoder.saveJimpleFiles(classes, appPath);
			writeJimpleFiles(classes);
		}
	}
	
	public Table<Unit, Abstraction, IConstraint> getSplResults()
	{
		return splResults;
	}
	
	public SPLIFDSSolver<Abstraction,AccessPath> getSPLSolver()
	{
		return splSolver;
	}
	
	public void setSootConfig(IInfoflowConfig config){
		sootConfig = config;
	}
	/*
	private void addLoadTimeSceneTransformer(List<String> sources) {
		InfoflowConfig config = new InfoflowConfig(stopAfterFirstFlow, 
				enableImplicitFlows, enableStaticFields, enableExceptions, computeResultPaths, flowSensitiveAliasing, inspectSources, inspectSinks, maxThreadNum);
		transformer = new LoadTimeSceneTransformer(iCfg, sources, config, callgraphAlgorithm);
		Transform transform = new Transform("wjtp.ifds", transformer);

        for (Transform tr : preProcessors){
            PackManager.v().getPack("wjtp").add(tr);
        }
		PackManager.v().getPack("wjtp").add(transform);
	}
	*/
	private void writeJimpleFiles(Map<String, StringBuilder> classes) {
		File dir = new File("JimpleFiles");
		if(!dir.exists()){
			dir.mkdir();
		}
		for (Entry<String, StringBuilder> entry : classes.entrySet()) {
			try {
				stringToTextFile(new File(".").getAbsolutePath() + System.getProperty("file.separator") +"JimpleFiles"+ System.getProperty("file.separator") + entry.getKey() + ".jimple", entry.getValue().toString());
			} catch (IOException e) {
				logger.error("Could not write jimple file: {}", entry.getKey() + ".jimple", e);
			}
		
		}
	}
	
	private void stringToTextFile(String fileName, String contents) throws IOException {
		BufferedWriter wr = null;
		try {
			wr = new BufferedWriter(new FileWriter(fileName));
			wr.write(contents);
			wr.flush();
		}
		finally {
			if (wr != null)
				wr.close();
		}
	}
	
	public LoadTimeInfoflowProblem getLoadtimeInfoflowProblem()
	{
		return forwardProblem;
	}

}
