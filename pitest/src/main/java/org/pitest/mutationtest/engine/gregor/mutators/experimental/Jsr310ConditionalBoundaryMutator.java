package org.pitest.mutationtest.engine.gregor.mutators.experimental;

import static java.util.function.Function.identity;

import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.pitest.mutationtest.engine.MutationIdentifier;
import org.pitest.mutationtest.engine.gregor.MethodInfo;
import org.pitest.mutationtest.engine.gregor.MethodMutatorFactory;
import org.pitest.mutationtest.engine.gregor.MutationContext;

public enum Jsr310ConditionalBoundaryMutator implements MethodMutatorFactory {
    EXPERIMENTAL_JAVA_TIME_CONDITIONAL_BOUNDARY;

    @Override
    public MethodVisitor create(
            MutationContext context, MethodInfo methodInfo, MethodVisitor methodVisitor) {
        return new LocalDateTimeConditionalBoundaryMutator(this, context, methodVisitor);
    }

    @Override
    public String getGloballyUniqueId() {
        return this.getClass().getName();
    }

    @Override
    public String toString() {
        return "EXPERIMENTAL_JAVA_TIME_CONDITIONAL_BOUNDARY";
    }

    @Override
    public String getName() {
        return toString();
    }

    private static class LocalDateTimeConditionalBoundaryMutator extends MethodVisitor {

        private static final Map<String, MatchedType> MATCHED_TYPES = Stream.of(
                new MatchedType("java/time/Year", "java/time/Year"),
                new MatchedType("java/time/YearMonth", "java/time/YearMonth"),
                new MatchedType("java/time/MonthDay", "java/time/MonthDay"),
                new MatchedType("java/time/LocalDate", "java/time/chrono/ChronoLocalDate"),
                new MatchedType("java/time/LocalTime", "java/time/LocalTime"),
                new MatchedType("java/time/OffsetTime", "java/time/OffsetTime"),
                new MatchedType("java/time/LocalDateTime", "java/time/chrono/ChronoLocalDateTime"),
                new MatchedType("java/time/OffsetDateTime", "java/time/OffsetDateTime"),
                new MatchedType("java/time/ZonedDateTime", "java/time/chrono/ChronoZonedDateTime"),
                new MatchedType("java/time/Instant", "java/time/Instant")
        ).collect(Collectors.toMap(type -> type.owner, identity()));

        private static final Map<String, Replacement> METHOD_REPLACEMENTS = Stream.of(
                        new Replacement("isBefore", "isAfter", "isBeforeOrEqual"),
                        new Replacement("isAfter", "isBefore", "isAfterOrEqual"))
                .collect(Collectors.toMap(replacement -> replacement.sourceName, identity()));

        private final MethodMutatorFactory factory;
        private final MutationContext context;

        LocalDateTimeConditionalBoundaryMutator(
                MethodMutatorFactory factory, MutationContext context,
                MethodVisitor visitor) {
            super(Opcodes.ASM6, visitor);
            this.factory = factory;
            this.context = context;
        }

