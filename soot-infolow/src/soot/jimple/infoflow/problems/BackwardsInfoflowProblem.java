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
import heros.flowfunc.Identity;
import heros.flowfunc.KillAll;
import heros.solver.PathEdge;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.ArrayType;
import soot.IntType;
import soot.Local;
import soot.RefType;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.BinopExpr;
import soot.jimple.CastExpr;
import soot.jimple.Constant;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.IdentityStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.LengthExpr;
import soot.jimple.ReturnStmt;
import soot.jimple.StaticFieldRef;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.Abstraction;
import soot.jimple.infoflow.data.AccessPath;
import soot.jimple.infoflow.solver.IInfoflowSolver;
import soot.jimple.infoflow.solver.functions.SolverCallFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverCallToReturnFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverNormalFlowFunction;
import soot.jimple.infoflow.solver.functions.SolverReturnFlowFunction;
import soot.jimple.infoflow.source.ISourceSinkManager;
import soot.jimple.infoflow.source.SourceInfo;
import soot.jimple.infoflow.taintWrappers.ITaintPropagationWrapper;
import soot.jimple.infoflow.util.BaseSelector;
import soot.jimple.toolkits.ide.icfg.BiDiInterproceduralCFG;

/**
 * class which contains the flow functions for the backwards solver. This is required for on-demand alias analysis.
 */
public class BackwardsInfoflowProblem extends AbstractInfoflowProblem {
	private IInfoflowSolver fSolver;
	
	public void setTaintWrapper(ITaintPropagationWrapper wrapper) {
		taintWrapper = wrapper;
	}
	
	public BackwardsInfoflowProblem(BiDiInterproceduralCFG<Unit, SootMethod> icfg,
			ISourceSinkManager sourceSinkManager) {
		super(icfg, sourceSinkManager);
	}

	public void setForwardSolver(IInfoflowSolver forwardSolver) {
		fSolver = forwardSolver;
	}
	
