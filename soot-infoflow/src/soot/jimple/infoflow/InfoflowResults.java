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
package soot.jimple.infoflow;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Value;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.infoflow.data.AccessPath;
import soot.tagkit.LineNumberTag;

/**
 * Class for collecting information flow results
 * 
 * @author Steven Arzt
 */
public class InfoflowResults {

    private final Logger logger = LoggerFactory.getLogger(getClass());
	
	/**
	 * Class for modeling information flowing out of a specific source
	 * @author Steven Arzt
	 */
	public class SourceInfo {
		private final Stmt context;
		private final Object userData;
		private final List<Stmt> path;
		
		public SourceInfo(Stmt context) {
			
			this.context = context;
			this.userData = null;
			this.path = null;
		}
		
		public SourceInfo(Stmt context, Object userData, List<Stmt> path) {

			this.context = context;
			this.userData = userData;
			this.path = path;
		}

		public Stmt getContext() {
			return this.context;
		}
		
		public Object getUserData() {
			return this.userData;
		}
		
		public List<Stmt> getPath() {
			return this.path;
		}

        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder(context.toString());

            if (context.hasTag("LineNumberTag"))
                sb.append(" on line ").append(((LineNumberTag) context.getTag("LineNumberTag")).getLineNumber());

            return sb.toString();
        }

