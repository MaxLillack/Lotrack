package controllers;



import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map.Entry;






import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.lang3.StringEscapeUtils;


import com.google.common.base.Joiner;
import com.mongodb.DBObject;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException.Missing;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import models.AnalysisResult;
import models.JSTreeNode;
import models.LoadTimeResult;


import models.MongoLoader;
import play.Logger;

import play.libs.Json;
import play.mvc.*;
import views.html.*;


public class Application extends Controller {
	
    private static final Map<String, String> projects;
    static
    {
    	projects = new HashMap<String, String>();
    	File file = new File("../soot-infoflow/src/application.conf");
    	Logger.info("File exists: {}", file.exists());
    	Config config = ConfigFactory.parseFile(file);
    	for(Entry<String, ConfigValue> entry : config.root().entrySet()) {
    		String name = entry.getKey();
    		String path = entry.getKey() + ".srcPath";
    		String srcPath = null;
    		if(config.hasPath(path)) {
    			srcPath = config.getString(path);
    		}
    		projects.put(name, srcPath);
    	}
    }

    
    
	public static Result index() {
		List<String> projectNames = new LinkedList<String>(projects.keySet());
		return ok(index.render(projectNames));
	}
	
	public static Result overview() {
		try {
			MongoLoader mongoLoader = new MongoLoader();
			List<LoadTimeResult> loadTimeResults = mongoLoader.getAllLoadTimeResults();
			return ok(overview.render(loadTimeResults));
		} catch(Exception e) {
			return internalServerError(e.getMessage());
		}
	}
	
	public static void addDir(File path, JSTreeNode tree, Map<String, Integer> counts)
	{
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
				if(counts.containsKey(file.getPath())) {
					int count = counts.get(file.getPath());
					node = new JSTreeNode(file.getPath(), file.getName() + " (" + count + ")");
				} else {
					Logger.info(file.getPath() + " not found in DB");
					node = new JSTreeNode(file.getPath(), file.getName());
				}
				node.icon = "glyphicon glyphicon-minus";
				tree.children.add(node);
			}
		}
	}
	
	public static void loadJimpleTree(String project, JSTreeNode tree) throws UnknownHostException
	{
		MongoLoader mongoLoader = new MongoLoader();
		for(Entry<String, String> entry : mongoLoader.getJimplePaths(project).entrySet())
		{
			JSTreeNode node = new JSTreeNode(entry.getKey(), entry.getValue());
			node.icon = "glyphicon glyphicon-minus";
			tree.children.add(node);
		}
	}
	
	public static Result fileTree(String project) throws UnknownHostException {
		
		if(!projects.containsKey(project)) {
			return internalServerError("Project " + project + "unknown");
		}
		
		JSTreeNode tree = new JSTreeNode(project);
		
		String path = projects.get(project);
		if(path == null) {
			loadJimpleTree(project, tree);
			return ok(Json.toJson(tree));
		}
		
		MongoLoader mongoLoader = new MongoLoader();
		Map<String, Integer> counts = mongoLoader.getResultCount(project);
		
		File file = new File(path);
		
		
		tree.state.opened = true;
		
		addDir(file,tree, counts);
		
		return ok(Json.toJson(tree));
	}
	

	
	public static Result loadResult() {
		try {
			MongoLoader mongoLoader = new MongoLoader();
			
			String path = request().body().asFormUrlEncoded().get("fileName")[0];
			String project = request().body().asFormUrlEncoded().get("project")[0];
			
			List<DBObject> results = mongoLoader.getResults(path,project);
			
			AnalysisResult analysisResult = new AnalysisResult();
			
			Map<String, String[]> jimpleSources = new HashMap<String, String[]>();
			
			if(!results.isEmpty()) {
				File javaSourceFile = new File((String) results.get(0).get("JavaPath"));
				String javaSource = FileUtils.readFileToString(javaSourceFile);
				javaSource = StringEscapeUtils.escapeHtml4(javaSource);
				String[] javaLines = null;
				if(javaSource != null) {
					javaLines = javaSource.split("\\r?\\n");
				}
				
				for(DBObject result : results) {
					String className = (String) result.get("Class");
					String jimplePath = (String) result.get("JimplePath");
					String constraint = (String) result.get("ConstraintPretty");
					
					if(jimpleSources != null && !jimpleSources.containsKey(jimplePath)) {
						String jimpleSource = StringEscapeUtils.escapeHtml4(mongoLoader.getJimpleSource(className));
						if(jimpleSource != null) {
							String[] jimpleLines = jimpleSource.split("\\r?\\n");
							jimpleSources.put(jimplePath, jimpleLines);
						}
					}
					
					String[] jimpleLines = jimpleSources.get(jimplePath);
					
					if(!constraint.equals("true")) {
						if(javaLines != null) {
							int javaLineNo = (int) result.get("JavaLineNo");
							if(javaLineNo > 0 && javaLineNo < javaLines.length && !javaLines[javaLineNo-1].contains("span")) {
								javaLines[javaLineNo-1] = String.format("<span style=\"background: #66FFCC\">%s</span><span style=\"background: #00FF66\">%s</span>", 
										javaLines[javaLineNo-1],
										constraint);
							}
						}
						
						int jimpleLineNo = (int) result.get("JimpleLineNo");
						
						if(jimpleLineNo > 0 && jimpleLineNo < jimpleLines.length) {
							jimpleLines[jimpleLineNo-1] = 
									String.format("<span style=\"background: #66FFCC\">%s</span><span style=\"background: #00FF66\">%s</span>", 
											jimpleLines[jimpleLineNo-1],
											constraint);
						}
					}
				}
				
				Map<String, String> jimpleResultSources = new HashMap<String, String>();
				for(String entry : jimpleSources.keySet()) {
					jimpleResultSources.put(entry,  Joiner.on("\n").join(jimpleSources.get(entry)));
				}
				
				analysisResult.javaSource = javaLines != null ? Joiner.on("\n").join(javaLines) : "";
				analysisResult.jimpleSource = jimpleResultSources;
			}
			
			return ok(Json.toJson(analysisResult));
		} catch (UnknownHostException e) {
			return internalServerError(e.getMessage());
		} catch (IOException e) {
			return internalServerError(e.getMessage());
		}
	}
}