	@Override
	public FlowFunctions<Unit, Abstraction, SootMethod> createFlowFunctionsFactory() {
		return new FlowFunctions<Unit, Abstraction, SootMethod>() {

			/**
			 * Computes the aliases for the given statement
			 * @param def The definition statement from which to extract
			 * the alias information
			 * @param d1 The abstraction at the method's start node
			 * @param source The source abstraction of the alias search
			 * from before the current statement
			 * @return The set of abstractions after the current statement
			 */
			private Set<Abstraction> computeAliases
					(final DefinitionStmt defStmt, Abstraction d1, Abstraction source) {
				assert !source.getAccessPath().isEmpty();
				
				final Set<Abstraction> res = new MutableTwoElementSet<Abstraction>();
				final Value leftValue = BaseSelector.selectBase(defStmt.getLeftOp(), true);
				
				// A backward analysis looks for aliases of existing taints and thus
				// cannot create new taints out of thin air
				if (source == getZeroValue())
					return Collections.emptySet();
				
				// Check whether the left side of the assignment matches our
				// current taint abstraction
				final boolean leftSideMatches = baseMatches(leftValue, source);
				if (!leftSideMatches)
					res.add(source);
				else {
					// The left side is overwritten completely
					
					// If we have an assignment to the base local of the current taint,
					// all taint propagations must be below that point, so this is the
					// right point to turn around.
					for (Unit u : interproceduralCFG().getPredsOf(defStmt))
						fSolver.processEdge(new PathEdge<Unit, Abstraction>(d1, u, source));
				}
				
				if (defStmt instanceof AssignStmt) {
					// Get the right side of the assignment
					final Value rightValue = BaseSelector.selectBase(defStmt.getRightOp(), false);
	
					// Is the left side overwritten completely?
					if (leftSideMatches) {
						// Termination shortcut: If the right side is a value we do not track,
						// we can stop here.
						if (!(rightValue instanceof Local || rightValue instanceof FieldRef))
							return Collections.emptySet();
					}
					
					// If we assign a constant, there is no need to track the right side
					// any further or do any forward propagation since constants cannot
					// carry taint.
					if (rightValue instanceof Constant)
						return res;
	
					// We only process heap objects. Binary operations can only
					// be performed on primitive objects.
					if (rightValue instanceof BinopExpr)
						return res;
									
					// If we have a = x with the taint "x" being inactive,
					// we must not taint the left side. We can only taint
					// the left side if the tainted value is some "x.y".
					boolean aliasOverwritten = baseMatchesStrict(rightValue, source)
							&& rightValue.getType() instanceof RefType
							&& !source.dependsOnCutAP();
					
					if (!aliasOverwritten) {
						// If the tainted value 'b' is assigned to variable 'a' and 'b'
						// is a heap object, we must also look for aliases of 'a' upwards
						// from the current statement.
						Abstraction newLeftAbs = null;
						if (rightValue instanceof InstanceFieldRef) {
							InstanceFieldRef ref = (InstanceFieldRef) rightValue;
							if (source.getAccessPath().isInstanceFieldRef()
									&& ref.getBase().equals(source.getAccessPath().getPlainValue())
									&& source.getAccessPath().firstFieldMatches(ref.getField())) {
								newLeftAbs = source.deriveNewAbstraction(leftValue, true,
										source.getAccessPath().getFirstFieldType());
							}
						}
						else if (enableStaticFields && rightValue instanceof StaticFieldRef) {
							StaticFieldRef ref = (StaticFieldRef) rightValue;
							if (source.getAccessPath().isStaticFieldRef()
									&& source.getAccessPath().firstFieldMatches(ref.getField())) {
								newLeftAbs = source.deriveNewAbstraction(leftValue, true,
										source.getAccessPath().getBaseType());
							}
						}
						else if (rightValue.equals(source.getAccessPath().getPlainValue())) {
							Type newType = source.getAccessPath().getBaseType();
							if (leftValue instanceof ArrayRef)
								newType = buildArrayOrAddDimension(newType);
							else if (defStmt.getRightOp() instanceof ArrayRef)
								newType = ((ArrayType) newType).getElementType();
							
							// If this is an unrealizable typecast, drop the abstraction
							if (defStmt.getRightOp() instanceof CastExpr) {
								CastExpr ce = (CastExpr) defStmt.getRightOp();
								if (!checkCast(source.getAccessPath(), ce.getCastType()))
									return Collections.emptySet();
							}
							// Special type handling for certain operations
							else if (defStmt.getRightOp() instanceof LengthExpr) {
								assert source.getAccessPath().getBaseType() instanceof ArrayType;
								newType = IntType.v();
								
								newLeftAbs = source.deriveNewAbstraction(new AccessPath(leftValue, null,
										IntType.v(), (Type[]) null, true), defStmt);
							}
							else {
								newLeftAbs = source.deriveNewAbstraction(source.getAccessPath().copyWithNewValue
										(leftValue, newType, false), defStmt);
							}
						}
						if (newLeftAbs != null) {
							res.add(newLeftAbs);
							
							// Inject the new alias into the forward solver
							for (Unit u : interproceduralCFG().getPredsOf(defStmt))
								fSolver.processEdge(new PathEdge<Unit, Abstraction>(d1, u, newLeftAbs));
						}
					}
					
					// If we have the tainted value on the left side of the assignment,
					// we also have to look or aliases of the value on the right side of
					// the assignment.
					if (rightValue instanceof Local
							|| rightValue instanceof FieldRef) {
						boolean addRightValue = false;
						boolean cutFirstField = false;
						Type targetType = null;
						
						// if both are fields, we have to compare their fieldName via equals and their bases via PTS
						if (leftValue instanceof InstanceFieldRef) {
							InstanceFieldRef leftRef = (InstanceFieldRef) leftValue;
							if (leftRef.getBase().equals(source.getAccessPath().getPlainLocal())) {
								if (source.getAccessPath().isInstanceFieldRef()) {
									if (source.getAccessPath().firstFieldMatches(leftRef.getField())) {
										targetType = source.getAccessPath().getFirstFieldType();
										addRightValue = true;
										cutFirstField = true;
									}
								}
							}
							// indirect taint propagation:
							// if leftValue is local and source is instancefield of this local:
						} else if (leftValue instanceof Local && source.getAccessPath().isInstanceFieldRef()) {
							Local base = source.getAccessPath().getPlainLocal(); // ?
							if (leftValue.equals(base)) {
								targetType = source.getAccessPath().getBaseType();
								addRightValue = true;
							}
						} else if (leftValue instanceof ArrayRef) {
							Local leftBase = (Local) ((ArrayRef) leftValue).getBase();
							if (leftBase.equals(source.getAccessPath().getPlainValue())) {
								addRightValue = true;
								targetType = source.getAccessPath().getBaseType();
								assert source.getAccessPath().getBaseType() instanceof ArrayType;
							}
							// generic case, is true for Locals, ArrayRefs that are equal etc..
						} else if (leftValue.equals(source.getAccessPath().getPlainValue())) {
							addRightValue = true;
							targetType = source.getAccessPath().getBaseType();
							
							// Check for unrealizable casts. If a = (O) b and a is tainted,
							// but incompatible to the type of b, this cast is impossible
							if (defStmt.getRightOp() instanceof CastExpr) {
								CastExpr ce = (CastExpr) defStmt.getRightOp();
								if (!checkCast(source.getAccessPath(), ce.getOp().getType()))
									return Collections.emptySet();
							}
						}
						
						// if one of them is true -> add rightValue
						if (addRightValue) {
							if (targetType != null) {
								// Special handling for some operations
								if (defStmt.getRightOp() instanceof ArrayRef)
									targetType = buildArrayOrAddDimension(targetType);
								else if (leftValue instanceof ArrayRef) {
									assert source.getAccessPath().getBaseType() instanceof ArrayType;
									targetType = ((ArrayType) targetType).getElementType();
									
									// If the types do not match, the right side cannot be an alias
									if (!canCastType(rightValue.getType(), targetType))
										addRightValue = false;
								}
							}
							
							// Special type handling for certain operations
							if (defStmt.getRightOp() instanceof LengthExpr)
								targetType = null;
							// We do not need to handle casts. Casts only make
							// types more imprecise when going backwards.
							if (addRightValue) {
								Abstraction newAbs = source.deriveNewAbstraction(rightValue, cutFirstField,
										targetType);
								res.add(newAbs);
								
								// Inject the new alias into the forward solver
								for (Unit u : interproceduralCFG().getPredsOf(defStmt))
									fSolver.processEdge(new PathEdge<Unit, Abstraction>(d1, u, newAbs));
							}
						}
					}
				}
				else if (defStmt instanceof IdentityStmt)
					res.add(source);
				
				return res;
			}
			
			@Override
			public FlowFunction<Abstraction> getNormalFlowFunction(final Unit src, final Unit dest) {
				
				if (src instanceof DefinitionStmt) {
					final DefinitionStmt defStmt = (DefinitionStmt) src;

					return new SolverNormalFlowFunction() {

						@Override
						public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
							if (source == getZeroValue())
								return Collections.emptySet();
							assert source.isAbstractionActive() || flowSensitiveAliasing;
							
							Set<Abstraction> res = computeAliases(defStmt, d1, source);
							
							if (dest instanceof DefinitionStmt && interproceduralCFG().isExitStmt(dest))
								for (Abstraction abs : res)
									computeAliases((DefinitionStmt) dest, d1, abs);
							
							return res;
						}

					};
				}
				return Identity.v();
			}

			@Override
			public FlowFunction<Abstraction> getCallFlowFunction(final Unit src, final SootMethod dest) {
				if (!dest.isConcrete())
					return KillAll.v();
				
				final Stmt stmt = (Stmt) src;
				final InvokeExpr ie = stmt.getInvokeExpr();

				final List<Value> callArgs = ie.getArgs();
				final List<Value> paramLocals = new ArrayList<Value>(dest.getParameterCount()); 
				for (int i = 0; i < dest.getParameterCount(); i++)
					paramLocals.add(dest.getActiveBody().getParameterLocal(i));
				
				final SourceInfo sourceInfo = sourceSinkManager != null
						? sourceSinkManager.getSourceInfo((Stmt) src, interproceduralCFG()) : null;
				final boolean isSink = sourceSinkManager != null
						? sourceSinkManager.isSink(stmt, interproceduralCFG()) : false;
				
				// This is not cached by Soot, so accesses are more expensive
				// than one might think
				final Local thisLocal = dest.isStatic() ? null : dest.getActiveBody().getThisLocal();	

				return new SolverCallFlowFunction() {

					@Override
					public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
						if (source == getZeroValue())
							return Collections.emptySet();
						assert source.isAbstractionActive() || flowSensitiveAliasing;
						
						//if we do not have to look into sources or sinks:
						if (!inspectSources && sourceInfo != null)
							return Collections.emptySet();
						if (!inspectSinks && isSink)
							return Collections.emptySet();
						
						// taint is propagated in CallToReturnFunction, so we do not
						// need any taint here if the taint wrapper is exclusive:
						if(taintWrapper != null && taintWrapper.isExclusive(stmt, source.getAccessPath(),
								interproceduralCFG()))
							return Collections.emptySet();
						
						Set<Abstraction> res = new HashSet<Abstraction>();
						
						// if the returned value is tainted - taint values from return statements
						if (src instanceof DefinitionStmt) {
							DefinitionStmt defnStmt = (DefinitionStmt) src;
							Value leftOp = defnStmt.getLeftOp();
							if (leftOp.equals(source.getAccessPath().getPlainValue())) {
								// look for returnStmts:
								for (Unit u : dest.getActiveBody().getUnits()) {
									if (u instanceof ReturnStmt) {
										ReturnStmt rStmt = (ReturnStmt) u;
										if (rStmt.getOp() instanceof Local
												|| rStmt.getOp() instanceof FieldRef) {
											Abstraction abs = source.deriveNewAbstraction
													(source.getAccessPath().copyWithNewValue
															(rStmt.getOp(), null, false), (Stmt) src);
											res.add(abs);
										}
									}
								}
							}
						}

						// easy: static
						if (enableStaticFields && source.getAccessPath().isStaticFieldRef())
							res.add(source);

						// checks: this/fields
						Value sourceBase = source.getAccessPath().getPlainValue();
						if (!dest.isStatic()) {
							InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
							if (iIExpr.getBase().equals(sourceBase)
									&& (hasCompatibleTypesForCall(source.getAccessPath(), dest.getDeclaringClass()))) {
								boolean param = false;
								// check if it is not one of the params (then we have already fixed it)
								for (int i = 0; i < dest.getParameterCount(); i++) {
									if (stmt.getInvokeExpr().getArg(i).equals(sourceBase)) {
										param = true;
										break;
									}
								}
								if (!param) {
									Abstraction abs = source.deriveNewAbstraction
											(source.getAccessPath().copyWithNewValue(thisLocal), (Stmt) src);
									res.add(abs);
								}
							}
						}
						
						// Map the parameter values into the callee
						if(!dest.getName().equals("<clinit>")) {
							assert dest.getParameterCount() == callArgs.size();
							// check if param is tainted:
							for (int i = 0; i < callArgs.size(); i++) {
								if (callArgs.get(i).equals(source.getAccessPath().getPlainLocal())) {
									Abstraction abs = source.deriveNewAbstraction(source.getAccessPath().copyWithNewValue
											(paramLocals.get(i)), stmt);
									res.add(abs);
								}
							}
						}
						
						// Check whether we need to directly map back any of
						// the parameters
						if (stmt instanceof DefinitionStmt) {
							Value leftVal = ((DefinitionStmt) stmt).getLeftOp();
							for (Unit sP : interproceduralCFG().getStartPointsOf(dest))
								if (sP instanceof ReturnStmt) {
									Value retVal = ((ReturnStmt) sP).getOp();
									for (Abstraction abs : res)
										if (abs.getAccessPath().getPlainValue() == retVal) {
											Abstraction retAbs = abs.deriveNewAbstraction
													(source.getAccessPath().copyWithNewValue(leftVal), stmt);
											for (Unit u : interproceduralCFG().getPredsOf(stmt))
												fSolver.processEdge(new PathEdge<Unit, Abstraction>(d1, u, retAbs));
										}
								}
						}
						
						return res;
					}
				};
			}

