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

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multithreaded version of a hash set
 * 
 * @author Steven Arzt
 */
public class ConcurrentHashSet<E> extends AbstractSet<E> implements Set<E> {

    protected ConcurrentHashMap<E,E> delegate;
    
    /**
     * Creates a new, empty ConcurrentHashSet. 
     */
    public ConcurrentHashSet() {
        delegate = new ConcurrentHashMap<E, E>();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.containsKey(o);
    }

    @Override
    public Iterator<E> iterator() {
        return delegate.keySet().iterator();
    }

    @Override
    public boolean add(E o) {
        return delegate.put(o, o)==null;
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o)!=null;
    }

    @Override
    public void clear() {
        delegate.entrySet().clear();
    }

    @Override
	public int hashCode() {
		return delegate.hashCode();
	}

    @Override
	public boolean equals(Object obj) {
		return obj instanceof ConcurrentHashSet && delegate.equals(obj);
	}
	
    @Override
	public String toString() {
		return delegate.keySet().toString();
	}

	
}
