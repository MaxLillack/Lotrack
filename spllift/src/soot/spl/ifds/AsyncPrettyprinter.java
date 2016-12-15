package soot.spl.ifds;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Solver;

public class AsyncPrettyprinter  {
	
	private ExecutorService executor;
	private static Context ctx;
	private static Solver solver;
	private MyZ3Prettyprinting prettyprinter = new MyZ3Prettyprinting();
	
	public AsyncPrettyprinter()
	{
		this.executor = Executors.newSingleThreadExecutor();
		
		ctx = new Context();				
		solver = ctx.mkSolver();
	}
	
	public Future<String> add(Expr expression)
	{
		solver.reset();
		Expr expr = expression.translate(ctx);
		PrettyprintTask task = new PrettyprintTask(prettyprinter, expr);
		return executor.submit(task);
	}
	
	private class PrettyprintTask implements Callable<String>
	{
		private MyZ3Prettyprinting prettyprinter;
		private Expr expr;
		
		public PrettyprintTask(MyZ3Prettyprinting prettyprinter, Expr expr)
		{
			this.prettyprinter = prettyprinter;
			this.expr = expr;
		}
		
		@Override
		public String call() throws Exception {
			return prettyprinter.prettyprintZ3(expr);
		}
		
	}
	
}
