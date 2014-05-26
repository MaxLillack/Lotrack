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
package soot.jimple.infoflow.problems;

import heros.FlowFunction;
import heros.FlowFunctions;
import heros.InterproceduralCFG;
import heros.flowfunc.Identity;
import heros.flowfunc.KillAll;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.ArrayType;
import soot.Body;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.SootField;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.CastExpr;
import soot.jimple.CaughtExceptionRef;
import soot.jimple.Constant;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.IfStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.LengthExpr;
import soot.jimple.LookupSwitchStmt;
import soot.jimple.NumericConstant;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.TableSwitchStmt;
import soot.jimple.ThrowStmt;
import soot.jimple.infoflow.InfoflowResults;
import soot.jimple.infoflow.aliasing.Aliasing;
import soot.jimple.infoflow.aliasing.IAliasingStrategy;
import soot.jimple.infoflow.aliasing.ImplicitFlowAliasStrategy;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AbstractionAtSink;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.data.SourceContext;
import soot.jimple.infoflow.data.SourceContextAndPath;
import soot.jimple.infoflow.handlers.TaintPropagationHandler;
import soot.jimple.infoflow.handlers.TaintPropagationHandler.FlowFunctionType;
import soot.jimple.infoflow.util.ConcurrentHashSet;
import soot.jimple.infoflow.solver.IInfoflowCFG.UnitContainer;
import soot.jimple.infoflow.solver.IInfoflowCFG;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.functions.SolverCallFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverCallToReturnFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverNormalFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverReturnFlowFunction;
import soot.jimple.infoflow.loadtime.FeatureInfo;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;
import soot.toolkits.graph.DirectedGraph;
import soot.toolkits.graph.UnitGraph;
import soot.toolkits.scalar.SimpleLiveLocals;




import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

public class LoadTimeInfoflowProblem extends AbstractInfoflowProblem {
	
	private final IAliasingStrategy aliasingStrategy;
	private final IAliasingStrategy implicitFlowAliasingStrategy;
	private final Aliasing aliasing;
	
	private ISourceSinkManager sourceSinkManager = null;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    private final Map<Unit, Set<Abstraction>> implicitTargets = new ConcurrentHashMap<Unit, Set<Abstraction>>();
    
	protected final Set<AbstractionAtSink> results = new ConcurrentHashSet<AbstractionAtSink>();
	protected InfoflowResults infoflowResults = null;
	
	private InterproceduralCFG<Unit, SootMethod> icfg;
	
	/**
	 * Computes the taints produced by a taint wrapper object
	 * @param d1 The context (abstraction at the method's start node)
	 * @param iStmt The call statement the taint wrapper shall check for well-
	 * known methods that introduce black-box taint propagation
	 * @param source The taint source
	 * @return The taints computed by the wrapper
	 */
	private Set<Abstraction> computeWrapperTaints
	(Abstraction d1,
	final Stmt iStmt,
	Abstraction source) {
		assert inspectSources || source != zeroValue;
		
		Set<Abstraction> res = new HashSet<Abstraction>();
		if(taintWrapper == null)
			return Collections.emptySet();
		
		if (!source.getAccessPath().isStaticFieldRef() && !source.getAccessPath().isEmpty()) {
			boolean found = false;
		
			// The base object must be tainted
			if (iStmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
				InstanceInvokeExpr iiExpr = (InstanceInvokeExpr) iStmt.getInvokeExpr();
				found = aliasing.mayAlias(iiExpr.getBase(), source.getAccessPath().getPlainValue());
			}
			
			// or one of the parameters must be tainted
			if (!found)
				for (Value param : iStmt.getInvokeExpr().getArgs())
					if (aliasing.mayAlias(source.getAccessPath().getPlainValue(), param)) {
						found = true;
						break;
				}
			
			// If nothing is tainted, we don't have any taints to propagate
			if (!found)
				return Collections.emptySet();
		}
		
		Set<AccessPath> vals = taintWrapper.getTaintsForMethod(iStmt, source.getAccessPath(),
				interproceduralCFG());
		if(vals != null) {
			for (AccessPath val : vals) {
				// The new abstraction gets activated where it was generated
				if (val.equals(source.getAccessPath())) {
					
					// TODO: Check is variable is overwritten:
					// !(call instanceof DefinitionStmt && mayAlias(((DefinitionStmt) call).getLeftOp(),
					//source.getAccessPath().getPlainLocal()))
					if(!source.getAccessPath().isEmpty() && !(iStmt instanceof DefinitionStmt && aliasing.mayAlias(((DefinitionStmt) iStmt).getLeftOp(),
							source.getAccessPath().getPlainLocal()))) {
						res.add(source);
					}
				}
				else if(!source.getAccessPath().isEmpty()) {
					Abstraction newAbs = source.deriveNewAbstraction(val, iStmt);
					
					FeatureInfo featureInfo = null;
					if(source.getSourceContext() != null && source.getSourceContext().getUserData() != null) {
						FeatureInfo old = (FeatureInfo) source.getSourceContext().getUserData();
						
						boolean taintedValueOverwritten = (iStmt instanceof DefinitionStmt)
								? baseMatches(((DefinitionStmt) iStmt).getLeftOp(), source) : false;
						
						if(!taintedValueOverwritten && iStmt instanceof DefinitionStmt) {
							taintedValueOverwritten = ((DefinitionStmt) iStmt).getLeftOp().equals(source.getSourceContext().getValue());
						}
						
						if(!(old.getIndex() == -1 || taintedValueOverwritten)) {
							featureInfo = new FeatureInfo(val, old.getValue(), old.getIndex());
						}
					}
					
					newAbs.setSourceContext(new SourceContext(val.getPlainValue(), iStmt, featureInfo));
					res.add(newAbs);
				
					// If the taint wrapper creates a new taint, this must be propagated
					// backwards as there might be aliases for the base object
					// Note that we don't only need to check for heap writes such as a.x = y,
					// but also for base object taints ("a" in this case).
					boolean taintsBaseValue = val.getBaseType() instanceof RefType
							&& !((RefType) val.getBaseType()).getSootClass().getName().equals("java.lang.String")
							&& newAbs.getAccessPath().getBaseType() instanceof RefType
							&& iStmt.getInvokeExpr() instanceof InstanceInvokeExpr
							&& ((InstanceInvokeExpr) iStmt.getInvokeExpr()).getBase() == val.getPlainValue();
					boolean taintsStaticField = enableStaticFields && newAbs.getAccessPath().isStaticFieldRef();
						
					// If the tainted value gets overwritten, it cannot have aliases afterwards
					boolean taintedValueOverwritten = (iStmt instanceof DefinitionStmt)
							? baseMatches(((DefinitionStmt) iStmt).getLeftOp(), newAbs) : false;
						
					if (!taintedValueOverwritten && (taintsStaticField
							|| (taintsBaseValue && newAbs.getAccessPath().getTaintSubFields())
							|| triggerInaktiveTaintOrReverseFlow(iStmt, val.getPlainValue(), newAbs)))
						computeAliasTaints(d1, (Stmt) iStmt, val.getPlainValue(), res,
								interproceduralCFG().getMethodOf(iStmt), newAbs);
				}
			}
		}
		
		return res;
		}
	
