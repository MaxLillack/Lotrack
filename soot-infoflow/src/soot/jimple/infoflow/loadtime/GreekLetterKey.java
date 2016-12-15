package soot.jimple.infoflow.loadtime;

import soot.Unit;

public class GreekLetterKey {
	private Unit src;
	private int feature;
	
	public GreekLetterKey(Unit src, int feature) {
		if(src == null) {
			throw new IllegalArgumentException();
		}
		
		this.src = src;
		this.feature = feature;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + feature;
		result = prime * result + ((src == null) ? 0 : src.hashCode());
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
		GreekLetterKey other = (GreekLetterKey) obj;
		if (feature != other.feature)
			return false;
		if (src == null) {
			if (other.src != null)
				return false;
		} else if (!src.equals(other.src))
			return false;
		return true;
	}
	
	
}
