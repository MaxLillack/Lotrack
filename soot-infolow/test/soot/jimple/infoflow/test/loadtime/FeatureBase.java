package soot.jimple.infoflow.test.loadtime;

public class FeatureBase {
	
	public static String foo = "Foo";
	public static String bar = "Bar";
	public static String irrelevant = "Foobar";

	@Feature(featureName = "Feature A", featureIndex = 0)
	public static boolean featureA()
	{
		return true;
	}

	@Feature(featureName = "Feature B", featureIndex = 1)
	public static boolean featureB()
	{
		return false;
	}
	
	
}
