/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/
package soot.jimple.infoflow.loadtime;

import heros.InterproceduralCFG;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.source.MethodBasedSourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;

/**
 * A {@link ISourceSinkManager} working on lists of source and sink methods
 * 
 * @author Steven Arzt
 */
public class LoadTimeSourceSinkManager extends MethodBasedSourceSinkManager {

	private List<String> sources;
	private final Logger logger = LoggerFactory.getLogger(getClass());
	private Map<Pattern, LoadTimeConfig> configPatterns;
	private Map<String, LoadTimeConfig> configs;
	private Set<Integer> preciseFeatures = new HashSet<Integer>();
	
	private Config featureConfig;

	public LoadTimeSourceSinkManager(List<String> sources, String configName) {
		this.sources = sources;
		
		// Load sources from config
		Config conf = ConfigFactory.load().getConfig(configName);
		List<? extends Config> fieldRefs = conf.getConfigList("fieldRefs");
		List<? extends Config> methods = conf.getConfigList("methods");
		featureConfig = conf.getConfig("features");
		
		configs = new HashMap<String, LoadTimeConfig>();
		configPatterns = new HashMap<Pattern, LoadTimeConfig>();
		
		for(Config fieldRef : fieldRefs)
		{
			LoadTimeConfig loadTimeConfig = new LoadTimeConfig(fieldRef.getString("name"), fieldRef.getConfig("feature").getInt("index"));
			configs.put(customEscaping(fieldRef.getString("name")), loadTimeConfig);
			if(fieldRef.getConfig("feature").getBoolean("precise")) {
				preciseFeatures.add(fieldRef.getConfig("feature").getInt("index"));
			}
			configPatterns.put(Pattern.compile(customEscaping(fieldRef.getString("name"))), loadTimeConfig);
		}
		for(Config method : methods)
		{
			LoadTimeConfig loadTimeConfig = new LoadTimeConfig(method.getString("name"), method.getConfig("feature").getInt("index"));
			configs.put(customEscaping(method.getString("name")), loadTimeConfig);
			if(method.getConfig("feature").getBoolean("precise")) {
				preciseFeatures.add(method.getConfig("feature").getInt("index"));
			}
			configPatterns.put(Pattern.compile(customEscaping(method.getString("name"))), loadTimeConfig);
		}
	}
	
	// Escape regex chars .,(,)
	private String customEscaping(String config)
	{
		config = config.replace(".", "\\.");
		config = config.replace("(", "\\(");
		config = config.replace(")", "\\)");
		config = config.replace("$", "\\$");
		
		return config;
	}
	
	@Override
	public Config getFeatureConfig() {
		return featureConfig;
	}
	
	@Override
	public SourceInfo getSourceInfo(Stmt sCallSite, InterproceduralCFG<Unit, SootMethod> cfg) {
		//boolean isSource = sources.contains(sCallSite.toString());
		
		SourceInfo sourceInfo = null;
		
		if(sCallSite.containsInvokeExpr()) {
			InvokeExpr expr = (InvokeExpr)sCallSite.getInvokeExpr();
			
			String methodText = null;
			
			synchronized (sources) {
				// Possible error if getMethod() is invoked in parallel?
				methodText = expr.getMethod().getSubSignature();
			}
			
			StringBuilder sb = new StringBuilder();
			sb.append("(");
			boolean first = true;
			for(Value arg : expr.getArgs())
			{
				if(!first)
				{
					sb.append(", ");
				} else {
					first = false;
				}
				sb.append(arg.toString());
			}
			sb.append(")");
			
			String methodWithParameter = methodText + sb.toString();
			
			for(Pattern config : configPatterns.keySet()) {
				if (config.matcher(methodText).matches()) {
					sourceInfo = new SourceInfo(true, configPatterns.get(config).getFeature());
				} else if(config.matcher(methodWithParameter).matches()) {
					sourceInfo = new SourceInfo(true, configPatterns.get(config).getFeature());
				}
			}
			
//			if(configs.containsKey(methodText)) {
//				sourceInfo = new SourceInfo(true, configs.get(methodText).getFeature());
//			} else if(configs.containsKey(methodWithParameter)) {
//				sourceInfo = new SourceInfo(true, configs.get(methodWithParameter).getFeature());
//			}
		} else {
			if(sCallSite instanceof AssignStmt) {
				AssignStmt assignStmt = (AssignStmt)sCallSite;
				
				Value rightOp = assignStmt.getRightOp();
				
				if(rightOp instanceof FieldRef)
				{
					String fieldRef = null;
					if(rightOp instanceof InstanceFieldRef) {
						fieldRef = ((InstanceFieldRef) rightOp).getFieldRef().toString();
					} else {
						fieldRef = rightOp.toString();
					}

//					if(configs.containsKey(fieldRef)) {
//						sourceInfo = new SourceInfo(true, configs.get(customEscaping(fieldRef)).getFeature());
//					}
					for(Pattern config : configPatterns.keySet()) {
						if (config.matcher(fieldRef).matches()) {
							sourceInfo = new SourceInfo(true, configPatterns.get(config).getFeature());
						}
					}
				}
			}
		}
		
		//logger.info("check source {}: {}", sCallSite, foundSource);
		return sourceInfo;
	}
	
	@Override
	public boolean isSinkMethod(SootMethod method) {
		throw new IllegalStateException();
	}


	@Override
	public SourceInfo getSourceMethodInfo(SootMethod method) {
		throw new UnsupportedOperationException("getSourceMethodInfo not implemented yet in LoadTimeSourceSinkManager");
	}

	@Override
	public boolean trackPrecise(SourceInfo sourceInfo) {
		return true;
//		return preciseFeatures.contains((Integer)sourceInfo.getUserData());
	}


}
