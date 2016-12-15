package soot.jimple.infoflow.loadtime;

import soot.jimple.infoflow.data.AccessPath;

public class FeatureInfo {
	private AccessPath variable;
	private Integer value;
	private int index;
	private Integer impreciseIndex;
	
	private String toStringCache;
	private boolean isAnti = false;
	// value = -1 indicates a unknown value
	// value = -2 indicate an irrelevant value, i.e., it only counts that this variable is tainted but not how, happens for implicit flows
	//            e.g. a is tainted if(a) b = Math.Random() -> value of b will be -2
 	public FeatureInfo(AccessPath variable, 
			   Integer value,
			   int index,
			   Integer impreciseIndex,
			   boolean isAnti) {
		this(variable, value, index, impreciseIndex);
		this.isAnti = isAnti;
	}
	
	public FeatureInfo(AccessPath variable, 
					   Integer value,
					   int index,
					   Integer impreciseIndex) {
		
		if(variable == null) {
			throw new IllegalArgumentException();
		}
		if(value == null) {
			throw new IllegalArgumentException();
		}

		this.variable = variable;
		this.value = value;
		this.index = index;
		this.impreciseIndex = impreciseIndex;

	}

	@Override
	public String toString() {
		if(toStringCache == null) {
			toStringCache = "FeatureInfo " + (isAnti ? "ANTI " : "")  + "[variableName=" + variable + ", value=" + value
				+ ", index=" + index + ", imprecise=" + impreciseIndex + "]";
		}
		return toStringCache;
	}

	public FeatureInfo(FeatureInfo original) {
		this.variable = original.variable;
		this.value = original.value;
		this.index = original.index;
		this.impreciseIndex = original.impreciseIndex;
		this.isAnti = original.isAnti;
	}

	public AccessPath getVariable() {
		return variable;
	}

	public Integer getValue() {
		return value;
	}
	
	public int getIndex()
	{
		return index;
	}
	
	public Integer getImpreciseIndex()
	{
		return impreciseIndex;
	}
	
	public Integer getBestIndexAvailable()
	{
		if(index > -1)
		{
			return index;
		} else {
			return impreciseIndex;
		}
	}
	
	public void setIsAnti(boolean isAnti)
	{
		this.isAnti = isAnti;
	}
	
	public boolean getIsAnti()
	{
		return isAnti;
	}
	
//	@Override
	// Currently ignores isAnti property
//	public int hashCode() {
//		final int prime = 31;
//		int result = 1;
//		result = prime * result + ((value == null) ? 0 : value.hashCode());
//		result = prime * result + ((variable == null) ? 0 : variable.hashCode());
//		result += index;
//		result = prime * result + ((impreciseIndex == null) ? 0 : impreciseIndex.hashCode());
//		return result;
//	}
	
	

//	@Override
//	// Currently ignores isAnti property
//	public boolean equals(Object obj) {
//		if (this == obj)
//			return true;
//		if (obj == null)
//			return false;
//		if (getClass() != obj.getClass())
//			return false;
//		FeatureInfo other = (FeatureInfo) obj;
//		if (value == null) {
//			if (other.value != null)
//				return false;
//		} else if (!value.equals(other.value))
//			return false;
//		if (!variable.equals(other.variable))
//			return false;
//		if (index != other.index)
//			return false;
//		if (impreciseIndex != other.impreciseIndex)
//			return false;
//		return true;
//	}

	public void setValue(Integer value) {
		if(value == null) {
			throw new IllegalArgumentException();
		}
		this.value = value;
		
		toStringCache = null;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((impreciseIndex == null) ? 0 : impreciseIndex.hashCode());
		result = prime * result + index;
		result = prime * result + (isAnti ? 1231 : 1237);
		result = prime * result + ((value == null) ? 0 : value.hashCode());
//		result = prime * result + ((variable == null) ? 0 : variable.hashCode());
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
		if (impreciseIndex == null) {
			if (other.impreciseIndex != null)
				return false;
		} else if (!impreciseIndex.equals(other.impreciseIndex))
			return false;
		if (index != other.index)
			return false;
		if (isAnti != other.isAnti)
			return false;
		if (value == null) {
			if (other.value != null)
				return false;
		} else if (!value.equals(other.value))
			return false;
//		if (variable == null) {
//			if (other.variable != null)
//				return false;
//		} else if (!variable.equals(other.variable))
//			return false;
		return true;
	}

	public void setVariable(AccessPath ap)
	{
		if(ap == null)
		{
			throw new IllegalArgumentException();
		}
		this.variable = ap;
		toStringCache = null;
	}
}
