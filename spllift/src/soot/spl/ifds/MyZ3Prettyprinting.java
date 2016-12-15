package soot.spl.ifds;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Z3Exception;
import com.microsoft.z3.enumerations.Z3_lbool;

public class MyZ3Prettyprinting implements IZ3Prettyprinting {
	
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
				if(boolExpr.isEq() || boolExpr.isIff())
				{
					result = prettyprintZ3Eq(boolExpr);
				}
				
				if(boolExpr.isGT())
				{
					result = prettyprintZ3Gt(boolExpr);
				}
				if(boolExpr.isGE())
				{
					result = prettyprintZ3Ge(boolExpr);
				}
				if(boolExpr.isLE())
				{
					result = prettyprintZ3Le(boolExpr);
				}
				if(boolExpr.isLT())
				{
					result = prettyprintZ3Lt(boolExpr);
				}

				if(result == null)
				{
//						throw new RuntimeException("Error prettyprinting " + expression.toString());
				}
			} else if(expression.isConst() || expression.isUMinus() || expression.isNumeral())
			{
				result = expression.toString();
			}
			else {
				throw new RuntimeException("Unable to prettyprint non-boolean expression " + expression.toString() + " type " + expression.getASTKind().name());
			}
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		return result;
	}
	
	private String prettprintZ3Or(BoolExpr orExpression) throws Z3Exception
	{
		List<String> parts = new LinkedList<String>(); 
		for(Expr arg : orExpression.getArgs())
		{
			parts.add(prettyprintZ3(arg));
		}
		return StringUtils.join(parts, " || ");
	}
	
	private String prettyprintZ3And(BoolExpr andExpression) throws Z3Exception
	{
		List<String> parts = new LinkedList<String>();
		
		for(Expr arg : andExpression.getArgs())
		{
			if(arg.isConst())
			{
				String name = arg.getFuncDecl().getName().toString();
				parts.add(arg.toString());
			} else if(arg.isNot()) {
				String name = arg.getArgs()[0].getFuncDecl().getName().toString();
				String temp = "!" + prettyprintZ3(arg.getArgs()[0]);
				
//				// Ad-hoc replacement of string-oriented constraints to Boolean constraints
//				Pattern p = Pattern.compile("!(\\|\\d+\\|) = 0");
//				Matcher m = p.matcher(temp);
//				
//				if(m.matches())
//				{
//					String option = m.group(1);
//					temp = option;
//				} else {
//					p = Pattern.compile("!(\\|\\d+\\|) = 1");
//					m = p.matcher(temp);
//					
//					if(m.matches())
//					{
//						String option = m.group(1);
//						temp = "!" + option;
//					}
//				}
				
				parts.add(temp);
			}
			else {
				parts.add(prettyprintZ3(arg));
			}
		}
		
		String result = "(" + StringUtils.join(parts, " ^ ") + ")";
		return result;		
	}
	
	private String prettyprintZ3Not(BoolExpr notExpression) throws Z3Exception
	{
		Expr[] args = notExpression.getArgs();
		if(args.length == 1 && args[0].isConst()) {
//			String name = args[0].getFuncDecl().getName().toString();
//			String result = "<" + name + ":0>";
//			return result;
			return String.format("!(%s)", prettyprintZ3((BoolExpr) args[0]));
		} else if(args[0].isBool()) { 
			return String.format("!(%s)", prettyprintZ3((BoolExpr) args[0]));
		} else {
			throw new RuntimeException("prettyprintZ3Not(): Unexpected expression.");
		}
	}
	
	private String prettyprintZ3Const(BoolExpr constExpression) throws Z3Exception
	{
//		String name = constExpression.getFuncDecl().getName().toString();
//		String result = "<" + name + ":1>";
//		return result;
		return constExpression.toString();
	}
	
	private String prettyprintZ3Eq(BoolExpr eqExpression) throws Z3Exception
	{
		Expr[] args = eqExpression.getArgs();
		boolean arg0isConst = args[0].isConst();
		boolean arg1isConst = args[1].isConst();
		boolean arg1isInt = args[1].isInt() || args[1].toString().equals("(- 1)");
		String result = null;
		
		if(arg1isInt) {
			IntExpr arg1AsInt = (IntExpr) args[1];
			// assume arg0isConst
			result = String.format("%s = %s", args[0].toString(), args[1].toString());			
		} else {
			result = String.format("%s = %s", prettyprintZ3(args[0]), prettyprintZ3(args[1]));		
		}
		
		return result;
	}
	
	private String prettyprintZ3Gt(BoolExpr gtExpression) throws Z3Exception
	{
		Expr[] args = gtExpression.getArgs();
		boolean arg0isConst = args[0].isConst();
		boolean arg1isConst = args[1].isConst();
		boolean arg1isInt = args[1].isInt();
		String result = null;
		
		if(arg1isInt) {
			IntExpr arg1AsInt = (IntExpr) args[1];
		
			// assume arg0isConst
			result = String.format("%s > %s", args[0].toString(), args[1].toString());
		} else {
			throw new RuntimeException("Expected boolexpr");
		}
		
		return result;
	}
	
	private String prettyprintZ3Ge(BoolExpr geExpression) throws Z3Exception
	{
		Expr[] args = geExpression.getArgs();
		boolean arg0isConst = args[0].isConst();
		boolean arg1isConst = args[1].isConst();
		boolean arg1isInt = args[1].isInt();
		String result = null;
		
		if(arg1isInt) {
			IntExpr arg1AsInt = (IntExpr) args[1];
		
			// assume arg0isConst
			result = String.format("%s >= %s", args[0].toString(), args[1].toString());
		} else {
			result = String.format("%s = %s", prettyprintZ3(args[0]), prettyprintZ3(args[1]));
		}
		
		return result;
	}
	
	private String prettyprintZ3Le(BoolExpr leExpression) throws Z3Exception
	{
		Expr[] args = leExpression.getArgs();
		String result = String.format("%s <= %s", args[0].toString(), args[1].toString());
		return result;
	}
	private String prettyprintZ3Lt(BoolExpr ltExpression) throws Z3Exception
	{
		Expr[] args = ltExpression.getArgs();
		String result = String.format("%s < %s", args[0].toString(), args[1].toString());
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
