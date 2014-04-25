package models;

public class LoadTimeResult {
	public String projectName;
	public String constraint;
	public int count;
	public LoadTimeResult(String projectName, String constraint, Integer count) {
		this.projectName = projectName;
		this.constraint = constraint;
		this.count = count;
	}
}
