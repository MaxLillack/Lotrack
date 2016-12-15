package controllers;



import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Joiner;
import com.mongodb.BasicDBList;
import com.mongodb.DBObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import models.AnalysisResult;
import models.JSTreeNode;
import models.LineInfo;
import models.LoadTimeResult;


import models.MongoLoader;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.Json;
import play.mvc.*;
import views.html.*;


public class Application extends Controller {
	
    private static final Map<String, String> projects;
    private static final String dataPathSearch;
    private static final String dataPathReplace;
    
    static
    {
    	projects = new HashMap<String, String>();
    	
    	Config conf = ConfigFactory.load();
    	String infoflowPath = conf.getString("SootInfoflowConfPath");
    	File file = new File(infoflowPath);
    	
    	Logger.info("File {} exists: {} infoflowPath {}", file.getAbsolutePath(), file.exists(), infoflowPath);
    	Config config = ConfigFactory.parseFile(file).resolve();
    	for(Entry<String, ConfigValue> entry : config.root().entrySet()) {
    		String name = entry.getKey();
    		String path = entry.getKey() + ".srcPath";
    		String srcPath = null;
    		if(config.hasPath(path)) {
    			srcPath = config.getString(path);
    		}
    		projects.put(name, srcPath);
    	}
    	
    	dataPathSearch = conf.getString("dataPathSearch");
    	dataPathReplace = conf.getString("dataPathReplace");
    }

	public static Result project(String name) {
		List<String> projectNames = new LinkedList<String>(projects.keySet());
		Collections.sort(projectNames);
		// TODO - Check name is in projectNames
		return ok(index.render(projectNames, name));
	}
    
	public static Result index() {
		List<String> projectNames = new LinkedList<String>(projects.keySet());
		Collections.sort(projectNames);

		String selected = projectNames.size() > 0 ? projectNames.get(0) :"";
		
		return ok(index.render(projectNames, selected));
	}
	
	public static Result detailedLineInfo(String app, String path, int lineNumber) throws Exception {
		String className = FilenameUtils.getBaseName(path);
		System.out.println("className " + className + " line number " + lineNumber);
		try {
			LineInfo lineInfo = new LineInfo();
			
			try(MongoLoader mongoLoader = new MongoLoader())
			{
				List<String> details = mongoLoader.getDetailedLog(app, className, lineNumber);
				Collections.sort(details);
				
				List<String> escapedDetails = new ArrayList<String>(details.size());
				for(String detail : details)
				{
					escapedDetails.add(StringEscapeUtils.escapeHtml4(detail));
				}
				
				boolean inSlice = mongoLoader.isInSlice(className, lineNumber);
	
				lineInfo.details = escapedDetails;
				lineInfo.inSlice = inSlice;
			}

			
			return ok(Json.toJson(lineInfo));
		} catch (UnknownHostException e) {
			return internalServerError(e.toString());
		}
	}
	
	public static Result overview() {
		try {
			List<LoadTimeResult> loadTimeResults;
			try(MongoLoader mongoLoader = new MongoLoader()) {
				loadTimeResults = mongoLoader.getAllLoadTimeResults();
			}
			return ok(overview.render(loadTimeResults));
		} catch(Exception e) {
			return internalServerError(e.getMessage());
		}
	}
	
	public static void addDir(File path, JSTreeNode tree, Map<String, Integer> counts)
	{
		if(path.exists()) {
			File[] dirs = path.listFiles();
			for(File dir : dirs)
			{
				if(dir.isDirectory() && !dir.getPath().equals(path.getPath())) {
					JSTreeNode dirNode = new JSTreeNode(dir.getName());
					addDir(dir, dirNode, counts);
					tree.children.add(dirNode);
				}
			}
			
			
			Collection<File> files = FileUtils.listFilesAndDirs(path, FileFileFilter.FILE, null);
			for(File file : files)
			{
				if(!file.isDirectory()) {
					JSTreeNode node = null;
					
					String filepath = file.getPath();
					filepath = StringUtils.replace(filepath, dataPathSearch, dataPathReplace);
					filepath = StringUtils.replace(filepath, "/", "\\");
					
					if(counts.containsKey(filepath)) {
						int count = counts.get(filepath);
						node = new JSTreeNode(file.getPath(), file.getName() + " (" + count + ")");
					} else {
						Logger.info(filepath + " not found in DB");
						node = new JSTreeNode(file.getPath(), file.getName());
					}
					node.icon = "glyphicon glyphicon-minus";
					tree.children.add(node);
				}
			}
		} else {
			Logger.info("addDir: path {} does not exist.", path.getAbsolutePath());
		}
	}
	
