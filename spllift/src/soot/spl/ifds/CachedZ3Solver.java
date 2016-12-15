package soot.spl.ifds;

import java.text.NumberFormat;
import java.time.LocalTime;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.print.attribute.standard.DateTimeAtCompleted;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.ConcurrentUtils;

import soot.spl.ifds.MongoLoader.CacheResult;
import soot.spl.ifds.SolverOperation.Operator;

import com.microsoft.z3.ApplyResult;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Goal;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;
import com.microsoft.z3.Symbol;
import com.microsoft.z3.Tactic;
import com.microsoft.z3.Z3Exception;
public class CachedZ3Solver {
	
	private static Map<SolverOperation, String> cache = new ConcurrentHashMap<SolverOperation, String>();
	private static int maxFeatureIndex = 0;
	
//	private static long counter = 0;
//	private static long hit = 0;
//	private static long miss = 0;
	
	
	// @ TODO remove duplicate cache
	private static Map<String, Future<String>> prettyprinted = new ConcurrentHashMap<>();
	private static IZ3Prettyprinting prettyprinting = new MyZ3Prettyprinting();
	
	// @ TODO -> find better solution than outside static initialization
	public static Map<Integer, String> featureNames = null;
	
	private static long counter = 0;
	private static long miss = 0;
	private static long hit = 0;
	private static long limitHit = 0;
	
	private static AsyncPrettyprinter asyncPrettyprinter = new AsyncPrettyprinter();
	
	private static Pattern p = Pattern.compile("\\w+_\\w+");
	
//	private static MongoLoader mongoLoader = new MongoLoader();
	private static boolean disableNewCacheEntries = true;
	private static boolean useStructuralCache = false;
	private static MongoLoader mongoLoader = null;
	
	public static void clearCache()
	{
		cache.clear();
		prettyprinted.clear();
		counter = 0;
		miss = 0;
		hit = 0;
		limitHit = 0;
	}
	
	private static void structuralcache_getUsedSymbols(String operand, Map<String, String> usedSymbols)
	{
		Matcher m = p.matcher(operand);
		while(m.find())
		{
			if(!usedSymbols.containsKey(m.group(0)))
			{
				// Use of sync block to handle to size operation
				usedSymbols.put(m.group(0), "Symbol_" + usedSymbols.size());
			}
		}
	}
	
	private static SolverOperation structuralcache_replaceNames(SolverOperation operation, Map<String, String> usedSymbols)
	{
		String op1replaced = operation.getOperand1();
		String op2replaced = operation.getOperand2();
		for(Entry<String,String> entry : usedSymbols.entrySet())
		{
			op1replaced = op1replaced.replaceAll(entry.getKey() + "(?=\\D|$)", entry.getValue());
			if(op2replaced != null)
			{
				op2replaced = op2replaced.replaceAll(entry.getKey() + "(?=\\D|$)", entry.getValue());
			}
		}
		SolverOperation operationReplaced;
		if(op2replaced != null) {
			operationReplaced = new SolverOperation(op1replaced, op2replaced, operation.getOperator()); 
		} else {
			operationReplaced = new SolverOperation(op1replaced, operation.getOperator()); 
		}
		return operationReplaced;
	}
	
