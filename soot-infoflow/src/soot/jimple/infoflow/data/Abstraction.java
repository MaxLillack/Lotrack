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
package soot.jimple.infoflow.data;


import heros.solver.LinkedNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import soot.NullType;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.loadtime.FeatureInfo;
import soot.jimple.infoflow.solver.IInfoflowCFG.UnitContainer;
import soot.jimple.infoflow.source.SourceInfo;

import soot.jimple.internal.JimpleLocal;

import com.google.common.collect.Sets;

/**
 * The abstraction class contains all information that is necessary to track the taint.
 * 
 * @author Steven Arzt
 * @author Christian Fritz
 */
public class Abstraction implements Cloneable, LinkedNode<Abstraction> {

	private static Abstraction zeroValue = null;
	private static boolean flowSensitiveAliasing;
		
	/**
	 * the access path contains the currently tainted variable or field
	 */
	private final AccessPath accessPath;
	
	private Abstraction predecessor = null;

	private Set<Abstraction> neighbors = null;
	private Stmt currentStmt = null;
	
	private SourceContext sourceContext = null;

	public void setSourceContext(SourceContext sourceContext) {
		this.sourceContext = sourceContext;
	}
	
	public Stmt getCurrentStmt()
	{
		return currentStmt;
	}

	// only used in path generation
	private Set<SourceContextAndPath> pathCache = null;
	
	/**
	 * Unit/Stmt which activates the taint when the abstraction passes it
	 */
	private Unit activationUnit = null;
	/**
	 * taint is thrown by an exception (is set to false when it reaches the catch-Stmt)
	 */
	private boolean exceptionThrown = false;
	private int hashCode = 0;

	/**
	 * The postdominators we need to pass in order to leave the current conditional
	 * branch. Do not use the synchronized Stack class here to avoid deadlocks.
	 */
	private List<UnitContainer> postdominators = null;
	private boolean isImplicit = false;
	
	/**
	 * Only valid for inactive abstractions. Specifies whether an access paths
	 * has been cut during alias analysis.
	 */
	private boolean dependsOnCutAP = false;
	
	public Abstraction(Value taint, SourceInfo sourceInfo,
			Value sourceVal, Stmt sourceStmt,
			boolean exceptionThrown,
			boolean isImplicit) {

		this(taint, sourceInfo.getTaintSubFields(),
				sourceVal, sourceStmt, sourceInfo.getUserData(),
				exceptionThrown, isImplicit);
	}

	public Abstraction(Value taint, boolean taintSubFields,
			Value sourceVal, Stmt sourceStmt, Object userData,
			boolean exceptionThrown,
			boolean isImplicit){
		this(taint, taintSubFields, new SourceContext(new AccessPath(taint, taintSubFields), sourceStmt, userData),
				exceptionThrown, null, isImplicit);
	}

	protected Abstraction(Value taint, boolean taintSubFields,
			SourceContext sourceContext,
			boolean exceptionThrown,
			Unit activationUnit,
			boolean isImplicit){
		
		if(sourceContext == null)
		{
			throw new IllegalArgumentException("sourceContext must not be null.");
		}
		
		this.sourceContext = sourceContext;
		this.accessPath = new AccessPath(taint, taintSubFields);
		
		if (flowSensitiveAliasing)
			this.activationUnit = activationUnit;
		else
			this.activationUnit = null;
		
		this.exceptionThrown = exceptionThrown;
		this.neighbors = null;
		this.isImplicit = isImplicit;
	}

	/**
	 * Creates an abstraction as a copy of an existing abstraction,
	 * only exchanging the access path. -> only used by AbstractionWithPath
	 * @param p The access path for the new abstraction
	 * @param original The original abstraction to copy
	 */
	protected Abstraction(AccessPath p, Abstraction original){
		if (original == null) {
			sourceContext = null;
			exceptionThrown = false;
			activationUnit = null;
			flowSensitiveAliasing = true;
			isImplicit = false;
		}
		else {
			sourceContext = original.sourceContext;
			exceptionThrown = original.exceptionThrown;
			activationUnit = original.activationUnit;
			
			postdominators = original.postdominators == null ? null
					: new ArrayList<UnitContainer>(original.postdominators);
			
			dependsOnCutAP = original.dependsOnCutAP;
			isImplicit = original.isImplicit;

		}
		accessPath = p;
		neighbors = null;
	}
	
