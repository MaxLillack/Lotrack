/*******************************************************************************
 * Copyright (c) 2015 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package heros.solver;

public class OrderHelper<N> {
	public int context;
	public int exitContext;
	public N target;
	
	public OrderHelper(int context, int exitContext, N target) {
		this.context = context;
		this.exitContext = exitContext;
		this.target = target;
	}
	
}
