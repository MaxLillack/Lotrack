/*******************************************************************************
 * Copyright (c) 2013 Eric Bodden.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Eric Bodden - initial API and implementation
 ******************************************************************************/
package heros.solver;

import heros.solver.JumpFunctions;

public class JumpFnSingleton {
    private JumpFunctions jumpFn;
    
    private static JumpFnSingleton singleton;
    
    public JumpFunctions getJumpFn() {
        return jumpFn;
    }

    public static void init(JumpFunctions jumpFn)
    {
        singleton = new JumpFnSingleton(jumpFn);
    }
    
    public static JumpFnSingleton getInstance()
    {
        return singleton;
    }
    
    private JumpFnSingleton(JumpFunctions jumpFn) {
        this.jumpFn = jumpFn;
    }
    
    
}
