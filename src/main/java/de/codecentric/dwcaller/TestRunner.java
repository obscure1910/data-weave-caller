package de.codecentric.dwcaller;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Pattern;

import org.mule.weave.v2.runtime.BindingValue;
import org.mule.weave.v2.runtime.DataWeaveResult;
import org.mule.weave.v2.runtime.DataWeaveScript;
import org.mule.weave.v2.runtime.ScriptingBindings;

import de.codecentric.dwcaller.test.TestResult;
import de.codecentric.dwcaller.test.TestStatus;
import de.codecentric.dwcaller.test.TextReporter;
import de.codecentric.dwcaller.utils.SynchronizeUtil;
import de.codecentric.dwcaller.utils.WeaveRunner;
import de.codecentric.dwcaller.utils.WeaveRunnerBuilder;

/**
 * Run a single test or tests in a directory.
 */
public class TestRunner {

	public static void main(String[] args) throws IOException {
		long start = System.currentTimeMillis();
		TestResult result = run(args);
		System.out.print(TextReporter.test2report(result));
		long finish = System.currentTimeMillis();
		System.out.println("Time needed: " + (finish - start) + "ms");
		System.exit(result.isAllSuccess() ? 0 : 1);
	}

	/**
	 * Run all tests (no args) or specific tests.
	 * @param args Directories with tests and/or tests to run.
	 * @return All tests successful (or ignored)?
	 * @throws IOException
	 */
	public static TestResult run(String[] args) throws IOException {
		File srcMainResources = new File("src/main/resources");
		File srcTestResources = new File("src/test/resources");
		File srcTestDw = new File("src/test/dw");
		File targetSrc = new File("target/classes");
		File targetTest = new File("target/test-classes");
		String dwtestResources = System.getProperty("dwtestResources");
		if (dwtestResources == null) {
			System.setProperty("dwtestResources", srcTestResources.getAbsolutePath());
		}

		SynchronizeUtil syncher = new SynchronizeUtil();
		syncher.addToDoNotDeletePatterns(Pattern.compile(".*\\.class"));
		syncher.addToDoNotDeletePatterns(Pattern.compile(".*\\.xml"));
		syncher.addToDoNotDeletePatterns(Pattern.compile(".*\\.dwl"));
		syncher.syncFileOrDirectory(srcMainResources, targetSrc);
		syncher.syncFileOrDirectory(srcTestResources, targetTest);
		syncher.syncFileOrDirectory(srcTestDw, targetTest);
		syncher.deleteUnexpectedNodes(targetTest);
		syncher.deleteUnexpectedNodes(targetSrc);

		TestRunner runner = new TestRunner(args);
		TestResult result = runner.runTests();
		return result;
	}

	private String[] args;

	private TestResult runTests() {
		WeaveRunner weaveRunner = new WeaveRunnerBuilder() //
				.withIgnorePattern(Pattern.compile("data-weave-testing-framework.*", Pattern.DOTALL)) //
				.withPathDir(new File("src/main/resources")) //
				.withPathDir(new File("src/test/resources")) //
				.withClassPath() //
				.build();
		TestResult result;
		if (args.length == 0) {
			File root = new File("src/test/");
			result = runTest(root, weaveRunner);
		} else if (args.length == 1) {
			result = runTest(new File(args[0]), weaveRunner);
		} else { // more than one test (suite)
			result = new TestResult("all tests");
			for (String arg : args) {
				TestResult oneResult = runTest(new File(arg), weaveRunner);
				if (isAddableResult(oneResult)) {
					result.addTest(oneResult);
				}
			}
		}
		return result;
	}

	public TestRunner(String[] args) {
		this.args = args;
	}

	private TestResult runTest(File fileOrDirectory, WeaveRunner weaveRunner) {
		if (fileOrDirectory.isDirectory()) {
			return runTestsInDirectory(fileOrDirectory, weaveRunner);
		} else if (fileOrDirectory.isFile()) {
			return runTestsInFile(fileOrDirectory, weaveRunner);
		} else {
			TestResult test = new TestResult(fileOrDirectory.getName(), TestStatus.ERROR, "Neither File nor directory: " + fileOrDirectory);
			return test;
		}
	}

	private TestResult runTestsInDirectory(File fileOrDirectory, WeaveRunner weaveRunner) {
		TestResult result = new TestResult(fileOrDirectory.getName());
		File[] files = fileOrDirectory.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.isDirectory() || (file.isFile() && file.getName().endsWith(".dwl"));
			}
		});
		for (File f : files) {
			TestResult oneResult = runTest(f, weaveRunner);
			if (isAddableResult(oneResult)) {
				result.addTest(oneResult);
			}
		}
		return result;
	}

	private boolean isAddableResult(TestResult oneResult) {
		// There must be a status or there must be enclosed tests.
		return oneResult != null && (oneResult.getStatus() != null || oneResult.getTests().size() > 0);
	}

	@SuppressWarnings("unchecked")
	private TestResult runTestsInFile(File fileOrDirectory, WeaveRunner weaveRunner) {
		ScriptingBindings bindings = new ScriptingBindings();
		addPredefinedBindings(bindings);
		DataWeaveScript script = weaveRunner.compile(fileOrDirectory, bindings);
		DataWeaveResult result = weaveRunner.runScript(script, bindings, "application/java");
		Object content = result.getContent();
		return new TestResult((Map<String, Object>) content);
	}

	private void addPredefinedBindings(ScriptingBindings bindings) {
		// see https://docs.mulesoft.com/dataweave/2.4/dataweave-variables-context for the source of this list
		String[] predefined = { "app", "attributes", "authentication", "correlationId", "flow", "message", "mule", "payload", "server", "vars" };
		for (String p : predefined) {
			bindings.addBinding(p, BindingValue.apply(Collections.emptyMap(), "application/java"));
		}
	}
}
