
package soot.jimple.infoflow.test.loadtime;

public class LoadTimeTestCode {
	
	public void sample01() {
		int i = 0; // <>
		if(FeatureBase.featureA()) { // <>
			i = 1; // <0:1>
			if(FeatureBase.featureB()) {  // <0:1>
				i = 3;  // <0:1, 1:1>
			} else {	
				i = 4;  // <0:1, 1:0>
			}
		}
		if(FeatureBase.featureB()) { // <>
			i = 2; // <1:1>
		}
	}
	
	public void sample02() {
		if(!FeatureBase.featureA()) { // <>
			int i = 1; // <0:0>
		}
	}
	
	public void sample03() {
		boolean featureA = FeatureBase.featureA();
		if(featureA) { // <>
			int i = 1; // <0:1>
		}
		if(System.getProperty("path.separator").equals("")) {
			int i = 4; // <>
		}
	}
	
	private boolean sample04_negate(boolean in)
	{
		return !in;
	}
	
	public void sample04() {
		boolean featureA = FeatureBase.featureA();
		
		if(featureA) { // <>
			int i = 1; // <0:1>
		}
		
		featureA = sample04_negate(featureA); // <>

		if(featureA) { // <>
			int i = 1; // <0:0>
		}
	}
	
	public void sample05() {
		boolean config = FeatureBase.featureA() || FeatureBase.featureB();
		
		if(config) { // <>
			int i = 1; // <0:0, 1:1><0:1>
		}
		
		int i = 0; // <>
	}
	
	public void sample06() {
		boolean A = FeatureBase.featureA();
		boolean NotA = !FeatureBase.featureA();
		
		if(A && NotA) {
			int i = 1; // <false>
		}
	}
	
	public void sample07() {
		if(FeatureBase.foo.equals("Foo")) {
			int i = 1; // <2:1>
		}
	}
	
	public void sample08() {
		if(FeatureBase.foo.equals("Foo")) {
			int i = 1; // <2:1>
			if(FeatureBase.bar.equals("Bar")) {
				i = 2; // <2:1, 3:1>
			}
		}
	}
	
	public void sample09() {
		if (FeatureBase.foo.equals("Foo") && FeatureBase.bar.equals("Bar")) {
			int i = 0; // <2:1, 3:1> 
		}
	}
	
	public void sample10() {
		if (FeatureBase.irrelevant.equals("Foo")) {
			int i = 0; // <>
		}
	}
	
	public void sample12() {
		boolean A = FeatureBase.featureA();
		if(A)
		{
			int i = 0; // <0:1>
		}
		int a = 0; // <>
	}
	
	private class MyClass {
		public int a;
		public boolean b;
	}
	
	private MyClass myClass;
	
	public void sample13a()
	{
		this.myClass = new MyClass();
		myClass.b = FeatureBase.featureA();
		sample13b();
	}
	
	public void sample13b()
	{
		if(myClass.b) {
			int j = 0; // <0:1>
		}
		int a = 0; // <>
	}
	
	public boolean sample14a()
	{
		if(FeatureBase.featureA()) {
			return false; // <0:1>
		} else {
			return true; // <0:0>
		}
	}
	
	public boolean sample14b()
	{
		boolean temp1 = !sample14a();
		if(temp1 && FeatureBase.featureB()) {
			return true; // <0:1, 1:1>
		} else {
			return false; // <0:0><0:1, 1:0>
		}
	}
	
	public void sample15()
	{
		if(FeatureBase.foo == null) {
			int i = 0; // <2:1>
			return;
		}
		int a = 0; // <2:0>
	}

	public void sample16()
	{
		if(!FeatureBase.foo.equals("A") && !FeatureBase.foo.equals("B")) {
			int i = 0; // <2:1>
			return;
		}
		int a = 0; // <2:0>
	}
	
	public void sample17()
	{
		if(!FeatureBase.foo.equals("A") && !FeatureBase.foo.equals("B")) {
			int i = 0; // <2:1>
			return;
		} else if (FeatureBase.foo.equals("C")) {
			int i = 1; // <2:1>
			return;
		}
		
		int a = 0; // <2:0>
	}
	
	public void sample18()
	{
		boolean e = false;
		if(FeatureBase.featureA()) {
			e = FeatureBase.featureB(); // <0:1>
		}
		if(e) {
			int a = 0; // <0:1, 1:1>
		}
		int b = 0; // <>
	}
	
	public static final boolean var19 = FeatureBase.featureA() == true;
	
	static class Sub19 {
		public static boolean subVar19 = FeatureBase.featureA() == true;
	}
	
	public void sample19()
	{
		if(var19) {
			int a = 0; // <0:1>
		}
		
		if(Sub19.subVar19) {
			int a = 0; // <0:1>
		}
		
//		if(FeatureBase.featureA1()) {
//			int a = 0; // <0:1>
//		}
//		
//		if(FeatureBase.a2) {
//			int a = 0; // <0:1>
//		}
		
//		if(FeatureBase.foo2 != null) {
//			int a = 0; // <2:1>
//		}
//		
//		if(var19) {
//			int a = 0; // <2:1>
//		}
		
		int b = 0; // <>
	}
	
	public void sample20(String test)
	{
		if(test == null) {
			test = FeatureBase.foo; // <>
			sample20(test);
		}
	}
	
	class Test21 {
		public boolean myField;
	}
	
	public void sample21()
	{
		Test21 test21 = new Test21();
		test21.myField = FeatureBase.featureA();
		sample21b(test21); // <>
	}
	
	public void sample21b(Test21 t)
	{
		if(t.myField) // <>
		{
			int a = 0; // <0:1>
		}
	}
	
	public void sample22()
	{
		boolean a = false;
		if(FeatureBase.foo != null) // <>
		{
			a = false; // <2:1>
		} else {
			a = true; // <2:0>
		}
		
		if(FeatureBase.featureA() || a)
		{
			int b = 0; // <0:0, 2:0><0:1>
		}	
	}
	
	public void sample23()
	{
		if(FeatureBase.foo != null) // <>
		{
			if(FeatureBase.foo.equals("test")) { // <2:1>
				int i = 0; // <2:1>
			} else {
				return;
			}
		}
		
		int a = 0;
	}
	
	public void sample24()
	{
		String foo = FeatureBase.foo;
		if(foo != null)
		{
			int a = 0; // <2:1>
		}
		
		if(foo != null)
		{
			int b = 0; // <2:1>
		}
		
		int c = 0; // <>
	}
}