	public static String solve(SolverOperation operation)
	{		
		Map<String, String> usedSymbols = new ConcurrentHashMap<>();
		
		SolverOperation operationReplaced = null;
		
		boolean enableQuerySizeLimit = true;
		int querySizeLimit = 5000;
		
		if(useStructuralCache)
		{
			structuralcache_getUsedSymbols(operation.getOperand1(), usedSymbols);
			if(operation.getOperand2() != null)
			{
				structuralcache_getUsedSymbols(operation.getOperand2(), usedSymbols);
			}
			
			// replace symbols with simple enumerated names
			operationReplaced = structuralcache_replaceNames(operation, usedSymbols);		
		} else {
			operationReplaced = operation;
		}
		
		String result = null;
		if(!cache.containsKey(operationReplaced))
		{
			// Try MongoLoader cache
			if(mongoLoader != null)
			{
				CacheResult cacheResult = mongoLoader.get(operationReplaced.toString());
				
				if(cacheResult != null)
				{
					result = cacheResult.getResult();
					cache.put(operationReplaced, result);
					String pretty = cacheResult.getPretty();
					prettyprinted.put(result, ConcurrentUtils.constantFuture(pretty));
					hit++;
				}
			}
			
			if(result == null 
					&& enableQuerySizeLimit 
					&& operation.getOperator() == Operator.OR
					&& (operation.getOperand1().length() > querySizeLimit || operation.getOperand2().length() > querySizeLimit))
			{
				 result = operation.getOperand1();
				 cache.put(operationReplaced, result);
				 limitHit++;
			}
			
			if(result == null)
			{
				miss++;
	//			System.out.println(operationReplaced);
				solveUsingZ3(operationReplaced);
			}
		} else {
			hit++;
		}
		
		result = cache.get(operationReplaced);
		
		if(enableQuerySizeLimit && operation.getOperator() == Operator.AND && result.length() > querySizeLimit)
		{
			if(operation.getOperand1().length() > querySizeLimit) {
				result = operation.getOperand1();
				cache.put(operationReplaced, result);
				limitHit++;
			}
		}
		
		
		// reverse
		String resultReplaced = result;
		Future<String> pretty = prettyprinted.get(result);
		
		if(useStructuralCache)
		{
			for(Entry<String,String> entry : usedSymbols.entrySet())
			{
				resultReplaced = resultReplaced.replaceAll(entry.getValue() + "(?=\\D|$)", entry.getKey());
				try {
					String prettyReplaced = pretty.get().replaceAll(entry.getValue() + "(?=\\D|$)", entry.getKey());
					pretty = ConcurrentUtils.constantFuture(prettyReplaced);
				} catch(InterruptedException | ExecutionException e)
				{
					throw new RuntimeException(e);
				}
			}
	
			result = resultReplaced;
		}
		
		// Update prettyprinted
		prettyprinted.put(result, pretty);
		
		// counter also used for explicit GC/Finalizer below
		counter++;
		if(counter % 50000 == 0) {			
			System.out.format("%s Counter %s (Hit %d, Miss: %d, LimitHit: %d) \n",
					LocalTime.now().toString(),
					NumberFormat.getNumberInstance(Locale.US).format(counter), 
					hit, 
					miss,
					limitHit);
		}
		
//		System.out.println("solve("+ operation + ") = " + result);
		
		if(result.startsWith("(null"))
		{
			throw new IllegalStateException("result " + result + " is not valid");
		}
		
		return result;
	}
	
	private static Context ctx;
	private static Solver solver;
	
