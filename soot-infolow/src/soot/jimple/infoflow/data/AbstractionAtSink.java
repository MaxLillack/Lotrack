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

import soot.Value;
import soot.jimple.Stmt;

public class AbstractionAtSink {
	
	private final Abstraction abstraction;
	private final Value sinkValue;
	private final Stmt sinkStmt;
	
	/**
	 * Creates a new instance of the {@link AbstractionAtSink} class
	 * @param abstraction The abstraction with which the sink has been reached
	 * @param sinkValue The value that triggered the sink, e.g, the InvokeExpr
	 * @param sinkStmt The statement that triggered the sink
	 */
	public AbstractionAtSink(Abstraction abstraction, Value sinkValue, Stmt sinkStmt) {
		this.abstraction = abstraction;
		this.sinkValue = sinkValue;
		this.sinkStmt = sinkStmt;
	}
	
	/**
	 * Gets the abstraction with which the sink has been reached
	 * @return The abstraction with which the sink has been reached
	 */
	public Abstraction getAbstraction() {
		return this.abstraction;
	}
	
	/**
	 * Gets the value that has triggered the sink, e.g., the InvokeExpr
	 * @return The value that has triggered the sink, e.g., the InvokeExpr
	 */
	public Value getSinkValue() {
		return this.sinkValue;
	}
	
	/**
	 * Gets the statement that triggered the sink
	 * @return The statement that triggered the sink
	 */
	public Stmt getSinkStmt() {
		return this.sinkStmt;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((abstraction == null) ? 0 : abstraction.hashCode());
		result = prime * result
				+ ((sinkStmt == null) ? 0 : sinkStmt.hashCode());
		result = prime * result
				+ ((sinkValue == null) ? 0 : sinkValue.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		AbstractionAtSink other = (AbstractionAtSink) obj;
		if (abstraction == null) {
			if (other.abstraction != null)
				return false;
		} else {
			if (!abstraction.equals(other.abstraction))
				return false;
		}
		if (sinkStmt == null) {
			if (other.sinkStmt != null)
				return false;
		} else if (!sinkStmt.equals(other.sinkStmt))
			return false;
		if (sinkValue == null) {
			if (other.sinkValue != null)
				return false;
		} else if (!sinkValue.equals(other.sinkValue))
			return false;
		return true;
	}
	
	@Override
	public String toString() {
		return abstraction + " at " + sinkStmt;
	}

}
