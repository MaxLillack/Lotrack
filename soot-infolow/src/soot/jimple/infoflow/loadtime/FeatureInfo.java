package soot.jimple.infoflow.loadtime;

import soot.Value;
import soot.jimple.infoflow.data.AccessPath;
import soot.spl.ifds.Constraint;

public class FeatureInfo {
	private AccessPath variable;
	private Boolean value;
	private int index;
	
	public FeatureInfo(AccessPath variable, 
					   Boolean value,
					   int index) {
		
		if(variable == null) {
			throw new IllegalArgumentException();
		}
		if(value == null) {
			throw new IllegalArgumentException();
		}

		this.variable = variable;
		this.value = value;
		this.index = index;
	}

	@Override
	public String toString() {
		return "FeatureInfo [variableName=" + variable + ", value=" + value
				+ ", index=" + index + "]";
	}

	public FeatureInfo(FeatureInfo original) {
		this.variable = original.variable;
		this.value = original.value;
		this.index = original.index;
	}

	public AccessPath getVariable() {
		return variable;
	}

	public Boolean getValue() {
		return value;
	}
	
	public int getIndex()
	{
		return index;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((value == null) ? 0 : value.hashCode());
//		result = prime * result + ((variable == null) ? 0 : variable.hashCode());
		result += index;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		FeatureInfo other = (FeatureInfo) obj;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
//		if (!variable.equals(other.variable))
//			return false;
		if (index != other.index)
			return false;
		return true;
	}

	public void setValue(boolean value) {
		this.value = value;
	}
}
