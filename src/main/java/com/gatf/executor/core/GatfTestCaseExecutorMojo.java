package com.gatf.executor.core;

/*
Copyright 2013-2014, Sumeet Chhetri

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.InstantiationStrategy;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.executor.TestCaseExecutorUtil;
import com.gatf.executor.finder.CSVTestCaseFinder;
import com.gatf.executor.finder.JSONTestCaseFinder;
import com.gatf.executor.finder.TestCaseFinder;
import com.gatf.executor.finder.XMLTestCaseFinder;
import com.gatf.executor.report.ReportHandler;
import com.gatf.executor.report.TestCaseReport;
import com.gatf.executor.report.TestSuiteStats;
import com.gatf.generator.core.ClassLoaderUtils;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.XppDriver;

/**
 * @author Sumeet Chhetri
 * The maven plugin main class for the Test case Executor/Workflow engine
 */
@Mojo(
		name = "gatf-executor", 
		aggregator = false, 
		executionStrategy = "always", 
		inheritByDefault = true, 
		instantiationStrategy = InstantiationStrategy.PER_LOOKUP, 
		defaultPhase = LifecyclePhase.INTEGRATION_TEST, 
		requiresDependencyResolution = ResolutionScope.TEST, 
		requiresDirectInvocation = false, 
		requiresOnline = false, 
		requiresProject = true, 
		threadSafe = true)
public class GatfTestCaseExecutorMojo extends AbstractMojo {

	@Component
    private MavenProject project;
	
	@Parameter(alias = "baseUrl")
	private String baseUrl;
	
	@Parameter(alias = "testCasesBasePath", defaultValue = "${project.build.testOutputDirectory}")
	private String testCasesBasePath;
	
	@Parameter(alias = "testCaseDir", defaultValue = "data")
	private String testCaseDir;
	
	@Parameter(alias = "outFilesBasePath", defaultValue = "${project.build.testOutputDirectory}")
	private String outFilesBasePath;
	
	@Parameter(alias = "outFilesDir", defaultValue = "out")
	private String outFilesDir;
	
	@Parameter(alias = "authEnabled")
	private boolean authEnabled;
	
	@Parameter(alias = "authUrl")
	private String authUrl;
	
	@Parameter(alias = "authExtractAuth")
	private String authExtractAuth;
	
	@Parameter(alias = "authParamsDetails")
	private String authParamsDetails;
	
	@Parameter(alias = "wsdlLocFile")
	private String wsdlLocFile;
	
	@Parameter(alias = "soapAuthEnabled")
	private boolean soapAuthEnabled;
	
	@Parameter(alias = "soapAuthWsdlKey")
	private String soapAuthWsdlKey;
	
	@Parameter(alias = "soapAuthOperation")
	private String soapAuthOperation;
	
	@Parameter(alias = "soapAuthExtractAuth")
	private String soapAuthExtractAuth;
	
	@Parameter(alias = "numConcurrentExecutions", defaultValue = "1")
	private Integer numConcurrentExecutions;
	
	@Parameter(alias = "configFile")
	private String configFile;
	
	@Parameter(alias = "httpCompressionEnabled", defaultValue = "true")
	private boolean httpCompressionEnabled;
	
	@Parameter(alias = "httpConnectionTimeout", defaultValue = "10000")
	private Integer httpConnectionTimeout;
	
	@Parameter(alias = "httpRequestTimeout", defaultValue = "10000")
	private Integer httpRequestTimeout;
	
	@Parameter(alias = "concurrentUserSimulationNum", defaultValue = "0")
	private Integer concurrentUserSimulationNum;
	
	@Parameter(alias = "testDataConfigFile")
	private String testDataConfigFile;
	
	@Parameter(alias = "simulationUsersProviderName")
	private String simulationUsersProviderName;
	
	@Parameter(alias = "compareEnabled")
	private boolean compareEnabled;
	
	@Parameter(alias = "testCaseHooksPaths")
    private String[] testCaseHooksPath;
	
	@Parameter(alias = "enabled", defaultValue = "true")
	private Boolean enabled;
	
	@Parameter(alias = "loadTestingEnabled")
	private boolean loadTestingEnabled;
	
	@Parameter(alias = "loadTestingTime")
	private Long loadTestingTime;
	
	@Parameter(alias = "gatfTestDataConfig")
	private GatfTestDataConfig gatfTestDataConfig;
	
