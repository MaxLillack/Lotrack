
package soot.jimple.infoflow.test.loadtime;

import java.util.Properties;

import javax.resource.spi.IllegalStateException;

public class LoadTimeTestCode {
	
	public void sample01() {
		int i = Integer.parseInt("0"); // <>
		if(FeatureBase.featureA()) { // <>
			i = Integer.parseInt("1"); // <A>
			if(FeatureBase.featureB()) {  // <A>
				i = Integer.parseInt("3");  // <(A ^ B)>
			} else {	
				i = Integer.parseInt("4");  // <(A ^ !B)>
			}
		}
		if(FeatureBase.featureB()) { // <>
			i = Integer.parseInt("2"); // <B>
		}
	}
	
	public void sample02() {
		if(!FeatureBase.featureA()) { // <>
			int i = 1; // <!A>
		}
	}
	
	public void sample03() {
		boolean featureA = FeatureBase.featureA();
		if(featureA) { // <>
			int i = 1; // <A>
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
			int i = 1; // <A>
		}
		
		featureA = sample04_negate(featureA); // <>

		if(featureA) { // <>
			int i = 1; // <!A>
		}
	}
	
	public void sample05() {
		boolean config = FeatureBase.featureA() || FeatureBase.featureB();
		
		if(config) {
			int i = 1; // <!(A = 0) || !(B = 0)>
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
			int i = 1; // <Foo_Alpha>
		}
	}
	
	public void sample08() {
		if(FeatureBase.foo.equals("Foo")) {
			int i = 1; // <Foo_Alpha>
			if(FeatureBase.bar.equals("Bar")) {
				i = 2; // <(Foo_Alpha ^ Bar_Alpha)>
			}
		}
	}
	
	public void sample09() {
		if (FeatureBase.foo.equals("Foo") && FeatureBase.bar.equals("Bar")) {
			int i = 0; // <(Foo_Alpha ^ Bar_Alpha)>
		}
		if (FeatureBase.foo.equals("Foo") || FeatureBase.bar.equals("Bar")) {
			int i = 0; // <Foo_Beta || Bar_Beta>
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
			int i = 0; // <A>
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
			int j = Integer.parseInt("0"); // <A>
		}
		if(myClass.a == 0) {
			int b = Integer.parseInt("0"); // <>
		}
		int a = Integer.parseInt("0"); // <>
	}
	
	public boolean sample14a()
	{
		if(FeatureBase.featureA()) {
			return false; // <A>
		} else {
			return true; // <!A>
		}
	}
	
	public boolean sample14b()
	{
		boolean temp1 = !sample14a();
		if(temp1 && FeatureBase.featureB()) {
			int j = 3; // <(A ^ B)>
			return true; 
		} else {
			int j = 4;  // <(!A || !B)>
			return false;
		}
	}
	
	public void sample15()
	{
		if(FeatureBase.foo == null) {
			int i = 0; // <Foo_Alpha>
			return;
		}
		int a = 0; // <!(Foo_Alpha)>
	}

	public void sample16()
	{
		if(!FeatureBase.foo.equals("A") && !FeatureBase.foo.equals("B")) {
			int i = 0; // <(!Foo_Alpha ^ !Foo_Beta)>
			return;
		}
		int a = 0; // <Foo_Alpha || Foo_Beta>
	}
	
	public void sample17()
	{
		if(!FeatureBase.foo.equals("A") && !FeatureBase.foo.equals("B")) {
			int i = 0; // <(!Foo_Alpha ^ !Foo_Gamma)>
			return;
		} else if (FeatureBase.foo.equals("C")) {
			int i = 1; // <(Foo_Alpha ^ Foo_Beta) || (Foo_Gamma ^ Foo_Beta)>
			return;
		}
		
		int a = 0; // <(Foo_Alpha ^ !Foo_Beta) || (Foo_Gamma ^ !Foo_Beta)>
	}
	
	public void sample18()
	{
		boolean e = false;
		if(FeatureBase.featureA()) {
			e = FeatureBase.featureB(); // <A>
		}
		if(e) {
			int a = 0; // <(A ^ B)>
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
			int a = 0; // <A>
		}
		
		if(Sub19.subVar19) {
			int a = 0; // <A>
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
		public boolean otherField;
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
			int a = 0; // <A>
		}
		
		if(t.otherField) // <>
		{
			int a = 0; // <>
		}
	}
	
