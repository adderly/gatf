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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import javax.ws.rs.core.HttpHeaders;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Assert;
import org.reficio.ws.builder.SoapBuilder;
import org.reficio.ws.builder.SoapOperation;
import org.reficio.ws.builder.core.Wsdl;
import org.w3c.dom.Document;

import com.gatf.executor.dataprovider.DatabaseTestDataSource;
import com.gatf.executor.dataprovider.FileTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataConfig;
import com.gatf.executor.dataprovider.GatfTestDataProvider;
import com.gatf.executor.dataprovider.GatfTestDataSource;
import com.gatf.executor.dataprovider.GatfTestDataSourceHook;
import com.gatf.executor.dataprovider.InlineValueTestDataProvider;
import com.gatf.executor.dataprovider.MongoDBTestDataSource;
import com.gatf.executor.dataprovider.RandomValueTestDataProvider;
import com.gatf.executor.dataprovider.TestDataHook;
import com.gatf.executor.dataprovider.TestDataProvider;
import com.gatf.executor.dataprovider.TestDataSource;
import com.gatf.executor.executor.PerformanceTestCaseExecutor;
import com.gatf.executor.executor.ScenarioTestCaseExecutor;
import com.gatf.executor.executor.SingleTestCaseExecutor;
import com.gatf.executor.report.TestCaseReport;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.XppDriver;

/**
 * @author Sumeet Chhetri
 * The Executor context, holds context information for the execution of testcases and also holds
 * test case report information
 */
public class AcceptanceTestContext {

	private Logger logger = Logger.getLogger(AcceptanceTestContext.class.getSimpleName());
	
	public final static String
	    PROP_SOAP_ACTION_11 = "SOAPAction",
	    PROP_SOAP_ACTION_12 = "action=",
	    PROP_CONTENT_TYPE = "Content-Type",
	    PROP_CONTENT_LENGTH = "Content-Length",
	    PROP_AUTH = "Authorization",
	    PROP_PROXY_AUTH = "Proxy-Authorization",
	    PROP_PROXY_CONN = "Proxy-Connection",
	    PROP_KEEP_ALIVE = "Keep-Alive",
	    PROP_BASIC_AUTH = "Basic",
	    PROP_DELIMITER = "; ";
	
	public final static String
	    SOAP_1_1_NAMESPACE = "http://schemas.xmlsoap.org/soap/envelope/",
	    SOAP_1_2_NAMESPACE = "http://www.w3.org/2003/05/soap-envelope";
	
	public final static String
	    MIMETYPE_TEXT_HTML = "text/html",
	    MIMETYPE_TEXT_PLAIN = "text/plain",
	    MIMETYPE_TEXT_XML = "text/xml",
	    MIMETYPE_APPLICATION_XML = "application/soap+xml";
	
	private final Map<String, String> httpHeaders = new HashMap<String, String>();

	private ConcurrentHashMap<Integer, String> sessionIdentifiers = new ConcurrentHashMap<Integer, String>();
	
	private final Map<String, String> soapEndpoints = new HashMap<String, String>();
	
	private final Map<String, Document> soapMessages = new HashMap<String, Document>();
	
	private final Map<String, String> soapActions = new HashMap<String, String>();
	
	private final Map<String, ConcurrentLinkedQueue<TestCaseReport>> finalTestResults = 
			new ConcurrentHashMap<String, ConcurrentLinkedQueue<TestCaseReport>>();
	
	private final Map<String, Integer> finalTestReportsDups = new ConcurrentHashMap<String, Integer>();
	
	private final WorkflowContextHandler workflowContextHandler = new WorkflowContextHandler();
	
	private GatfExecutorConfig gatfExecutorConfig;
	
	private ClassLoader projectClassLoader;
	
	private Map<String, Method> prePostTestCaseExecHooks = new HashMap<String, Method>();
	
	public ClassLoader getProjectClassLoader() {
		return projectClassLoader;
	}

	private final Map<String, List<Map<String, String>>> providerTestDataMap = new HashMap<String, List<Map<String,String>>>();
	
	public Map<String, List<Map<String, String>>> getProviderTestDataMap() {
		return providerTestDataMap;
	}
	
	private final Map<String, TestDataSource> dataSourceMap = new HashMap<String, TestDataSource>();
	
	private final Map<String, GatfTestDataSourceHook> dataSourceHooksMap = new HashMap<String, GatfTestDataSourceHook>();

