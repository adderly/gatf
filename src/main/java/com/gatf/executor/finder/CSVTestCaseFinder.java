package com.gatf.executor.finder;

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
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.gatf.executor.core.AcceptanceTestContext;
import com.gatf.executor.core.TestCase;
import com.gatf.executor.report.TestCaseReport;

/**
 * @author Sumeet Chhetri
 * Finds all test cases from the csv files inside a given test case directory
 */
public class CSVTestCaseFinder implements TestCaseFinder {

	public List<TestCase> findTestCases(File dir, AcceptanceTestContext context)
	{
		List<TestCase> testcases = new ArrayList<TestCase>();
		if (dir.isDirectory()) {
			File[] csvFiles = dir.listFiles(new FilenameFilter() {
				public boolean accept(File folder, String name) {
					return name.toLowerCase().endsWith(".csv");
				}
			});

			for (File file : csvFiles) {
				try {
					Scanner s = new Scanner(file);
					s.useDelimiter("\n");
					List<String> list = new ArrayList<String>();
					while (s.hasNext()) {
						String csvLine = s.next().replace("\r", "");
						if(!csvLine.trim().isEmpty() && !csvLine.trim().startsWith("//")) {
							list.add(csvLine);
						}
					}
					s.close();
	
					if(!list.isEmpty())
					{
						for (String csvLine : list) {
							TestCase testCase = new TestCase(csvLine);
							testCase.setSourcefileName(file.getName());
							if(testCase.getSimulationNumber()==null)
							{
								testCase.setSimulationNumber(0);
							}
							testCase.setBaseUrl(context.getGatfExecutorConfig().getBaseUrl());
							testcases.add(testCase);
						}
						
						Integer runNums = context.getGatfExecutorConfig().getConcurrentUserSimulationNum();
						if(context.getGatfExecutorConfig().getCompareBaseUrlsNum()!=null)
						{
							runNums = context.getGatfExecutorConfig().getCompareBaseUrlsNum();
						}
						
						if(runNums!=null && runNums>1)
						{
							for (int i = 0; i < runNums; i++)
							{
								context.getFinalTestResults().put("Run-" + (i+1), new ConcurrentLinkedQueue<TestCaseReport>());
							}
						}
						else
						{
							context.getFinalTestResults().put(file.getName(), new ConcurrentLinkedQueue<TestCaseReport>());
						}
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return testcases;
	}
}
