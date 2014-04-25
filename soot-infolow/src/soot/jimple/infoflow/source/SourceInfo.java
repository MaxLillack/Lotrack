package soot.jimple.infoflow.source;

/**
 * Class containing additional information about a source. Users of FlowDroid
 * can derive from this class when implementing their own SourceSinkManager
 * to associate additional information with a source.
 * 
 * @author Steven Arzt
 */
public class SourceInfo {
	
	private boolean taintSubFields = true;
	private Object userData = null;
	
	/**
	 * Creates a new instance of the {@link SourceInfo} class
	 * @param taintSubFields True if all fields reachable through the source
	 * shall also be considered as tainted, false if only the source as such
	 * shall be tainted.
	 */
	public SourceInfo(boolean taintSubFields) {
		this.taintSubFields = taintSubFields;
	}
	
	/**
	 * Creates a new instance of the {@link SourceInfo} class
	 * @param taintSubFields True if all fields reachable through the source
	 * shall also be considered as tainted, false if only the source as such
	 * shall be tainted.
	 * @param userData Additional user data to be propagated with the source
	 */
	public SourceInfo(boolean taintSubFields, Object userData) {
		this.taintSubFields = taintSubFields;
		this.userData = userData;
	}

	@Override
	public int hashCode() {
		return 31 * (taintSubFields ? 1 : 0)
				+ 31 * (this.userData == null ? 0 : this.userData.hashCode());
	}
	
	@Override
	public boolean equals(Object other) {
		if (other == null || !(other instanceof SourceInfo))
			return false;
		SourceInfo otherInfo = (SourceInfo) other;
		if (taintSubFields != otherInfo.taintSubFields)
			return false;
		if (this.userData == null) {
			if (otherInfo.userData != null)
				return false;
		}
		else if (!this.userData.equals(otherInfo.userData))
			return false;
		return true;
	}

	/**
	 * Gets whether all fields reachable through the source shall also be
	 * considered as tainted.
	 * @return True if all fields reachable through the source shall also
	 * be considered as tainted, false if only the source as such shall be
	 * tainted.
	 */
	public boolean getTaintSubFields() {
		return taintSubFields;
	}
	
	/**
	 * Gets the user data to be tracked together with this source
	 * @return The user data to be tracked together with this source
	 */
	public Object getUserData() {
		return this.userData;
	}

}
