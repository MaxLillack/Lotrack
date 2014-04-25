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
package soot.jimple.infoflow.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.BinopExpr;
import soot.jimple.UnopExpr;
import soot.jimple.internal.JCastExpr;

/**
 * BaseSelector removes unnecessary information from a value such as casts
 */
public class BaseSelector {
	
	/**
	 * the operations that are not relevant for analysis like "not" or casts
	 * are removed - array refs are only removed if explicitly stated
	 * @param val the value which should be pruned
	 * @param keepArrayRef if false then array refs are pruned to the base array object
	 * @return the value (possibly pruned to base object)
	 */ //we want to keep ArrayRef for objects on the right side of the assignment
	public static Value selectBase(Value val, boolean keepArrayRef){
		//we taint base of array instead of array elements
		if (val instanceof ArrayRef && !keepArrayRef) {
			return selectBase(((ArrayRef) val).getBase(), keepArrayRef);
		}
		
		if (val instanceof JCastExpr) {
			return selectBase(((JCastExpr) val).getOpBox().getValue(), keepArrayRef);
		}
		
		// Check for unary operators like "not" or "length"
		if (val instanceof UnopExpr)
			return selectBase(((UnopExpr) val).getOp(), keepArrayRef);
		
		return val;
	}

	/**
	 * the operations that are not relevant for analysis like "not" or casts
	 * are removed - array refs are only removed if explicitly stated
	 * BinOpExpr are divided into two values
	 * @param val the value which should be pruned
	 * @param keepArrayRef if false then array refs are pruned to the base array object
	 * @return one or more values 
	 */
	public static Set<Value> selectBaseList(Value val, boolean keepArrayRef){
		if (val instanceof BinopExpr) {
			Set<Value> set = new HashSet<Value>();
			BinopExpr expr = (BinopExpr) val;
			set.add(expr.getOp1());
			set.add(expr.getOp2());
			return set;
		}
		return Collections.singleton(selectBase(val, keepArrayRef));
	}
	
}
