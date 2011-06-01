/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */

package org.pitest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.pitest.extension.Configuration;
import org.pitest.extension.Container;
import org.pitest.extension.GroupingStrategy;
import org.pitest.extension.ResultSource;
import org.pitest.extension.StaticConfiguration;
import org.pitest.extension.TestDiscoveryListener;
import org.pitest.extension.TestFilter;
import org.pitest.extension.TestListener;
import org.pitest.extension.TestUnit;
import org.pitest.extension.common.CompoundTestDiscoveryListener;
import org.pitest.extension.common.CompoundTestFilter;
import org.pitest.functional.F;
import org.pitest.functional.F2;
import org.pitest.functional.FCollection;
import org.pitest.functional.Option;
import org.pitest.functional.SideEffect1;
import org.pitest.internal.ContainerParser;
import org.pitest.internal.TestClass;
import org.pitest.util.Log;

public class Pitest {

  private final static Logger       LOG = Log.getLogger();

  private final Configuration       initialConfig;
  private final StaticConfiguration initialStaticConfig;

  public Pitest(final StaticConfiguration initialStaticConfig,
      final Configuration initialConfig) {
    this.initialConfig = new ConcreteConfiguration(initialConfig);
    this.initialStaticConfig = initialStaticConfig;
  }

  public void run(final Container defaultContainer, final Class<?>... classes) {
    run(defaultContainer, Arrays.asList(classes));
  }

  public void run(final Container defaultContainer,
      final F2<Class<?>, Container, Container> containerUpdateFunction,
      final Class<?>... classes) {
    run(defaultContainer, containerUpdateFunction, Arrays.asList(classes));
  }

  public void run(final Container defaultContainer,
      final Collection<Class<?>> classes) {
    final F2<Class<?>, Container, Container> containerUpdateFunction = new F2<Class<?>, Container, Container>() {

      public Container apply(final Class<?> c, final Container defaultContainer) {
        return new ContainerParser(c).create(defaultContainer);
      }

    };
    run(defaultContainer, containerUpdateFunction, classes);
  }

  public void run(final Container defaultContainer,
      final F2<Class<?>, Container, Container> containerUpdateFunction,
      final Collection<Class<?>> classes) {
    for (final Class<?> c : classes) {

      final Container container = containerUpdateFunction.apply(c,
          defaultContainer);

      final StaticConfiguration staticConfig = this.initialConfig
          .staticConfigurationUpdater().apply(this.initialStaticConfig, c);

      final Option<TestFilter> filter = createTestFilter(staticConfig);

      run(container,
          staticConfig,
          findTestUnitsForAllSuppliedClasses(
              this.initialConfig,
              new CompoundTestDiscoveryListener(staticConfig
                  .getDiscoveryListeners()),
              staticConfig.getGroupingStrategy(), filter, c));
    }
  }

  private Option<TestFilter> createTestFilter(
      final StaticConfiguration staticConfig) {
    if (staticConfig.getTestFilters().isEmpty()) {
      return Option.none();
    } else {
      return Option.<TestFilter> some(new CompoundTestFilter(staticConfig
          .getTestFilters()));
    }
  }

  public void run(final Container container,
      final List<? extends TestUnit> testUnits) {
    this.run(container, new DefaultStaticConfig(this.initialStaticConfig),
        testUnits);
  }

  private void run(final Container container,
      final StaticConfiguration staticConfig,
      final List<? extends TestUnit> testUnits) {

    final List<? extends TestUnit> orderedTestUnits = staticConfig
        .getOrderStrategy().order(testUnits);

    LOG.info("Running " + orderedTestUnits.size() + " units");

    signalRunStartToAllListeners(staticConfig);

    final Thread feederThread = startFeederThread(container, orderedTestUnits);

    processResultsFromQueue(container, feederThread, staticConfig);
  }

  private void signalRunStartToAllListeners(
      final StaticConfiguration staticConfig) {
    FCollection.forEach(staticConfig.getTestListeners(),
        new SideEffect1<TestListener>() {
          public void apply(final TestListener a) {
            a.onRunStart();
          }
        });
  }

  public static List<TestUnit> findTestUnitsForAllSuppliedClasses(
      final Configuration startConfig, final TestDiscoveryListener listener,
      final GroupingStrategy groupStrategy,
      final Option<TestFilter> testFilter, final Class<?>... classes) {
    final List<TestUnit> testUnits = new ArrayList<TestUnit>();

    for (final Class<?> c : classes) {
      final Collection<TestUnit> testUnitsFromClass = new TestClass(c)
          .getTestUnits(startConfig, listener, groupStrategy);
      testUnits.addAll(testUnitsFromClass);
    }

    if (testFilter.hasSome()) {
      return applyTestFilter(testFilter.value(), testUnits);
    } else {
      return testUnits;
    }

  }

  private static List<TestUnit> applyTestFilter(final TestFilter testFilter,
      final List<TestUnit> testUnits) {
    final F<TestUnit, Iterable<TestUnit>> f = new F<TestUnit, Iterable<TestUnit>>() {

      public Iterable<TestUnit> apply(final TestUnit a) {
        return a.filter(testFilter);
      }

    };
    return FCollection.flatMap(testUnits, f);
  }

  private void processResultsFromQueue(final Container container,
      final Thread feederThread, final StaticConfiguration staticConfig) {

    final ResultSource results = container.getResultSource();

    boolean isAlive = feederThread.isAlive();
    while (isAlive) {
      processResults(staticConfig, results);
      try {
        feederThread.join(100);
      } catch (final InterruptedException e) {
        // swallow
      }
      isAlive = feederThread.isAlive();

    }

    container.shutdownWhenProcessingComplete();

    while (!container.awaitCompletion() || results.resultsAvailable()) {
      processResults(staticConfig, results);
    }

    signalRunEndToAllListeners(staticConfig);

    LOG.info("Finished");

  }

  private void signalRunEndToAllListeners(final StaticConfiguration staticConfig) {
    FCollection.forEach(staticConfig.getTestListeners(),
        new SideEffect1<TestListener>() {
          public void apply(final TestListener a) {
            a.onRunEnd();
          }
        });
  }

  private Thread startFeederThread(final Container container,
      final List<? extends TestUnit> callables) {
    final Runnable feeder = new Runnable() {
      public void run() {
        for (final TestUnit unit : callables) {
          container.submit(unit);
        }
      }
    };
    final Thread feederThread = new Thread(feeder);
    feederThread.setDaemon(true);
    feederThread.start();
    return feederThread;
  }

  private void processResults(final StaticConfiguration staticConfig,
      final ResultSource source) {
    final List<TestResult> results = source.getAvailableResults();

    for (final TestResult result : results) {
      final ResultType classifiedResult = staticConfig.getClassifier()
          .classify(result);
      FCollection.forEach(staticConfig.getTestListeners(),
          classifiedResult.getListenerFunction(result));
    }

  }

}