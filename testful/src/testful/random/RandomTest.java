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

package testful.random;

import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import testful.TestFul;
import testful.coverage.CoverageInformation;
import testful.coverage.CoverageTestExecutor;
import testful.coverage.TrackerDatum;
import testful.model.Operation;
import testful.model.OptimalTestCreator;
import testful.model.ReferenceFactory;
import testful.model.Test;
import testful.model.TestCluster;
import testful.model.TestCoverage;
import testful.model.TestSuite;
import testful.model.executor.TestExecutorInput;
import testful.runner.DataFinder;
import testful.runner.Job;
import testful.runner.RunnerPool;
import testful.utils.ElementManager;
import ec.util.MersenneTwisterFast;

public abstract class RandomTest {
	protected static Logger logger = Logger.getLogger("testful.random");

	protected long start, stop;
	private long numCall;

	protected final TestCluster cluster;
	protected final ReferenceFactory refFactory;

	private AtomicInteger testsDone = new AtomicInteger();

	protected final BlockingQueue<Entry<Operation[], Future<ElementManager<String, CoverageInformation>>>> tests = new LinkedBlockingQueue<Entry<Operation[], Future<ElementManager<String, CoverageInformation>>>>();
	private final OptimalTestCreator optimal;
	private final DataFinder finder;
	private final boolean reloadClasses;
	private final TrackerDatum[] data;

	protected final MersenneTwisterFast random;

	protected volatile boolean keepRunning = true;

	public RandomTest(DataFinder finder, boolean reloadClasses, TestCluster cluster, ReferenceFactory refFactory, long seed, TrackerDatum ... data) {
		optimal = new OptimalTestCreator();

		logger.config("RandomTest: initializing MersenneTwisterFast with seed " + seed);
		random = new MersenneTwisterFast(seed);

		this.cluster = cluster;
		this.refFactory = refFactory;

		this.finder = finder;
		this.reloadClasses = reloadClasses;
		this.data = data;
	}

	protected Future<ElementManager<String, CoverageInformation>> execute(Operation[] ops) {
		Job<TestExecutorInput, ElementManager<String, CoverageInformation>, CoverageTestExecutor> ctx = CoverageTestExecutor.getContext(finder, new Test(cluster, refFactory, ops), reloadClasses, data);
		return RunnerPool.getRunnerPool().execute(ctx);
	}

	protected abstract void work(long duration);

	public final void test(long duration) {
		startNotificationThread();

		work(duration);

		try {
			while(getRunningJobs() > 0)
				Thread.sleep(1000);
		} catch(InterruptedException e) {}

		keepRunning = false;
	}

	public ElementManager<String, CoverageInformation> getExecutionInformation() {
		return optimal.getCoverage();
	}

	public Iterable<TestCoverage> getOptimalTests() {
		return optimal.get();
	}

	public int getRunningJobs() {
		return tests.size();
	}

	private void startNotificationThread() {
		Thread t = new Thread(new Runnable() {

			@Override
			public void run() {
				while(keepRunning) {
					try {
						if(tests.isEmpty()) {
							Thread.sleep(1000);
						} else {
							Entry<Operation[], Future<ElementManager<String, CoverageInformation>>> entry = tests.take();
							ElementManager<String, CoverageInformation> cov = entry.getValue().get();
							testsDone.incrementAndGet();
							numCall += entry.getKey().length;

							final TestCoverage testCoverage = new TestCoverage(new Test(cluster, refFactory, entry.getKey()), cov);
							optimal.update(testCoverage);
						}
					} catch(InterruptedException e) {
						logger.log(Level.WARNING, "Interrupted: " + e.getMessage(), e);
						TestFul.debug(e);
					} catch(ExecutionException e) {
						logger.log(Level.WARNING, "Error during a test evaluation: " + e, e.getCause());
						TestFul.debug(e);
					}
				}
			}
		}, "futureWaiter");
		t.setDaemon(true);
		t.start();

		t = new Thread(new Runnable() {

			@Override
			public void run() {
				while(keepRunning) {
					try {
						TimeUnit.SECONDS.sleep(1);
					} catch(InterruptedException e) {
						return;
					}

					long now = System.currentTimeMillis();

					optimal.log(null, numCall, (now - start));

					if(logger.isLoggable(Level.INFO)) {
						StringBuilder sb = new StringBuilder();

						long remaining = (stop - now) / 1000;

						sb.append(String.format("%5.2f%% %d:%02d to go ",
								(100.0 * (now - start))/(stop-start),
								remaining / 60,
								remaining % 60
						));


						sb.append("Running ").append(getRunningJobs()).append(" jobs (").append(testsDone.get()).append(" done)\n");

						if(!optimal.getCoverage().isEmpty()) {
							sb.append("Coverage:\n");
							for(CoverageInformation info : optimal.getCoverage())
								sb.append("  ").append(info.getName()).append(": ").append(info.getQuality()).append("\n");
						}

						logger.info(sb.toString());
					}
				}
			}
		}, "notification");

		t.setDaemon(true);
		t.start();
	}

	/**
	 * @author Tudor
	 * @return The list of Tests(OpSequences) generated and selected by RT
	 * Note: This function is useful only after it processes something :)
	 */
	public TestSuite getResults() {
		TestSuite ret = new TestSuite();
		for (TestCoverage test : optimal.get())
			ret.add(test);

		return ret;
	}
}
