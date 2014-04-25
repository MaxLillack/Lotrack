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

import soot.ArrayType;
import soot.Local;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.FieldRef;

public class DataTypeHandler {
	
	/**
	 * Determines whether a given value is a field ref or an array
	 * @param val the value to inspect
	 * @return true if fieldref/array
	 */
	public static boolean isFieldRefOrArrayRef(Value val){
		if(val == null){
			return false;
		}
		if(val instanceof FieldRef || val instanceof ArrayRef
				// TODO: necessary?
				|| (val instanceof Local && ((Local)val).getType() instanceof ArrayType)){
			return true;
		}
		return false;
	}

}