		@Override
		public int hashCode() {
			return 7 * this.context.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (super.equals(o))
				return true;
			if (o == null || !(o instanceof SourceInfo))
				return false;
			SourceInfo si = (SourceInfo) o;
			return this.context.equals(si.context);
		}
	}
	
	/**
	 * Class for modeling information flowing into a specific source
	 * @author Steven Arzt
	 */
	public class SinkInfo {
		private final Value sink;
		private final Stmt context;
		
		public SinkInfo(Value sink, Stmt context) {
			assert sink != null;

			this.sink = sink;
			this.context = context;
		}
		
		public Value getSink() {
			return this.sink;
		}
		
		public Stmt getContext() {
			return this.context;
		}
		
		@Override
		public String toString() {
            StringBuilder sb = new StringBuilder(context.toString());

            if (context.hasTag("LineNumberTag"))
                sb.append(" on line ").append(((LineNumberTag)context.getTag("LineNumberTag")).getLineNumber());

			return sb.toString();
		}

		@Override
		public int hashCode() {
			return 31 * this.sink.hashCode()
					+ 7 * this.context.hashCode();
		}
		
		@Override
		public boolean equals(Object o) {
			if (super.equals(o))
				return true;
			if (o == null || !(o instanceof SinkInfo))
				return false;
			SinkInfo si = (SinkInfo) o;
			return this.sink.equals(si.sink)
					&& this.context.equals(si.context);
		}
	}
	
	private final Map<SinkInfo, Set<SourceInfo>> results = new ConcurrentHashMap<SinkInfo, Set<SourceInfo>>();
	
	public InfoflowResults() {
		
	}
	
	/**
	 * Gets the number of entries in this result object
	 * @return The number of entries in this result object
	 */
	public int size() {
		return this.results.size();
	}
	
	/**
	 * Gets whether this result object is empty, i.e. contains no information
	 * flows
	 * @return True if this result object is empty, otherwise false.
	 */
	public boolean isEmpty() {
		return this.results.isEmpty();
	}
	
	/**
	 * Checks whether this result object contains a sink that exactly matches the
	 * given value.
	 * @param sink The sink to check for
	 * @return True if this result contains the given value as a sink, otherwise
	 * false.
	 */
	public boolean containsSink(Value sink) {
		for (SinkInfo si : this.results.keySet())
			if (si.getSink().equals(sink))
				return true;
		return false;
	}
	
	/**
	 * Checks whether this result object contains a sink with the given method
	 * signature
	 * @param sinkSignature The method signature to check for
	 * @return True if there is a sink with the given method signature in this
	 * result object, otherwise false.
	 */
	public boolean containsSinkMethod(String sinkSignature) {
		return !findSinkByMethodSignature(sinkSignature).isEmpty();
	}

	public void addResult(Value sink, Stmt sinkStmt, Value source, Stmt sourceStmt) {
		this.addResult(new SinkInfo(sink, sinkStmt), new SourceInfo(sourceStmt));
	}
	
	public void addResult(Value sink, Stmt sinkStmt, Value source,
			Stmt sourceStmt, Object userData, List<Stmt> propagationPath) {
		this.addResult(new SinkInfo(sink, sinkStmt), new SourceInfo(sourceStmt, userData, propagationPath));
	}

	public void addResult(Value sink, Stmt sinkContext, AccessPath accessPath,
			Stmt sourceStmt, Object userData, List<Stmt> propagationPath, Stmt stmt) {
		List<Stmt> newPropPath = new LinkedList<Stmt>(propagationPath);
		newPropPath.add(stmt);
		this.addResult(new SinkInfo(sink, sinkContext),
				new SourceInfo(sourceStmt, userData, newPropPath));
	}

	public synchronized void addResult(SinkInfo sink, SourceInfo source) {
		assert sink != null;
		assert source != null;
		
		Set<SourceInfo> sourceInfo = this.results.get(sink);
		if (sourceInfo == null) {
			sourceInfo = new HashSet<SourceInfo>();
			this.results.put(sink, sourceInfo);
		}
		sourceInfo.add(source);
	}

	/**
	 * Gets all results in this object as a hash map.
	 * @return All results in this object as a hash map.
	 */
	public Map<SinkInfo, Set<SourceInfo>> getResults() {
		return this.results;
	}
	
	/**
	 * Checks whether there is a path between the given source and sink.
	 * @param sink The sink to which there may be a path
	 * @param source The source from which there may be a path
	 * @return True if there is a path between the given source and sink, false
	 * otherwise
	 */
	public boolean isPathBetween(Value sink, Value source) {
		Set<SourceInfo> sources = null;
		for(SinkInfo sI : this.results.keySet()){
			if(sI.getSink().equals(sink)){
				sources = this.results.get(sI);
				break;
			}
		}
		if (sources == null)
			return false;
		return false;
	}
	
	/**
	 * Checks whether there is a path between the given source and sink.
	 * @param sink The sink to which there may be a path
	 * @param source The source from which there may be a path
	 * @return True if there is a path between the given source and sink, false
	 * otherwise
	 */
	public boolean isPathBetween(String sink, String source) {
		for (SinkInfo si : this.results.keySet())
			if (si.getSink().toString().equals(sink)) {
				Set<SourceInfo> sources = this.results.get(si);
				for (SourceInfo src : sources)
					if (src.toString().equals(source))
						return true;
		}
		return false;
	}

	/**
	 * Checks whether there is an information flow between the two
	 * given methods (specified by their respective Soot signatures). 
	 * @param sinkSignature The sink to which there may be a path
	 * @param sourceSignature The source from which there may be a path
	 * @return True if there is a path between the given source and sink, false
	 * otherwise
	 */
	public boolean isPathBetweenMethods(String sinkSignature, String sourceSignature) {
		List<SinkInfo> sinkVals = findSinkByMethodSignature(sinkSignature);
		for (SinkInfo si : sinkVals) {
			Set<SourceInfo> sources = this.results.get(si);
			if (sources == null)
				return false;
//			for (SourceInfo src : sources)
//				if (src.source instanceof InvokeExpr) {
//					InvokeExpr expr = (InvokeExpr) src.source;
//					if (expr.getMethod().getSignature().equals(sourceSignature))
//						return true;
//				}
		}
		return false;
	}

	/**
	 * Finds the entry for a sink method with the given signature
	 * @param sinkSignature The sink's method signature to look for
	 * @return The key of the entry with the given method signature if such an
	 * entry has been found, otherwise null.
	 */
	private List<SinkInfo> findSinkByMethodSignature(String sinkSignature) {
		List<SinkInfo> sinkVals = new ArrayList<SinkInfo>();
		for (SinkInfo si : this.results.keySet())
			if (si.getSink() instanceof InvokeExpr) {
				InvokeExpr expr = (InvokeExpr) si.getSink();
				if (expr.getMethod().getSignature().equals(sinkSignature))
					sinkVals.add(si);
			}
		return sinkVals;
	}

	/**
	 * Prints all results stored in this object to the standard output
	 */
	public void printResults() {
		for (SinkInfo sink : this.results.keySet()) {
			logger.info("Found a flow to sink {}, from the following sources:", sink);
			for (SourceInfo source : this.results.get(sink)) {
//				logger.info("\t- {}", source.getSource());
				if (source.getPath() != null && !source.getPath().isEmpty())
					logger.info("\t\ton Path {}", source.getPath());
			}
		}
	}

	/**
	 * Prints all results stored in this object to the given writer
	 * @param wr The writer to which to print the results
	 * @throws IOException Thrown when data writing fails
	 */
	public void printResults(Writer wr) throws IOException {
		for (SinkInfo sink : this.results.keySet()) {
			wr.write("Found a flow to sink " + sink + ", from the following sources:\n");
			for (SourceInfo source : this.results.get(sink)) {
//				wr.write("\t- " + source.getSource() + "\n");
				if (source.getPath() != null && !source.getPath().isEmpty())
					wr.write("\t\ton Path " + source.getPath() + "\n");
			}
		}
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (SinkInfo sink : this.results.keySet())
			for (SourceInfo source : this.results.get(sink)) {
				sb.append(source);
				sb.append(" -> ");
				sb.append(sink);
			}
		return sb.toString();
	}

}
