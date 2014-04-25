/* Soot - a J*va Optimization Framework
 * Copyright (C) 2008 Ben Bellamy 
 * 
 * All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */
package soot.jimple.toolkits.typing.fast;

import soot.*;

/**
 * @author Ben Bellamy
 */
public class Integer127Type extends PrimType implements IntegerType
{
	private static Integer127Type instance;
	static { instance = new Integer127Type(); }
	
	public static Integer127Type v() { return instance; }
	
	private Integer127Type() { }
	
	public String toString() { return "[0..127]"; }
	public boolean equals(Object t) { return this == t; }

    @Override
    public RefType boxedType() {
    	return RefType.v("java.lang.Integer");
    }
}