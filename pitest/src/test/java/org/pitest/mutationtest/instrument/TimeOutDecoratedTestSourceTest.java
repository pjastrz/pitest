/*
 * Copyright 2011 Henry Coles
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
package org.pitest.mutationtest.instrument;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.pitest.Description;
import org.pitest.classinfo.ClassName;
import org.pitest.coverage.domain.TestInfo;
import org.pitest.extension.ResultCollector;
import org.pitest.extension.TestUnit;
import org.pitest.functional.Option;
import org.pitest.mutationtest.execute.Reporter;

public class TimeOutDecoratedTestSourceTest {

  private TimeOutDecoratedTestSource testee;

  @Mock
  private TimeoutLengthStrategy      timeoutStrategy;

  @Mock
  private Reporter                   reporter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    this.testee = new TimeOutDecoratedTestSource(this.timeoutStrategy,
        Arrays.asList(makeTestUnit("one"), makeTestUnit("two")), this.reporter);
  }

  @Test
  public void shouldReturnNoTestUnitsWhenNoTestDetailsSupplied() {
    assertTrue(this.testee.translateTests(Collections.<TestInfo> emptyList())
        .isEmpty());
  }

  @Test
  public void shouldReturnNoTestUnitWhenNonMatchingTestDetailSupplied() {
    assertTrue(this.testee
        .translateTests(
            Arrays.asList(new TestInfo("foo", "bar", 42, Option
                .<ClassName> none()))).isEmpty());
  }

  @Test
  public void shouldReturnTestUnitWhenMatchingTestDetailSupplied() {
    final List<TestUnit> actual = this.testee.translateTests(Arrays
        .asList(new TestInfo("foo", "one", 42, Option.<ClassName> none())));
    assertEquals(1, actual.size());
  }

  private TestUnit makeTestUnit(final String name) {
    return new TestUnit() {
      private final Description description = new Description(name);

      public void execute(final ClassLoader loader, final ResultCollector rc) {
      }

      public Description getDescription() {
        return this.description;
      }

    };
  }

}
