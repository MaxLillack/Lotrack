package soot.spl.ifds;

// Helper class to model a pair of constraints:
//            
// 
// a=B         A
// if(a)
//   ...       A && B -> A is base, B is extension

public class ConstraintSet {
	private IConstraint base;
	private IConstraint extension;
	private boolean isIncompleteFix = false;
	
	public ConstraintSet(IConstraint base, IConstraint extension) {
		this.base = base;
		this.extension = extension;
	}
	
	public ConstraintSet(IConstraint base, IConstraint extension, boolean isIncompleteFix) {
		this.base = base;
		this.extension = extension;
		this.isIncompleteFix = isIncompleteFix;
	}
	
	public IConstraint getBase() {
		return base;
	}
	public IConstraint getExtension() {
		return extension;
	}
	public boolean isImcompleteFix()
	{
		return isIncompleteFix;
	}
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((base == null) ? 0 : base.hashCode());
		result = prime * result
				+ ((extension == null) ? 0 : extension.hashCode());
		result = prime * result + (isIncompleteFix ? 1231 : 1237);
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
		ConstraintSet other = (ConstraintSet) obj;
		if (base == null) {
			if (other.base != null)
				return false;
		} else if (!base.equals(other.base))
			return false;
		if (extension == null) {
			if (other.extension != null)
				return false;
		} else if (!extension.equals(other.extension))
			return false;
		if (isIncompleteFix != other.isIncompleteFix)
			return false;
		return true;
	}

	
}
