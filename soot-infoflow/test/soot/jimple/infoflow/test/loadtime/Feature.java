package soot.jimple.infoflow.test.loadtime;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
public @interface Feature {
	String featureName();
	int featureIndex();
}
