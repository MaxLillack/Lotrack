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

import java.util.ArrayList;

import soot.jimple.infoflow.test.android.ConnectionManager;
import soot.jimple.infoflow.test.android.TelephonyManager;
import soot.jimple.infoflow.test.utilclasses.ClassWithField;
import soot.jimple.infoflow.test.utilclasses.ClassWithStatic;

public class MaxTest {
	
	public class Y{
		public String f;
	}
	
	public static String taint = TelephonyManager.getDeviceId();
	
	private Y a;
	public void test1a(){
		String taint = TelephonyManager.getDeviceId();
		a = new Y();
		a.f = taint;
		
		test1b();
	}
	
	public void test1b(){
		ConnectionManager cm = new ConnectionManager();
		cm.publish(a.f);
	}
	
	public void test1c(){
		ConnectionManager cm = new ConnectionManager();
		cm.publish(taint);
	}
	
	public void test2a()
	{
		Y y = new Y();
		y.f = TelephonyManager.getDeviceId();
		test2b(y);
	}
	
	public void test2b(Y y)
	{
		ConnectionManager cm = new ConnectionManager();
		cm.publish(y.f);
	}
	
	public void test3()
	{
		String taint = TelephonyManager.getDeviceId();
		boolean test = test3a(taint);
		if(test)
		{
			ConnectionManager cm = new ConnectionManager();
			cm.publish(taint);
		}
	}
	
	public boolean test3a(String taint)
	{
		return taint != null;
	}

}
