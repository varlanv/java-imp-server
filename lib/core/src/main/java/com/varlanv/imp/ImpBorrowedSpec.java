package com.varlanv.imp;

import java.util.List;

public final class ImpBorrowedSpec {

    private final DefaultImpShared parent;

    ImpBorrowedSpec(DefaultImpShared parent) {
        this.parent = parent;
    }

    public ImpBorrowed alwaysRespond(ImpAlwaysRespond action) {
        Preconditions.nonNull(action, "action");
        var specEnd = action.apply(new ImpTemplateSpec.AlwaysRespondSpecStart());
        Preconditions.nonNull(specEnd, "alwaysRespond function result");
        return new ImpBorrowed(
                ImmutableStartedServerConfig.builder()
                        .server(parent.config().server())
                        .decision(new ResponseDecision(List.of(specEnd.toResponseCandidate())))
                        .fallback(new Teapot(List.of()))
                        .build(),
                parent);
    }

    public SpecContinue matchRequest(ImpRequestMatch action) {
        Preconditions.nonNull(action, "action");
        var specEnd = action.apply(new ImpTemplateSpec.RequestMatchingSpecStart());
        Preconditions.nonNull(specEnd, "matchRequest function result");
        var responseCandidate = specEnd.toResponseCandidate();
        return new SpecContinue(parent, List.of(responseCandidate));
    }

    public static final class SpecContinue {

        private final DefaultImpShared parent;
        private final List<ResponseCandidate> responseCandidates;

        SpecContinue(DefaultImpShared parent, List<ResponseCandidate> responseCandidates) {
            this.parent = parent;
            this.responseCandidates = responseCandidates;
        }

        public SpecContinue matchRequest(ImpRequestMatch action) {
            Preconditions.nonNull(action, "action");
            var specEnd = action.apply(new ImpTemplateSpec.RequestMatchingSpecStart());
            Preconditions.nonNull(specEnd, "matchRequest function result");
            var responseCandidate = specEnd.toResponseCandidate();
            return new SpecContinue(parent, InternalUtils.addToNewListFinal(responseCandidates, responseCandidate));
        }

        public ImpBorrowed fallbackForNonMatching(
                ImpFn<ImpResponse.BuilderStatus, ImpResponse.BuilderHeaders> fallbackFn) {
            Preconditions.nonNull(fallbackFn, "fallbackFn");
            var fallbackImpResponse = fallbackFn.apply(ImpResponse.builder()).build();
            return new ImpBorrowed(
                    ImmutableStartedServerConfig.builder()
                            .server(parent.config().server())
                            .decision(new ResponseDecision(responseCandidates))
                            .fallback(candidates -> fallbackImpResponse)
                            .build(),
                    parent);
        }

        public ImpBorrowed rejectNonMatching() {
            return new ImpBorrowed(
                    ImmutableStartedServerConfig.builder()
                            .server(parent.config().server())
                            .decision(new ResponseDecision(responseCandidates))
                            .fallback(new Teapot(responseCandidates))
                            .build(),
                    parent);
        }
    }
}
