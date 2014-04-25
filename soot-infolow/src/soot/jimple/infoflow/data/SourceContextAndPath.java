package soot.jimple.infoflow.data;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import soot.Value;
import soot.jimple.Stmt;

/**
 * Extension of {@link SourceContext} that also allows a paths from the source
 * to the current statement to be stored
 * 
 * @author Steven Arzt
 */
public class SourceContextAndPath extends SourceContext implements Cloneable {
	private final List<Stmt> path = new LinkedList<Stmt>();
	
	public SourceContextAndPath(Value value, Stmt stmt) {
		super(value, stmt);
	}
	
	public SourceContextAndPath(Value value, Stmt stmt, Object userData) {
		super(value, stmt, userData);
	}

	public SourceContextAndPath(Abstraction symbolic) {
		super(symbolic);
	}

	public SourceContextAndPath(Abstraction symbolic, Object userData) {
		super(symbolic, userData);
	}

	public List<Stmt> getPath() {
		return Collections.unmodifiableList(this.path);
	}
	
	public SourceContextAndPath extendPath(Stmt s) {
		SourceContextAndPath scap = clone();
		scap.path.add(s);
		return scap;
	}
	
	public SourceContextAndPath extendPath(Collection<Stmt> s) {
		SourceContextAndPath scap = clone();
		scap.path.addAll(s);
		return scap;
	}

	@Override
	public boolean equals(Object other) {
		return super.equals(other);
	}
	
	@Override
	public int hashCode() {
		return super.hashCode();
	}
	
	@Override
	public SourceContextAndPath clone() {
		final SourceContextAndPath scap;
		if (getSymbolic() == null)
			scap = new SourceContextAndPath(getValue(), getStmt(), getUserData());
		else
			scap = new SourceContextAndPath(getSymbolic(), getUserData());
		scap.path.addAll(this.path);
		assert scap.equals(this);
		return scap;
	}
	
	@Override
	public String toString() {
		return super.toString() + "\n\ton Path: " + path;
	}	
}