        @Override
        public void visitMethodInsn(
                int opcode, String owner, String name, String descriptor,
                boolean isInterface) {
            if (!MATCHED_TYPES.containsKey(owner) || opcode != Opcodes.INVOKEVIRTUAL) {
                this.mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            MatchedType matchedType = MATCHED_TYPES.get(owner);
            Replacement replacement = METHOD_REPLACEMENTS.get(name);
            if (replacement != null && matchedType.getComparisonMethodDescriptor().equals(descriptor)) {
                MutationIdentifier identifier = context.registerMutation(factory, 
                        formatMutationDescription(matchedType, replacement));
                if (context.shouldMutate(identifier)) {
                    this.mv.visitMethodInsn(
                            opcode,
                            owner,
                            replacement.destinationName,
                            matchedType.getComparisonMethodDescriptor(),
                            isInterface);

                    // negate 
                    Label l1 = new Label();
                    mv.visitJumpInsn(Opcodes.IFNE, l1);
                    mv.visitInsn(Opcodes.ICONST_1);
                    Label l2 = new Label();
                    mv.visitJumpInsn(Opcodes.GOTO, l2);
                    mv.visitLabel(l1);
                    mv.visitInsn(Opcodes.ICONST_0);
                    mv.visitLabel(l2);
                    return;
                }
            }

            this.mv.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                           Object... bootstrapMethodArguments) {
            Object[] methodArgs = new Object[bootstrapMethodArguments.length];
            for (int i = 0; i < bootstrapMethodArguments.length; i++) {
                Object bootstrapMethodArgument = bootstrapMethodArguments[i];
                if (bootstrapMethodArgument instanceof Handle) {
                    methodArgs[i] = mutateHandle((Handle) bootstrapMethodArgument);
                } else {
                    methodArgs[i] = bootstrapMethodArgument;
                }
            }
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, methodArgs);
        }

        /**
         * Mutates a handle within an invoke virtual.
         */
        private Handle mutateHandle(Handle handle) {
            int opcode = handle.getTag();
            String owner = handle.getOwner();
            String name = handle.getName();
            String descriptor = handle.getDesc();

            if (MATCHED_TYPES.containsKey(owner) && opcode == Opcodes.H_INVOKEVIRTUAL) {
                if (METHOD_REPLACEMENTS.containsKey(name)) {
                    MatchedType matchedType = MATCHED_TYPES.get(owner);
                    Replacement replacement = METHOD_REPLACEMENTS.get(name);
                    if (matchedType.getComparisonMethodDescriptor().equals(descriptor)) {
                        MutationIdentifier id = context.registerMutation(factory, 
                                formatMutationDescription(matchedType, replacement));
                        if (context.shouldMutate(id)) {
                            return new Handle(
                                    Opcodes.H_INVOKESTATIC,
                                    JavaTimeComparisonFunctions.class.getName().replace(".", "/"),
                                    replacement.dynamicDestinationName,
                                    matchedType.getDynamicReplacementMethodDescriptor(),
                                    false);
                        }
                    }
                }
            }
            return handle;
        }

        private String formatMutationDescription(MatchedType matchedType,
                                                 Replacement replacement) {
            String[] typeNameParts = matchedType.owner.split("/");
            String typeName = typeNameParts[typeNameParts.length - 1];
            String template = "Replaced %s#%s with !%s#%s.";
            return String.format(template, 
                    typeName,
                    replacement.sourceName,
                    typeName,
                    replacement.destinationName);
        }
        
        private static final class Replacement {

            private final String sourceName;
            private final String destinationName;
            private final String dynamicDestinationName;

            Replacement(String sourceName, String destinationName, String dynamicDestinationName) {
                this.sourceName = sourceName;
                this.destinationName = destinationName;
                this.dynamicDestinationName = dynamicDestinationName;
            }
        }

        private static final class MatchedType {
            private final String owner;
            private final String comparisonMethodParamType;

            private MatchedType(String owner, String comparisonMethodParamType) {
                this.owner = owner;
                this.comparisonMethodParamType = comparisonMethodParamType;
            }
            
            public String getComparisonMethodDescriptor() {
                String format = "(L%s;)Z";
                return String.format(format, comparisonMethodParamType);
            }
            
            public String getDynamicReplacementMethodDescriptor() {
                String format = "(L%s;L%s;)Z";
                return String.format(format, owner, comparisonMethodParamType);
            }
        }
    }
    
    public static class JavaTimeComparisonFunctions {
        public static boolean isBeforeOrEqual(LocalDateTime dateTime1, ChronoLocalDateTime<?> dateTime2) {
            return !dateTime1.isAfter(dateTime2);
        }

        public static boolean isAfterOrEqual(LocalDateTime dateTime1, ChronoLocalDateTime<?> dateTime2) {
            return !dateTime1.isBefore(dateTime2);
        }
    }
}