	private final SingleTestCaseExecutor singleTestCaseExecutor = new SingleTestCaseExecutor();
	
	private final ScenarioTestCaseExecutor scenarioTestCaseExecutor = new ScenarioTestCaseExecutor();
	
	private final PerformanceTestCaseExecutor performanceTestCaseExecutor = new PerformanceTestCaseExecutor();

	public SingleTestCaseExecutor getSingleTestCaseExecutor() {
		return singleTestCaseExecutor;
	}

	public ScenarioTestCaseExecutor getScenarioTestCaseExecutor() {
		return scenarioTestCaseExecutor;
	}

	public PerformanceTestCaseExecutor getPerformanceTestCaseExecutor() {
		return performanceTestCaseExecutor;
	}

	public AcceptanceTestContext(GatfExecutorConfig gatfExecutorConfig, ClassLoader projectClassLoader)
	{
		this.gatfExecutorConfig = gatfExecutorConfig;
		this.projectClassLoader = projectClassLoader;
		getWorkflowContextHandler().init();
	}
	
	public GatfExecutorConfig getGatfExecutorConfig() {
		return gatfExecutorConfig;
	}

	public void setGatfExecutorConfig(GatfExecutorConfig gatfExecutorConfig) {
		this.gatfExecutorConfig = gatfExecutorConfig;
	}

	public ConcurrentHashMap<Integer, String> getSessionIdentifiers() {
		return sessionIdentifiers;
	}

	public void setSessionIdentifiers(
			ConcurrentHashMap<Integer, String> sessionIdentifiers) {
		this.sessionIdentifiers = sessionIdentifiers;
	}

	public void setSessionIdentifier(String identifier, TestCase testCase) {
		Integer simulationNumber = testCase.getSimulationNumber();
		if(simulationNumber==null) {
			simulationNumber = 0;
		}
		sessionIdentifiers.put(simulationNumber, identifier);
	}
	
	public String getSessionIdentifier(TestCase testCase) {
		if(testCase.getSimulationNumber()!=null)
			return sessionIdentifiers.get(testCase.getSimulationNumber());
		return sessionIdentifiers.get(0);
	}
	
	public Map<String, String> getHttpHeaders() {
		return httpHeaders;
	}

	public Map<String, String> getSoapEndpoints() {
		return soapEndpoints;
	}

	public Map<String, Document> getSoapMessages() {
		return soapMessages;
	}

	public Map<String, String> getSoapActions() {
		return soapActions;
	}

	public Map<String, ConcurrentLinkedQueue<TestCaseReport>> getFinalTestResults() {
		return finalTestResults;
	}

	public WorkflowContextHandler getWorkflowContextHandler() {
		return workflowContextHandler;
	}
	
	public void addTestCaseHooks(Method method) {
		if(method!=null && Modifier.isStatic(method.getModifiers())) {
			
			Annotation preHook = method.getAnnotation(PreTestCaseExecutionHook.class);
            Annotation postHook = method.getAnnotation(PostTestCaseExecutionHook.class);
			
			if(preHook!=null)
			{
				PreTestCaseExecutionHook hook = (PreTestCaseExecutionHook)preHook;
				
				if(method.getParameterTypes().length!=1 || !method.getParameterTypes()[0].equals(TestCase.class))
				{
					logger.severe("PreTestCaseExecutionHook annotated methods should " +
							"confirm to the method signature - `public static void {methodName} (" +
							"TestCase testCase)`");
					return;
				}
				
				if(hook.value()!=null && hook.value().length>0)
				{
					for (String testCaseName : hook.value()) {
						if(testCaseName!=null && !testCaseName.trim().isEmpty())  {
							prePostTestCaseExecHooks.put("pre"+testCaseName, method);
						}
					}
				}
				else
				{
					prePostTestCaseExecHooks.put("preAll", method);
				}
			}
			if(postHook!=null)
			{
				PostTestCaseExecutionHook hook = (PostTestCaseExecutionHook)postHook;
				
				if(method.getParameterTypes().length!=1 || !method.getParameterTypes()[0].equals(TestCaseReport.class))
				{
					logger.severe("PostTestCaseExecutionHook annotated methods should " +
							"confirm to the method signature - `public static void {methodName} (" +
							"TestCaseReport testCaseReport)`");
					return;
				}
				
				if(hook.value()!=null && hook.value().length>0)
				{
					for (String testCaseName : hook.value()) {
						if(testCaseName!=null && !testCaseName.trim().isEmpty())  {
							prePostTestCaseExecHooks.put("post"+testCaseName, method);
						}
					}
				}
				else
				{
					prePostTestCaseExecHooks.put("postAll", method);
				}
			}
		}
	}
	