	public static void loadJimpleTree(String project, JSTreeNode tree) throws Exception
	{
		try(MongoLoader mongoLoader = new MongoLoader())
		{
			for(Entry<String, String> entry : mongoLoader.getJimplePaths(project).entrySet())
			{
				JSTreeNode node = new JSTreeNode(entry.getKey(), entry.getValue());
				node.icon = "glyphicon glyphicon-minus";
				tree.children.add(node);
			}
		}
	}
	
	public static Result fileTree(String project) throws Exception {
		
		if(!projects.containsKey(project)) {
			return internalServerError("Project " + project + "unknown");
		}
		
		JSTreeNode tree = new JSTreeNode(project);
		
		String path = projects.get(project);
		Logger.info("fileTree() path {} ", path);
		if(path == null) {
			loadJimpleTree(project, tree);
			return ok(Json.toJson(tree));
		}
		
		File file = new File(path);
		
		if(!file.exists()) {
			return internalServerError("File " + file.getAbsolutePath() + " not found."); 
		}
		


		Map<String, Integer> counts;
		try(MongoLoader mongoLoader = new MongoLoader()) {
			counts = mongoLoader.getResultCount(project);
		}
		
		tree.state.opened = true;
		
		addDir(file, tree, counts);
		
		return ok(Json.toJson(tree));
	}
	

	
	public static Result loadResult() throws Exception {
		MongoLoader mongoLoader = null;
		try {
			mongoLoader = new MongoLoader();
			
			String path = request().body().asFormUrlEncoded().get("fileName")[0];
			String project = request().body().asFormUrlEncoded().get("project")[0];
			
			Logger.info("path {} replace {} with {}", path, dataPathSearch, dataPathReplace);
			
			path = StringUtils.replace(path, dataPathSearch, dataPathReplace);
			path = StringUtils.replace(path, "/", "\\");
			
			Logger.info("Path {}", path);

			List<DBObject> results = mongoLoader.getResults(path, project);
			
			AnalysisResult analysisResult = new AnalysisResult();
			
			Map<String, String[]> jimpleSources = new HashMap<String, String[]>();
			
			if(!results.isEmpty()) {
				
				String javaPath = (String) results.get(0).get("JavaPath");
				
				String[] javaLines = loadJavaLines(javaPath);
				
				for(DBObject result : results) {
					
//					Logger.info("Result class {} jimplePath {} constraint {} javaLineNo {} JimpleLineNo {}", 
//							(String) result.get("Class"),
//							(String) result.get("JimplePath"),
//							(String) result.get("ConstraintPretty"),
//							(int) result.get("JavaLineNo"),
//							(int) result.get("JimpleLineNo"));
					
					String className = (String) result.get("Class");
					String jimplePath = (String) result.get("JimplePath");
					String constraint = (String) result.get("ConstraintPretty");
					
					constraint = constraint.replaceAll("_Alpha", "<sub>α</sub>");
					constraint = constraint.replaceAll("_Beta", "<sub>β</sub>");
					constraint = constraint.replaceAll("_Gamma", "<sub>γ</sub>");
					
					if(jimpleSources != null && !jimpleSources.containsKey(jimplePath)) {
						String jimpleSource = StringEscapeUtils.escapeHtml4(mongoLoader.getJimpleSource(className));
						if(jimpleSource != null) {
							String[] jimpleLines = jimpleSource.split("\\r?\\n");
							jimpleSources.put(jimplePath, jimpleLines);
						}
					}
					
					String[] jimpleLines = jimpleSources.get(jimplePath);
					int jimpleLineNo = (int) result.get("JimpleLineNo");
//					boolean isInSlice = mongoLoader.isInSlice(className, jimpleLineNo);
					boolean isInSlice = false;
					String sliceText = isInSlice ? "<span style=\"background: #FF9933\">  SLICE  </span>" : "";
					
					String bcMethodName = (String) result.get("methodBytecodeSignatureJoanaStyle");
					BasicDBList bytecodeIndexes = (BasicDBList) result.get("bytecodeIndexes");
					   
					// Look for joana results
//					Set<String> joanaSlice = mongoLoader.joanaSlice(project, bcMethodName, bytecodeIndexes);
//					if(!joanaSlice.isEmpty()) {
//						sliceText += "<span style=\"background: #CC99FF\">  JOANA {" + StringUtils.join(joanaSlice, ",") + "}  </span>";
//					}
					
					if(!constraint.equals("true")) {
						if(javaLines != null) {
							int javaLineNo = (int) result.get("JavaLineNo");
							if(javaLineNo > 0 && javaLineNo < javaLines.length && !javaLines[javaLineNo-1].contains("span")) {
								javaLines[javaLineNo-1] = String.format("<span style=\"background: #66FFCC\">%s</span><span style=\"background: #00FF66\">  %s  </span>%s", 
										javaLines[javaLineNo-1],
										constraint, sliceText);
							}
						}
						
						if(jimpleLineNo > 0 && jimpleLineNo < jimpleLines.length) {
							jimpleLines[jimpleLineNo-1] = 
									String.format("<span style=\"background: #66FFCC\">%s</span><span style=\"background: #00FF66\">  %s  </span>%s", 
											jimpleLines[jimpleLineNo-1],
											constraint,
											sliceText);
						}
					} else {
						if(jimpleLineNo > 0 && jimpleLineNo < jimpleLines.length) {
							jimpleLines[jimpleLineNo-1] = 
									String.format("%s%s", 
											jimpleLines[jimpleLineNo-1],
											sliceText);
						}
						if(javaLines != null) {
							int javaLineNo = (int) result.get("JavaLineNo");
							if(javaLineNo > 0 && javaLineNo < javaLines.length && !javaLines[javaLineNo-1].contains("span")) {
								javaLines[javaLineNo-1] = String.format("%s%s", 
										javaLines[javaLineNo-1],
										sliceText);
							}
						}
					}
				}
				
				Map<String, String> jimpleResultSources = new TreeMap<String, String>(Comparator.reverseOrder());
				for(String entry : jimpleSources.keySet()) {
					jimpleResultSources.put(entry,  Joiner.on("\n").join(jimpleSources.get(entry)));
				}
				
				analysisResult.javaSource = javaLines != null ? Joiner.on("\n").join(javaLines) : "";
				analysisResult.jimpleSource = jimpleResultSources;
			} else {
				Logger.info("No results found for path " + path);
				
				String[] javaLines = loadJavaLines(path);
				analysisResult.javaSource = javaLines != null ? Joiner.on("\n").join(javaLines) : "";
			}
			
			return ok(Json.toJson(analysisResult));
		} catch (UnknownHostException e) {
			return internalServerError(e.getMessage());
		} catch (IOException e) {
			return internalServerError(e.getMessage());
		} finally {
			if(mongoLoader != null) {
				mongoLoader.close();
			}
		}
	}

	private static String[] loadJavaLines(String javaPath) throws IOException {
		javaPath = StringUtils.replace(javaPath, dataPathReplace, dataPathSearch);
		javaPath = StringUtils.replace(javaPath, "\\", File.separator);
		
		String[] javaLines = null;
		if(javaPath != null) {
			File javaSourceFile = new File(javaPath);
			
			if(!javaSourceFile.exists()) {
				Logger.info("java soruce file {} not found", javaPath);
			} else {
			
				String javaSource = FileUtils.readFileToString(javaSourceFile);
				javaSource = StringEscapeUtils.escapeHtml4(javaSource);
				
				if(javaSource != null) {
					javaLines = javaSource.split("\\r?\\n");
				}
			}
		}
		return javaLines;
	}
}