			@Override
			public FlowFunction<Abstraction> getReturnFlowFunction(final Unit callSite, final SootMethod callee,
					final Unit exitStmt, final Unit retSite) {
				final ReturnStmt returnStmt = (exitStmt instanceof ReturnStmt) ? (ReturnStmt) exitStmt : null;
				
				final List<Value> paramLocals = new ArrayList<Value>(callee.getParameterCount()); 
				for (int i = 0; i < callee.getParameterCount(); i++)
					paramLocals.add(callee.getActiveBody().getParameterLocal(i));

				// This is not cached by Soot, so accesses are more expensive
				// than one might think
				final Local thisLocal = callee.isStatic() ? null : callee.getActiveBody().getThisLocal();	

				return new SolverReturnFlowFunction() {
					
					@Override
					public Set<Abstraction> computeTargets(Abstraction source,
							Collection<Abstraction> callerD1s) {
						if (source == getZeroValue())
							return Collections.emptySet();
						assert source.isAbstractionActive() || flowSensitiveAliasing;
						
						// If we have no caller, we have nowhere to propagate. This
						// can happen when leaving the main method.
						if (callSite == null)
							return Collections.emptySet();
						
						// easy: static
						if (enableStaticFields && source.getAccessPath().isStaticFieldRef()) {
							registerActivationCallSite(callSite, callee, source);
							return Collections.singleton(source);
						}

						final Value sourceBase = source.getAccessPath().getPlainLocal();
						Set<Abstraction> res = new HashSet<Abstraction>();

						// if we have a returnStmt we have to look at the returned value:
						if (returnStmt != null && callSite instanceof DefinitionStmt) {
							DefinitionStmt defnStmt = (DefinitionStmt) callSite;
							Value leftOp = defnStmt.getLeftOp();
							
							if (leftOp == source.getAccessPath().getPlainValue()) {
								Abstraction abs = source.deriveNewAbstraction
										(source.getAccessPath().copyWithNewValue(leftOp), (Stmt) exitStmt);
								res.add(abs);
								registerActivationCallSite(callSite, callee, abs);
							}
						}
						
						// check one of the call params are tainted (not if simple type)
						{
						Value originalCallArg = null;
						for (int i = 0; i < callee.getParameterCount(); i++) {
							if (paramLocals.get(i) == sourceBase) 
								if (callSite instanceof Stmt) {
									Stmt iStmt = (Stmt) callSite;
									originalCallArg = iStmt.getInvokeExpr().getArg(i);
									
									// If this is a constant parameter, we can safely ignore it
									if (!AccessPath.canContainValue(originalCallArg))
										continue;
									if (!checkCast(source.getAccessPath(), originalCallArg.getType()))
										continue;
									
									Abstraction abs = source.deriveNewAbstraction
											(source.getAccessPath().copyWithNewValue(originalCallArg), (Stmt) exitStmt);
									res.add(abs);
									registerActivationCallSite(callSite, callee, abs);
								}
						}
						}
						
						{
						if (!callee.isStatic()) {
							if (thisLocal == sourceBase && hasCompatibleTypesForCall
									(source.getAccessPath(), callee.getDeclaringClass())) {
								boolean param = false;
								// check if it is not one of the params (then we have already fixed it)
								for (int i = 0; i < callee.getParameterCount(); i++) {
									if (paramLocals.get(i) == sourceBase) {
										param = true;
										break;
									}
								}
								if (!param) {
									if (callSite instanceof Stmt) {
										Stmt stmt = (Stmt) callSite;
										if (stmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
											InstanceInvokeExpr iIExpr = (InstanceInvokeExpr) stmt.getInvokeExpr();
											Abstraction abs = source.deriveNewAbstraction
													(source.getAccessPath().copyWithNewValue(iIExpr.getBase()), stmt);
											res.add(abs);
											registerActivationCallSite(callSite, callee, abs);
										}
									}
								}
							}
							}
						}
						
						return res;
					}
				};
			}