	private static void initContext()
	{
		if(ctx == null)
		{
			ctx = new Context();				
			solver = ctx.mkSolver();
		} else {
			solver.reset();
		}
		
	}
	private static Pattern pattern = Pattern.compile("(\\w+)_(\\w+)");
	private static synchronized void solveUsingZ3(SolverOperation operation)
	{
		// create context and solver
		
		// add consts
		
		// select operator
		
		// add formulas
		
		// save in cache
		
//		Context ctx = null;
//		Solver solver = null;
		
		try {
			
			initContext();
			
			FuncDecl[] decls = {};
			Symbol[] names = {};
			
			// TODO Is this needed?
//			ctx.mkBoolConst("0");
			
			
			
			StringBuilder input = new StringBuilder();
			for(int i = 0; i <= maxFeatureIndex ; i++) {
				input.append("(declare-const |" + i + "| Int)\n");
			}
			
			String operationText = operation.toString();
			
			// For imprecise features FOO_Alpha, we add (declare-const Foo_Alpha Bool) 
			
			Matcher matcher = pattern.matcher(operationText);
			
			Set<String> added = new HashSet<>();
			while(matcher.find()) {
				String def = "(declare-const " + matcher.group(1) + "_" + matcher.group(2) + " Bool)\n";
				if(added.add(def))
				{
					input.append(def);		
				}
			}
			
			Pattern z3tempVars = Pattern.compile("\\w\\!\\d+");
			Matcher z3tempVarMatch = z3tempVars.matcher(operationText);
			while(z3tempVarMatch.find())
			{
				String def = "(declare-const " + z3tempVarMatch.group(0) + " Bool)\n";
				if(added.add(def))
				{
					input.append(def);		
				}
			}
			
			BoolExpr op1 = null;
			BoolExpr op2 = null;
			
			if(operation.getOperand1().equals("true")) {
				op1 = ctx.mkBool(true);
			} else if(operation.getOperand1().equals("false")) {
				op1 = ctx.mkBool(false);
			} else {
				try {
					op1 = ctx.parseSMTLIB2String(input.toString() + "(assert " + operation.getOperand1() + ")", null, null, names, decls);
				} catch(Z3Exception e) {
					System.err.println(input.toString() + "(assert " + operation.getOperand1() + ")");
					throw e;
				}
			}
			
			if(operation.getOperand2() != null) {
				if(operation.getOperand2().equals("true")) {
					op2 = ctx.mkBool(true);
				} else if(operation.getOperand2().equals("false")) {
					op2 = ctx.mkBool(false);
				} else {
					try {
						op2 = ctx.parseSMTLIB2String(input.toString() + "(assert " + operation.getOperand2() + ")", null, null, names, decls);
					} catch(Z3Exception e) {
						System.err.println(input.toString() + "(assert " + operation.getOperand2() + ")");
						throw e;
					}
				}
			}
			
			String result = null;
			BoolExpr expr = null;
			if(operation.getOperator() == Operator.AND) {
				expr = ctx.mkAnd(op1, op2);
			}
			if(operation.getOperator() == Operator.OR) {
				expr = ctx.mkOr(op1, op2);
			}
			if(operation.getOperator() == Operator.NEGATE) {
				expr = ctx.mkNot(op1);
			}
			if(operation.getOperator() == Operator.EQ) {
				expr = ctx.mkEq(op1, op2);
			}
			if(operation.getOperator() == Operator.IMPLIES)
			{
				expr = ctx.mkImplies(op1, op2);
			}
			
			// Used to build a dummy operation for pretty printing
			if(operation.getOperator() == Operator.DUMMY) {
				expr = op1;
			}
			
			if(operation.getOperator() == Operator.EQUIVALENCE) {
				BoolExpr e = ctx.mkNot(ctx.mkEq(op1, op2));
				solver.add(e);
				Status status = solver.check();
				if(status == Status.UNSATISFIABLE)
				{
					result = "true";
				} else {
					result = "false";
				}
				
				cache.put(operation, result);
				if(mongoLoader != null && !disableNewCacheEntries)
				{
					mongoLoader.add(operation.toString(), result, result);
				}
				
				prettyprinted.put(result, ConcurrentUtils.constantFuture(result));
				return;
			}
			
			if(operation.getOperator() == Operator.CNF)
			{
				expr = op1;
			}


			
//			Tactic tactic = ctx.mkTactic("simplify");
			Tactic tacticSimplify = ctx.mkTactic("ctx-solver-simplify");
			Tactic ctxSimplify = ctx.mkTactic("ctx-simplify");
			Tactic splitClause = ctx.mkTactic("split-clause");
			Tactic skip = ctx.skip();
			Tactic orElse = ctx.orElse(splitClause, skip);
			Tactic propagateValues = ctx.mkTactic("propagate-values");
			
			Tactic tactic = ctx.andThen(tacticSimplify, ctxSimplify, orElse, propagateValues);
			
			// Overwrite tactic
			if(operation.getOperator() == Operator.CNF)
			{
				Tactic cnf = ctx.mkTactic("tseitin-cnf");
				tactic = cnf;
			}
			
			// Measured - not an improvement
//			Tactic tactic = ctx.parAndThen(tacticSimplify, ctx.parAndThen(ctxSimplify, ctx.parAndThen(orElse, propagateValues)));
//			Tactic tactic = ctx.mkTactic("ctx-solver-simplify");
			
			Goal g = ctx.mkGoal(false, false, false);
			g.add(expr);
			
			
			long start = System.currentTimeMillis();
			ApplyResult applyResult = tactic.apply(g);
			long duration = System.currentTimeMillis() - start;
			if(duration > 500)
			{
				System.out.println("long query " + duration);
			}
			
			
			List<BoolExpr> parts = new LinkedList<BoolExpr>();
			for(Goal subGoal : applyResult.getSubgoals()) {
				BoolExpr boolExpr = null;
				
				if(subGoal.getNumExprs() == 0)
				{
					boolExpr = ctx.mkTrue();
				} else {
					if(subGoal.getFormulas().length > 1) {
						boolExpr = ctx.mkAnd(subGoal.getFormulas());
					} else {
						boolExpr = subGoal.getFormulas()[0];
					}
				}
				parts.add(boolExpr);
			}
			
			BoolExpr combined = null;
			if(parts.size() > 1) {
				combined = ctx.mkOr(parts.toArray(new BoolExpr[parts.size()]));
			} else {
				combined = parts.get(0);
			}
			
			result = combined.toString();
			
			cache.put(operation, result);
			if(mongoLoader != null && !disableNewCacheEntries)
			{
				// break async pretty printing
				String pretty = asyncPrettyprinter.add(combined).get();
				prettyprinted.put(result, ConcurrentUtils.constantFuture(pretty));
				mongoLoader.add(operationText, result, pretty);
			} else {
				Future<String> pretty = asyncPrettyprinter.add(combined);
				prettyprinted.put(result, pretty);
			}
		

		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			System.err.println(operation);
			throw new RuntimeException("Error in Z3");
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
//			try {
//				if(solver != null) {
//					solver.dispose();
//					solver = null;
//				}
//			} catch (Z3Exception e) {
//				e.printStackTrace();
//			} 
//			
//			if(ctx != null) {
//				ctx.dispose();
//			}
		}
		
//		ctx = null;
		if(counter % 10000 == 0) {
//			System.gc();
//			System.runFinalization();
//			System.out.println("GC / Finalizer");
		}
		
//		try {
//			Thread.sleep(5000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}

	public static synchronized String initializeConstraint(BitSet elems, Integer value, Set<Integer> trackPrecise)
	{
		Context ctx = null;
		Solver solver = null;
		
		try {
			ctx = new Context();
			solver = ctx.mkSolver();
			
			maxFeatureIndex = Math.max(maxFeatureIndex, elems.length());
			
			for(int i=elems.nextSetBit(0); i>=0; i=elems.nextSetBit(i+1)) {
				IntExpr intExpr = ctx.mkIntConst(Integer.toString(i));
				
				if(trackPrecise.contains(i)) {
					if(value == -1) {
//						solver.add(ctx.mkGe(intExpr, ctx.mkInt(0)));
						solver.add(ctx.mkTrue());
					} else {
						solver.add(ctx.mkEq(intExpr, ctx.mkInt(value)));
					}
				} else {
					solver.add(ctx.mkTrue());
				}
			}
			
			if(solver.getNumAssertions() == 1)
			{
				String result = solver.getAssertions()[0].toString();
				
				if(!prettyprinted.containsKey(result)) {
//					String pretty = prettyprinting.prettyprintZ3(solver.getAssertions()[0]);
					Future<String> pretty = asyncPrettyprinter.add(solver.getAssertions()[0]);
//					System.out.println(result + " => " + pretty);
					
					prettyprinted.put(result, pretty);
				}
				
				return result;
			} else {
				throw new IllegalStateException("Error: Initialization resulted in " + solver.getNumAssertions() + " assertions.");
			}
					
		} catch (Z3Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			throw new RuntimeException("Error in Z3");
		} finally {
			try {
				if(solver != null) {
					solver.dispose();
					solver = null;
				}
			} catch (Z3Exception e) {
				e.printStackTrace();
			} 
			
			if(ctx != null) {
				ctx.dispose();
				ctx = null;
			}
			if(counter++ % 10000 == 0) {
				System.gc();
				System.runFinalization();
			}
		}
		

	}
	
	public static boolean constraintEquals(String c1, String c2)
	{
		SolverOperation op = new SolverOperation(c1, c2, Operator.EQUIVALENCE);
		return solve(op).equals("true");
	}
	
	public static String getPrettyprinted(String expression)
	{
		String pretty;
		try {
			pretty = prettyprinted.get(expression).get();
		} catch (InterruptedException | ExecutionException e) {
			throw new RuntimeException(e);
		}
		
		for(Entry<Integer, String> featureEntry : featureNames.entrySet())
		{
			pretty = StringUtils.replace(pretty, "|" + Integer.toString(featureEntry.getKey()) + "|", featureEntry.getValue());
		}
		
		// TODO DEBUG some overwrites
		if(pretty.equals("A = 0")) pretty = "!A";
		if(pretty.equals("!(A = 0)")) pretty = "A";
		if(pretty.equals("!(B = 0)")) pretty = "B";
		if(pretty.equals("A = 0 || B = 0")) pretty = "(!A || !B)";
		if(pretty.equals("(!A = 0 ^ !B = 0)")) pretty = "(A ^ B)";
		if(pretty.equals("(!A = 0 ^ B = 0)")) pretty = "(A ^ !B)";
		return pretty;
	}

	public static void setMaxFeatureIndex(int maxFeatureIndex) {
		CachedZ3Solver.maxFeatureIndex = maxFeatureIndex;
	}

}
