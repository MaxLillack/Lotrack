package soot.spl.ifds;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.z3.ApplyResult;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Goal;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Tactic;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.Context;
import com.microsoft.z3.enumerations.Z3_lbool;

public class BDDZ3Prettyprinting implements IZ3Prettyprinting {
	
	@Override
	public String prettyprintZ3(Expr expression)
	{
		String result = null;
		try {
			if(expression.isBool()) {
				BoolExpr boolExpr = (BoolExpr) expression;
				
				// dispatch
				if(boolExpr.isOr())
				{
					result = prettprintZ3Or(boolExpr);
				}
				if(boolExpr.isAnd())
				{
					result = prettyprintZ3And(boolExpr);
				}
				if(boolExpr.isNot())
				{
					result = prettyprintZ3Not(boolExpr);
				}
				if(boolExpr.isConst())
				{
					if(boolExpr.isTrue()) {
						result = "true";
					} else if(boolExpr.isFalse()) {
						result = "false";
					} else {
						result = prettyprintZ3Const(boolExpr);
					}
				}
				if(boolExpr.isEq())
				{
					result = prettyprintZ3Eq(boolExpr);
				}

				if(result == null)
				{
					throw new RuntimeException("Error prettyprinting " + expression.toString());
				}
			} else {
				throw new RuntimeException("Unable to prettyprint non-boolean expression " + expression.toString());
			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return result;
	}
	
	private String prettprintZ3Or(BoolExpr orExpression) throws Z3Exception
	{
		StringBuilder result = new StringBuilder();
		for(Expr arg : orExpression.getArgs())
		{
			result.append(prettyprintZ3(arg));
		}
		return result.toString();
	}
	
	private String prettyprintZ3And(BoolExpr andExpression) throws Z3Exception
	{
		List<String> parts = new LinkedList<String>();
		
		for(Expr arg : andExpression.getArgs())
		{
			if(arg.isConst())
			{
				String name = arg.getFuncDecl().getName().toString();
				parts.add(name + ":1");
			} else if(arg.isNot()) {
				String name = arg.getArgs()[0].getFuncDecl().getName().toString();
				parts.add(name + ":0");
			}
			else {
				parts.add(prettyprintZ3(arg));
			}
		}
		
		String result = "<" + StringUtils.join(parts, ", ") + ">";
		return result;		
	}
	
	private String prettyprintZ3Not(BoolExpr notExpression) throws Z3Exception
	{
		Expr[] args = notExpression.getArgs();
		if(args.length == 1 && args[0].isConst()) {
			String name = args[0].getFuncDecl().getName().toString();
			String result = "<" + name + ":0>";
			return result;
		} else if(args[0].isBool()) { 
			return String.format("!(%s)", prettyprintZ3((BoolExpr) args[0]));
		} else {
			throw new RuntimeException("prettyprintZ3Not(): Unexpected expression.");
		}
	}
	
	private String prettyprintZ3Const(BoolExpr constExpression) throws Z3Exception
	{
		String name = constExpression.getFuncDecl().getName().toString();
		String result = "<" + name + ":1>";
		return result;
	}
	
	private String prettyprintZ3Eq(BoolExpr eqExpression) throws Z3Exception
	{
		Expr[] args = eqExpression.getArgs();
		boolean arg0isConst = args[0].isConst();
		boolean arg1isConst = args[1].isConst();
		boolean arg1isBool = args[1].isBool();
		String result = null;
		
		if(arg1isBool) {
			BoolExpr arg1AsBool = (BoolExpr) args[1];
			Z3_lbool boolvalue = arg1AsBool.getBoolValue();
			
			// assume arg0isConst
			String name = args[0].getFuncDecl().getName().toString();
			
			if(boolvalue == Z3_lbool.Z3_L_FALSE)
			{
				
				result = "<" + name + ":0>";
			}
			
			if(boolvalue == Z3_lbool.Z3_L_TRUE)
			{
				result = "<" + name + ":1>";
			}
		} else {
			throw new RuntimeException("Expected boolexpr");
		}
		
		return result;
	}
//	
//	public String prettyprintZ3(BoolExpr expression, String reference)
//	{
//
//		String result = null;
//		try {
//			Tactic tactic = ctx.mkTactic("ctx-solver-simplify");
////			Tactic tactic = ctx.mkTactic("propagate-values");
//			tactic = ctx.andThen(tactic, ctx.orElse(ctx.mkTactic("split-clause"), ctx.skip()), ctx.mkTactic("propagate-values"));
//
//			Goal g = ctx.mkGoal(true, false, false);
//
//			g.add(expression);
//			
//			ApplyResult applyResult = tactic.apply(g);
//			
//			
//			List<BoolExpr> parts = new LinkedList<BoolExpr>();
//			for(Goal subGoal : applyResult.getSubgoals()) {
//				BoolExpr boolExpr = subGoal.getFormulas()[0];
//				
//				if(subGoal.getFormulas().length > 1) {
//					boolExpr = ctx.mkAnd(subGoal.getFormulas());
//				}
//				
//				parts.add(boolExpr);
//			}
//			
//			BoolExpr combined = null;
//			if(parts.size() > 1) {
//				combined = ctx.mkOr(parts.toArray(new BoolExpr[parts.size()]));
//			} else {
//				combined = parts.get(0);
//			}
//			
//	
//			result = prettyprintZ3(combined);
//
//			if(!reference.equals(result)) {
//				int a = 0;
//			}
//			
//		} catch (Z3Exception e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//
//		return result;
//	}
	

}
