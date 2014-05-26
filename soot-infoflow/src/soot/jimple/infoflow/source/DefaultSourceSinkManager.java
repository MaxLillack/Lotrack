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
package soot.jimple.infoflow.source;

import heros.InterproceduralCFG;

import java.util.List;

import soot.SootMethod;
import soot.Unit;
import soot.jimple.IdentityStmt;
import soot.jimple.ParameterRef;
import soot.jimple.ReturnStmt;
import soot.jimple.Stmt;

/**
 * A {@link ISourceSinkManager} working on lists of source and sink methods
 * 
 * @author Steven Arzt
 */
public class DefaultSourceSinkManager extends MethodBasedSourceSinkManager {

	private List<String> sources;
	private List<String> sinks;
	
	private List<String> parameterTaintMethods;
	private List<String> returnTaintMethods;
	
	private static final SourceInfo sourceInfo = new SourceInfo(true);
	
	/**
	 * Creates a new instance of the {@link DefaultSourceSinkManager} class
	 * @param sources The list of methods to be treated as sources
	 * @param sinks The list of methods to be treated as sins
	 */
	public DefaultSourceSinkManager(List<String> sources, List<String> sinks) {
		this.sources = sources;
		this.sinks = sinks;
		this.parameterTaintMethods = null;
		this.returnTaintMethods = null;
	}

	/**
	 * Creates a new instance of the {@link DefaultSourceSinkManager} class
	 * @param sources The list of methods to be treated as sources
	 * @param sinks The list of methods to be treated as sins
	 * @param parameterTaintMethods The list of methods whose parameters shall
	 * be regarded as sources
	 * @param returnTaintMethods The list of methods whose return values shall
	 * be regarded as sinks
	 */
	public DefaultSourceSinkManager
			(List<String> sources,
			List<String> sinks,
			List<String> parameterTaintMethods,
			List<String> returnTaintMethods) {
		this.sources = sources;
		this.sinks = sinks;
		this.parameterTaintMethods = parameterTaintMethods;
		this.returnTaintMethods = returnTaintMethods;
	}

	/**
	 * Sets the list of methods to be treated as sources
	 * @param sources The list of methods to be treated as sources
	 */
	public void setSources(List<String> sources){
		this.sources = sources;
	}
	
	/**
	 * Sets the list of methods to be treated as sinks
	 * @param sources The list of methods to be treated as sinks
	 */
	public void setSinks(List<String> sinks){
		this.sinks = sinks;
	}
	
	@Override
	public SourceInfo getSourceMethodInfo(SootMethod sMethod) {
		if (!sources.contains(sMethod.toString()))
			return null;
		return sourceInfo;
	}
	
	@Override
	public boolean isSinkMethod(SootMethod sMethod) {
		return sinks.contains(sMethod.toString());
	}
	
	@Override
	public SourceInfo getSourceInfo(Stmt sCallSite, InterproceduralCFG<Unit, SootMethod> cfg) {
		SourceInfo si = super.getSourceInfo(sCallSite, cfg);
		if (si != null)
			return si;
		
		if (sCallSite instanceof IdentityStmt) {
			IdentityStmt is = (IdentityStmt) sCallSite;
			if (is.getRightOp() instanceof ParameterRef)
				if (this.parameterTaintMethods != null && this.parameterTaintMethods.contains
						(cfg.getMethodOf(sCallSite).getSignature()))
					return sourceInfo;
		}
		
		return null;
	}

	@Override
	public boolean isSink(Stmt sCallSite, InterproceduralCFG<Unit, SootMethod> cfg) {
		if (super.isSink(sCallSite, cfg))
			return true;

		if (sCallSite instanceof ReturnStmt)
			if (this.returnTaintMethods != null && this.returnTaintMethods.contains
					(cfg.getMethodOf(sCallSite).getSignature()))
				return true;
	
		return false;
	}

	/**
	 * Sets the list of methods whose parameters shall be regarded as taint
	 * sources
	 * @param parameterTaintMethods The list of methods whose parameters shall
	 * be regarded as taint sources
	 */
	public void setParameterTaintMethods(List<String> parameterTaintMethods) {
		this.parameterTaintMethods = parameterTaintMethods;
	}
	
	/**
	 * Sets the list of methods whose return values shall be regarded as taint
	 * sinks
	 * @param parameterTaintMethods The list of methods whose return values
	 * shall be regarded as taint sinks
	 */
	public void setReturnTaintMethods(List<String> returnTaintMethods) {
		this.returnTaintMethods = returnTaintMethods;
	}

	@Override
	public boolean trackPrecise(SourceInfo sourceInfo) {
		return false;
	}

}
