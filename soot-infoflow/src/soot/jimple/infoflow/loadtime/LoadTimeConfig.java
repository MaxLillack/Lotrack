package soot.jimple.infoflow.loadtime;

public class LoadTimeConfig {
	
	private String name;
	private int feature;
	
	public LoadTimeConfig(String name, int feature)
	{
		this.name = name;
		this.feature = feature;
	}
	
	public String getName()
	{
		return name;
	}

	public int getFeature()
	{
		return feature;
	}
}
