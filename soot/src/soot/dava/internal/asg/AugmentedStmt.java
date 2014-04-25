/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Jerome Miecznikowski
 * Copyright (C) 2004-2005 Nomair A. Naeem
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

package soot.dava.internal.asg;
import soot.*;

import java.util.*;
import soot.util.*;
import soot.jimple.*;
import soot.dava.internal.SET.*;


public class AugmentedStmt 
{
    public List bpreds, bsuccs, cpreds, csuccs;
    public SETNode myNode;

    private final IterableSet dominators;
	private IterableSet reachers;
    private Stmt s;


    public AugmentedStmt( Stmt s)
    {
	this.s = s;

	dominators = new IterableSet();
	reachers   = new IterableSet();

	reset_PredsSuccs();
    }
    
    public void set_Stmt( Stmt s)
    {
	this.s = s;
    }

    public boolean add_BPred( AugmentedStmt bpred)
    {
	if (add_CPred( bpred) == false)
	    return false;

	if (bpreds.contains( bpred)) {
	    cpreds.remove( bpred);
	    return false;
	}

	bpreds.add( bpred);
	return true;
    }

    public boolean add_BSucc( AugmentedStmt bsucc)
    {
	if (add_CSucc( bsucc) == false)
	    return false;

	if (bsuccs.contains( bsucc)) {
	    csuccs.remove( bsucc);
	    return false;
	}
	
	bsuccs.add( bsucc);
	return true;
    }

    public boolean add_CPred( AugmentedStmt cpred)
    {
	if (cpreds.contains( cpred) == false) {
	    cpreds.add( cpred);
	    return true;
	}

	return false;
    }

    public boolean add_CSucc( AugmentedStmt csucc)
    {
	if (csuccs.contains( csucc) == false) {
	    csuccs.add( csucc);
	    return true;
	}

	return false;
    }

    public boolean remove_BPred( AugmentedStmt bpred)
    {
	if (remove_CPred( bpred) == false)
	    return false;

	if (bpreds.contains( bpred)) {
	    bpreds.remove( bpred);
	    return true;
	}
	
	cpreds.add( bpred);
	return false;
    }
    
    public boolean remove_BSucc( AugmentedStmt bsucc)
    {
	if (remove_CSucc( bsucc) == false)
	    return false;

	if (bsuccs.contains( bsucc)) {
	    bsuccs.remove( bsucc);
	    return true;
	}
	
	csuccs.add( bsucc);
	return false;
    }

    public boolean remove_CPred( AugmentedStmt cpred)
    {
	if (cpreds.contains( cpred)) {
	    cpreds.remove( cpred);
	    return true;
	}

	return false;
    }

    public boolean remove_CSucc( AugmentedStmt csucc)
    {
	if (csuccs.contains( csucc)) {
	    csuccs.remove( csucc);
	    return true;
	}

	return false;
    }

    public Stmt get_Stmt()
    {
	return s;
    }

    public IterableSet get_Dominators()
    {
	return dominators;
    }

    public IterableSet get_Reachers()
    {
	return reachers;
    }

    public void set_Reachability( IterableSet reachers)
    {
	this.reachers = reachers;
    }

    public void dump()
    {
	G.v().out.println( toString());
    }

    public String toString()
    {
	return "(" + s.toString() + " @ " + hashCode() + ")";
    }

    public void reset_PredsSuccs()
    {
	bpreds = new LinkedList();
	bsuccs = new LinkedList();
	cpreds = new LinkedList();
	csuccs = new LinkedList();
    }

    public Object clone()
    {
	return new AugmentedStmt( (Stmt) s.clone());
    }
}
