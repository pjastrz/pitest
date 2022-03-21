package org.pitest.mutationtest.engine.gregor.mutators;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import org.junit.Before;
import org.junit.Test;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.gregor.MutatorTestBase;
import org.pitest.mutationtest.engine.gregor.mutators.experimental.DateAndTimeConditionalBoundaryMutator;

public class LocalDateTimeConditionalBoundaryMutatorTest extends MutatorTestBase {

    @Before
    public void setupEngineToMutateOnlyReturnVals() {
        createTesteeWith(DateAndTimeConditionalBoundaryMutator.EXPERIMENTAL_JAVA_TIME_CONDITIONAL_BOUNDARY);
    }

    @Test
    public void shouldReplaceLocalDateTimeIsBeforeWithIsBeforeOrEqual() throws Exception {
        final Collection<MutationDetails> actual = findMutationsFor(IsBefore.class);
        final Mutant mutant = getFirstMutant(actual);
        LocalDateTime today = LocalDateTime.of(2022, 7, 15, 12, 30);
        LocalDateTime tomorrow = today.plusDays(1);
        
        assertMutantCallableReturns(new IsBefore(today, tomorrow), mutant, true);
        assertMutantCallableReturns(new IsBefore(today, today), mutant, true);
        assertMutantCallableReturns(new IsBefore(tomorrow, today), mutant, false);
    }

    @Test
    public void shouldReplaceLocalDateTimeIsBeforeOrEqualWithIsBefore() throws Exception {
        final Collection<MutationDetails> actual = findMutationsFor(IsBeforeOrEqual.class);
        final Mutant mutant = getFirstMutant(actual);
        LocalDateTime today = LocalDateTime.of(2022, 7, 15, 12, 30);
        LocalDateTime tomorrow = today.plusDays(1);

        assertMutantCallableReturns(new IsBeforeOrEqual(today, tomorrow), mutant, true);
        assertMutantCallableReturns(new IsBeforeOrEqual(today, today), mutant, false);
        assertMutantCallableReturns(new IsBeforeOrEqual(tomorrow, today), mutant, false);
    }


    @Test
    public void shouldReplaceLocalDateTimeIsAfterWithIsAfterOrEqual() throws Exception {
        final Collection<MutationDetails> actual = findMutationsFor(IsAfter.class);
        final Mutant mutant = getFirstMutant(actual);
        LocalDateTime today = LocalDateTime.of(2022, 7, 15, 12, 30);
        LocalDateTime tomorrow = today.plusDays(1);

        assertMutantCallableReturns(new IsAfter(today, tomorrow), mutant, false);
        assertMutantCallableReturns(new IsAfter(today, today), mutant, true);
        assertMutantCallableReturns(new IsAfter(tomorrow, today), mutant, true);
    }

    @Test
    public void shouldReplaceLocalDateTimeIsAfterOrEqualWithIsAfter() throws Exception {
        final Collection<MutationDetails> actual = findMutationsFor(IsAfterOrEqual.class);
        final Mutant mutant = getFirstMutant(actual);
        LocalDateTime today = LocalDateTime.of(2022, 7, 15, 12, 30);
        LocalDateTime tomorrow = today.plusDays(1);

        assertMutantCallableReturns(new IsAfterOrEqual(today, tomorrow), mutant, false);
        assertMutantCallableReturns(new IsAfterOrEqual(today, today), mutant, false);
        assertMutantCallableReturns(new IsAfterOrEqual(tomorrow, today), mutant, true);
    }

    @Test
    public void shouldReplaceLocalDateTimeIsBeforeLambdaWithIsBeforeOrEqual() throws Exception {
        final Collection<MutationDetails> actual = findMutationsFor(IsBeforeLambda.class);
        final Mutant mutant = getFirstMutant(actual);
        LocalDateTime today = LocalDateTime.of(2022, 7, 15, 12, 30);
        LocalDateTime tomorrow = today.plusDays(1);

        assertMutantCallableReturns(new IsBeforeLambda(today, tomorrow), mutant, true);
        assertMutantCallableReturns(new IsBeforeLambda(today, today), mutant, true);
        assertMutantCallableReturns(new IsBeforeLambda(tomorrow, today), mutant, false);
    }

    @Test
    public void shouldReplaceLocalDateTimeIsAfterLambdaWithIsAfterOrEqual() throws Exception {
        final Collection<MutationDetails> actual = findMutationsFor(IsAfterLambda.class);
        final Mutant mutant = getFirstMutant(actual);
        LocalDateTime today = LocalDateTime.of(2022, 7, 15, 12, 30);
        LocalDateTime tomorrow = today.plusDays(1);

        assertMutantCallableReturns(new IsAfterLambda(today, tomorrow), mutant, false);
        assertMutantCallableReturns(new IsAfterLambda(today, today), mutant, true);
        assertMutantCallableReturns(new IsAfterLambda(tomorrow, today), mutant, true);
    }
    
    private static class IsBefore implements Callable<Boolean> {
        private final LocalDateTime dateTime1;
        private final LocalDateTime dateTime2;

        public IsBefore(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        @Override
        public Boolean call() throws Exception {
            return dateTime1.isBefore(dateTime2);
        }
    }

    private static class IsBeforeOrEqual implements Callable<Boolean> {
        private final LocalDateTime dateTime1;
        private final LocalDateTime dateTime2;

        public IsBeforeOrEqual(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        @Override
        public Boolean call() throws Exception {
            return !dateTime1.isAfter(dateTime2);
        }
    }

    private static class IsAfter implements Callable<Boolean> {
        private final LocalDateTime dateTime1;
        private final LocalDateTime dateTime2;

        public IsAfter(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        @Override
        public Boolean call() throws Exception {
            return dateTime1.isAfter(dateTime2);
        }
    }

    private static class IsAfterOrEqual implements Callable<Boolean> {
        private final LocalDateTime dateTime1;
        private final LocalDateTime dateTime2;

        public IsAfterOrEqual(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        @Override
        public Boolean call() throws Exception {
            return !dateTime1.isBefore(dateTime2);
        }
    }

    private static class IsBeforeLambda implements Callable<Boolean> {
        private final LocalDateTime dateTime1;
        private final LocalDateTime dateTime2;

        public IsBeforeLambda(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        @Override
        public Boolean call() throws Exception {
            BiFunction<LocalDateTime, ChronoLocalDateTime<?>, Boolean> function = LocalDateTime::isBefore;
            return function.apply(dateTime1, dateTime2);
        }
    }

    private static class IsAfterLambda implements Callable<Boolean> {
        private final LocalDateTime dateTime1;
        private final LocalDateTime dateTime2;

        public IsAfterLambda(LocalDateTime dateTime1, LocalDateTime dateTime2) {
            this.dateTime1 = dateTime1;
            this.dateTime2 = dateTime2;
        }

        @Override
        public Boolean call() throws Exception {
            BiFunction<LocalDateTime, ChronoLocalDateTime<?>, Boolean> function = LocalDateTime::isAfter;
            return function.apply(dateTime1, dateTime2);
        }
    }
}