	public void sample22()
	{
		boolean a = false;
		if(FeatureBase.foo != null) // <>
		{
			a = false; // <!(Foo_Alpha)>
		} else {
			a = true; // <Foo_Alpha>
		}
		
		if(FeatureBase.featureA() || a)
		{
			int b = 0; // <!(A = 0) || Foo_Alpha>
		}	
	}
	
	public void sample23()
	{
		if(FeatureBase.foo != null) // <>
		{
			if(FeatureBase.foo.equals("test")) { // <!(Foo_Alpha)>
				int i = 0; // <(!Foo_Alpha ^ Foo_Beta)>
			} else {
				return;
			}
		}
		
		int a = 0; // <Foo_Alpha || Foo_Beta>
	}
	
	public void sample24()
	{
		String foo = FeatureBase.foo;
		if(foo != null)
		{
			int a = 0; // <!(Foo_Alpha)>
		}
		
		if(foo != null)
		{
			int b = 0; // <!(Foo_Beta)>
		}
		
		int c = 0; // <>
	}
	
	public void sample25()
	{
		IPointsToDummy dummy = null;
		if(FeatureBase.featureA())
		{
			dummy = new PointsToDummy1(); // <A>
		} else {
			dummy = new PointsToDummy2(); // <!A>
		}
		dummy.foo();
	}
	
	public void sample26()
	{
		if(FeatureBase.featureA())
		{
			sample26b(); // <A>
		}
		
		sample26b(); // <>
	}
	
	public void sample26b()
	{
		int a = 0; // <>
	}
	
	
	
	class Test27 {
		public Test27a myField;
		
	}
	
	class Test27a {
		public boolean otherField;
		public boolean otherField2;
	}
	
	public void sample27()
	{
		Test27 test27 = new Test27();
		Test27a test27a = new Test27a();
		test27a.otherField = FeatureBase.featureA();
		test27.myField = test27a;
		
		if(test27 != null)
		{
			int a = 0; // <>
		}
		
		if(test27.myField != null)
		{
			int b = 1; // <>
		}
		
		if(test27.myField.otherField)
		{
			int c = 2; // <A>
		}
		
		if(test27.myField.otherField2)
		{
			int d = 3; // <>
		}
	}
	
	public int sample28a(int i)
	{
		sample28(); // <A>
		return i + 1;
	}
	
	public void sample28()
	{
		boolean b = false;
		if(FeatureBase.featureA()) {
			b = true; // <A>
		}
		int i = 0;
		if(b) {
			while(sample28a(i) < 1000) {
				int a = 0; // <A>
			}
		}
	}
	
	// Tainted parameters are essentially passed through
	public boolean sample29a(boolean a)
	{
		int b = a ? 1 : 0;
		int c = b + 2;
		return a;
	}
	
	// Learn about summary functions
	public void sample29()
	{
		boolean taint = FeatureBase.featureA();
		
		// first call
		boolean b1 = sample29a(taint);
		if(b1)
		{
			int a = 1;
		}
		// second call
//		boolean b2 = sample29a(taint);
//		if(b2)
//		{
//			int a = 2;
//		}
	}
	
	public void sample30()
	{
		boolean a = FeatureBase.featureA();
		final boolean b = false;
		if(b == a) {
			int c = 1; // <!A>
		}
	}
	
	public void sample31()
	{
		boolean a = FeatureBase.featureA();
		
		boolean b;
		if(FeatureBase.featureB()) {
			b = true;
		} else {
			b = false;
		}
		
		boolean c = FeatureBase.featureB();
		if(a == b) {
			int d = 1; // <(!(B = 0 ^ !A = 0) ^ !(!B = 0 ^ !A = 1))> 
		}
	}
	
	public void sample32()
	{
		boolean a = FeatureBase.featureA();
		if(a)
		{
			// implicit taint
			int x = 10;
			int i = 0;
			while(i < x)
			{
				System.out.println(i);
				i++;
				while(i < x+5) {
					int b = 0;
					break;
				}
			}
		}
	}	
	
