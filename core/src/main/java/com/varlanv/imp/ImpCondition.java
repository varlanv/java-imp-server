package com.varlanv.imp;

import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

public final class ImpCondition {

    static final String DEFAULT_GROUP = "<DEFAULT_GROUP>";
    final String group;
    final ImpPredicate<ImpRequestView> predicate;
    final Supplier<String> context;
    final Kind kind;
    final List<ImpCondition> nested;

    ImpCondition(
            String group,
            ImpPredicate<ImpRequestView> predicate,
            Supplier<String> context,
            Kind kind,
            List<ImpCondition> nested) {
        this.group = group;
        this.predicate = predicate;
        this.context = context;
        this.kind = kind;
        this.nested = nested;
    }

    ImpCondition(String group, ImpPredicate<ImpRequestView> predicate, Supplier<String> context, Kind kind) {
        this(group, predicate, context, kind, List.of());
    }

    ImpCondition(ImpPredicate<ImpRequestView> predicate, Kind kind, List<ImpCondition> nested) {
        this(DEFAULT_GROUP, predicate, () -> "", kind, nested);
    }

    @VisibleForTesting
    boolean test(ImpRequestView requestView) {
        var result = evaluate(requestView).result;
        return result != null && result;
    }

    static final class EvaluateContext {

        final StringList message = new StringList();
        private final ImpRequestView requestView;
        private final ImpCondition condition;
        private final int nestLevel;
        private final int indentLength;

        @Nullable private Boolean result;

        @Nullable private Boolean knownResult;

        private EvaluateContext(ImpRequestView requestView, ImpCondition condition, int nestLevel, int indentLength) {
            this.requestView = requestView;
            this.condition = condition;
            this.nestLevel = nestLevel;
            this.indentLength = indentLength;
        }

        private EvaluateContext next(ImpCondition condition, int indentLength) {
            return new EvaluateContext(requestView, condition, nestLevel + 1, indentLength);
        }
    }

    EvaluateContext evaluate(ImpRequestView requestView) {
        return evaluateRecursive(new EvaluateContext(requestView, this, 0, 0));
    }

    EvaluateContext evaluateRecursive(EvaluateContext evaluateContext) {
        var nestLevel = evaluateContext.nestLevel;
        var indentLength = evaluateContext.indentLength;
        if (evaluateContext.condition.kind == Kind.AND) {
            var andMsg = new StringList().addSupplier(() -> {
                var mark = " ".repeat(nestLevel == 0 ? 0 : indentLength);
                var isKnownResult = evaluateContext.knownResult != null;
                return new StringBuilder()
                        .append(mark)
                        .append(nestLevel == 0 ? "" : "|———— ")
                        .append("AND -> ")
                        .append(isKnownResult ? "N/E" : evaluateContext.result)
                        .append(System.lineSeparator())
                        .toString();
            });
            var andRes = true;
            for (var nestedCondition : evaluateContext.condition.nested) {
                var nextContext = evaluateContext.next(nestedCondition, nestLevel == 0 ? 0 : indentLength + 7);
                if (evaluateContext.knownResult != null) {
                    nextContext.knownResult = evaluateContext.knownResult;
                    nextContext.result = evaluateContext.knownResult;
                } else if (!andRes) {
                    nextContext.knownResult = false;
                    nextContext.result = false;
                }
                var nextEvaluated = evaluateRecursive(nextContext);
                andMsg.addAll(nextEvaluated.message);
                andRes = nextEvaluated.result != null && nextEvaluated.result && andRes;
            }
            evaluateContext.result = andRes;
            evaluateContext.message.addAll(andMsg);
            return evaluateContext;
        } else if (evaluateContext.condition.kind == Kind.OR) {
            var orMsg = new StringList().addSupplier(() -> {
                var mark = " ".repeat(nestLevel == 0 ? 0 : indentLength);
                var isKnownResult = evaluateContext.knownResult != null;
                return new StringBuilder()
                        .append(mark)
                        .append(nestLevel == 0 ? "" : "|———— ")
                        .append("OR -> ")
                        .append(isKnownResult ? "N/E" : evaluateContext.result)
                        .append(System.lineSeparator())
                        .toString();
            });
            var orRes = false;
            for (var nestedCondition : evaluateContext.condition.nested) {
                var nextContext = evaluateContext.next(nestedCondition, indentLength == 0 ? 7 : indentLength + 7);
                if (evaluateContext.knownResult != null) {
                    nextContext.knownResult = evaluateContext.knownResult;
                    nextContext.result = evaluateContext.knownResult;
                } else if (orRes) {
                    nextContext.knownResult = true;
                    nextContext.result = true;
                }
                var nextEvaluated = evaluateRecursive(nextContext);
                orMsg.addAll(nextEvaluated.message);
                orRes = (nextEvaluated.result != null && nextEvaluated.result) || orRes;
            }
            evaluateContext.result = orRes;
            evaluateContext.message.addAll(orMsg);
            return evaluateContext;
        } else if (evaluateContext.condition.kind == Kind.CONDITION || evaluateContext.condition.kind == Kind.NOT) {
            if (evaluateContext.knownResult == null) {
                var res = false;
                if (evaluateContext.condition.kind == Kind.CONDITION) {
                    res = evaluateContext.condition.predicate.test(evaluateContext.requestView);
                } else {
                    res = !evaluateContext.condition.predicate.test(evaluateContext.requestView);
                }
                evaluateContext.result = res;
                var resFinal = res;
                evaluateContext.message.addSupplier(() -> {
                    return new StringBuilder()
                            .append(" ".repeat(evaluateContext.indentLength))
                            .append("|———— ")
                            .append(String.format(
                                    "%s -> %s -> " + resFinal,
                                    evaluateContext.condition.group,
                                    evaluateContext.condition.context.get(),
                                    nestLevel))
                            .append(System.lineSeparator())
                            .toString();
                });
            } else {
                evaluateContext.message.addSupplier(() -> {
                    return new StringBuilder()
                            .append(" ".repeat(indentLength))
                            .append("|———— ")
                            .append(String.format(
                                    "%s -> %s -> N/E",
                                    evaluateContext.condition.group,
                                    evaluateContext.condition.context.get(),
                                    evaluateContext.knownResult))
                            .append(System.lineSeparator())
                            .toString();
                });
            }
            return evaluateContext;
        }
        throw new IllegalStateException("Unexpected value: " + evaluateContext.condition.kind);
    }

    enum Kind {
        AND,
        OR,
        CONDITION,
        NOT
    }
}