	public final Abstraction deriveInactiveAbstraction(Unit activationUnit){
		if (!flowSensitiveAliasing)
			return this;
		
		// If this abstraction is already inactive, we keep it
		if (!this.isAbstractionActive())
			return this;

		Abstraction a = deriveNewAbstractionMutable(accessPath, null);
		a.postdominators = null;
		a.activationUnit = activationUnit;
		return a;
	}

	public Abstraction deriveNewAbstraction(AccessPath p, Stmt currentStmt){
		return deriveNewAbstraction(p, currentStmt, isImplicit);
	}
	
	public Abstraction deriveNewAbstraction(AccessPath p, Stmt currentStmt,
			boolean isImplicit){
		// If the new abstraction looks exactly like the current one, there is
		// no need to create a new object
		if (this.accessPath.equals(p) && this.currentStmt == currentStmt
				&& this.isImplicit == isImplicit)
			return this;
		
		Abstraction abs = deriveNewAbstractionMutable(p, currentStmt);
		abs.isImplicit = isImplicit;
		return abs;
	}
	
	private Abstraction deriveNewAbstractionMutable(AccessPath p, Stmt currentStmt){
		if (this.accessPath.equals(p) && this.currentStmt == currentStmt) {
			Abstraction abs = clone();
			abs.currentStmt = currentStmt;
			return abs;
		}
		
		Abstraction abs = new Abstraction(p, this);
		abs.predecessor = this;
		abs.currentStmt = currentStmt;
		
		if (!abs.getAccessPath().isEmpty())
			abs.postdominators = null;
		if (!abs.isAbstractionActive())
			abs.dependsOnCutAP = abs.dependsOnCutAP || p.isCutOffApproximation();
		
		abs.sourceContext = null;		
		return abs;
	}
	
	public final Abstraction deriveNewAbstraction(Value taint, boolean cutFirstField, Type baseType){
		return deriveNewAbstraction(taint, cutFirstField, null, baseType);
	}

	public final Abstraction deriveNewAbstraction(Value taint, boolean cutFirstField, Stmt currentStmt,
			Type baseType){
		assert !this.getAccessPath().isEmpty();

		SootField[] orgFields = accessPath.getFields();
		SootField[] fields = null;

		if (orgFields != null) {
			fields = new SootField[cutFirstField ? orgFields.length - 1 : orgFields.length];
			for (int i = cutFirstField ? 1 : 0; i < orgFields.length; i++)
				fields[cutFirstField ? i - 1 : i] = orgFields[i];
		}

		Type[] orgTypes = accessPath.getFieldTypes();
		Type[] types = null;
		
		if (orgTypes != null) {
			types = new Type[cutFirstField ? orgTypes.length - 1 : orgTypes.length];
			for (int i = cutFirstField ? 1 : 0; i < orgTypes.length; i++)
				types[cutFirstField ? i - 1 : i] = orgTypes[i];
		}
		
		if (cutFirstField)
			baseType = accessPath.getFirstFieldType();
		
		AccessPath newAP = new AccessPath(taint, fields, baseType, types,
				accessPath.getTaintSubFields());
		
		if (this.getAccessPath().equals(newAP) && this.currentStmt == currentStmt)
			return this;
		return deriveNewAbstractionMutable(newAP, currentStmt);
	}

	/**
	 * Derives a new abstraction that models the current local being thrown as
	 * an exception
	 * @param throwStmt The statement at which the exception was thrown
	 * @return The newly derived abstraction
	 */
	public final Abstraction deriveNewAbstractionOnThrow(Stmt throwStmt){
		assert !this.exceptionThrown;
		Abstraction abs = clone();
		
		abs.currentStmt = throwStmt;
		abs.sourceContext = null;
		abs.exceptionThrown = true;
		return abs;
	}
	
	/**
	 * Derives a new abstraction that models the current local being caught as
	 * an exception
	 * @param taint The value in which the tainted exception is stored
	 * @return The newly derived abstraction
	 */
	public final Abstraction deriveNewAbstractionOnCatch(Value taint){
		assert this.exceptionThrown;
		Abstraction abs = deriveNewAbstractionMutable(new AccessPath(taint, true), null);
		abs.exceptionThrown = false;
		return abs;
	}
		
