package com.varlanv.imp;

import java.util.List;
import java.util.function.Supplier;
import org.intellij.lang.annotations.MagicConstant;
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

    @VisibleForTesting
    boolean test(ImpRequestView requestView) {
        return kind == Kind.ALWAYS_TRUE || toEvaluated(requestView).result;
    }

    EvaluatedCondition toEvaluated(ImpRequestView requestView) {
        if (nested.isEmpty()) {
            var result = predicate.test(requestView);
            var stringList = new StringList();
            stringList.addSupplier(() -> group + " -> " + context.get() + " -> " + result);
            return new EvaluatedCondition(result, stringList);
        } else {
            // Initial call, nestLevel and indentLength are 0
            var evaluateContext = evaluateRecursive(new EvaluateContext(requestView, this, 0, 0));
            return new EvaluatedCondition(Boolean.TRUE.equals(evaluateContext.result), evaluateContext.message);
        }
    }

    private EvaluateContext evaluateRecursive(EvaluateContext currentContext) {
        if (currentContext.condition.kind == Kind.AND) {
            return processBranchEvaluation(currentContext, "AND", true, false, (res, childRes) -> res && childRes);
        } else if (currentContext.condition.kind == Kind.OR) {
            return processBranchEvaluation(currentContext, "OR", false, true, (res, childRes) -> res || childRes);
        } else {
            return processLeafEvaluation(currentContext);
        }
    }

    private EvaluateContext processBranchEvaluation(
            EvaluateContext branchContext,
            @MagicConstant(stringValues = {"AND", "OR"}) String branchType,
            boolean initialCumulativeResult,
            boolean shortCircuitTriggerValue,
            ConditionAccumulator accumulator) {

        var branchMessages = new StringList();
        // Add a supplier for the header message of this branch.
        // branchContext.result will be set correctly before this supplier is evaluated.
        branchMessages.addSupplier(() -> formatBranchHeaderMessage(branchContext, branchType));

        var cumulativeResult = initialCumulativeResult;

        // If this branch's result is predetermined by a parent's short-circuiting
        if (branchContext.knownResult != null) {
            cumulativeResult = branchContext.knownResult;
        }

        for (var nestedCondition : branchContext.condition.nested) {
            var childIndentLength = calculateChildIndentLength(branchContext);
            var childContext = branchContext.next(nestedCondition, childIndentLength);

            if (branchContext.knownResult != null) {
                // This entire branch is skipped due to parent's short-circuiting
                childContext.knownResult = branchContext.knownResult;
                childContext.result = branchContext.knownResult; // Ensure child's result reflects this
            } else if (cumulativeResult == shortCircuitTriggerValue) {
                // This branch is short-circuiting its remaining children
                childContext.knownResult = shortCircuitTriggerValue;
                childContext.result = shortCircuitTriggerValue; // Ensure child's result reflects this
            }

            var evaluatedChildContext = evaluateRecursive(childContext);
            branchMessages.addAll(evaluatedChildContext.message);

            // Only accumulate if this branch itself wasn't short-circuited by a parent
            if (branchContext.knownResult == null) {
                // evaluatedChildContext.result is guaranteed to be non-null by evaluateRecursive
                cumulativeResult = accumulator.apply(
                        cumulativeResult, evaluatedChildContext.result != null && evaluatedChildContext.result);
            }
        }

        branchContext.result = cumulativeResult; // Set the final result for this branch
        branchContext.message.addAll(branchMessages);
        return branchContext;
    }

    private EvaluateContext processLeafEvaluation(EvaluateContext leafContext) {
        String resultTextForMessage; // Text like "true", "false", or "N/E"

        if (leafContext.knownResult == null) { // Only evaluate if not already determined by the parent
            var evaluationResult = leafContext.condition.predicate.test(leafContext.requestView);
            leafContext.result = evaluationResult;
            resultTextForMessage = String.valueOf(evaluationResult);
        } else {
            leafContext.result = leafContext.knownResult; // Set result from knownResult
            resultTextForMessage = "N/E";
        }

        // Ensure resultTextForMessage is effectively final for the lambda
        final var finalResultText = resultTextForMessage;
        leafContext.message.addSupplier(() -> formatLeafMessage(leafContext, finalResultText));
        return leafContext;
    }

    private String formatBranchHeaderMessage(
            EvaluateContext context, @MagicConstant(stringValues = {"AND", "OR"}) String type) {
        var mark = " ".repeat(context.nestLevel == 0 ? 0 : context.indentLength);
        // context.result will reflect the outcome of this branch (or knownResult if N/E)
        // when this supplier is eventually evaluated.
        return mark + (context.nestLevel == 0 ? "" : "|---> ")
                + type
                + ("OR".equals(type) ? " " : "")
                + " -> "
                + (context.knownResult != null ? "N/E" : context.result)
                + "\n";
    }

    private String formatLeafMessage(EvaluateContext context, String resultOutputString) {
        // resultOutputString is "true", "false", or "N/E"
        // Supplier is called here, lazily
        return " ".repeat(context.indentLength) + "|---> "
                + context.condition.group
                + " -> "
                + context.condition.context.get()
                + " -> "
                + resultOutputString
                + "\n";
    }

    private int calculateChildIndentLength(EvaluateContext parentContext) {
        return parentContext.nestLevel == 0 ? 1 : parentContext.indentLength + 7;
    }

    enum Kind {
        AND,
        OR,
        CONDITION,
        NOT,
        ALWAYS_TRUE
    }

    static final class EvaluateContext {

        final StringList message = new StringList();
        private final ImpRequestView requestView;
        private final ImpCondition condition;
        final int nestLevel; // Renamed for clarity from 'evaluateContext.nestLevel' to 'currentContext.nestLevel'
        final int indentLength; // Renamed for clarity

        // This field will now always be set to a non-null Boolean by the end of evaluateRecursive
        // if knownResult was null, or to knownResult if it was non-null.
        @Nullable private Boolean result;

        @Nullable private Boolean knownResult; // If non-null, evaluation is skipped

        EvaluateContext(ImpRequestView requestView, ImpCondition condition, int nestLevel, int indentLength) {
            this.requestView = requestView;
            this.condition = condition;
            this.nestLevel = nestLevel;
            this.indentLength = indentLength;
        }

        // Creates context for a nested condition
        EvaluateContext next(ImpCondition nestedCondition, int childIndentLength) {
            return new EvaluateContext(requestView, nestedCondition, nestLevel + 1, childIndentLength);
        }
    }

    static final class EvaluatedCondition {

        final boolean result;
        final StringList message;

        EvaluatedCondition(boolean result, StringList message) {
            this.result = result;
            this.message = message;
        }
    }
}
