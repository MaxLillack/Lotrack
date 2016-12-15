package soot.spl.ifds;

import com.microsoft.z3.Expr;

public interface IZ3Prettyprinting {

	public abstract String prettyprintZ3(Expr expression);

}