			@Override
			public FlowFunction<Abstraction> getCallToReturnFlowFunction(final Unit call, final Unit returnSite) {
				// special treatment for native methods:
				if (call instanceof Stmt) {
					final Stmt iStmt = (Stmt) call;
					final List<Value> callArgs = iStmt.getInvokeExpr().getArgs();
					
					return new SolverCallToReturnFlowFunction() {
						@Override
						public Set<Abstraction> computeTargets(Abstraction d1, Abstraction source) {
							if (source == getZeroValue())
								return Collections.emptySet();
							assert source.isAbstractionActive() || flowSensitiveAliasing;
							
							// We never pass static taints over the call-to-return edge
							if (source.getAccessPath().isStaticFieldRef())
								return Collections.emptySet();
							
							// We may not pass on a taint if it is overwritten by this call
							if (iStmt instanceof DefinitionStmt && ((DefinitionStmt) iStmt).getLeftOp().equals
									(source.getAccessPath().getPlainValue()))
								return Collections.emptySet();
							
							// If the base local of the invocation is tainted, we do not
							// pass on the taint
							if (iStmt.getInvokeExpr() instanceof InstanceInvokeExpr) {
								InstanceInvokeExpr iinv = (InstanceInvokeExpr) iStmt.getInvokeExpr();
								if (iinv.getBase().equals(source.getAccessPath().getPlainValue()))
									return Collections.emptySet();
							}
							
							// We do not pass taints on parameters over the call-to-return edge
							for (int i = 0; i < callArgs.size(); i++)
								if (callArgs.get(i).equals(source.getAccessPath().getPlainLocal()))
									return Collections.emptySet();
							
							return Collections.singleton(source);
						}
					};
				}
				return Identity.v();
			}
		};
	}
	
}