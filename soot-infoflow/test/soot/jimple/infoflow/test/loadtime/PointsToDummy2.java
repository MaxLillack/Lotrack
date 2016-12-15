package soot.jimple.infoflow.test.loadtime;

public class PointsToDummy2 implements IPointsToDummy {
	@Override
	public void foo() {
		int b = 1;
		int a = 0; // <0:0>
	}
}