	/**
	 * Gets the path of statements from the source to the current statement
	 * with which this abstraction is associated. If this path is ambiguous,
	 * a single path is selected randomly.
	 * @return The path from the source to the current statement
	 */
	public Set<SourceContextAndPath> getPaths() {
        //throw new UnsupportedOperationException("deactived for loadtime-analysis");
        return getPaths(true, this);
	}
	
	/**
	 * Gets the path of statements from the source to the current statement
	 * with which this abstraction is associated. If this path is ambiguous,
	 * a single path is selected randomly.
	 * @return The path from the source to the current statement
	 */
	public Set<SourceContextAndPath> getSources() {
        throw new UnsupportedOperationException("deactived for loadtime-analysis");
		//Runtime.getRuntime().gc();
		//return getPaths(false, this);
	}
	
	private Abstraction sinkAbs = null;
	
	/**
	 * Gets the path of statements from the source to the current statement
	 * with which this abstraction is associated. If this path is ambiguous,
	 * a single path is selected randomly.
	 * @return The path from the source to the current statement
	 */
	private Set<SourceContextAndPath> getPaths(boolean reconstructPaths, Abstraction flagAbs) {
        //throw new UnsupportedOperationException("deactived for loadtime-analysis");

        
		// If we run into a loop, we symbolically save where to continue on the
		// next run and abort for now
		if (sinkAbs == flagAbs)
			if (pathCache == null)
				return Collections.singleton(new SourceContextAndPath(this));
			else
				return Collections.unmodifiableSet(pathCache);

		// If we have a partial path from a previous run, we extend it instead
		// of computing it all anew.
		if (sinkAbs != flagAbs && pathCache != null) {
			sinkAbs = flagAbs;
			
			Set<SourceContextAndPath> newScaps = new HashSet<SourceContextAndPath>();
			Iterator<SourceContextAndPath> scapIt = this.pathCache.iterator();
			while (scapIt.hasNext()) {
				SourceContextAndPath scap = scapIt.next();
				if (scap.getSymbolic() != null) {
					for (SourceContextAndPath symbolicScap : scap.getSymbolic().getPaths(reconstructPaths, flagAbs))
						newScaps.add(symbolicScap.extendPath(scap.getPath()));
					scapIt.remove();
				}
			}
			pathCache.addAll(newScaps);
			
			return Collections.unmodifiableSet(pathCache);
		}
		
		this.sinkAbs = flagAbs;
		this.pathCache = Sets.newHashSet();
		if (sourceContext != null) {
			// Construct the path root
			SourceContextAndPath sourceAndPath = new SourceContextAndPath
					(sourceContext.getAccessPath(), sourceContext.getStmt(),
							sourceContext.getUserData()).extendPath(sourceContext.getStmt());
			pathCache.add(sourceAndPath);
			
			// Sources may not have predecessors
			assert predecessor == null;
		}
		else {
			for (SourceContextAndPath curScap : predecessor.getPaths(reconstructPaths, flagAbs)) {
				SourceContextAndPath extendedPath = (currentStmt == null || !reconstructPaths)
						? curScap : curScap.extendPath(currentStmt);
				this.pathCache.add(extendedPath);
			}
		}
			
		if (neighbors != null)
			for (Abstraction nb : neighbors)
				this.pathCache.addAll(nb.getPaths(reconstructPaths, flagAbs));
		
		assert pathCache != null;
		return Collections.unmodifiableSet(pathCache);
		
	}
	
	public boolean isAbstractionActive(){
		return activationUnit == null;
	}
	
	public boolean isImplicit() {
		return isImplicit;
	}
	
	@Override
	public String toString(){
		
		String featureInfoString = "";
		if(getSourceContext() != null) {
			SourceContext context = getSourceContext();
			if(context.getUserData() != null) {
				FeatureInfo featureInfo = (FeatureInfo) context.getUserData();
				featureInfoString = featureInfo.toString();
			}
		}
		
//		if(activationUnit != null) {
//			featureInfoString += " activationUnit: " + activationUnit.toString() + "(" + activationUnit.hashCode() + ")";
//		}
//		
//		if(sourceContext != null) {
//			featureInfoString += " sourceContext: " + "(" + sourceContext.hashCode() + ")";
//		}
//		
//		if(postdominators != null) {
//			featureInfoString += " postdominators: " + "(" + postdominators.hashCode() + ")";
//		}
//		
//		if(accessPath != null) {
//			featureInfoString += " accessPath: " + accessPath.toString() + "(" + accessPath.hashCode() + ")";
//		}
//		
//		featureInfoString += " exceptionThrown: " + exceptionThrown;
//		featureInfoString += " dependsOnCutAP: " + dependsOnCutAP;
//		featureInfoString += " isImplicit: " + isImplicit;
		
		return (isAbstractionActive()?"":"_")
				+accessPath.toString() 
				+ " | "+(activationUnit==null?"":activationUnit.toString()) 
				+ ">>"
//				+ (accessPath.toString().equals(" *") ? currentStmt.toString() : "")
				+ featureInfoString;
	}
	
