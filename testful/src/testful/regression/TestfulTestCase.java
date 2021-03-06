/*
 * TestFul - http://code.google.com/p/testful/
 * Copyright (C) 2010 Matteo Miraz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package testful.regression;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.TestCase;
import junit.framework.TestResult;
import junit.textui.TestRunner;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import testful.ConfigProject;
import testful.IConfigProject;
import testful.TestFul;
import testful.model.OperationResult;
import testful.model.TestReader;
import testful.model.executor.TestExecutor;
import testful.model.executor.TestExecutorInput;
import testful.runner.ClassType;
import testful.runner.DataFinder;
import testful.runner.DataFinderCaching;
import testful.runner.DataFinderImpl;
import testful.runner.Job;
import testful.runner.RunnerPool;

/**
 * Executes testful's binary tests in JUnit test cases
 * (without converting them, as the {@link JUnitTestGenerator} does).
 * @author matteo
 */
public class TestfulTestCase extends TestCase {

	static {
		TestFul.printHeader("JUnit Test Executor");
	}

	private static final Logger logger = Logger.getLogger("testful.regression");

	private final boolean enableAssertions;
	private final DataFinder finder;
	private final List<String> tests;

	public TestfulTestCase(Config config) throws ClassNotFoundException {
		super("testFul"); // the name of the method to run

		enableAssertions = !config.disableAssertions;

		try {
			finder = new DataFinderCaching(new DataFinderImpl(new ClassType(config)));
		} catch (RemoteException e) {
			// never happens
			logger.log(Level.WARNING, "Remote exception (should never happen): " + e.toString(), e);
			throw new ClassNotFoundException("Cannot contact the remote class loading facility", e);
		}

		tests = config.tests;
	}

	public static class FaultTestExecutor extends TestExecutor<Boolean> {

		/* (non-Javadoc)
		 * @see testful.runner.ExecutionManager#setup()
		 */
		@Override
		protected void setup() throws ClassNotFoundException { }

		/* (non-Javadoc)
		 * @see testful.runner.ExecutionManager#getResult()
		 */
		@Override
		protected Boolean getResult() {
			return faults > 0;
		}

	}

	/**
	 * Reads and executes the serialized test
	 */
	public void testFul() throws Exception {
		TestReader reader = new TestReader() {

			@Override
			protected void read(String fileName, testful.model.Test test) {

				logger.info("Executing " + test.getTest().length + " operations, read from " + fileName);

				if(enableAssertions) OperationResult.Verifier.insertOperationResultVerifier(test.getTest());
				else OperationResult.remove(test.getTest());

				Job<TestExecutorInput, Boolean, FaultTestExecutor> ctx =
						new Job<TestExecutorInput, Boolean, FaultTestExecutor>(
								FaultTestExecutor.class, finder, new TestExecutorInput(test, true));

				ctx.setReloadClasses(true);

				Future<Boolean> r = RunnerPool.getRunnerPool().execute(ctx);

				try {
					if(r.get()) fail("Test " + fileName + " failed");
				} catch (Exception e) {
					logger.log(Level.WARNING, "Exception during the execution of the test: " + e.getMessage(), e);
				}

			}

			@Override
			protected Logger getLogger() {
				return logger;
			}
		};

		reader.read(tests);
	}

	public static class Config extends ConfigProject implements IConfigProject.Args4j {

		@Option(required = false, name = "-disableAssertions", usage = "Do not use the recorded behavior to create assertions")
		public boolean disableAssertions;

		@Argument
		public List<String> tests = new ArrayList<String>();
	}

	public static void main(String[] args) throws ClassNotFoundException {

		Config config = new Config();
		TestFul.parseCommandLine(config, args, TestfulTestCase.class, "JUnit Test executor");

		TestRunner runner = new TestRunner();
		TestResult result = runner.doRun(new TestfulTestCase(config), false);

		System.exit(result.wasSuccessful() ? 0 : 1);
	}
}
