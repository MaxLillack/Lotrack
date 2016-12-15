/*******************************************************************************
 * Copyright (c) 2015 Johannes Lerch.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Johannes Lerch - initial API and implementation
 ******************************************************************************/
package heros.fieldsens;

import heros.fieldsens.AccessPath.Delta;
import heros.fieldsens.structs.DeltaConstraint;
import heros.fieldsens.structs.WrappedFact;
import heros.fieldsens.structs.WrappedFactAtStatement;

public class ControlFlowJoinResolver<Field, Fact, Stmt, Method> extends ResolverTemplate<Field, Fact, Stmt, Method, WrappedFact<Field, Fact, Stmt, Method>> {

	private Stmt joinStmt;
	private AccessPath<Field> resolvedAccPath;
	private boolean propagated = false;

	public ControlFlowJoinResolver(PerAccessPathMethodAnalyzer<Field, Fact, Stmt, Method> analyzer, Stmt joinStmt) {
		this(analyzer, joinStmt, new AccessPath<Field>(), null);
		propagated=false;
	}
	
	private ControlFlowJoinResolver(PerAccessPathMethodAnalyzer<Field, Fact, Stmt, Method> analyzer, Stmt joinStmt, AccessPath<Field> resolvedAccPath, ControlFlowJoinResolver<Field, Fact, Stmt, Method> parent) {
		super(analyzer, parent);
		this.joinStmt = joinStmt;
		this.resolvedAccPath = resolvedAccPath;
		propagated=true;
	}
	
	@Override
	protected AccessPath<Field> getAccessPathOf(WrappedFact<Field, Fact, Stmt, Method> inc) {
		return inc.getAccessPath();
	}

	protected void processIncomingGuaranteedPrefix(heros.fieldsens.structs.WrappedFact<Field,Fact,Stmt,Method> fact) {
		if(!propagated) {
			propagated=true;
			analyzer.processFlowFromJoinStmt(new WrappedFactAtStatement<Field, Fact, Stmt, Method>(joinStmt, new WrappedFact<Field, Fact, Stmt, Method>(
					fact.getFact(), new AccessPath<Field>(), this)));
		}
	};
	
	@Override
	protected void processIncomingPotentialPrefix(WrappedFact<Field, Fact, Stmt, Method> fact) {
		lock();
		Delta<Field> delta = fact.getAccessPath().getDeltaTo(resolvedAccPath);
		fact.getResolver().resolve(new DeltaConstraint<Field>(delta), new InterestCallback<Field, Fact, Stmt, Method>() {
			@Override
			public void interest(PerAccessPathMethodAnalyzer<Field, Fact, Stmt, Method> analyzer, 
					Resolver<Field, Fact, Stmt, Method> resolver) {
				ControlFlowJoinResolver.this.interest();
			}

			@Override
			public void canBeResolvedEmpty() {
				ControlFlowJoinResolver.this.canBeResolvedEmpty();
			}
		});
		unlock();
	}
	
	@Override
	protected ResolverTemplate<Field, Fact, Stmt, Method, WrappedFact<Field, Fact, Stmt, Method>> createNestedResolver(AccessPath<Field> newAccPath) {
		return new ControlFlowJoinResolver<Field, Fact, Stmt, Method>(analyzer, joinStmt, newAccPath, this);
	}

	@Override
	protected void log(String message) {
		analyzer.log("Join Stmt "+toString()+": "+message);
	}

	@Override
	public String toString() {
		return "<"+resolvedAccPath+":"+joinStmt+">";
	}

	@Override
	public AccessPath<Field> getResolvedAccessPath() {
		return resolvedAccPath;
	}

	public Stmt getJoinStmt() {
		return joinStmt;
	}
}
