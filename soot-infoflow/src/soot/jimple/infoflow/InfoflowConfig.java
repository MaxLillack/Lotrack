package soot.jimple.infoflow;

public class InfoflowConfig {
	public String androidPath;
	public boolean forceAndroidJar;
	public boolean stopAfterFirstFlow;
	public boolean enableImplicitFlows;
	public boolean enableStaticFields;
	public boolean enableExceptions;
	public boolean computeResultPaths;
	public boolean flowSensitiveAliasing;
	public boolean inspectSources;
	public boolean inspectSinks;
	public int maxThreadNum;

	public InfoflowConfig(boolean stopAfterFirstFlow,
			boolean enableImplicitFlows, boolean enableStaticFields,
			boolean enableExceptions, boolean computeResultPaths,
			boolean flowSensitiveAliasing, boolean inspectSources,
			boolean inspectSinks, int maxThreadNum) {
		this.stopAfterFirstFlow = stopAfterFirstFlow;
		this.enableImplicitFlows = enableImplicitFlows;
		this.enableStaticFields = enableStaticFields;
		this.enableExceptions = enableExceptions;
		this.computeResultPaths = computeResultPaths;
		this.flowSensitiveAliasing = flowSensitiveAliasing;
		this.inspectSources = inspectSources;
		this.inspectSinks = inspectSinks;
		this.maxThreadNum = maxThreadNum;
	}
}