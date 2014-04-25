package soot.jimple.infoflow.data;

import soot.Value;
import soot.jimple.Stmt;

/**
 * Class representing a source value together with the statement that created it
 * 
 * @author Steven Arzt
 */
public class SourceContext implements Cloneable {
	private final Value value;
	private final Stmt stmt;
	private final Object userData;
	private final Abstraction symbolic;
	
	public SourceContext(Value value, Stmt stmt) {
		this.value = value;
		this.stmt = stmt;
		this.symbolic = null;
		this.userData = null;
	}
	
	public SourceContext(Value value, Stmt stmt, Object userData) {
		this.value = value;
		this.stmt = stmt;
		this.symbolic = null;
		this.userData = userData;
	}

	public SourceContext(Abstraction symbolic) {
		this.value = null;
		this.stmt = null;
		this.symbolic = symbolic;
		this.userData = null;
	}
	
	public SourceContext(Abstraction symbolic, Object userData) {
		this.value = null;
		this.stmt = null;
		this.symbolic = symbolic;
		this.userData = userData;
	}

	public Value getValue() {
		return this.value;
	}
	
	public Stmt getStmt() {
		return this.stmt;
	}
	
	public Object getUserData() {
		return this.userData;
	}
	
	public Abstraction getSymbolic() {
		return this.symbolic;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((stmt == null) ? 0 : stmt.hashCode());
		result = prime * result + ((value == null) ? 0 : value.hashCode());
		result = prime * result + ((userData == null) ? 0 : userData.hashCode());
		// bug: possible loop if this.symbolic equals an abstraction with this in its sourceContext
		//result = prime * result + ((symbolic == null) ? 0 : symbolic.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;
		SourceContext other = (SourceContext) obj;
		if (stmt == null) {
			if (other.stmt != null)
				return false;
		} else if (!stmt.equals(other.stmt))
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
		if (userData == null) {
			if (other.userData != null)
				return false;
		} else if (!userData.equals(other.userData))
			return false;
		if (symbolic == null) {
			if (other.symbolic != null)
				return false;
		}/* else if (!symbolic.equals(other.symbolic))
			return false;*/
		return true;
	}
	
	@Override
	public SourceContext clone() {
		SourceContext sc = new SourceContext(value, stmt, userData);
		assert sc.equals(this);
		return sc;
	}

	@Override
	public String toString() {
		if (symbolic != null)
			return "SYMBOLIC: " + symbolic;
		return value.toString();
	}
}
