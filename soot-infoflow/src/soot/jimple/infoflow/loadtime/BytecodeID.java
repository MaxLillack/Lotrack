package soot.jimple.infoflow.loadtime;

public class BytecodeID {
	private String bcMethod;
	private int bcIndex;
	
	public BytecodeID(String bcMethod, int bcIndex) {
		if(bcMethod == null)
		{
			throw new IllegalArgumentException();
		}
		this.bcMethod = bcMethod;
		this.bcIndex = bcIndex;
	}
	
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + bcIndex;
		result = prime * result
				+ ((bcMethod == null) ? 0 : bcMethod.hashCode());
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
		BytecodeID other = (BytecodeID) obj;
		if (bcIndex != other.bcIndex)
			return false;
		if (bcMethod == null) {
			if (other.bcMethod != null)
				return false;
		} else if (!bcMethod.equals(other.bcMethod))
			return false;
		return true;
	}
	
	
}