	@Parameter(alias = "concurrentUserRampUpTime", defaultValue = "0")
	private Long concurrentUserRampUpTime;
	
	@Parameter(alias = "loadTestingReportSamples", defaultValue = "3")
	private Integer loadTestingReportSamples;
	
	@Parameter(alias = "debugEnabled", defaultValue = "false")
	private boolean debugEnabled;
	
	private Long startTime = 0L;
	
	private AcceptanceTestContext context;
	
	public void setProject(MavenProject project) {
		this.project = project;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public void setTestCasesBasePath(String testCasesBasePath) {
		this.testCasesBasePath = testCasesBasePath;
	}

	public void setTestCaseDir(String testCaseDir) {
		this.testCaseDir = testCaseDir;
	}

	public void setOutFilesBasePath(String outFilesBasePath) {
		this.outFilesBasePath = outFilesBasePath;
	}

	public void setOutFilesDir(String outFilesDir) {
		this.outFilesDir = outFilesDir;
	}

	public void setAuthEnabled(boolean authEnabled) {
		this.authEnabled = authEnabled;
	}

	public void setAuthUrl(String authUrl) {
		this.authUrl = authUrl;
	}

	public void setAuthExtractAuth(String authExtractAuth) {
		this.authExtractAuth = authExtractAuth;
	}

	public void setAuthParamsDetails(String authParamsDetails) {
		this.authParamsDetails = authParamsDetails;
	}

	public void setWsdlLocFile(String wsdlLocFile) {
		this.wsdlLocFile = wsdlLocFile;
	}

	public void setSoapAuthEnabled(boolean soapAuthEnabled) {
		this.soapAuthEnabled = soapAuthEnabled;
	}

	public void setSoapAuthWsdlKey(String soapAuthWsdlKey) {
		this.soapAuthWsdlKey = soapAuthWsdlKey;
	}

	public void setSoapAuthOperation(String soapAuthOperation) {
		this.soapAuthOperation = soapAuthOperation;
	}

	public void setSoapAuthExtractAuth(String soapAuthExtractAuth) {
		this.soapAuthExtractAuth = soapAuthExtractAuth;
	}

	public void setNumConcurrentExecutions(Integer numConcurrentExecutions) {
		this.numConcurrentExecutions = numConcurrentExecutions;
	}

	public void setConfigFile(String configFile) {
		this.configFile = configFile;
	}

	public void setHttpCompressionEnabled(boolean httpCompressionEnabled) {
		this.httpCompressionEnabled = httpCompressionEnabled;
	}

	public void setHttpConnectionTimeout(Integer httpConnectionTimeout) {
		this.httpConnectionTimeout = httpConnectionTimeout;
	}

	public void setHttpRequestTimeout(Integer httpRequestTimeout) {
		this.httpRequestTimeout = httpRequestTimeout;
	}

	public void setConcurrentUserSimulationNum(Integer concurrentUserSimulationNum) {
		this.concurrentUserSimulationNum = concurrentUserSimulationNum;
	}

	public void setTestDataConfigFile(String testDataConfigFile) {
		this.testDataConfigFile = testDataConfigFile;
	}

	public void setSimulationUsersProviderName(String simulationUsersProviderName) {
		this.simulationUsersProviderName = simulationUsersProviderName;
	}

	public void setCompareEnabled(boolean compareEnabled) {
		this.compareEnabled = compareEnabled;
	}

	public void setTestCaseHooksPath(String[] testCaseHooksPath) {
		this.testCaseHooksPath = testCaseHooksPath;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setLoadTestingEnabled(boolean loadTestingEnabled) {
		this.loadTestingEnabled = loadTestingEnabled;
	}

	public void setLoadTestingTime(Long loadTestingTime) {
		this.loadTestingTime = loadTestingTime;
	}

	public void setConcurrentUserRampUpTime(Long concurrentUserRampUpTime) {
		this.concurrentUserRampUpTime = concurrentUserRampUpTime;
	}

	public void setLoadTestingReportSamples(Integer loadTestingReportSamples) {
		this.loadTestingReportSamples = loadTestingReportSamples;
	}

	public void setStartTime(Long startTime) {
		this.startTime = startTime;
	}

	private void sortAndOrderTestCases(List<TestCase> allTestCases, GatfExecutorConfig configuration) {
		List<TestCase> pretestcases = new ArrayList<TestCase>();
		List<TestCase> posttestcases = new ArrayList<TestCase>();
		for (TestCase testCase : allTestCases) {
			if((testCase.getUrl()!=null && testCase.getUrl().equalsIgnoreCase(configuration.getAuthUrl())) 
					|| configuration.isSoapAuthTestCase(testCase)) {
				pretestcases.add(testCase);
			}
			testCase.setSimulationNumber(0);
		}
		for (TestCase testCase : allTestCases) {
			if((testCase.getUrl()!=null && !testCase.getUrl().equalsIgnoreCase(configuration.getAuthUrl()) 
					&& !configuration.isSoapAuthTestCase(testCase)) ||
					(testCase.getUrl()==null && testCase.isSoapBase())) {
				posttestcases.add(testCase);
			}
		}
		
		Collections.sort(posttestcases, new Comparator<TestCase>() {
			public int compare(TestCase o1, TestCase o2) {
				 return o1==null ?
				         (o2==null ? 0 : Integer.MIN_VALUE) :
				         (o2==null ? Integer.MAX_VALUE : new Integer(o1.getSequence()).compareTo(o2.getSequence()));
			}
		});
		
		allTestCases.clear();
		allTestCases.addAll(pretestcases);
		allTestCases.addAll(posttestcases);
		
		int sequence = 0;
		for (TestCase testCase : allTestCases) {
			if(testCase.getSequence()<=0)
				testCase.setSequence(sequence++);
			else
				sequence = testCase.getSequence()+1;
		}
	}
	
	private List<TestCase> getAllTestCases(AcceptanceTestContext context)
	{
		List<TestCase> allTestCases = new ArrayList<TestCase>();
		File testCaseDirectory = context.getResourceFile(context.getGatfExecutorConfig().getTestCaseDir());
		
		TestCaseFinder finder = new XMLTestCaseFinder();
		allTestCases.addAll(finder.findTestCases(testCaseDirectory, context));
		
		finder = new JSONTestCaseFinder();
		allTestCases.addAll(finder.findTestCases(testCaseDirectory, context));
		
		finder = new CSVTestCaseFinder();
		allTestCases.addAll(finder.findTestCases(testCaseDirectory, context));
		
		return allTestCases;
	}
	
	public List<TestCase> copyTestCases(List<TestCase> testCases, Integer runNumber, String baseUrl)
	{
		List<TestCase> allTestCases = new ArrayList<TestCase>();
		for (TestCase testCase : testCases) {
			TestCase testCaseCopy = new TestCase(testCase);
			testCaseCopy.setSimulationNumber(runNumber);
			if(baseUrl!=null) {
				testCaseCopy.setBaseUrl(baseUrl);
			}
			allTestCases.add(testCaseCopy);
		}
		return allTestCases;
	}
	
	public void execute() throws MojoFailureException {
		
		GatfExecutorConfig configuration = new GatfExecutorConfig();
		configuration.setAuthEnabled(authEnabled);
		configuration.setAuthExtractAuth(authExtractAuth);
		configuration.setAuthUrl(authUrl);
		configuration.setAuthParamsDetails(authParamsDetails);
		configuration.setBaseUrl(baseUrl);
		configuration.setNumConcurrentExecutions(numConcurrentExecutions);
		configuration.setOutFilesBasePath(outFilesBasePath);
		configuration.setOutFilesDir(outFilesDir);
		configuration.setSoapAuthEnabled(soapAuthEnabled);
		configuration.setSoapAuthExtractAuth(soapAuthExtractAuth);
		configuration.setSoapAuthOperation(soapAuthOperation);
		configuration.setSoapAuthWsdlKey(soapAuthWsdlKey);
		configuration.setTestCaseDir(testCaseDir);
		configuration.setTestCasesBasePath(testCasesBasePath);
		configuration.setWsdlLocFile(wsdlLocFile);
		configuration.setHttpCompressionEnabled(httpCompressionEnabled);
		configuration.setHttpConnectionTimeout(httpConnectionTimeout);
		configuration.setHttpRequestTimeout(httpRequestTimeout);
		configuration.setConcurrentUserSimulationNum(concurrentUserSimulationNum);
		configuration.setTestDataConfigFile(testDataConfigFile);
		configuration.setSimulationUsersProviderName(simulationUsersProviderName);
		configuration.setCompareEnabled(compareEnabled);
		configuration.setEnabled(enabled);
		configuration.setLoadTestingEnabled(loadTestingEnabled);
		configuration.setLoadTestingTime(loadTestingTime);
		configuration.setConcurrentUserRampUpTime(concurrentUserRampUpTime);
		configuration.setLoadTestingReportSamples(loadTestingReportSamples);
		configuration.setDebugEnabled(debugEnabled);
		configuration.setGatfTestDataConfig(gatfTestDataConfig);
		
		if(configFile!=null) {
			try {
				File resource = null;
				File basePath = new File(testCasesBasePath);
				resource = new File(basePath, configFile);
				if(resource.exists()) {
					XStream xstream = new XStream(new XppDriver());
					xstream.processAnnotations(new Class[]{GatfExecutorConfig.class,
							 GatfTestDataConfig.class, GatfTestDataProvider.class});
					xstream.alias("gatf-testdata-source", GatfTestDataSource.class);
					xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
					xstream.alias("gatf-testdata-source-hook", GatfTestDataSourceHook.class);
					xstream.alias("gatfTestDataConfig", GatfTestDataConfig.class);
					xstream.alias("args", String[].class);
					xstream.alias("arg", String.class);
					xstream.alias("testCaseHooksPaths", String[].class);
					xstream.alias("testCaseHooksPath", String.class);
					
					configuration = (GatfExecutorConfig)xstream.fromXML(resource);
					
					if(configuration.getTestCasesBasePath()==null)
						configuration.setTestCasesBasePath(testCasesBasePath);
					
					if(configuration.getOutFilesBasePath()==null)
						configuration.setOutFilesBasePath(outFilesBasePath);
					
					if(configuration.getTestCaseDir()==null)
						configuration.setTestCaseDir(testCaseDir);
					
					if(configuration.getNumConcurrentExecutions()==null)
						configuration.setNumConcurrentExecutions(numConcurrentExecutions);
					
					if(configuration.getHttpConnectionTimeout()==null)
						configuration.setHttpConnectionTimeout(httpConnectionTimeout);
					
					if(configuration.getHttpRequestTimeout()==null)
						configuration.setHttpRequestTimeout(httpRequestTimeout);
					
					if(!configuration.isHttpCompressionEnabled())
						configuration.setHttpCompressionEnabled(httpCompressionEnabled);
					
					if(configuration.getConcurrentUserSimulationNum()==null)
						configuration.setConcurrentUserSimulationNum(concurrentUserSimulationNum);
					
					if(configuration.getLoadTestingReportSamples()==null)
						configuration.setLoadTestingReportSamples(loadTestingReportSamples);
					
					if(configuration.getConcurrentUserRampUpTime()==null)
						configuration.setConcurrentUserRampUpTime(concurrentUserRampUpTime);
					
					if(configuration.isEnabled()==null)
						configuration.setEnabled(true);
				}
			} catch (Exception e) {
				throw new AssertionError(e);
			}
		}
		try {
			doExecute(configuration);
		} finally {
			//Fire all data source shutdown hooks
			if(context!=null) {
				context.shutdown();
			}
		}
	}
	
	public void doExecute(GatfExecutorConfig configuration) throws MojoFailureException {
		
		context = new AcceptanceTestContext(configuration, getClassLoader());
		try {
			context.validateAndInit();
			setupTestCaseHooks(context);
		} catch (Throwable e) {
			getLog().error(e);
			throw new MojoFailureException("Configuration is invalid", e);
		}
		
		final TestCaseExecutorUtil testCaseExecutorUtil = new TestCaseExecutorUtil(context);
		
		boolean compareEnabledOnlySingleTestCaseExec = configuration.isCompareEnabled() 
				&& configuration.getGatfTestDataConfig()!=null 
				&& configuration.getGatfTestDataConfig().getCompareEnvBaseUrls()!=null
				&& !configuration.getGatfTestDataConfig().getCompareEnvBaseUrls().isEmpty(); 
		
		Set<String> baseUrls = null;
		List<String> baseUrlList = null;
		if(compareEnabledOnlySingleTestCaseExec) {
			baseUrls = new LinkedHashSet<String>();
			baseUrls.add(configuration.getBaseUrl());
			baseUrls.addAll(configuration.getGatfTestDataConfig().getCompareEnvBaseUrls());
			
			if(baseUrls.size()==1)
			{
				baseUrls = null;
				compareEnabledOnlySingleTestCaseExec = false;
			}
			else
			{
				configuration.setCompareBaseUrlsNum(baseUrls.size());
				baseUrlList = new ArrayList<String>(baseUrls);
			}
		}
		
		List<TestCase> allTestCases = getAllTestCases(context);
		sortAndOrderTestCases(allTestCases, configuration);
		
		int numberOfRuns = 1;
		if(compareEnabledOnlySingleTestCaseExec)
		{
			numberOfRuns = baseUrls.size();
		}
		else if(configuration.getConcurrentUserSimulationNum()!=null 
				&& configuration.getConcurrentUserSimulationNum()>1)
		{
			numberOfRuns = configuration.getConcurrentUserSimulationNum();
		}
		
		context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
		
		//If load testing time is greater than 10sec only then run load tests..
		boolean isLoadTestingEnabled = !compareEnabledOnlySingleTestCaseExec 
				&& configuration.isLoadTestingEnabled() && configuration.getLoadTestingTime()>10000;
		
		int loadTstReportsCount = 0;
		
		ReportHandler reportHandler = new ReportHandler();
		
		TestSuiteStats loadStats = null;
		
		int loadTestRunNum = 1;
		
		long concurrentUserRampUpTimeMs = 0;
		if(configuration.getConcurrentUserSimulationNum()>1 && configuration.getConcurrentUserRampUpTime()>0)
			concurrentUserRampUpTimeMs = configuration.getConcurrentUserRampUpTime()
											/configuration.getConcurrentUserSimulationNum();
		
		int loadTestingReportSamplesNum = configuration.getLoadTestingReportSamples();
		if(loadTestingReportSamplesNum<3)
			loadTestingReportSamplesNum = 3;
		
		long reportSampleTimeMs = 0;
		if(isLoadTestingEnabled)
			reportSampleTimeMs = configuration.getLoadTestingTime()/(loadTestingReportSamplesNum-1);
		
		startTime = System.currentTimeMillis();
		
		ExecutorService threadPool = null;
		if(numberOfRuns>1) {
			threadPool = Executors.newFixedThreadPool(numberOfRuns);
		}
		
		while(true)
		{
			long suiteStartTime = isLoadTestingEnabled?System.currentTimeMillis():startTime;
			
			if(numberOfRuns>1)
			{
				List<Future<Void>> userSimulations = doConcurrentRunExecution(compareEnabledOnlySingleTestCaseExec, 
						numberOfRuns, allTestCases, baseUrlList, testCaseExecutorUtil, concurrentUserRampUpTimeMs,
						threadPool);
				
				concurrentUserRampUpTimeMs = 0;
				
				for (Future<Void> future : userSimulations) {
					try {
						future.get();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
			else
			{
				executeTestCases(allTestCases, testCaseExecutorUtil, compareEnabledOnlySingleTestCaseExec);
			}
			
			if(isLoadTestingEnabled) {
				long currentTime = System.currentTimeMillis();
				
				boolean done = (currentTime - startTime) > configuration.getLoadTestingTime();
				
				long elapsedFraction = (currentTime - startTime);
				
				boolean dorep = done || loadTstReportsCount==0;

				if(!dorep && elapsedFraction>=reportSampleTimeMs*loadTstReportsCount) 
				{
					dorep = true;
				}
				
				if(dorep) {
					TestSuiteStats stats = reportHandler.doReporting(context, suiteStartTime, currentTime+".html");
					if(loadStats==null) {
						loadStats = stats;
					} else {
						loadStats.updateStats(stats);
					}
					loadTstReportsCount ++;
					reportHandler.addToLoadTestResources(loadTestRunNum++, currentTime);
				} else {
					TestSuiteStats stats = reportHandler.doLoadTestReporting(context, suiteStartTime);
					loadStats.updateStats(stats);
				}
				
				context.getWorkflowContextHandler().initializeSuiteContext(numberOfRuns);
				
				if(done) {
					break;
				}
			} else {
				loadStats = reportHandler.doReporting(context, suiteStartTime, null);
				break;
			}
		}
		
		if(numberOfRuns>1) {
			threadPool.shutdown();
		}
		
		if(isLoadTestingEnabled) {
			reportHandler.doFinalLoadTestReport(loadStats, context);
		}
		
		if(loadStats!=null) {
			getLog().info(loadStats.show());
			if(loadStats.getFailedTestCount()>0) {
				throw new MojoFailureException(loadStats.getFailedTestCount() + " testcases have failed");
			}
		}
	}
	
	
	private List<Future<Void>> doConcurrentRunExecution(boolean compareEnabledOnlySingleTestCaseExec, Integer numberOfRuns,
			List<TestCase> allTestCases, List<String> baseUrlList, final TestCaseExecutorUtil testCaseExecutorUtil,
			Long concurrentUserRampUpTimeMs, ExecutorService threadPool)
	{
		final boolean onlySingleTestCaseExecl = compareEnabledOnlySingleTestCaseExec;
		
		List<Future<Void>> userSimulations = new ArrayList<Future<Void>>();
		for (int i = 0; i < numberOfRuns; i++) {
			List<TestCase> simTestCases = null;
			if(onlySingleTestCaseExecl)
			{
				simTestCases = copyTestCases(allTestCases, i+1, baseUrlList.get(i));
			}
			else
			{
				simTestCases = copyTestCases(allTestCases, i+1, null);
			}
			
			final List<TestCase> simTestCasesCopy = simTestCases;
			userSimulations.add(
					threadPool.submit(new Callable<Void>() {
						public Void call() throws Exception {
							executeTestCases(simTestCasesCopy, testCaseExecutorUtil, onlySingleTestCaseExecl);
							return null;
						}
					})
			);
			try {
				Thread.sleep(concurrentUserRampUpTimeMs);
			} catch (InterruptedException e) {
			}
		}
		return userSimulations;
	}
	
	private void executeTestCase(TestCase testCase, TestCaseExecutorUtil testCaseExecutorUtil, 
			boolean onlySingleTestCaseExec) throws Exception
	{
		AcceptanceTestContext context = testCaseExecutorUtil.getContext();
		
		List<Map<String, String>> sceanrios = testCase.getRepeatScenarios();
		if(!onlySingleTestCaseExec) {
			if(testCase.getRepeatScenarioProviderName()!=null) {
				List<Map<String, String>> sceanriosp = 
						context.getProviderTestDataMap().get(testCase.getRepeatScenarioProviderName());
				if(sceanriosp!=null) {
					if(sceanrios!=null) {
						sceanrios.addAll(sceanriosp);
					} else {
						sceanrios = sceanriosp;
					}
				} else {
					List<Map<String, String>> sceanriowk = context.getWorkflowContextHandler()
							.getSuiteWorkflowScnearioContextValues(testCase, testCase.getRepeatScenarioProviderName());
					if(sceanrios!=null) {
						sceanrios.addAll(sceanriowk);
					} else {
						sceanrios = sceanriowk;
					}
				}
			}
			testCase.setRepeatScenarios(sceanrios);
		} else {
			testCase.setRepeatScenarios(null);
			testCase.setNumberOfExecutions(1);
		}
		
		List<TestCaseReport> reports = null;
		if(testCase.getNumberOfExecutions()!=null && testCase.getNumberOfExecutions()>1)
		{
			reports = context.getPerformanceTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		else if(sceanrios==null || sceanrios.isEmpty())
		{
			reports = context.getSingleTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		else
		{
			reports = context.getScenarioTestCaseExecutor().execute(testCase, testCaseExecutorUtil);
		}
		
		if(reports!=null) {
			for (TestCaseReport testCaseReport : reports) {
				
				context.addTestCaseReport(testCaseReport);
				
				if(context.getGatfExecutorConfig().isDebugEnabled() && testCase.isDetailedLog())
				{
					getLog().info(testCaseReport.toString());
				}
				
				List<Method> postHook = context.getPrePostHook(testCase, false);
				if(postHook!=null) {
					try {
						TestCaseReport report = new TestCaseReport(testCaseReport);
						for (Method method : postHook) {
							method.invoke(null, new Object[]{report});
						}
						testCaseReport.setResponseContent(report.getResponseContent());
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
				
				if(testCase.getPostExecutionDataSourceHookName()!=null) {
					try {
						context.executeDataSourceHook(testCase.getPostExecutionDataSourceHookName());
					} catch (Throwable e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	
	private void executeTestCases(List<TestCase> allTestCases, TestCaseExecutorUtil testCaseExecutorUtil,
			boolean onlySingleTestCaseExec) {
		for (TestCase testCase : allTestCases) {
			try {
				
				getLog().info("Running acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
				if(testCase.isSkipTest())
				{
					getLog().info("Skipping acceptance test for " + testCase.getName()+"/"+testCase.getDescription());
					continue;
				}
				if(testCaseExecutorUtil.getContext().getGatfExecutorConfig().isDebugEnabled()
						&& testCase.isDetailedLog())
				{
					getLog().info(testCase.toString());
				}
				try {
					testCase.validate(testCaseExecutorUtil.getContext().getHttpHeaders());
				} catch (RuntimeException e) {
					getLog().error("Got exception while running acceptance test " + testCase.getName()+"/"+testCase.getDescription(), e);
					throw e;
				}
				
				executeTestCase(testCase, testCaseExecutorUtil, onlySingleTestCaseExec);
				
				getLog().info("Successfully ran acceptance test " + testCase.getName()+"/"+testCase.getDescription());
				getLog().info("============================================================\n\n\n");
				
			} catch (Exception e) {
				getLog().error(e);
			} catch (Error e) {
				getLog().error(e);
			}
		}
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
    private ClassLoader getClassLoader()
    {
        try
        {
            List classpathElements = project.getCompileClasspathElements();
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            URL[] urls = new URL[classpathElements.size()];
            for (int i = 0; i < classpathElements.size(); i++)
            {
                urls[i] = new File((String) classpathElements.get(i)).toURI().toURL();
            }
            return new URLClassLoader(urls, getClass().getClassLoader());
        }
        catch (Exception e)
        {
            getLog().error("Couldn't get the classloader.");
        }
        return getClass().getClassLoader();
    }
	
	@SuppressWarnings("rawtypes")
	public void setupTestCaseHooks(AcceptanceTestContext context)
    {
        Thread currentThread = Thread.currentThread();
        ClassLoader oldClassLoader = currentThread.getContextClassLoader();
        try
        {
            currentThread.setContextClassLoader(getClassLoader());
            getLog().info("Searching for pre/post testcase execution hooks....");
            List<Class> allClasses = new ArrayList<Class>();
            if (context.getGatfExecutorConfig().getTestCaseHooksPaths() != null)
            {
                for (String item : context.getGatfExecutorConfig().getTestCaseHooksPaths())
                {
                    if (item.endsWith(".*"))
                    {
                        List<Class> classes = ClassLoaderUtils.getClasses(item.substring(0, item.indexOf(".*")));
                        if (classes != null && classes.size() > 0)
                        {
                            allClasses.addAll(classes);
                            getLog().info("Adding pre/post testcase execution hook package " + item);
                        }
                        else
                        {
                            getLog().error("Error:pre/post testcase execution hook package not found - " + item);
                        }
                    }
                    else
                    {
                        try
                        {
                            allClasses.add(Thread.currentThread().getContextClassLoader().loadClass(item));
                            getLog().info("Adding pre/post testcase execution hook class " + item);
                        }
                        catch (Exception e)
                        {
                            getLog().error("Error:pre/post testcase execution hook class not found - " + item);
                        }
                    }
                }
                if(!allClasses.isEmpty())
                {
                	loadAllPrePostHooks(allClasses, context);
                }
            }
            else
            {
            	getLog().info("No pre/post testcase execution hooks found..");
            }
            
            getLog().info("Done scanning pre/post testcase execution hooks...");
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        finally
        {
            currentThread.setContextClassLoader(oldClassLoader);
        }
    }
	
	@SuppressWarnings("rawtypes")
    private void loadAllPrePostHooks(List<Class> classes, AcceptanceTestContext context)
    {
        try
        {
            for (Class claz : classes)
            {
                Method[] methods = claz.getMethods();
                for (Method method : methods)
                {
                    context.addTestCaseHooks(method);
                }
            }
        }
        catch (Exception e)
        {
            getLog().error(e);
        }
    }
	
	public static void main(String[] args) throws MojoFailureException {
		
		if(args.length>1 && args[0].equals("-executor") && !args[1].trim().isEmpty()) {
			GatfTestCaseExecutorMojo mojo = new GatfTestCaseExecutorMojo();
			mojo.setConfigFile(args[1]);
			mojo.setNumConcurrentExecutions(1);
			mojo.setHttpConnectionTimeout(10000);
			mojo.setHttpRequestTimeout(10000);
			mojo.setHttpCompressionEnabled(true);
			mojo.setNumConcurrentExecutions(1);
			mojo.setConcurrentUserSimulationNum(0);
			mojo.setTestCaseDir("data");
			mojo.setOutFilesDir("out");
			mojo.setLoadTestingReportSamples(3);
			mojo.setConcurrentUserRampUpTime(0L);
			mojo.setEnabled(true);
			mojo.execute();
		}
	}
}