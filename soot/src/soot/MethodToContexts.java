/* Soot - a J*va Optimization Framework
 * Copyright (C) 2003 Ondrej Lhotak
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

package soot;
import java.util.*;

/** Keeps track of the various contexts associated with each method.
 * @author Ondrej Lhotak
 */
public final class MethodToContexts
{ 
    private final Map<SootMethod, List> map = new HashMap<SootMethod, List>();
    public void add( MethodOrMethodContext momc ) {
        SootMethod m = momc.method();
        List<MethodOrMethodContext> l = map.get(m);
        if( l == null ) map.put(m, l = new ArrayList<MethodOrMethodContext>() );
        l.add(momc);
    }
    public MethodToContexts() {
    }
    public MethodToContexts( Iterator it ) {
    	add(it);
    }
    public void add( Iterator it ) {
        while( it.hasNext() ) {
            MethodOrMethodContext momc = (MethodOrMethodContext) it.next();
            add(momc);
        }
    }
    public List get( SootMethod m ) {
        List ret = map.get(m);
        if( ret == null ) ret = new ArrayList();
        return ret;
    }
}
