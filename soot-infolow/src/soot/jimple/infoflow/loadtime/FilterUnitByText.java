package soot.jimple.infoflow.loadtime;

import soot.Unit;

import com.google.common.base.Predicate;

public class FilterUnitByText implements Predicate<Unit> {

	String unitText;
	
	public FilterUnitByText(String unitText)
	{
		this.unitText = unitText;
	}
	
	@Override
	public boolean apply(Unit unit) {
		return unit.toString().equals(unitText);
	}

}
