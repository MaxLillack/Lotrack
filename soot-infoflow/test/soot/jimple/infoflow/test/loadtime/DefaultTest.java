package soot.jimple.infoflow.test.loadtime;

public class DefaultTest {
	public void default1()
	{
		boolean f1 = false;
		f1 = FeatureBase.featureA();
		if(f1)
		{
			int a = 0; // <0:1>
		}
	}
	
	public void default2()
	{
		boolean f1 = false;
		if(FeatureBase.featureA())
		{
			f1 = true;
		}
		if(f1)
		{
			int a = 0; // <0:1>
		}
	}	
	
	public void default3()
	{
		boolean f1 = true;
		if(!FeatureBase.featureA())
		{
			f1 = false;
		}
		if(f1)
		{
			int a = 0; // <0:0>
		}
	}
	
	public void default4()
	{
		boolean f1 = true;
		
		default4b(f1);
		
		f1 = FeatureBase.featureA();
		default4b(f1);
	}
	
	public void default4b(boolean f)
	{
		if(f)
		{
			int a = 0; // <>
		}
	}
}