	/**
	 * Computes the taints for the aliases of a given tainted variable
	 * @param d1 The context in which the variable has been tainted
	 * @param src The statement that tainted the variable
	 * @param targetValue The target value which has been tainted
	 * @param taintSet The set to which all generated alias taints shall be
	 * added
	 * @param method The method containing src
	 * @param newAbs The newly generated abstraction for the variable taint
	 */
	private void computeAliasTaints
			(final Abstraction d1, final Stmt src,
			final Value targetValue, Set<Abstraction> taintSet,
			SootMethod method, Abstraction newAbs) {
		// If we are not in a conditionally-called method, we run the
		// full alias analysis algorithm. Otherwise, we use a global
		// non-flow-sensitive approximation.
		if(d1 != null) {
			if (!d1.getAccessPath().isEmpty()) {
				aliasingStrategy.computeAliasTaints(d1, src, targetValue, taintSet, method, newAbs);
			} else if (targetValue instanceof InstanceFieldRef) {
				assert enableImplicitFlows;
				implicitFlowAliasingStrategy.computeAliasTaints(d1, src, targetValue, taintSet, method, newAbs);
			}
		}
	}
	
	
	private void commonComputeTargets(Abstraction a1, Abstraction a2, Unit src)
	{
		commonComputeTargets(a2, src);
	}
	
	private void commonComputeTargets(Abstraction a1, Unit src)
	{
		//int i = 0;
		//logger.info("commonComputeTargets({})", a1);
		
		//logger.info("Statement is {}", src);			
	}
	