	public AccessPath getAccessPath(){
		return accessPath;
	}
	
	public Unit getActivationUnit(){
		return this.activationUnit;
	}
	
	public Abstraction getActiveCopy(){
		assert !this.isAbstractionActive();
		
		Abstraction a = clone();
		a.sourceContext = null;
		a.activationUnit = null;
		return a;
	}
	
	/**
	 * Gets whether this value has been thrown as an exception
	 * @return True if this value has been thrown as an exception, otherwise
	 * false
	 */
	public boolean getExceptionThrown() {
		return this.exceptionThrown;
	}
	
	public final Abstraction deriveConditionalAbstractionEnter(UnitContainer postdom,
			Stmt conditionalUnit) {
		assert this.isAbstractionActive();
		
		if (postdominators != null && postdominators.contains(postdom))
			return this;
		
		Abstraction abs = deriveNewAbstractionMutable
				(AccessPath.getEmptyAccessPath(), conditionalUnit);
		if (abs.postdominators == null)
			abs.postdominators = Collections.singletonList(postdom);
		else
			abs.postdominators.add(0, postdom);
		return abs;
	}
	
	public final Abstraction deriveConditionalAbstractionCall(Unit conditionalCallSite) {
		assert this.isAbstractionActive();
		assert conditionalCallSite != null;
		
		Abstraction abs = deriveNewAbstractionMutable
				(AccessPath.getEmptyAccessPath(), (Stmt) conditionalCallSite);
		
		// Postdominators are only kept intraprocedurally in order to not
		// mess up the summary functions with caller-side information
		abs.postdominators = null;

		return abs;
	}
	
	public final Abstraction dropTopPostdominator() {
		if (postdominators == null || postdominators.isEmpty())
			return this;
		
		Abstraction abs = clone();
		abs.sourceContext = null;
		abs.postdominators.remove(0);
		return abs;
	}
	
	public UnitContainer getTopPostdominator() {
		if (postdominators == null || postdominators.isEmpty())
			return null;
		return this.postdominators.get(0);
	}
	
	public boolean isTopPostdominator(Unit u) {
		UnitContainer uc = getTopPostdominator();
		if (uc == null)
			return false;
		return uc.getUnit() == u;
	}

	public boolean isTopPostdominator(SootMethod sm) {
		UnitContainer uc = getTopPostdominator();
		if (uc == null)
			return false;
		return uc.getMethod() == sm;
	}
	
	@Override
	public Abstraction clone() {
		Abstraction abs = new Abstraction(accessPath, this);
		abs.predecessor = this;
		abs.neighbors = null;
		abs.currentStmt = null;
		
		assert abs.equals(this);
		return abs;
	}
	
	@Override
	public boolean equals(Object obj) {
		if (super.equals(obj))
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		Abstraction other = (Abstraction) obj;
		if (accessPath == null) {
			if (other.accessPath != null)
				return false;
		} else if (!accessPath.equals(other.accessPath))
			return false;
		
		return localEquals(other);
	}
	
