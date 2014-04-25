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
package soot.jimple.infoflow.nativ;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import soot.Value;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.Abstraction;

public class DefaultNativeCallHandler extends NativeCallHandler {
	
	@Override
	public Set<Abstraction> getTaintedValues(Stmt call, Abstraction source, List<Value> params){
		HashSet<Abstraction> set = new HashSet<Abstraction>();
		
		//check some evaluated methods:
		
		//arraycopy:
		//arraycopy(Object src, int srcPos, Object dest, int destPos, int length)
        //Copies an array from the specified source array, beginning at the specified position,
		//to the specified position of the destination array.
		if(call.getInvokeExpr().getMethod().toString().contains("arraycopy")){
			if(params.get(0).equals(source.getAccessPath().getPlainValue())){
				Abstraction abs = source.deriveNewAbstraction(params.get(2), false, call,
						source.getAccessPath().getBaseType());
				set.add(abs);
			}
		}else{
			//generic case: add taint to all non-primitive datatypes:
//			for (int i = 0; i < params.size(); i++) {
//				Value argValue = params.get(i);
//				if (DataTypeHandler.isFieldRefOrArrayRef(argValue) && !(argValue instanceof Constant)) {
//					Abstraction abs = source.deriveNewAbstraction(argValue, call);
//				}
//			}	
		}
		//add the  returnvalue:
//		if(call instanceof DefinitionStmt){
//			DefinitionStmt dStmt = (DefinitionStmt) call;
//			Abstraction abs = source.deriveNewAbstraction(dStmt.getLeftOp(), call);
//		}
		
		return set;
	}
	
}