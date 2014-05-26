package soot.jimple.infoflow.test;

import soot.jimple.infoflow.test.android.ConnectionManager;

/**
 * Target class for the SourceSinkTests
 * 
 * @author Steven Arzt
 */
public class SourceSinkTestCode {
	
	private class Base {
		
		private String x = "foo";
		
		public String toString() {
			return x;
		}
		
	}
	
	private class A extends Base {
		private String data;
		
		public A(String data) {
			this.data = data;
		}
	}
	
	private class B extends Base {
		
	}
	
	private A getSecret() {
		return new A("Secret");
	}
	
	private B getSecret2() {
		return new B();
	}
	
	public void testDataObject() {
		A a = getSecret();
		ConnectionManager cm = new ConnectionManager();
		cm.publish(a.data);
	}
	
	private void doSomething(Object o) {
		ConnectionManager cm = new ConnectionManager();
		cm.publish("" + o);
	}
	
	public void testAccessPathTypes() {
		A a = getSecret();
		doSomething(a);
		B b = getSecret2();
		doSomething(b);
	}
	
}