	class Test33 {
		private boolean mode;

		public boolean getMode() {
			return mode;
		}

		public void setMode(boolean mode) {
			this.mode = mode;
		}
	}
	
	public void sample33()
	{
		Test33 test33 = new Test33();
		if(FeatureBase.featureA()) {
			test33.setMode(true);
		}
		if(test33.getMode())
		{
			int d = 1; // <A>
		}
	}
	
	public void sample33a()
	{
		Test33 test33 = new Test33();
		if(FeatureBase.featureA()) {
			test33.setMode(false);
		}
		if(test33.getMode())
		{
			int d = 1; // <!A>
		}
	}
	
	// Implicit taint for string
	public void sample34()
	{
		String foo = null;
		if(FeatureBase.featureA()) {
			foo = "Bar";
		}
		System.out.println(foo);
	}	
	
	// Test with try-catch
	public void sample35()
	{
		String foo = null;
		if(FeatureBase.featureA()) {
			int a = Integer.parseInt("0"); // <A>
			try {
				System.out.println(foo); // <A>
			} catch(RuntimeException ex)
			{
				return;
			}
			int b = Integer.parseInt("1"); // <A>
		}
		System.out.println(foo); // <>
	}	
	
	// Condition evaluation places
	public void sample36()
	{
		int v1 = FeatureBase.featureA() ? 1 : 0;
		int v2 = FeatureBase.featureB() ? 1 : 0;
		boolean k = v1 == v2;
		if(k)
		{
			int y = Integer.parseInt("0"); // <(!B = 0 ^ !A = 0) || (B = 0 ^ A = 0)>
		}
		
		
		boolean a = FeatureBase.featureA();
		boolean b = FeatureBase.featureB();
		boolean x = (a == b);
		if(x)
		{
			int y = Integer.parseInt("0"); // <A = B>
		}	
	}
	
	// check some implicit flows
	public void implicitTest01()
	{
		implicitTest01sub(FeatureBase.featureA()); // <>
		implicitTest01sub(true);	
		implicitTest01sub(true);	
	}
	
	public void implicitTest01sub(boolean a)
	{
		if(a)
		{
			int d = Integer.parseInt("3"); // <>
		}
	}
	
	public void implicitTest02()
	{
		implicitTest02sub(FeatureBase.featureA()); // <>
		implicitTest02sub(true);	
		implicitTest02sub(true);	
	}
	
	public void implicitTest02sub(boolean a)
	{
		if(a)
		{
			boolean b = a;
			int d = Integer.parseInt("3"); // <>
			if(b)
			{
				int e = Integer.parseInt("4"); // <>
			}
		}
	}
	
	public void sample39()
	{
		boolean a = FeatureBase.featureA();
		if(a)
		{
			try {
				
			} catch(Exception e)
			{
				
			}
		}
		int b = Integer.parseInt("1"); // <>
	}

	public void sample40()
	{
		boolean a = FeatureBase.featureA();
		if(a)
		{
			try {
				throw new Exception();
			} catch(Exception e)
			{
				
			}
		}
		int b = Integer.parseInt("1"); // <>
		if(b > 2)
		{
			int c = Integer.parseInt("2"); // <>
		}
	}
	
	public void sample41()
	{
		boolean a = FeatureBase.featureA();
		if(a)
		{
			int b = Integer.parseInt("1"); // <A>
			if(b > 2)
			{
				int c = Integer.parseInt("1"); // <A ^ A_alpha> 
				throw new IllegalArgumentException();
			}
		}
		int d = Integer.parseInt("2"); // <>
		
	}
	
	public void sample42()
	{
		boolean a = FeatureBase.featureA();
		if(a)
		{
			try {
				int b = Integer.parseInt("0"); // <A>		
			} catch(Exception e)
			{
				throw new RuntimeException();
			}
		}
		int d = Integer.parseInt("2"); // <>
	}
	
	public void sample43() throws IllegalStateException
	{
		boolean a = FeatureBase.featureA();
		if(a)
		{
			int b = (int) Math.random(); // <A>
			if(b > 0)
			{
				throw new IllegalStateException(); // <(!A = 0 ^ A_Alpha)>
			}
		}
		int d = Integer.parseInt("2"); // <A = 0 || !(A_Alpha)>
	}
	