	@Override
	public FlowFunctions<Unit, Abstraction, SootMethod> createFlowFunctionsFactory() {
		return new FlowFunctions<Unit, Abstraction, SootMethod>() {

			private Set<Abstraction> notifyOutFlowHandlers(Unit stmt, Set<Abstraction> res,
					FlowFunctionType functionType) {
				if (res != null && !res.isEmpty())
					for (TaintPropagationHandler tp : taintPropagationHandlers)
						tp.notifyFlowOut(stmt, res, interproceduralCFG(), functionType);
				return res;
			}
			
			/**
			 * Creates a new taint abstraction for the given value
			 * @param src The source statement from which the taint originated
			 * @param targetValue The target value that shall now be tainted
			 * @param source The incoming taint abstraction from the source
			 * @param taintSet The taint set to which to add all newly produced
			 * taints
			 */
			private Abstraction addTaintViaStmt
					(final Abstraction d1,
					final Stmt src,
					final Value targetValue,
					Abstraction source,
					Set<Abstraction> taintSet,
					boolean cutFirstField,
					SootMethod method,
					Type targetType) {
				// Keep the original taint
				taintSet.add(source);
				
				// Do not taint static fields unless the option is enabled
				if (!enableStaticFields && targetValue instanceof StaticFieldRef)
					return null;
				
				// Strip array references to their respective base
				Value baseTarget = targetValue;
				if (targetValue instanceof ArrayRef)
					baseTarget = ((ArrayRef) targetValue).getBase();

				// also taint the target of the assignment
				Abstraction newAbs = null;
				if (source.getAccessPath().isEmpty())
					newAbs = source.deriveNewAbstraction(new AccessPath(targetValue, true), src, true);
				else
					newAbs = source.deriveNewAbstraction(baseTarget, cutFirstField, src, targetType);
				
				// @ TODO
				newAbs.setSourceContext(source.getSourceContext());
				
				taintSet.add(newAbs);
								
				if (triggerInaktiveTaintOrReverseFlow(src, targetValue, newAbs)
						&& newAbs.isAbstractionActive()) {
					// If we overwrite the complete local, there is no need for
					// a backwards analysis
					if (!(aliasing.mustAlias(targetValue, newAbs.getAccessPath().getPlainValue())
							&& newAbs.getAccessPath().isLocal()))
						if(d1 != null)
							computeAliasTaints(d1, src, targetValue, taintSet, method, newAbs);
				}
				
				return newAbs;
			}
			
			private boolean isFieldReadByCallee(
					final Set<SootField> fieldsReadByCallee, Abstraction source) {
				if (fieldsReadByCallee == null)
					return true;
				return fieldsReadByCallee.contains(source.getAccessPath().getFirstField());
			}

			@Override
			public FlowFunction<Abstraction> getNormalFlowFunction(final Unit src, final Unit dest) {
				final SourceInfo sourceInfo = sourceSinkManager != null
						? sourceSinkManager.getSourceInfo((Stmt) src, interproceduralCFG()) : null;
						
				// If we compute flows on parameters, we create the initial
				// flow fact here
				if (src instanceof IdentityStmt) {
					final IdentityStmt is = (IdentityStmt) src;
					
					return new SolverNormalFlowFunction() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
							commonComputeTargets(source, src);
							//logger.info("getNormalFlowFunction: {}", src != null ? src.toString() : "null");
							
							if (stopAfterFirstFlow && !results.isEmpty())
								return Collections.emptySet();
							
							// Notify the handler if we have one
							for (TaintPropagationHandler tp : taintPropagationHandlers)
								tp.notifyFlowIn(is, Collections.singleton(source),
										interproceduralCFG(), FlowFunctionType.NormalFlowFunction);

							// Check whether we must leave a conditional branch
							if (source.isTopPostdominator(is)) {
								source = source.dropTopPostdominator();
								// Have we dropped the last postdominator for an empty taint?
								if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
									return Collections.emptySet();
							}

							// This may also be a parameter access we regard as a source
							Set<Abstraction> res = new HashSet<Abstraction>();
							if (source == zeroValue && sourceInfo != null) {
								// @TODO -> makes no sense, source == zeroValue
								SourceContext oldSourceContext = source.getSourceContext();
								FeatureInfo oldFeatureInfo = (FeatureInfo) oldSourceContext.getUserData();
								
								
								Value rightVal = is.getRightOp();
								boolean value = false;
								int featureIndex = -1;
								if(rightVal instanceof NumericConstant) {
									int val = Integer.parseInt(rightVal.toString());
									if(val == 1) {
										value = true;
									}
								} else if(rightVal.equals(oldFeatureInfo.getVariable().getPlainLocal())) {
									value = oldFeatureInfo.getValue();
									featureIndex = oldFeatureInfo.getIndex();
								} else {
									throw new UnsupportedOperationException("Undefined handling of rightVal " + rightVal.toString());
								}
								
								FeatureInfo featureInfo = new FeatureInfo(new AccessPath(is.getLeftOp(), false), value, featureIndex);
								
								final Abstraction abs = new Abstraction(is.getLeftOp(), sourceInfo.getTaintSubFields(), is.getRightOp(), 
										is, featureInfo, false, false);

								res.add(abs);
								
								// Compute the aliases
								if (triggerInaktiveTaintOrReverseFlow(is, is.getLeftOp(), abs))
									computeAliasTaints(d1, is, is.getLeftOp(), res, interproceduralCFG().getMethodOf(is), abs);
								
								return res;
							}
							
							boolean addOriginal = true;
							if (is.getRightOp() instanceof CaughtExceptionRef) {
								if (source.getExceptionThrown()) {
									res.add(source.deriveNewAbstractionOnCatch(is.getLeftOp()));
									addOriginal = false;
								}
							}

							if (addOriginal)
								res.add(source);
							
							return res;
						}
					};

				}

				// taint is propagated with assignStmt
				else if (src instanceof AssignStmt) {
					final AssignStmt assignStmt = (AssignStmt) src;
					final Value right = assignStmt.getRightOp();
					final Value left = assignStmt.getLeftOp();

					final Value leftValue = BaseSelector.selectBase(left, true);
					final Set<Value> rightVals = BaseSelector.selectBaseList(right, true);

					//final boolean isSink = sourceSinkManager != null
					//		? sourceSinkManager.isSink(assignStmt, interproceduralCFG()) : false;

					return new SolverNormalFlowFunction() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
								
							commonComputeTargets(d1, source, src);
							
							if (stopAfterFirstFlow && !results.isEmpty())
								return Collections.emptySet();
							
							// Notify the handler if we have one
							for (TaintPropagationHandler tp : taintPropagationHandlers)
								tp.notifyFlowIn(assignStmt, Collections.singleton(source),
										interproceduralCFG(), FlowFunctionType.NormalFlowFunction);

							// Make sure nothing all wonky is going on here
							assert source.getAccessPath().isEmpty()
									|| source.getTopPostdominator() == null;
							assert source.getTopPostdominator() == null
									|| interproceduralCFG().getMethodOf(src) == source.getTopPostdominator().getMethod()
									|| interproceduralCFG().getMethodOf(src).getActiveBody().getUnits().contains
											(source.getTopPostdominator().getUnit());
							
							boolean addLeftValue = false;
							boolean copyTaintInformation = false;
							Set<Abstraction> res = new HashSet<Abstraction>();
							
							// Fields can be sources in some cases
                            if (source.equals(zeroValue) && sourceInfo != null) {
                            	
                            	Value rightVal = assignStmt.getRightOp();
                            	// We are precise about NumericConstant but in all other cases assume true for boolean value
//								boolean value = true;
//								if(rightVal instanceof NumericConstant) {
//									int val = Integer.parseInt(rightVal.toString());
//									if(val != 1) {
//										value = false;
//									}
//								}
								
								FeatureInfo featureInfo = new FeatureInfo(new AccessPath(assignStmt.getLeftOp(), false), true, (int) sourceInfo.getUserData());
                            	
								final Abstraction abs = new Abstraction(assignStmt.getLeftOp(), sourceInfo.getTaintSubFields(), assignStmt.getRightOp(), 
										assignStmt, featureInfo, false, false);
								
								res.add(abs);
								
								if(sourceSinkManager.trackPrecise(sourceInfo)) {
									FeatureInfo featureInfo2 = new FeatureInfo(new AccessPath(assignStmt.getLeftOp(), false), false, (int) sourceInfo.getUserData());
	                            	
									final Abstraction abs2 = new Abstraction(assignStmt.getLeftOp(), sourceInfo.getTaintSubFields(), assignStmt.getRightOp(), 
											assignStmt, featureInfo2, false, false);
									res.add(abs2);
								}
                                
                                
                                
                                // Compute the aliases
								if (triggerInaktiveTaintOrReverseFlow(assignStmt, assignStmt.getLeftOp(), abs))
									computeAliasTaints(d1, assignStmt, assignStmt.getLeftOp(), res,
											interproceduralCFG().getMethodOf(assignStmt), abs);
								
                                return res;
                            }

                            // on NormalFlow taint cannot be created
							if (source.equals(zeroValue))
								return Collections.emptySet();

							// Check whether we must leave a conditional branch
							if (source.isTopPostdominator(assignStmt)) {
								source = source.dropTopPostdominator();
								// Have we dropped the last postdominator for an empty taint?
								if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
									return Collections.emptySet();
							}
							
							final Abstraction newSource;
							if (!source.isAbstractionActive() && src.equals(source.getActivationUnit()))
								newSource = source.getActiveCopy();
							else
								newSource = source;
							
							// If we have a non-empty postdominator stack, we taint
							// every assignment target
							if (newSource.getTopPostdominator() != null || newSource.getAccessPath().isEmpty()) {
								assert enableImplicitFlows;
								
								// We can skip over all local assignments inside conditionally-
								// called functions since they are not visible in the caller
								// anyway
								if (d1 != null && d1.getAccessPath().isEmpty() && !(leftValue instanceof FieldRef))
									return Collections.singleton(newSource);
								
								if (newSource.getAccessPath().isEmpty())
									addLeftValue = true;
							}
							
							// If we have a = x with the taint "x" being inactive,
							// we must not taint the left side. We can only taint
							// the left side if the tainted value is some "x.y".
							boolean aliasOverwritten = !addLeftValue && !newSource.isAbstractionActive()
									&& baseMatchesStrict(right, newSource)
									&& right.getType() instanceof RefType
									&& !source.dependsOnCutAP();
							
							boolean cutFirstField = false;
							AccessPath mappedAP = newSource.getAccessPath();
							Type targetType = null;
							if (!addLeftValue && !aliasOverwritten) {
								for (Value rightValue : rightVals) {
									if (rightValue instanceof FieldRef) {
										// Get the field reference and check for aliasing
										FieldRef rightRef = (FieldRef) rightValue;
										mappedAP = aliasing.mayAlias(newSource.getAccessPath(),
												new AccessPath(rightRef, false));

										// check if static variable is tainted (same name, same class)
										//y = X.f && X.f tainted --> y, X.f tainted
										if (rightValue instanceof StaticFieldRef) {
											if (enableStaticFields && mappedAP != null) {
												addLeftValue = true;
												cutFirstField = true;
												copyTaintInformation = true;
											}
										}
										// if both are fields, we have to compare their fieldName via equals and their bases
										//y = x.f && x tainted --> y, x tainted
										//y = x.f && x.f tainted --> y, x tainted
										else if (rightValue instanceof InstanceFieldRef) {								
											Local rightBase = (Local) ((InstanceFieldRef) rightRef).getBase();
											Local sourceBase = newSource.getAccessPath().getPlainLocal();

											// We need to compare the access path on the right side
											// with the start of the given one
											if (mappedAP != null) {
												addLeftValue = true;
												copyTaintInformation = true;
												cutFirstField = (mappedAP.getFieldCount() > 0
														&& mappedAP.getFirstField().equals(rightRef.getField()));
											}
											else if (aliasing.mayAlias(rightBase, sourceBase)
													&& newSource.getAccessPath().getFieldCount() == 0
													&& newSource.getAccessPath().getTaintSubFields()) {
												addLeftValue = true;
												copyTaintInformation = true;
												targetType = rightRef.getField().getType();
											}
										}
									}
									// indirect taint propagation:
									// if rightvalue is local and source is instancefield of this local:
									// y = x && x.f tainted --> y.f, x.f tainted
									// y.g = x && x.f tainted --> y.g.f, x.f tainted
									else if (rightValue instanceof Local && newSource.getAccessPath().isInstanceFieldRef()) {
										Local base = newSource.getAccessPath().getPlainLocal();
										if (aliasing.mayAlias(rightValue, base)) {
											addLeftValue = true;
											copyTaintInformation = true;
											targetType = newSource.getAccessPath().getBaseType();
											
										}
									}
									//y = x[i] && x tainted -> x, y tainted
									else if (rightValue instanceof ArrayRef) {
										Local rightBase = (Local) ((ArrayRef) rightValue).getBase();
										if (aliasing.mayAlias(rightBase, newSource.getAccessPath().getPlainValue())) {
											addLeftValue = true;
											copyTaintInformation = true;
											targetType = newSource.getAccessPath().getBaseType();
											assert targetType instanceof ArrayType;
										}
									}
									// generic case, is true for Locals, ArrayRefs that are equal etc..
									//y = x && x tainted --> y, x tainted
									else if (aliasing.mayAlias(rightValue, newSource.getAccessPath().getPlainValue())) {
										addLeftValue = true;
										copyTaintInformation = true;
										targetType = newSource.getAccessPath().getBaseType();
									}
								}
							}

							// if one of them is true -> add leftValue
							if (addLeftValue) {
								// If the right side is a typecast, it must be compatible,
								// or this path is not realizable
								if (assignStmt.getRightOp() instanceof CastExpr) {
									CastExpr ce = (CastExpr) assignStmt.getRightOp();
									if (!checkCast(newSource.getAccessPath(), ce.getCastType()))
										return Collections.emptySet();
								}
								
								if (!newSource.getAccessPath().isEmpty()) {
									// Special type handling for certain operations
									if (assignStmt.getRightOp() instanceof LengthExpr) {
										assert newSource.getAccessPath().getBaseType() instanceof ArrayType;
										targetType = IntType.v();
									}
									else if (assignStmt.getRightOp() instanceof CastExpr) {
										// If we cast java.lang.Object to an array type,
										// we must update our typing information
										CastExpr cast = (CastExpr) assignStmt.getRightOp();
										if (cast.getType() instanceof ArrayType && !(targetType instanceof ArrayType)) {
											assert targetType instanceof RefType;
											assert ((RefType) targetType).getSootClass()
													.getName().equals("java.lang.Object");
											targetType = cast.getType();
										}
									}
									
									// TODO: other direction. test case?

									
									// Special handling for array (de)construction
									if (targetType != null) {
										if (leftValue instanceof ArrayRef)
											targetType = ArrayType.v(targetType, 1);
										else if (assignStmt.getRightOp() instanceof ArrayRef)
											targetType = ((ArrayType) targetType).getArrayElementType();
									}
								}
								else
									assert targetType == null;
								
								if( newSource.isAbstractionActive())
								{
									Abstraction newAbs = addTaintViaStmt(d1, (Stmt) src, leftValue, newSource, res, cutFirstField,
											interproceduralCFG().getMethodOf(src), targetType);
									
									assert rightVals != null;
									assert rightVals.size() < 2;
									
									if(rightVals != null) {
										assert rightVals.size() == 1;
										// TODO - Variable aufnehmen und dann SPÄTER über Abstractions den Wert auslessen
										
										Value rightVal = rightVals.iterator().next();
										Boolean value = null;
										int featureIndex = -1;
										
										SourceContext oldSourceContext = newSource.getSourceContext();
										FeatureInfo featureInfo = null;
										if(oldSourceContext != null) {
											featureInfo = (FeatureInfo) oldSourceContext.getUserData();
										}
										

										if(rightVal instanceof IntConstant) {
											int val = Integer.parseInt(rightVals.iterator().next().toString());
											if(val == 0) {
												value = false;
											}
											if(val == 1) {
												value = true;
											}
										}
										AccessPath ap = null;
										if(featureInfo != null /*&& rightVal.equals(featureInfo.getVariableName()*/ /*&& aliasing.mayAlias(rightVal, featureInfo.getVariable().getPlainValue())*/
												&& copyTaintInformation) {

										    value = featureInfo.getValue();
											featureIndex = featureInfo.getIndex();
											ap = featureInfo.getVariable().copyWithNewValue(leftValue);
										} 
										
										if(ap == null)
										{
											ap = new AccessPath(leftValue, false);
										}
										
										SourceContext sourceContext = new SourceContext(leftValue, 
												 										(Stmt) src, 
												 										value != null ? new FeatureInfo(ap, value, featureIndex) : null);
										newAbs.setSourceContext(sourceContext);
									}
								}
								
								res.add(newSource);
								return res;
							}
							
							// If we have propagated taint, we have returned from this method by now
							
							//if leftvalue contains the tainted value -> it is overwritten - remove taint:
							//but not for arrayRefs:
							// x[i] = y --> taint is preserved since we do not distinguish between elements of collections 
							//because we do not use a MUST-Alias analysis, we cannot delete aliases of taints 
							if (assignStmt.getLeftOp() instanceof ArrayRef)
								return Collections.singleton(newSource);
							
							if(newSource.getAccessPath().isInstanceFieldRef()) {
								//x.f = y && x.f tainted --> no taint propagated
								if (leftValue instanceof InstanceFieldRef) {
									InstanceFieldRef leftRef = (InstanceFieldRef) leftValue;
									if (aliasing.mustAlias(leftRef.getBase(), newSource.getAccessPath().getPlainValue())) {
										if (aliasing.mustAlias(leftRef.getField(), newSource.getAccessPath().getFirstField())) {
											return Collections.emptySet();
										}
									}
								}
								//x = y && x.f tainted -> no taint propagated
								else if (leftValue instanceof Local){
									if (aliasing.mustAlias(leftValue, newSource.getAccessPath().getPlainValue())) {
										return Collections.emptySet();
									}
								}	
							}
							//X.f = y && X.f tainted -> no taint propagated. Kills are allowed even if
							// static field tracking is disabled
							else if (newSource.getAccessPath().isStaticFieldRef()){
								if(leftValue instanceof StaticFieldRef
										&& aliasing.mustAlias(((StaticFieldRef)leftValue).getField(), newSource.getAccessPath().getFirstField())){
									return Collections.emptySet();
								}
								
							}
							//when the fields of an object are tainted, but the base object is overwritten
							// then the fields should not be tainted any more
							//x = y && x.f tainted -> no taint propagated
							else if (newSource.getAccessPath().isLocal()
									&& aliasing.mustAlias(leftValue, newSource.getAccessPath().getPlainValue())){
								return Collections.emptySet();
							}
							
							//nothing applies: z = y && x tainted -> taint is preserved
							res.add(newSource);
							return res;
						}
					};
				}
				// for unbalanced problems, return statements correspond to
				// normal flows, not return flows, because there is no return
				// site we could jump to
				else if (src instanceof ReturnStmt) {
					final ReturnStmt returnStmt = (ReturnStmt) src;
					return new FlowFunction<Abstraction>() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction source) {
							commonComputeTargets(source, src);
							//logger.info("getNormalFlowFunction: {}", source != null ? source.toString() : "null");
							
							if (stopAfterFirstFlow && !results.isEmpty())
								return Collections.emptySet();

							// Notify the handler if we have one
							for (TaintPropagationHandler tp : taintPropagationHandlers)
								tp.notifyFlowIn(returnStmt, Collections.singleton(source),
										interproceduralCFG(), FlowFunctionType.NormalFlowFunction);

							// Check whether we must leave a conditional branch
							if (source.isTopPostdominator(returnStmt)) {
								source = source.dropTopPostdominator();
								// Have we dropped the last postdominator for an empty taint?
								if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
									return Collections.emptySet();
							}
							
							// Check whether we have reached a sink
							/*if (returnStmt.getOp().equals(source.getAccessPath().getPlainValue())
									&& source.isAbstractionActive()
									&& sourceSinkManager.isSink(returnStmt, interproceduralCFG())
									&& source.getAccessPath().isEmpty())
								results.add(new AbstractionAtSink(source, returnStmt.getOp(), returnStmt));
							*/
							return Collections.singleton(source);
						}
					};
				}
				else if (enableExceptions && src instanceof ThrowStmt) {
					final ThrowStmt throwStmt = (ThrowStmt) src;
					return new FlowFunction<Abstraction>() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction source) {
							commonComputeTargets(source, src);
							if (stopAfterFirstFlow && !results.isEmpty())
								return Collections.emptySet();
							
							// Notify the handler if we have one
							for (TaintPropagationHandler tp : taintPropagationHandlers)
								tp.notifyFlowIn(throwStmt, Collections.singleton(source),
										interproceduralCFG(), FlowFunctionType.NormalFlowFunction);

							// Check whether we must leave a conditional branch
							if (source.isTopPostdominator(throwStmt)) {
								source = source.dropTopPostdominator();
								// Have we dropped the last postdominator for an empty taint?
								if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
									return Collections.emptySet();
							}

							if (aliasing.mayAlias(throwStmt.getOp(), source.getAccessPath().getPlainLocal()))
								return Collections.singleton(source.deriveNewAbstractionOnThrow(throwStmt));
							return Collections.singleton(source);
						}
					};
				}
				// IF statements can lead to implicit flows
				else if (enableImplicitFlows && (src instanceof IfStmt || src instanceof LookupSwitchStmt
						|| src instanceof TableSwitchStmt)) {
					final Value condition = src instanceof IfStmt ? ((IfStmt) src).getCondition()
							: src instanceof LookupSwitchStmt ? ((LookupSwitchStmt) src).getKey()
							: ((TableSwitchStmt) src).getKey();	
							
					return new FlowFunction<Abstraction>() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction source) {
							commonComputeTargets(source, src);
							//logger.info("detected if statement {}", src);
									
							if (stopAfterFirstFlow && !results.isEmpty())
								return Collections.emptySet();

							// Notify the handler if we have one
							for (TaintPropagationHandler tp : taintPropagationHandlers)
								tp.notifyFlowIn((Stmt) src, Collections.singleton(source),
										interproceduralCFG(), FlowFunctionType.NormalFlowFunction);

							// Check whether we must leave a conditional branch
							if (source.isTopPostdominator(src)) {
								source = source.dropTopPostdominator();
								// Have we dropped the last postdominator for an empty taint?
								if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
									return Collections.emptySet();
							}
							
							// If we are in a conditionally-called method, there is no
							// need to care about further conditionals, since all
							// assignment targets will be tainted anyway
							if (source.getAccessPath().isEmpty())
								return Collections.singleton(source);
							
							Set<Abstraction> res = new HashSet<Abstraction>();
							res.add(source);

							Set<Value> values = new HashSet<Value>();
							if (condition instanceof Local)
								values.add(condition);
							else
								for (ValueBox box : condition.getUseBoxes())
									values.add(box.getValue());
														
							for (Value val : values)
								if (aliasing.mayAlias(val, source.getAccessPath().getPlainValue())) {
									// ok, we are now in a branch that depends on a secret value.
									// We now need the postdominator to know when we leave the
									// branch again.
									
//									List<Tag> tags = src.getTags();
//									logger.info("src now has {} tags", tags.size());
									
									UnitContainer postdom = interproceduralCFG().getPostdominatorOf(src);
									if (!(postdom.getMethod() == null
											&& source.getTopPostdominator() != null
											&& interproceduralCFG().getMethodOf(postdom.getUnit()) == source.getTopPostdominator().getMethod())) {
										Abstraction newAbs = source.deriveConditionalAbstractionEnter(postdom, (Stmt) src);
										newAbs.setSourceContext(source.getSourceContext());
										res.add(newAbs);
										break;
									}
								}
							return res;
						}
					};
				} 
				return Identity.v();
			}

			@Override
			public FlowFunction<Abstraction> getCallFlowFunction(final Unit src, final SootMethod dest) {
                if (!dest.isConcrete()){
                    logger.debug("Call skipped because target has no body: {} -> {}", src, dest);
                    return KillAll.v();
                }
                
				final Stmt stmt = (Stmt) src;
				final InvokeExpr ie = stmt.getInvokeExpr();
				
				final List<Value> callArgs = ie.getArgs();				
				final List<Value> paramLocals = new ArrayList<Value>(dest.getParameterCount());
				for (int i = 0; i < dest.getParameterCount(); i++)
					paramLocals.add(dest.getActiveBody().getParameterLocal(i));
				
//				final SourceInfo sourceInfo = sourceSinkManager != null
//						? sourceSinkManager.getSourceInfo((Stmt) src, interproceduralCFG()) : null;
				//final boolean isSink = sourceSinkManager != null
				//		? sourceSinkManager.isSink(stmt, interproceduralCFG()) : false;
				


				return new SolverCallFlowFunction() {

					@Override
					public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
						commonComputeTargets(d1, source, src);
						//logger.info("getCallFlowFunction: {}", src != null ? src.toString() : "null");
						
						if (stopAfterFirstFlow && !results.isEmpty())
							return Collections.emptySet();
						if (source.equals(zeroValue))
							return Collections.singleton(source);
						
						// Notify the handler if we have one
						for (TaintPropagationHandler tp : taintPropagationHandlers)
							tp.notifyFlowIn(stmt, Collections.singleton(source),
									interproceduralCFG(), FlowFunctionType.CallFlowFunction);

						// If we have an exclusive taint wrapper for the target
						// method, we do not perform an own taint propagation. 
						if(taintWrapper != null && taintWrapper.isExclusive(stmt, source.getAccessPath(),
								interproceduralCFG())) {
							//taint is propagated in CallToReturnFunction, so we do not need any taint here:
							return Collections.emptySet();
						}
						
						//if we do not have to look into sources or sinks:
						//if (!inspectSources && isSource)
						//	return Collections.emptySet();
						//if (!inspectSinks && isSink)
						//	return Collections.emptySet();
						
						// Check whether we must leave a conditional branch
						if (source.isTopPostdominator(stmt)) {
							source = source.dropTopPostdominator();
							// Have we dropped the last postdominator for an empty taint?
							if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
								return Collections.emptySet();
						}
						
						// If no parameter is tainted, but we are in a conditional, we create a
						// pseudo abstraction. We do not map parameters if we are handling an
						// implicit flow anyway.
						if (source.getAccessPath().isEmpty()) {
							assert enableImplicitFlows;
							
							// Block the call site for further explicit tracking
							if (d1 != null) {
								synchronized (implicitTargets) {
									if (!implicitTargets.containsKey(src))
										implicitTargets.put(src, new ConcurrentHashSet<Abstraction>());
								}
								implicitTargets.get(src).add(d1);
							}
							
							Abstraction abs = source.deriveConditionalAbstractionCall(src);
							abs.setSourceContext(new SourceContext(abs, source.getSourceContext().getUserData()));
							return Collections.singleton(abs);
						}
						else if (source.getTopPostdominator() != null)
							return Collections.emptySet();
						
						// If we have already tracked implicits flows through this method,
						// there is no point in tracking explicit ones afterwards as well.
						if (implicitTargets.containsKey(src) && (d1 == null || implicitTargets.get(src).contains(d1)))
							return Collections.emptySet();

						// Only propagate the taint if the target field is actually read
						if (enableStaticFields && source.getAccessPath().isStaticFieldRef()) {
							final Set<SootField> fieldsReadByCallee = enableStaticFields ? interproceduralCFG().getReadVariables
									(interproceduralCFG().getMethodOf(stmt), stmt) : null;
							if (fieldsReadByCallee != null && !isFieldReadByCallee(fieldsReadByCallee, source))
								return Collections.emptySet();
						}
						
						Set<Abstraction> res = new HashSet<Abstraction>();
						// check if whole object is tainted (happens with strings, for example:)
						if (!dest.isStatic() && ie instanceof InstanceInvokeExpr) {
							InstanceInvokeExpr vie = (InstanceInvokeExpr) ie;
							if (aliasing.mayAlias(vie.getBase(), source.getAccessPath().getPlainValue()))
								if (hasCompatibleTypesForCall(source.getAccessPath(), dest.getDeclaringClass())) {
									Abstraction abs = source.deriveNewAbstraction(source.getAccessPath().copyWithNewValue
											(dest.getActiveBody().getThisLocal()), stmt);
									abs.setSourceContext(new SourceContext(dest.getActiveBody().getThisLocal(), stmt, source.getSourceContext().getUserData()));
									res.add(abs);
								}
						}
						
						// staticfieldRefs must be analyzed even if they are not part of the params:
						if (enableStaticFields && source.getAccessPath().isStaticFieldRef())
							res.add(source);

						//special treatment for clinit methods - no param mapping possible	
						if(!dest.getName().equals("<clinit>")) {
							assert dest.getParameterCount() == callArgs.size();
							// check if param is tainted:
							for (int i = 0; i < callArgs.size(); i++) {
								if (aliasing.mayAlias(callArgs.get(i), source.getAccessPath().getPlainLocal())) {
									if(i < paramLocals.size()) {
										AccessPath newAP = source.getAccessPath().copyWithNewValue(paramLocals.get(i));
										Abstraction abs = source.deriveNewAbstraction(newAP, stmt);
										
										FeatureInfo originalFeatureInfo =  null;
										if(source.getSourceContext() != null && source.getSourceContext().getUserData() != null) {
											SourceContext sourceContext = source.getSourceContext();
											originalFeatureInfo = (FeatureInfo)sourceContext.getUserData();
										}
										
										FeatureInfo featureInfo = null;
										if(originalFeatureInfo != null) {
											featureInfo = new FeatureInfo(newAP, originalFeatureInfo.getValue(), originalFeatureInfo.getIndex());
										}
										abs.setSourceContext(new SourceContext(paramLocals.get(i), stmt, featureInfo));
										res.add(abs);
									}
								}
							}
						}
	
						return res;
					}
				};
			}

			@Override
			public FlowFunction<Abstraction> getReturnFlowFunction(final Unit callSite, final SootMethod callee, final Unit exitStmt, final Unit retSite) {
				
				final ReturnStmt returnStmt = (exitStmt instanceof ReturnStmt) ? (ReturnStmt) exitStmt : null;
				//final boolean isSink = (returnStmt != null && sourceSinkManager != null)
				//		? sourceSinkManager.isSink(returnStmt, interproceduralCFG()) : false;

				return new SolverReturnFlowFunction() {

					@Override
					public Set<Abstraction> computeTargets(Abstraction source, Collection<Abstraction> callerD1s) {
						commonComputeTargets(source, callSite);
						//logger.info("getReturnFlowFunction: {} with Abstraction {}", callSite != null ? callSite.toString() : "null", source);
						
						if (stopAfterFirstFlow && !results.isEmpty())
							return Collections.emptySet();
						if (source.equals(zeroValue))
							return Collections.emptySet();
						
						// Notify the handler if we have one
						for (TaintPropagationHandler tp : taintPropagationHandlers)
							tp.notifyFlowIn(exitStmt, Collections.singleton(source),
									interproceduralCFG(), FlowFunctionType.ReturnFlowFunction);
						
						boolean callerD1sConditional = false;
						for (Abstraction d1 : callerD1s)
							if (d1.getAccessPath().isEmpty()) {
								callerD1sConditional = true;
								break;
							}
						
						// Activate taint if necessary
						Abstraction newSource = source;
						if(!source.isAbstractionActive())
							if(callSite != null)
								if (callSite.equals(source.getActivationUnit())
										|| isCallSiteActivatingTaint(callSite, source.getActivationUnit()))
									newSource = source.getActiveCopy();
						
						// Empty access paths are never propagated over return edges
						if (source.getAccessPath().isEmpty()) {
							// If we return a constant, we must taint it
							if (returnStmt != null && returnStmt.getOp() instanceof Constant)
								if (callSite instanceof DefinitionStmt) {
									DefinitionStmt def = (DefinitionStmt) callSite;
									Abstraction abs = newSource.deriveNewAbstraction
											(newSource.getAccessPath().copyWithNewValue(def.getLeftOp()), (Stmt) exitStmt);
									
									
									FeatureInfo featureInfo = null;
									if(newSource.getSourceContext() != null) {
										Boolean value = null;
										if(returnStmt.getOp() instanceof IntConstant) {
											if(((IntConstant) returnStmt.getOp()).value == 0)
											{
												value = false;
											} else {
												value = true;
											}
										}
										FeatureInfo oldFeatureInfo = (FeatureInfo) newSource.getSourceContext().getUserData();
										if(oldFeatureInfo != null) {
											if(value == null) {
												value = oldFeatureInfo.getValue();
											}
											featureInfo = new FeatureInfo(new AccessPath(def.getLeftOp(), false), value, oldFeatureInfo.getIndex());
										}
									}

									abs.setSourceContext(new SourceContext(def.getLeftOp(), (Stmt) exitStmt, featureInfo));

									
									HashSet<Abstraction> res = new HashSet<Abstraction>();
									res.add(abs);
									
									// If we taint a return value because it is implicit,
									// we must trigger an alias analysis
									if(triggerInaktiveTaintOrReverseFlow(def, def.getLeftOp(), abs) && !callerD1sConditional)
										for (Abstraction d1 : callerD1s)
											computeAliasTaints(d1, (Stmt) callSite, def.getLeftOp(), res,
													interproceduralCFG().getMethodOf(callSite), abs);
									
									return res;
								}
							
							// Kill the empty abstraction
							return Collections.emptySet();
						}

						// Are we still inside a conditional? We check this before we
						// leave the method since the return value is still assigned
						// inside the method.
						boolean insideConditional = newSource.getTopPostdominator() != null
								|| newSource.getAccessPath().isEmpty();

						// Check whether we must leave a conditional branch
						if (newSource.isTopPostdominator(exitStmt) || newSource.isTopPostdominator(callee)) {
							newSource = newSource.dropTopPostdominator();
							// Have we dropped the last postdominator for an empty taint?
							if (!insideConditional
									&& newSource.getAccessPath().isEmpty()
									&& newSource.getTopPostdominator() == null)
								return Collections.emptySet();
						}

						//if abstraction is not active and activeStmt was in this method, it will not get activated = it can be removed:
						if(!newSource.isAbstractionActive() && newSource.getActivationUnit() != null)
							if (interproceduralCFG().getMethodOf(newSource.getActivationUnit()).equals(callee))
								return Collections.emptySet();
						
						Set<Abstraction> res = new HashSet<Abstraction>();

						// Check whether this return is treated as a sink
//						if (returnStmt != null) {
//							assert returnStmt.getOp() == null
//									|| returnStmt.getOp() instanceof Local
//									|| returnStmt.getOp() instanceof Constant;
//							
//							boolean mustTaintSink = insideConditional;
//							mustTaintSink |= returnStmt.getOp() != null
//									&& newSource.getAccessPath().isLocal()
//									&& aliasing.mayAlias(newSource.getAccessPath().getPlainValue(), returnStmt.getOp());
//							if (mustTaintSink && isSink && newSource.isAbstractionActive())
//								results.add(new AbstractionAtSink(newSource, returnStmt.getOp(), returnStmt));
//						}
												
						// If we have no caller, we have nowhere to propagate. This
						// can happen when leaving the main method.
						if (callSite == null)
							return Collections.emptySet();
						
						// if we have a returnStmt we have to look at the returned value:
						if (returnStmt != null && callSite instanceof DefinitionStmt) {
							Value retLocal = returnStmt.getOp();
							DefinitionStmt defnStmt = (DefinitionStmt) callSite;
							Value leftOp = defnStmt.getLeftOp();
							
							if ((insideConditional && leftOp instanceof FieldRef)
									|| aliasing.mayAlias(retLocal, newSource.getAccessPath().getPlainLocal())) {
								Abstraction abs = newSource.deriveNewAbstraction
										(newSource.getAccessPath().copyWithNewValue(leftOp), (Stmt) exitStmt);

								SourceContext sourceContext = newSource.getSourceContext();
								FeatureInfo featureInfo = null;
								if(sourceContext != null && sourceContext.getUserData() != null) {
									FeatureInfo originalFeatureInfo = (FeatureInfo)sourceContext.getUserData();
									featureInfo = new FeatureInfo(new AccessPath(leftOp, false), originalFeatureInfo.getValue(), originalFeatureInfo.getIndex());
								}
								abs.setSourceContext(new SourceContext(leftOp, defnStmt, featureInfo));
								res.add(abs);
								
								// Aliases of implicitly tainted variables must be mapped back
								// into the caller's context on return when we leave the last
								// implicitly-called method
								if ((abs.isImplicit()
										&& triggerInaktiveTaintOrReverseFlow(defnStmt, leftOp, abs)
										&& !callerD1sConditional) || aliasingStrategy.requiresAnalysisOnReturn())
									for (Abstraction d1 : callerD1s)
										computeAliasTaints(d1, (Stmt) callSite, leftOp, res,
												interproceduralCFG().getMethodOf(callSite), abs);
							}
						}

						// easy: static
						if (enableStaticFields && newSource.getAccessPath().isStaticFieldRef()) {
							
							// @TODO check if abs fieldref is live at callsite
							boolean isLive = true;
							
//							if(icfg instanceof BiDiInterproceduralCFG<?,?>) {
//								BiDiInterproceduralCFG<Unit, SootMethod> biDiICFG = (BiDiInterproceduralCFG<Unit, SootMethod>) icfg;
//								SootMethod method = icfg.getMethodOf(callSite);
//								UnitGraph unitGraph = (UnitGraph) biDiICFG.getOrCreateUnitGraph(method);
//								
//								if(!liveLocalsAnalysis.containsKey(unitGraph)) {
//									liveLocalsAnalysis.put(unitGraph, new SimpleLiveLocals(unitGraph));
//								}
//								SimpleLiveLocals liveAnalysis = liveLocalsAnalysis.get(unitGraph);
//								List liveLocals = liveAnalysis.getLiveLocalsAfter(callSite);
//								if(!liveLocals.contains(newSource.getAccessPath().getPlainLocal())) {
//									isLive = false;
//								}
//							}
							
							
							// Simply pass on the taint
							Abstraction abs = newSource;
							if(isLive) {
								res.add(abs);
							}

							// Aliases of implicitly tainted variables must be mapped back
							// into the caller's context on return when we leave the last
							// implicitly-called method
							if ((abs.isImplicit() && !callerD1sConditional)
									 || aliasingStrategy.requiresAnalysisOnReturn())
								for (Abstraction d1 : callerD1s)
									computeAliasTaints(d1, (Stmt) callSite, null, res,
											interproceduralCFG().getMethodOf(callSite), abs);
						}
						
						// checks: this/params/fields

						// check one of the call params are tainted (not if simple type)
						Value sourceBase = newSource.getAccessPath().getPlainLocal();
						{
						Value originalCallArg = null;
						for (int i = 0; i < callee.getParameterCount(); i++) {
							if (aliasing.mayAlias(callee.getActiveBody().getParameterLocal(i), sourceBase)) {
								if (callSite instanceof Stmt) {
									Stmt iStmt = (Stmt) callSite;
									originalCallArg = iStmt.getInvokeExpr().getArg(i);
									
									// If this is a constant parameter, we can safely ignore it
									if (!AccessPath.canContainValue(originalCallArg))
										continue;
									
									Abstraction abs = newSource.deriveNewAbstraction
											(newSource.getAccessPath().copyWithNewValue(originalCallArg), (Stmt) exitStmt);
									
									abs.setSourceContext(new SourceContext(originalCallArg, (Stmt) exitStmt, newSource.getSourceContext().getUserData()));
			
									res.add(abs);

									// Aliases of implicitly tainted variables must be mapped back
									// into the caller's context on return when we leave the last
									// implicitly-called method
									if ((abs.isImplicit()
											&& triggerInaktiveTaintOrReverseFlow((Stmt) callSite, originalCallArg, newSource)
											&& !callerD1sConditional) || aliasingStrategy.requiresAnalysisOnReturn())
										for (Abstraction d1 : callerD1s)
											computeAliasTaints(d1, (Stmt) callSite, originalCallArg, res,
												interproceduralCFG().getMethodOf(callSite), abs);											
								}
							}
						}
						}

						
						{
						if (!callee.isStatic()) {
							Local thisL = callee.getActiveBody().getThisLocal();
							if (aliasing.mayAlias(thisL, sourceBase)) {
								boolean param = false;
								// check if it is not one of the params (then we have already fixed it)
								for (int i = 0; i < callee.getParameterCount(); i++) {
									if (aliasing.mayAlias(callee.getActiveBody().getParameterLocal(i), sourceBase)) {
										param = true;
										break;
									}
								}
								if (!param) {
									if (callSite instanceof Stmt) {
										Stmt stmt = (Stmt) callSite;
										if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
											InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
											Abstraction abs = newSource.deriveNewAbstraction
													(newSource.getAccessPath().copyWithNewValue(iIExpr.getBase()), stmt);
											abs.setSourceContext(new SourceContext(iIExpr.getBase(), stmt, newSource.getSourceContext().getUserData()));
											res.add(abs);
											
											// Aliases of implicitly tainted variables must be mapped back
											// into the caller's context on return when we leave the last
											// implicitly-called method
											if ((abs.isImplicit()
													&& triggerInaktiveTaintOrReverseFlow(stmt, iIExpr.getBase(), abs)
													&& !callerD1sConditional) || aliasingStrategy.requiresAnalysisOnReturn())
												for (Abstraction d1 : callerD1s)
													computeAliasTaints(d1, (Stmt) callSite, iIExpr.getBase(), res,
															interproceduralCFG().getMethodOf(callSite), abs);											
										}
									}
								}
							}
							}
						}
						/*
						for(Abstraction abs : res)
						{
							logger.info("Created Abstraction {}", abs);
						}
						 */
						return res;
					}

				};
			}

			@Override
			public FlowFunction<Abstraction> getCallToReturnFlowFunction(final Unit call, final Unit returnSite) {
				// special treatment for native methods:
				if (call instanceof Stmt) {
					final Stmt iStmt = (Stmt) call;
					final InvokeExpr invExpr = iStmt.getInvokeExpr();
					final List<Value> callArgs = iStmt.getInvokeExpr().getArgs();
					
					final SourceInfo sourceInfo = sourceSinkManager != null
							? sourceSinkManager.getSourceInfo((Stmt) call, interproceduralCFG()) : null;
					//final boolean isSink = (sourceSinkManager != null)
					//		? sourceSinkManager.isSink(iStmt, interproceduralCFG()) : false;
					

					return new SolverCallToReturnFlowFunction() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
							Set<Abstraction> res = computeTargetsInternal(d1, source);
							return notifyOutFlowHandlers(call, res, FlowFunctionType.CallToReturnFlowFunction);
						}
						
						private Set<Abstraction> computeTargetsInternal(Abstraction d1, Abstraction source) {
							commonComputeTargets(d1, source, call);
							//logger.info("getCallToReturnFlowFunction: {}", call != null ? call.toString() : "null");
							
							if (stopAfterFirstFlow && !results.isEmpty())
								return Collections.emptySet();
							
							// Notify the handler if we have one
							for (TaintPropagationHandler tp : taintPropagationHandlers)
								tp.notifyFlowIn(call, Collections.singleton(source),
										interproceduralCFG(), FlowFunctionType.CallToReturnFlowFunction);

							// Check whether we must leave a conditional branch
							if (source.isTopPostdominator(iStmt)) {
								source = source.dropTopPostdominator();
								// Have we dropped the last postdominator for an empty taint?
								if (source.getAccessPath().isEmpty() && source.getTopPostdominator() == null)
									return Collections.emptySet();
							}

							
							Set<Abstraction> res = new HashSet<Abstraction>();

							// Sources can either be assignments like x = getSecret() or
							// instance method calls like constructor invocations
							if (source == zeroValue && sourceInfo != null) {
								// If we have nothing to taint, we can skip this source
								if (!(iStmt instanceof AssignStmt || invExpr instanceof InstanceInvokeExpr))
									return Collections.emptySet();
								
								final Value target;
								if (iStmt instanceof AssignStmt)
									target = ((AssignStmt) iStmt).getLeftOp();
								else
									target = ((InstanceInvokeExpr) invExpr).getBase();
								
								Object userData = sourceInfo.getUserData();
								FeatureInfo featureInfo = new FeatureInfo(new AccessPath(target, false), true, userData != null ? (int) userData : -1);
								
								final Abstraction abs = new Abstraction(target, sourceInfo.getTaintSubFields(), iStmt.getInvokeExpr(), 
										iStmt, featureInfo, false, false);
								res.add(abs);
			
								if(sourceSinkManager.trackPrecise(sourceInfo)) {
									FeatureInfo featureInfo2 = new FeatureInfo(new AccessPath(target, false), false, userData != null ? (int) userData : -1);
									
									final Abstraction abs2 = new Abstraction(target, sourceInfo.getTaintSubFields(), iStmt.getInvokeExpr(), 
											iStmt, featureInfo2, false, false);
			
									res.add(abs2);
								}
								
								
								// Compute the aliases
								if (triggerInaktiveTaintOrReverseFlow(iStmt, target, abs))
									computeAliasTaints(d1, iStmt, target, res, interproceduralCFG().getMethodOf(call), abs);
								
								return res;
							}
							
							//check inactive elements:
							final Abstraction newSource;
							if (!source.isAbstractionActive() && (call.equals(source.getActivationUnit())
									|| isCallSiteActivatingTaint(call, source.getActivationUnit())))
								newSource = source.getActiveCopy();
							else
								newSource = source;
							
							// Compute the taint wrapper taints
							res.addAll(computeWrapperTaints(d1, iStmt, newSource));
							
							// Implicit flows: taint return value
							if (call instanceof DefinitionStmt && (newSource.getTopPostdominator() != null
									|| newSource.getAccessPath().isEmpty())) {
								Value leftVal = ((DefinitionStmt) call).getLeftOp();
								Abstraction abs = newSource.deriveNewAbstraction(new AccessPath(leftVal, true),
										(Stmt) call);
								abs.setSourceContext(new SourceContext(leftVal, (DefinitionStmt) call, null));
								res.add(abs);
							}

							// We can only pass on a taint if it is neither a parameter nor the
							// base object of the current call. If this call overwrites the left
							// side, the taint is never passed on.
							boolean passOn = !newSource.getAccessPath().isStaticFieldRef()
									&& !(call instanceof DefinitionStmt && aliasing.mayAlias(((DefinitionStmt) call).getLeftOp(),
											newSource.getAccessPath().getPlainLocal()));

							// If the callee does not read the given value, we also need to pass it on
							// since we do not propagate it into the callee.
							if (enableStaticFields && source.getAccessPath().isStaticFieldRef()) {
								Set<SootField> fieldsWrittenByCallee = enableStaticFields ? interproceduralCFG().getWriteVariables
										(interproceduralCFG().getMethodOf(call), iStmt) : null;
								Set<SootField> fieldsReadByCallee = enableStaticFields ? interproceduralCFG().getReadVariables
								        (interproceduralCFG().getMethodOf(call), iStmt) : null;
								if (fieldsReadByCallee != null && !isFieldReadByCallee(fieldsReadByCallee, source)
										&& !isFieldReadByCallee(fieldsWrittenByCallee, source)) {
									passOn = true;
								}
							}
							
							// Implicit taints are always passed over conditionally called methods
							passOn |= source.getTopPostdominator() != null || source.getAccessPath().isEmpty();
							
							if(passOn) {
								if(source.getSourceContext() != null) {
									Value value = source.getSourceContext().getValue();
									if(value != null && call instanceof DefinitionStmt) {
										if(((DefinitionStmt) call).getLeftOp() == value) {
											passOn = false;
										}
									}
								}
							}
							
							
							if (passOn)
								if (newSource != zeroValue)
									res.add(newSource);
							
							if (iStmt.getInvokeExpr().getMethod().isNative())
								if (callArgs.contains(newSource.getAccessPath().getPlainValue())) {
									// java uses call by value, but fields of complex objects can be changed (and tainted), so use this conservative approach:
									Set<Abstraction> nativeAbs = ncHandler.getTaintedValues(iStmt, newSource, callArgs);
									res.addAll(nativeAbs);
									
									// Compute the aliases
									for (Abstraction abs : nativeAbs)
										if (abs.getAccessPath().isStaticFieldRef()
												|| triggerInaktiveTaintOrReverseFlow(iStmt, abs.getAccessPath().getPlainValue(), abs))
											computeAliasTaints(d1, (Stmt) call, abs.getAccessPath().getPlainValue(), res,
													interproceduralCFG().getMethodOf(call), abs);
								}
							
							
							// if we have called a sink we have to store the path from the source - in case one of the params is tainted!
			
							// If we are inside a conditional branch, we consider every sink call a leak
							boolean conditionalCall = enableImplicitFlows 
									&& !interproceduralCFG().getMethodOf(call).isStatic()
									&& interproceduralCFG().getMethodOf(call).getActiveBody().getThisLocal().equals
											(newSource.getAccessPath().getPlainValue())
									&& newSource.getAccessPath().getFirstField() == null;
							boolean taintedParam = (newSource.getTopPostdominator() != null
										|| newSource.getAccessPath().isEmpty()
										|| conditionalCall)
									&& newSource.isAbstractionActive();
							// If the base object is tainted, we also consider the "code" associated
							// with the object's class as tainted.
							if (!taintedParam) {
								for (int i = 0; i < callArgs.size(); i++) {
									if (callArgs.get(i).equals(newSource.getAccessPath().getPlainLocal())) {
										taintedParam = true;
										break;
									}
								}
							}
							
							/*if (newSource.isAbstractionActive() && taintedParam && callArgs.size() > 0) {
								if (call instanceof DefinitionStmt) {
									
									//SootMethod m = icfg.getMethodOf(call);
									
									Value leftVal = ((DefinitionStmt) call).getLeftOp();
								
									Abstraction abs = newSource.deriveNewAbstraction(new AccessPath(leftVal, false), iStmt);
									FeatureInfo oldFeatureInfo = (FeatureInfo) newSource.getSourceContext().getUserData();
									FeatureInfo featureInfo = new FeatureInfo(leftVal, true, false, oldFeatureInfo != null ? oldFeatureInfo.getIndex() : -1);
									abs.setSourceContext(new SourceContext(leftVal, (Stmt) call, featureInfo));
									res.add(abs);
									
									//logger.info("Additional abs a");
								}
							}*/
							/*
							// if the base object which executes the method is tainted the sink is reached, too.
							if (iStmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
								String temp = iStmt.toString();
								InstanceInvokeExpr vie = (InstanceInvokeExpr) iStmt.getInvokeExpr();
								if (newSource.isAbstractionActive() && vie.getBase().equals(newSource.getAccessPath().getPlainValue())) {
									if (call instanceof DefinitionStmt) {
										
										//SootMethod m = icfg.getMethodOf(call);
										Value leftVal = ((DefinitionStmt) call).getLeftOp();
									
										Abstraction abs = newSource.deriveNewAbstraction(new AccessPath(leftVal, false),
												iStmt);
										FeatureInfo oldFeatureInfo = (FeatureInfo) newSource.getSourceContext().getUserData();
										FeatureInfo featureInfo = new FeatureInfo(leftVal, true, false, oldFeatureInfo != null ? oldFeatureInfo.getIndex() : -1);
										abs.setSourceContext(new SourceContext(leftVal, (Stmt) call, featureInfo));
										res.add(abs);
										
										//logger.info("Additional abs b");
									}
								}
							}
							*/
							
							
							return res;
						}

						/**
						 * Checks whether the given call has at least one valid target,
						 * i.e. a callee with a body.
						 * @param call The call site to check
						 * @return True if there is at least one callee implementation
						 * for the given call, otherwise false
						 */
						private boolean hasValidCallees(Unit call) {
							Set<SootMethod> callees = interproceduralCFG().getCalleesOfCallAt(call);
							for (SootMethod callee : callees)
								if (callee.isConcrete())
										return true;
							return false;
						}


					};
				}
				return Identity.v();
			}
		};
	}

	public LoadTimeInfoflowProblem(IInfoflowCFG icfg, ISourceSinkManager sourceSinkManager,
			IAliasingStrategy aliasingStrategy) {
		super(icfg,sourceSinkManager);
		this.sourceSinkManager = sourceSinkManager;
		this.icfg = icfg;
		
		if(aliasingStrategy == null) {
			throw new IllegalArgumentException("aliasingStrategy must not be null.");
		}
		
		this.aliasingStrategy = aliasingStrategy;
		this.aliasing = new Aliasing(aliasingStrategy);
		this.implicitFlowAliasingStrategy = new ImplicitFlowAliasStrategy(icfg);
	}

	
	public InterproceduralCFG<Unit, SootMethod> getICfg()
	{
		return this.icfg;
	}
	
	@Override
	public boolean autoAddZero() {
		return false;
	}


}

