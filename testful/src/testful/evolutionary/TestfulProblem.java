/*
 * TestFul - http://code.google.com/p/testful/
 * Copyright (C) 2010  Matteo Miraz
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

package testful.evolutionary;

import java.io.Serializable;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import jmetal.util.PseudoRandom;
import testful.coverage.CoverageInformation;
import testful.coverage.CoverageTestExecutor;
import testful.coverage.TrackerDatum;
import testful.coverage.behavior.AbstractorRegistry;
import testful.coverage.whiteBox.WhiteBoxAnalysisData;
import testful.model.Operation;
import testful.model.OptimalTestCreator;
import testful.model.ReferenceFactory;
import testful.model.Test;
import testful.model.TestCluster;
import testful.model.TestClusterBuilder;
import testful.model.TestCoverage;
import testful.model.TestSuite;
import testful.model.executor.TestExecutorInput;
import testful.runner.ClassType;
import testful.runner.DataFinderCaching;
import testful.runner.DataFinderImpl;
import testful.runner.Job;
import testful.runner.ObjectType;
import testful.runner.RemoteClassLoader;
import testful.runner.RunnerPool;
import testful.utils.ElementManager;

/**
 * Describe the problem being addressed.
 * @author matteo
 */
public class TestfulProblem implements Serializable {

	private static final long serialVersionUID = 519447614813889830L;

	private static final Logger logger = Logger.getLogger("testful.evolutionary");

	private final DataFinderCaching finder;
	private final TestCluster cluster;
	private final ReferenceFactory refFactory;
	private final ObjectType objectType;
	private final WhiteBoxAnalysisData whiteAnalysis;
	private final TrackerDatum[] data;
	private final boolean reloadClasses;

	/** Saves the tests with the best coverage (including fault coverage!) */
	private final OptimalTestCreator optimal = new OptimalTestCreator();

	/** cumulative number of invocations */
	private AtomicLong invTot = new AtomicLong(0);

	public TestfulProblem(IConfigEvolutionary config) throws ClassNotFoundException {
		try {
			reloadClasses = config.isReloadClasses();

			final ClassType classType = new ClassType(config);
			objectType = new ObjectType();
			final DataFinderImpl finderImpl = new DataFinderImpl(classType, objectType);

			whiteAnalysis = new WhiteBoxAnalysisData();
			classType.addClassData(whiteAnalysis);

			finder = new DataFinderCaching(finderImpl);
			RemoteClassLoader tcl = new RemoteClassLoader(finder);

			TestClusterBuilder clusterBuilder = new TestClusterBuilder(tcl, config);

			cluster = clusterBuilder.getTestCluster();
			objectType.addObject(cluster);

			if(config.isBehavioral()) objectType.addObject(new AbstractorRegistry(cluster, clusterBuilder.getXmlRegistry()));

			data = new TrackerDatum[0];

			refFactory = new ReferenceFactory(cluster, config.getNumVarCut(), config.getNumVar());

		} catch (RemoteException e) {
			// never happens
			logger.log(Level.WARNING, "Remote exception (should never happen): " + e.toString(), e);
			throw new ClassNotFoundException("Cannot contact the remote class loading facility", e);
		}
	}

	public TestCluster getCluster() {
		return cluster;
	}

	public ReferenceFactory getReferenceFactory() {
		return refFactory;
	}

	public DataFinderCaching getFinder() {
		return finder;
	}

	public boolean isReloadClasses() {
		return reloadClasses;
	}

	public TrackerDatum[] getData() {
		return data;
	}

	/**
	 * Returns the optimal test suite
	 * @return the optimal test suite
	 */
	public Collection<TestCoverage> getOptimalTests() {
		return optimal.get();
	}

	/**
	 * Returns the optimal test creator
	 * @return the optimal test creator
	 */
	public OptimalTestCreator getOptimal() {
		return optimal;
	}

	/**
	 * Updates the optimal test suite considering the new test
	 * @param testCoverage the new test to consider
	 */
	public void updateOptimal(TestCoverage testCoverage) {
		optimal.update(testCoverage);
	}

	public Future<ElementManager<String, CoverageInformation>> evaluate(Test test) {
		return evaluate(test, data);
	}

	public Future<ElementManager<String, CoverageInformation>> evaluate(Test test, TrackerDatum[] data) {
		if(data == null) data = this.data;

		invTot.addAndGet(test.getTest().length);

		Job<TestExecutorInput, ElementManager<String, CoverageInformation>, CoverageTestExecutor> ctx =
			CoverageTestExecutor.getContext(finder, test, reloadClasses, data);

		return RunnerPool.getRunnerPool().execute(ctx);
	}

	public long getNumberOfExecutedOperations() {
		return invTot.get();
	}

	public Test getTest(List<Operation> ops) {
		return new Test(cluster, refFactory, ops.toArray(new Operation[ops.size()]));
	}

	public WhiteBoxAnalysisData getWhiteAnalysis() {
		return whiteAnalysis;
	}

	private final TestSuite reserve = new TestSuite();

	/**
	 * Add tests to the reserve
	 * @param tests the tests to add
	 */
	public void addReserve(TestSuite tests) {
		if(tests == null) return;

		reserve.add(tests);
	}

	/**
	 * Add a test to the reserve
	 * @param test the test to add
	 */
	public void addReserve(TestCoverage test) {
		if(test == null) return;

		reserve.add(test);
	}

	public List<Operation> generateTest() {
		Operation[] ops = reserve.getBestTest();
		if (ops!=null) {
			ops = Operation.adapt(ops, cluster, refFactory);
			List<Operation> ret = new ArrayList<Operation>(ops.length);
			for (Operation o : ops) ret.add(o);
			return ret;
		}

		List<Operation> ret = new ArrayList<Operation>(10);

		for (int i = 0; i < 10; i++)
			ret.add(Operation.randomlyGenerate(cluster, refFactory, PseudoRandom.getMersenneTwisterFast()));

		return ret;
	}
}