	public void sample44()
	{
		if(FeatureBase.foo.equals("") && FeatureBase.bar.equals(""))
		{
			int d = Integer.parseInt("1"); // <(Foo_Alpha ^ Bar_Alpha)>
		}
	}

	
	public void sample45()
	{
		
		Properties properties = new Properties();
		String a = FeatureBase.foo;
		String derived = properties.getProperty(a);
		if(derived == null)
		{
			int d = Integer.parseInt("1"); // <!(Foo_Alpha)>
		}
	}
	
	public void sample46()
	{
		try {
			String a = FeatureBase.foo;
			if(a != null)
			{
				int d = Integer.parseInt("1"); // <!(Foo_Alpha)>
			}
			int b = Integer.parseInt("2");
			if(b > 0)
			{
				int d = Integer.parseInt("3"); // <>
			}
		} catch(Exception e)
		{
			System.err.println("");
		}
		System.out.println("b");
	}
	

	public void sample47()
	{
		int d = FeatureBase.featureD();
		int a = 0;
		if(d == 2)
		{
			a = 1;
		}
		if(d == 3)
		{
			a = 2;
		}
		if(a >= 0)
		{
			int e = Integer.parseInt("1"); // <!(IncompleteSymb_Alpha) || D = 2 || D = 3>
		}
		Integer.parseInt("2"); // <>
	}
	
	public void sample48()
	{
		int d = FeatureBase.featureD();
		int a = 0;
		if(d == 2)
		{
			a = 1;
		}
		if(d == 3)
		{
			a = 2;
		}
		if(FeatureBase.featureA() && a >= 0)
		{
			int e = Integer.parseInt("1"); // <(!A = 0 ^ !IncompleteSymb_Alpha) || (!A = 0 ^ D = 2) || (!A = 0 ^ D = 3))>
		}
		Integer.parseInt("2"); // <>
	}

	public void sample49()
	{
		int d = FeatureBase.featureD();
		int a = 0;
		if(d > Integer.parseInt("0"))
		{
			a = 1;
		}
		if(a > Integer.parseInt("0"))
		{
			Integer.parseInt("2"); // <unclear?>
		}
		if(a > 0)
		{
			Integer.parseInt("2"); // <!(IncompleteSymb_Alpha) || !(D_Alpha)>
		}
	}
	
	public void sample50()
	{
		String a = "";
		if(FeatureBase.foo.equals("a") && FeatureBase.bar.equals("a"))
		{
			a = "a";
		}
		if(a.equals(""))
		{
			Integer.parseInt("1"); // <...>
			return;
		}
		if(a.equals(""))
		{
			Integer.parseInt("2"); // <...>
			return;
		}
		Integer.parseInt("3"); // <...>
	}
	
	public void sample51()
	{
		int d = FeatureBase.featureD();
		if(d > 8)
		{
			boolean b = FeatureBase.foo.startsWith("http:");
			if(b)
			{
				Integer.parseInt("1"); // <...>
			}
		}
	}
	
	public void sample52()
	{
		int d = FeatureBase.featureD();
		if(d > 8)
		{
			boolean b = FeatureBase.foo.startsWith("http:") || FeatureBase.bar.startsWith("http:");
			if(b)
			{
				Integer.parseInt("1"); // <...>
			}
		}
	}
	
	public void sample53()
	{
		int d = FeatureBase.featureD();
		if(d > 8)
		{
			int a = FeatureBase.foo.length();
			int b = FeatureBase.bar.length();
			int c = a + b;
			if(c > 0)
			{
				Integer.parseInt("1"); // <...>
			}
		}
	}
	
	public void paperExample()
	{
		boolean z = isConfig();
		if(z)
		{
			System.out.println("");
		}
		z = false;
		boolean e = false;
		boolean c = FeatureBase.featureC();
		if(c)
		{
			e = FeatureBase.featureE();
			return;
		}
		c = false;
		int d = FeatureBase.featureD();
		if(d > Math.random())
		{
			if(d < Math.random())
			{
				return;
			}
		}
		System.out.println("");
	}
	
	private boolean isConfig()
	{
		return FeatureBase.featureA() && !FeatureBase.featureB();
	}
}

