package dev.reconrail.investigations.domain;

import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/** Frozen values only. No clock, framework, storage or remote dependencies. */
public final class ComparisonModel {
    private ComparisonModel() {}
    public record Timing(Instant start, Instant end, BigInteger errorUs, Set<String> present) {
        public Timing { present=Set.copyOf(present); }
        public Timing(Instant start,Instant end,BigInteger errorUs){this(start,end,errorUs,Set.of("readStart","readEnd","clockErrorUs"));}
    }
    public record Fact(String kind, String id, Map<String,String> values,
                       Map<String,Boolean> flags, Set<String> present, Timing timing, int index) {
        public Fact {
            Objects.requireNonNull(kind); Objects.requireNonNull(id); Objects.requireNonNull(timing);
            values=Collections.unmodifiableMap(new TreeMap<>(values));
            flags=Collections.unmodifiableMap(new TreeMap<>(flags)); present=Set.copyOf(present);
        }
        public String text(String field) { return values.get(field); }
        public BigInteger amount(String field) { String value=text(field); return value==null?null:new BigInteger(value); }
    }
    public record Config(BigInteger graceUs, BigInteger freshnessUs, Map<String,Boolean> declarations, Set<String> present) {
        public Config { declarations=Collections.unmodifiableMap(new TreeMap<>(declarations)); present=Set.copyOf(present); }
        public Config(BigInteger graceUs,BigInteger freshnessUs,Map<String,Boolean> declarations){this(graceUs,freshnessUs,declarations,Set.of("graceUs","freshnessLimitUs","flowDeclarations"));}
    }
    public record Versions(String rules, String currency, String normalizer) {}
    public record FrozenInput(String inputId, String providerDigest, String applicationDigest,
                              List<Fact> provider, List<Fact> orders, Config config, Versions versions,
                              Instant referenceAt, String providerCoverage, String applicationCoverage) {
        public FrozenInput { provider=List.copyOf(provider); orders=List.copyOf(orders); Objects.requireNonNull(config); Objects.requireNonNull(versions); Objects.requireNonNull(referenceAt); }
    }
    public enum Classification { CONSISTENT, DISCREPANCY, MORE_EVIDENCE_NEEDED, UNSUPPORTED, AMBIGUOUS }
    public record SourceRef(String artifactRole, int recordIndex, String fieldPointer) {}
    public record Finding(String ruleId, Classification classification, String subjectKind,
                          String subjectId, String explanationCode, List<String> qualifiers,
                          Map<String,String> observedValues, List<SourceRef> sourceRefs) {
        public Finding { qualifiers=List.copyOf(qualifiers); observedValues=Collections.unmodifiableMap(new TreeMap<>(observedValues)); sourceRefs=List.copyOf(sourceRefs); }
    }
    public record CheckCounts(long eligible, long consistent, long discrepancy,
                              long moreEvidenceNeeded, long unsupported, long ambiguous) {}
    public record Result(FrozenInput frozenInput, long paymentIntents, long charges, long orders,
                         Map<String,CheckCounts> checks, List<Finding> findings, List<String> qualifiers) {
        public Result { checks=Map.copyOf(checks); findings=List.copyOf(findings); qualifiers=List.copyOf(qualifiers); }
    }
}
