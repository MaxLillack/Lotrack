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
	
	@Feature(featureName = "Feature C", featureIndex = 2)
	public static boolean featureC()
	{
		return false;
	}
	
	@Feature(featureName = "Feature D", featureIndex = 3)
	public static int featureD()
	{
		return 0;
	}
	
	
	@Feature(featureName = "Feature E", featureIndex = 4)
	public static boolean featureE()
	{
		return false;
	}
}
