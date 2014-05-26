/*******************************************************************************
 * Copyright (c) 2012 Secure Software Engineering Group at EC SPRIDE.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser Public License v2.1
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors: Christian Fritz, Steven Arzt, Siegfried Rasthofer, Eric
 * Bodden, and others.
 ******************************************************************************/

package soot.jimple.infoflow.test;

import soot.jimple.infoflow.test.android.Bundle;
import soot.jimple.infoflow.test.android.ConnectionManager;
import soot.jimple.infoflow.test.android.TelephonyManager;

/**
 * Test code for the type checker
 * 
 * @author Steven Arzt
 */
public class TypeTestCode {
	
	public void typeTest1() {
		String tainted = TelephonyManager.getDeviceId();
		
		Object obj = (Object) tainted;
		String newStr = obj.toString();
		
		ConnectionManager cm = new ConnectionManager();
		cm.publish(newStr);
	}
	
	private class A {
		String data;
		String data2;
		
		public A() {
			
		}
		
		@SuppressWarnings("unused")
		public A(String data) {
			this.data = data;
		}
		
		String bar() {
			return this.data;
		}
		
		void leak() {
			ConnectionManager cm = new ConnectionManager();
			cm.publish("A: " + data);
		}
		
		@Override
		public String toString() {
			return "data: " + data + ", data2: " + data2;
		}
	}
	
	private class B extends A {
		String foo() {
			return this.data;
		}
		
		@Override
		void leak() {
			ConnectionManager cm = new ConnectionManager();
			cm.publish("B: " + data);
		}
	}
	
	private class B2 extends A {
		@Override
		void leak() {
			ConnectionManager cm = new ConnectionManager();
			cm.publish("B2: " + data);
		}
	}
	
	private class C {
		String data;
	}
	
	public void classCastTest1() {
		String tainted = TelephonyManager.getDeviceId();
		B b = new B();
		b.data = tainted;
		
		A a = (A) b;
		String newStr = a.bar();
		
		ConnectionManager cm = new ConnectionManager();
		cm.publish(newStr);
	}

	public void classCastTest2() {
		String tainted = TelephonyManager.getDeviceId();
		B b = new B();
		b.data = tainted;
		
		A a = (A) b;
		B b2 = (B) a;
		String newStr = b2.foo();
		
		ConnectionManager cm = new ConnectionManager();
		cm.publish(newStr);
	}

	public void classCastTest3() {
		String tainted = TelephonyManager.getDeviceId();
		B b = new B();
		b.data = tainted;
		
		A a = (A) b;
		B b2 = (B) a;
		String newStr = b2.bar();
		
		ConnectionManager cm = new ConnectionManager();
		cm.publish(newStr);
	}

	public void instanceofTest1() {
		String tainted = TelephonyManager.getDeviceId();
		
		A a;
		if (tainted.startsWith("x"))
			a = new A();
		else
			a = new B();
		a.data = tainted;

		ConnectionManager cm = new ConnectionManager();
		if (a instanceof A)
			cm.publish(a.bar());
		else if (a instanceof B)
			cm.publish(((B) a).foo());
		else {
			Object o = (Object) a;
			C c = (C) o;
			cm.publish(c.data);
		}
	}
	
	private void callIt(A a) {
		a.leak();
	}
	
	public void callTargetTest1() {
		A b2 = new B2();
		callIt(b2);
		b2.data = TelephonyManager.getDeviceId();
		
		A b = new B();
		b.data = TelephonyManager.getDeviceId();
		callIt(b);
	}
	
	public void arrayObjectCastTest() {
		Object obj = Bundle.get("foo");
		A foo2[] = (A[]) obj;
		ConnectionManager cm = new ConnectionManager();
		cm.publish(foo2[0].data);
	}

	public void arrayObjectCastTest2() {
		Object obj = Bundle.get("foo");
		A foo2[] = (A[]) obj;
		obj = foo2[0];
		A a = (A) obj;
		a.data2 = a.data;
		ConnectionManager cm = new ConnectionManager();
		cm.publish(a.data);
	}
	
	public void callTypeTest() {
		String[] x = new String[1];
		x[0] = TelephonyManager.getDeviceId();
		objArgFunction(x);
		ConnectionManager cm = new ConnectionManager();
		cm.publish(x[0]);
	}
	
	private void objArgFunction(Object[] x) {
		System.out.println(x);
	}

	public void callTypeTest2() {
		String[] x = new String[1];
		objArgFunction2(x);
		ConnectionManager cm = new ConnectionManager();
		cm.publish(x[0]);
	}
	
	private void objArgFunction2(Object[] x) {
		x[0] = TelephonyManager.getDeviceId();
	}

}