	/**
	 * Checks whether this object locally equals the given object, i.e. the both
	 * are equal modulo the access path
	 * @param other The object to compare this object with
	 * @return True if this object is locally equal to the given one, otherwise
	 * false
	 */
	private boolean localEquals(Abstraction other) {
		
//		boolean looksSimiliar = toString().equals(other.toString());
		
		// deliberately ignore prevAbs
		if (sourceContext == null) {
			if (other.sourceContext != null)
				return false;
		}
		else if (!sourceContext.equals(other.sourceContext)) {
//			if(looksSimiliar) {
//				int a = 0;
//				sourceContext.equals(other.sourceContext);
//			}
			return false;
		}
		// @TODO: Hack: Only considers feature value but not other parts of source context
//		if (sourceContext != null && other.sourceContext != null && sourceContext.getUserData() != null && other.sourceContext.getUserData() != null) {
//			FeatureInfo featureInfo = (FeatureInfo) sourceContext.getUserData();
//			FeatureInfo featureInfoOther = (FeatureInfo) other.sourceContext.getUserData();
//			if(!featureInfo.getValue().equals(featureInfoOther.getValue()))
//			{
//				return false;
//			}
//		} 	
		if (activationUnit == null) {
			if (other.activationUnit != null)
				return false;
		} else if (!activationUnit.equals(other.activationUnit))
			return false;
		if (this.exceptionThrown != other.exceptionThrown)
			return false;
		if (postdominators == null) {
			if (other.postdominators != null)
				return false;
		} else if (!postdominators.equals(other.postdominators))
			return false;
		if(this.dependsOnCutAP != other.dependsOnCutAP)
			return false;
//		if(this.isImplicit != other.isImplicit)
//			return false;

		return true;
	}
	
	@Override
	public int hashCode() {
//		if (this.hashCode != 0)
//			return hashCode;

		final int prime = 31;
		int result = 1;
	
		// deliberately ignore prevAbs
//		result = prime * result + ((sourceContext == null) ? 0 : sourceContext.hashCode());
		// @TODO: Hack: Only considers feature value but not other parts of source context
//		if(sourceContext != null)
//		{
//			FeatureInfo featureInfo = (FeatureInfo) sourceContext.getUserData();
//			if(featureInfo != null) {
//				result = prime * result + featureInfo.getValue().hashCode();
//			}
//		}
		result = prime * result + ((sourceContext == null) ? 0 : sourceContext.hashCode());
		result = prime * result + ((accessPath == null) ? 0 : accessPath.hashCode());
		result = prime * result + ((activationUnit == null) ? 0 : activationUnit.hashCode());
		result = prime * result + (exceptionThrown ? 1231 : 1237);
		result = prime * result + ((postdominators == null) ? 0 : postdominators.hashCode());
		result = prime * result + (dependsOnCutAP ? 1231 : 1237);
//		result = prime * result + (isImplicit ? 1231 : 1237);
		this.hashCode = result;
		
		return this.hashCode;
	}
	
	/**
	 * Checks whether this abstraction entails the given abstraction, i.e. this
	 * taint also taints everything that is tainted by the given taint.
	 * @param other The other taint abstraction
	 * @return True if this object at least taints everything that is also tainted
	 * by the given object
	 */
	public boolean entails(Abstraction other) {
		if (accessPath == null) {
			if (other.accessPath != null)
				return false;
		} else if (!accessPath.entails(other.accessPath))
			return false;
		return localEquals(other);
	}

	/**
	 * Gets the context of the taint, i.e. the statement and value of the source
	 * @return The statement and value of the source
	 */
	public SourceContext getSourceContext() {
		return sourceContext;
	}
	
	public boolean dependsOnCutAP() {
		return dependsOnCutAP;
	}
	
	Abstraction getPredecessor() {
        //throw new UnsupportedOperationException("deactived for loadtime-analysis");
		return this.predecessor;
	}

	@Override
	public void setCallingContext(Abstraction callingContext) {
	}
	
	@Override
	public void addNeighbor(Abstraction originalAbstraction) {
        //throw new UnsupportedOperationException("deactived for loadtime-analysis");
        
		assert this != zeroValue;
		assert originalAbstraction.equals(this);
		
		// We should not register ourselves as a neighbor
		if (originalAbstraction == this)
			return;
		
		synchronized (this) {
			if (neighbors == null)
				neighbors = Sets.newIdentityHashSet();
			
			if (this.predecessor != originalAbstraction.predecessor
					|| this.currentStmt != originalAbstraction.currentStmt)
				this.neighbors.add(originalAbstraction);
		}
	}
		
	public static Abstraction getZeroAbstraction(boolean flowSensitiveAliasing) {
		if (zeroValue == null) {
			zeroValue = new Abstraction(new JimpleLocal("zero", NullType.v()), new SourceInfo(false), null,
					null, false, false);
			Abstraction.flowSensitiveAliasing = flowSensitiveAliasing;
		}
		return zeroValue;
	}
	
}