	public List<Method> getPrePostHook(TestCase testCase, boolean isPreHook) {
		
		List<Method> methods = new ArrayList<Method>();
		String hookKey = (isPreHook?"pre":"post") + testCase.getName();
		Method meth = prePostTestCaseExecHooks.get(hookKey);
		if(meth!=null)
			methods.add(meth);
		meth = prePostTestCaseExecHooks.get((isPreHook?"preAll":"postAll"));
		if(meth!=null)
			methods.add(meth);
		return methods;
	}
	
	public File getResourceFile(String filename) {
		try {
			if(gatfExecutorConfig.getTestCasesBasePath()!=null)
			{
				File basePath = new File(gatfExecutorConfig.getTestCasesBasePath());
				File resource = new File(basePath, filename);
				return resource;
			}
			else
			{
				URL url = Thread.currentThread().getContextClassLoader().getResource(filename);
				File resource = new File(url.getPath());
				return resource;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	public File getNewOutResourceFile(String filename) {
		try {
			if(gatfExecutorConfig.getOutFilesBasePath()!=null)
			{
				File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
				File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
				File file = new File(resource, filename);
				if(!file.exists())
					file.createNewFile();
				return file;
			}
			else
			{
				URL url = Thread.currentThread().getContextClassLoader().getResource(gatfExecutorConfig.getOutFilesDir());
				File resource = new File(url.getPath());
				File file = new File(resource, filename);
				if(!file.exists())
					file.createNewFile();
				return file;
			}
		} catch (Exception e) {
			return null;
		}
	}
	
	public static void removeFolder(File folder)
	{
		if(folder==null || !folder.exists())return;
		String[] entries = folder.list();
		for(String s: entries){
		    File currentFile = new File(folder.getPath(), s);
		    currentFile.delete();
		}
		folder.delete();
	}
	
	public void validateAndInit() throws Exception
	{
		gatfExecutorConfig.validate();
		
		Assert.assertEquals("Testcase directory not found...", getResourceFile(gatfExecutorConfig.getTestCaseDir()).exists(), true);
		
		if(gatfExecutorConfig.getOutFilesDir()!=null && !gatfExecutorConfig.getOutFilesDir().trim().isEmpty())
		{
			try
			{
				if(gatfExecutorConfig.getOutFilesBasePath()!=null)
				{
					File basePath = new File(gatfExecutorConfig.getOutFilesBasePath());
					File resource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
					removeFolder(resource);
					File nresource = new File(basePath, gatfExecutorConfig.getOutFilesDir());
					nresource.mkdirs();
				}
				else
				{
					URL url = Thread.currentThread().getContextClassLoader().getResource(".");
					File resource = new File(url.getPath());
					File file = new File(resource, gatfExecutorConfig.getOutFilesDir());
					removeFolder(file);
					File nresource = new File(resource, gatfExecutorConfig.getOutFilesDir());
					nresource.mkdirs();
				}
			} catch (Exception e) {
				gatfExecutorConfig.setOutFilesDir(null);
			}
			Assert.assertNotNull("Testcase out file directory not found...", gatfExecutorConfig.getOutFilesDir());
		}
		else
		{
			URL url = Thread.currentThread().getContextClassLoader().getResource("out");
			File resource = new File(url.getPath());
			removeFolder(resource);
			File nresource = new File(url.getPath());
			nresource.mkdir();
			gatfExecutorConfig.setOutFilesDir("out");
			gatfExecutorConfig.setOutFilesBasePath(gatfExecutorConfig.getTestCasesBasePath());
		}
		
		initSoapContextAndHttpHeaders();
		
		initTestDataProviderAndGlobalVariables();
	}
	
	private void initTestDataProviderAndGlobalVariables() {
		GatfTestDataConfig gatfTestDataConfig = null;
		if(gatfExecutorConfig.getTestDataConfigFile()!=null) {
			File file = getResourceFile(gatfExecutorConfig.getTestDataConfigFile());
			Assert.assertNotNull("Testdata configuration file not found...", file);
			Assert.assertEquals("Testdata configuration file not found...", file.exists(), true);
			
			XStream xstream = new XStream(new XppDriver());
			xstream.processAnnotations(new Class[]{GatfTestDataConfig.class, GatfTestDataProvider.class});
			xstream.alias("gatf-testdata-provider", GatfTestDataProvider.class);
			xstream.alias("args", String[].class);
			xstream.alias("arg", String.class);
			
			gatfTestDataConfig = (GatfTestDataConfig)xstream.fromXML(file);
			gatfExecutorConfig.setGatfTestDataConfig(gatfTestDataConfig);
		} else {
			gatfTestDataConfig = gatfExecutorConfig.getGatfTestDataConfig();
		}
		
		if(gatfTestDataConfig!=null) {
			getWorkflowContextHandler().addGlobalVariables(gatfTestDataConfig.getGlobalVariables());
			
			if(gatfTestDataConfig.getDataSourceList()!=null)
			{
				handleDataSources(gatfTestDataConfig.getDataSourceList());
			}
			
			if(gatfTestDataConfig.getDataSourceHooks()!=null)
			{
				handleHooks(gatfTestDataConfig.getDataSourceHooks());
			}
				
			if(gatfTestDataConfig.getProviderTestDataList()!=null)
			{
				handleProviders(gatfTestDataConfig.getProviderTestDataList());
			}
		}
	}

	private void initSoapContextAndHttpHeaders() throws Exception
	{
		Field[] declaredFields = HttpHeaders.class.getDeclaredFields();
		for (Field field : declaredFields) {
			if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
					&& field.getType().equals(String.class)) {
				httpHeaders.put(field.get(null).toString().toLowerCase(), field.get(null).toString());
			}
		}
		
		File file = null;
		if(gatfExecutorConfig.getWsdlLocFile()!=null && !gatfExecutorConfig.getWsdlLocFile().trim().isEmpty())
			file = getResourceFile(gatfExecutorConfig.getWsdlLocFile());
		
		if(file!=null)
		{
			Scanner s = new Scanner(file);
			s.useDelimiter("\n");
			List<String> list = new ArrayList<String>();
			while (s.hasNext()) {
				list.add(s.next().replace("\r", ""));
			}
			s.close();
	
			for (String wsdlLoc : list) {
				if(!wsdlLoc.trim().isEmpty())
				{
					String[] wsdlLocParts = wsdlLoc.split(",");
					logger.info("Started Parsing WSDL location - " + wsdlLocParts[1]);
					Wsdl wsdl = Wsdl.parse(wsdlLocParts[1]);
					for (QName bindingName : wsdl.getBindings()) {
						SoapBuilder builder = wsdl.getBuilder(bindingName);
						for (SoapOperation operation : builder.getOperations()) {
							String request = builder.buildInputMessage(operation);
							DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
							DocumentBuilder db = dbf.newDocumentBuilder();
							Document soapMessage = db.parse(new ByteArrayInputStream(request.getBytes()));
							soapMessages.put(wsdlLocParts[0]+operation.getOperationName(), soapMessage);
							if(operation.getSoapAction()!=null) {
								soapActions.put(wsdlLocParts[0]+operation.getOperationName(), operation.getSoapAction());
							}
							logger.info("Adding message for SOAP operation - " + operation.getOperationName());
						}
						soapEndpoints.put(wsdlLocParts[0], builder.getServiceUrls().get(0));
						logger.info("Adding SOAP Service endpoint - " + builder.getServiceUrls().get(0));
					}
					logger.info("Done Parsing WSDL location - " + wsdlLocParts[1]);
				}
			}
		}
	}
	
	public void addTestCaseReport(TestCaseReport testCaseReport) {
		String key = testCaseReport.getTestCase().getIdentifier() + testCaseReport.getTestCase().getName();
		getFinalTestResults().get(testCaseReport.getTestCase().getIdentifier()).add(testCaseReport);
		if(!finalTestReportsDups.containsKey(key)) {
			finalTestReportsDups.put(key, 1);
		} else {
			Integer ncount = finalTestReportsDups.get(key) + 1;
			String ext = "-" + ncount;
			finalTestReportsDups.put(key, ncount);
			testCaseReport.setTestIdentifier(testCaseReport.getTestIdentifier()+ext);
		}
	}
	
	public void clearTestResults() {
		
		for (Map.Entry<String, ConcurrentLinkedQueue<TestCaseReport>> entry :  finalTestResults.entrySet()) {
			entry.getValue().clear();
		}
		finalTestReportsDups.clear();
	}

	public void shutdown() {
		for (GatfTestDataSourceHook dataSourceHook : dataSourceHooksMap.values()) {
			TestDataSource dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
			
			Assert.assertNotNull("No DataSource found", dataSource);
			
			if(dataSourceHook.isExecuteOnShutdown()) {
				try {
					boolean flag = dataSource.execute(dataSourceHook.getQueryStr());
					if(!flag) {
						Assert.assertNotNull("DataSourceHook execution for " + dataSourceHook.getHookName()
								+ " failed...", null);
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
		
		for (GatfTestDataSourceHook dataSourceHook : dataSourceHooksMap.values()) {
			TestDataSource dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
			
			Assert.assertNotNull("No DataSource found", dataSource);
			
			dataSource.destroy();
		}
	}
	
	public void executeDataSourceHook(String hookName) {
		GatfTestDataSourceHook dataSourceHook = dataSourceHooksMap.get(hookName);
		Assert.assertNotNull("No DataSourceHook found", dataSourceHook);
		
		TestDataSource dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
		Assert.assertNull("No DataSource found", dataSource);
		
		boolean flag = dataSource.execute(dataSourceHook.getQueryStr());
		if(!flag) {
			Assert.assertNotNull("DataSourceHook execution for " + dataSourceHook.getHookName()
					+ " failed...", null);
		}
	}
	
	@SuppressWarnings("rawtypes")
	private void handleDataSources(List<GatfTestDataSource> dataSourceList)
	{
		for (GatfTestDataSource dataSource : dataSourceList) {
			Assert.assertNotNull("DataSource name is not defined", dataSource.getDataSourceName());
			Assert.assertNotNull("DataSource class is not defined", dataSource.getDataSourceClass());
			Assert.assertNotNull("DataSource args not defined", dataSource.getArgs());
			Assert.assertTrue("DataSource args empty", dataSource.getArgs().length>0);
			Assert.assertNull("Duplicate DataSource name found", dataSourceMap.get(dataSource.getDataSourceName()));
			
			TestDataSource testDataSource = null;
			if(DatabaseTestDataSource.class.getCanonicalName().equals(dataSource.getDataSourceClass())) {
				testDataSource = new DatabaseTestDataSource();
			} else if(MongoDBTestDataSource.class.getCanonicalName().equals(dataSource.getDataSourceClass())) {
				testDataSource = new MongoDBTestDataSource();
			} else {
				try {
					Class claz = getProjectClassLoader().loadClass(dataSource.getDataSourceClass());
					Class[] classes = claz.getInterfaces();
					boolean validProvider = false;
					if(classes!=null) {
						for (Class class1 : classes) {
							if(class1.equals(TestDataSource.class)) {
								validProvider = true;
								break;
							}
						}
					}
					Assert.assertTrue("DataSource class should extend the TestDataSource class", validProvider);
					Object providerInstance = claz.newInstance();
					testDataSource = (TestDataSource)providerInstance;
				} catch (Throwable e) {
					throw new AssertionError(e);
				}
			}
			
			testDataSource.setArgs(dataSource.getArgs());
			testDataSource.setContext(this);
			testDataSource.setDataSourceName(dataSource.getDataSourceName());
			if(dataSource.getPoolSize()>1)
			{
				testDataSource.setPoolSize(dataSource.getPoolSize());
			}
			
			testDataSource.init();
			
			dataSourceMap.put(dataSource.getDataSourceName(), testDataSource);
		}
	}
	
	@SuppressWarnings("rawtypes")
	private void handleHooks(List<GatfTestDataSourceHook> dataSourceHooks)
	{
		for (GatfTestDataSourceHook dataSourceHook : dataSourceHooks) {
			Assert.assertNotNull("DataSourceHook name is not defined", dataSourceHook.getHookName());
			Assert.assertNotNull("DataSourceHook query string is not defined", dataSourceHook.getQueryStr());
			Assert.assertNull("Duplicate DataSourceHook name found", dataSourceHooksMap.get(dataSourceHook.getHookName()));
			
			TestDataSource dataSource = null;
			if(dataSourceHook.getDataSourceName()!=null && dataSourceHook.getHookClass()==null)
			{
				dataSource = dataSourceMap.get(dataSourceHook.getDataSourceName());
				Assert.assertNotNull("No DataSource found", dataSource);
			}
			else if(dataSourceHook.getDataSourceName()!=null && dataSourceHook.getHookClass()!=null)
			{
				Assert.assertNotNull("Specify either hookClass or dataSourceName", null);
			}
			else if(dataSourceHook.getDataSourceName()==null && dataSourceHook.getHookClass()==null)
			{
				Assert.assertNotNull("Specify any one of hookClass or dataSourceName", null);
			}
			
			dataSourceHooksMap.put(dataSourceHook.getHookName(), dataSourceHook);
			
			TestDataHook testDataHook = null;
			if(dataSourceHook.getHookClass()!=null) {
				try {
					Class claz = getProjectClassLoader().loadClass(dataSourceHook.getHookClass());
					Class[] classes = claz.getInterfaces();
					boolean validProvider = false;
					if(classes!=null) {
						for (Class class1 : classes) {
							if(class1.equals(TestDataProvider.class)) {
								validProvider = true;
								break;
							}
						}
					}
					Assert.assertTrue("Hook class should implement the TestDataHook class", validProvider);
					Object providerInstance = claz.newInstance();
					testDataHook = (TestDataHook)providerInstance;
				} catch (Throwable e) {
					throw new AssertionError(e);
				}
			} else {
				testDataHook = dataSource;
			}
			
			if(dataSourceHook.isExecuteOnStart()) {
				try {
					boolean flag = testDataHook.execute(dataSourceHook.getQueryStr());
					if(!flag) {
						Assert.assertNotNull("DataSourceHook execution for " + dataSourceHook.getHookName()
								+ " failed...", null);
					}
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	@SuppressWarnings("rawtypes")
	private void handleProviders(List<GatfTestDataProvider> providerTestDataList)
	{
		for (GatfTestDataProvider provider : providerTestDataList) {
			Assert.assertNotNull("Provider name is not defined", provider.getProviderName());
			
			Assert.assertNotNull("Provider properties is not defined", provider.getProviderProperties());
			
			Assert.assertNull("Duplicate Provider name found", providerTestDataMap.get(provider.getProviderName()));
			
			TestDataSource dataSource = null;
			if(provider.getDataSourceName()!=null && provider.getProviderClass()==null)
			{	
				Assert.assertNotNull("Provider DataSource name is not defined", provider.getDataSourceName());
				dataSource = dataSourceMap.get(provider.getDataSourceName());
				
				Assert.assertNotNull("No DataSource found", dataSource);
				
				if(dataSource instanceof MongoDBTestDataSource)
				{
					Assert.assertNotNull("Provider query string is not defined", provider.getQueryStr());
					Assert.assertNotNull("Provider source properties not defined", provider.getSourceProperties());
				}
				
				if(dataSource instanceof DatabaseTestDataSource)
				{
					Assert.assertNotNull("Provider query string is not defined", provider.getQueryStr());
				}
			}
			else if(provider.getDataSourceName()!=null && provider.getProviderClass()!=null)
			{
				Assert.assertNotNull("Specify either providerClass or dataSourceName", null);
			}
			else if(provider.getDataSourceName()==null && provider.getProviderClass()==null)
			{
				Assert.assertNotNull("Specify any one of providerClass or dataSourceName", null);
			}
			
			
			if(provider.isEnabled()==null) {
				provider.setEnabled(true);
			}
			
			if(!provider.isEnabled()) {
				logger.info("Provider " + provider.getProviderName() + " is Disabled...");
				continue;
			}
			
			TestDataProvider testDataProvider = null;
			List<Map<String, String>> testData = null;
			if(provider.getProviderClass()!=null) {
				if(FileTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass())) {
					testDataProvider = new FileTestDataProvider();
				} else if(InlineValueTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass())) {
					testDataProvider = new InlineValueTestDataProvider();
				} else if(RandomValueTestDataProvider.class.getCanonicalName().equals(provider.getProviderClass())) {
					testDataProvider = new RandomValueTestDataProvider();
				} else {
					try {
						Class claz = getProjectClassLoader().loadClass(provider.getProviderClass());
						Class[] classes = claz.getInterfaces();
						boolean validProvider = false;
						if(classes!=null) {
							for (Class class1 : classes) {
								if(class1.equals(TestDataProvider.class)) {
									validProvider = true;
									break;
								}
							}
						}
						Assert.assertTrue("Provider class should implement the TestDataProvider class", validProvider);
						Object providerInstance = claz.newInstance();
						testDataProvider = (TestDataProvider)providerInstance;
					} catch (Throwable e) {
						throw new AssertionError(e);
					}
				}
			} else {
				testDataProvider = dataSource;
			}
			
			testData = testDataProvider.provide(provider, this);
			providerTestDataMap.put(provider.getProviderName(), testData);
		}
	}
}
