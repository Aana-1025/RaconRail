package dev.reconrail.investigations.domain;

import dev.reconrail.evidence.codec.*;
import dev.reconrail.investigations.EvidenceComparison;
import static dev.reconrail.investigations.domain.ComparisonModel.*;
import static dev.reconrail.investigations.domain.ComparisonModel.Classification.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.math.BigInteger;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class QualifiedEvaluatorTest {
    static List<Map<String,Object>> cases(){try{return ((List<?>)StrictJson.object(StrictJson.parse(Files.readAllBytes(Path.of("src/test/resources/domain/d019-oracles.json")))).get("cases")).stream().map(StrictJson::object).toList();}catch(Exception e){throw new IllegalStateException(e);}}
    static Stream<Map<String,Object>> oracles(){return cases().stream();}
    static byte[] bytes(Object value){return AccountingCodec.json(value).getBytes(StandardCharsets.UTF_8);}
    static FrozenInput freeze(Map<String,Object> c){var p=StrictJson.object(c.get("provider"));return EvidenceComparison.freeze("00000000-0000-4000-8000-000000000001",bytes(p),bytes(c.get("application")),StrictJson.object(p.get("providerNamespace")),StrictJson.object(p.get("appNamespace")),StrictJson.object(c.get("config")),QualifiedEvaluator.SUPPORTED,"2026-01-01T10:12:00Z");}
    static FrozenInput base(){return freeze(cases().getFirst());}
    @ParameterizedTest(name="{index}: {0}") @MethodSource("oracles")
    void approvedOracles(Map<String,Object> c){
        if(c.get("expected").equals("REJECT")){assertThrows(EvidenceException.class,()->freeze(c));return;}
        var result=new QualifiedEvaluator().evaluate(freeze(c));String expected=(String)c.get("expected");
        for(String rule:List.of("DOM-F-001","DOM-F-002","DOM-F-003")){
            var found=result.findings().stream().filter(f->f.subjectKind().equals("ORDER")&&f.ruleId().equals(rule)).toList();assertEquals(((BigInteger)c.get("expectedOrders")).intValueExact(),found.size());
            char symbol=expected.charAt(Integer.parseInt(rule.substring(6))-1);var classification=switch(symbol){case 'D'->DISCREPANCY;case 'C'->CONSISTENT;case 'M'->MORE_EVIDENCE_NEEDED;case 'U'->UNSUPPORTED;case 'A'->AMBIGUOUS;default->throw new AssertionError();};for(var finding:found)assertEquals(classification,finding.classification(),c.get("id")+" "+rule);
        }
        assertEquals(((BigInteger)c.get("expectedCharges")).longValueExact(),result.charges());assertEquals(((BigInteger)c.get("expectedOrders")).longValueExact(),result.orders());
        result.checks().forEach((k,count)->assertEquals(count.eligible(),count.consistent()+count.discrepancy()));
        assertNotNull(EvidenceComparison.semanticDigest(result));
    }
    static Fact text(Fact f,String key,String value){var v=new TreeMap<>(f.values());v.put(key,value);var present=new HashSet<>(f.present());present.add(key);return new Fact(f.kind(),f.id(),v,f.flags(),present,f.timing(),f.index());}
    static FrozenInput input(FrozenInput b,List<Fact> p,List<Fact> orders,Config c,Versions v,Instant reference){return new FrozenInput(b.inputId(),b.providerDigest(),b.applicationDigest(),p,orders,c,v,reference,b.providerCoverage(),b.applicationCoverage());}
    static Result evaluate(FrozenInput i){return new QualifiedEvaluator().evaluate(i);}
    @Test void permutationPreservesSemanticsButLocatorsRemainAccurate(){var a=evaluate(base());var b=evaluate(freeze(cases().get(21)));assertEquals(EvidenceComparison.semanticDigest(a),EvidenceComparison.semanticDigest(b));assertNotEquals(a.frozenInput().providerDigest(),b.frozenInput().providerDigest());assertNotEquals(a.findings().getFirst().sourceRefs(),b.findings().getFirst().sourceRefs());}
    @Test void frozenTupleRepeatsAndDoesNotMutate(){var b=base();var r=evaluate(b);assertEquals(r,evaluate(b));var changed=new Config(new BigInteger("700000000"),null,b.config().declarations());var other=evaluate(input(b,b.provider(),b.orders(),changed,b.versions(),b.referenceAt()));assertNotEquals(EvidenceComparison.semanticDigest(r),EvidenceComparison.semanticDigest(other));assertEquals(DISCREPANCY,r.findings().getFirst().classification());}
    @Test void unsupportedVersionsCannotSilentlyChangeRules(){var b=base();for(Versions v:List.of(new Versions("new",b.versions().currency(),b.versions().normalizer()),new Versions(b.versions().rules(),"new",b.versions().normalizer()),new Versions(b.versions().rules(),b.versions().currency(),"new")))assertThrows(IllegalArgumentException.class,()->evaluate(input(b,b.provider(),b.orders(),b.config(),v,b.referenceAt())));}
    @Test void exactLargeMoneyAndCaseSensitiveBinding(){var b=base();String amount="99999999999999999999999999999999999999";var pi=text(text(b.provider().get(0),"amount",amount),"amountReceived",amount);var ch=text(text(b.provider().get(1),"amount",amount),"amountCaptured",amount);var order=text(b.orders().getFirst(),"expectedAmount",amount);var r=evaluate(input(b,List.of(pi,ch),List.of(order),b.config(),b.versions(),b.referenceAt()));assertEquals(1,r.checks().get("amount").consistent());var wrong=text(order,"paymentReference","PI_DEMO");assertEquals(0,evaluate(input(b,List.of(pi,ch),List.of(wrong),b.config(),b.versions(),b.referenceAt())).checks().get("state").eligible());}
    @Test void missingRevisionCannotEraseState(){var b=base();var o=text(b.orders().getFirst(),"expectationRevision",null);var r=evaluate(input(b,b.provider(),List.of(o),b.config(),b.versions(),b.referenceAt()));assertEquals(1,r.checks().get("state").discrepancy());assertEquals(0,r.checks().get("amount").eligible());}
    @Test void timingBoundaryAndBothProviderClocksAreConservative(){var b=base();for(long us:List.of(59999999L,60000000L,60000001L)){var o=b.orders().getFirst();o=new Fact(o.kind(),o.id(),o.values(),o.flags(),o.present(),new Timing(Instant.parse("2026-01-01T10:00:00Z").plusNanos(us*1000),o.timing().end(),BigInteger.ZERO),o.index());assertEquals(us>=60000000?1:0,evaluate(input(b,b.provider(),List.of(o),b.config(),b.versions(),b.referenceAt())).checks().get("state").discrepancy());}
        var ch=b.provider().get(1);ch=new Fact(ch.kind(),ch.id(),ch.values(),ch.flags(),ch.present(),new Timing(ch.timing().start(),Instant.parse("2026-01-01T10:09:30Z"),BigInteger.ZERO),ch.index());assertEquals(0,evaluate(input(b,List.of(b.provider().get(0),ch),b.orders(),b.config(),b.versions(),b.referenceAt())).checks().get("state").eligible());}
    @Test void emptyInputsNeverHaveEligibleChecks(){var b=base();var r=evaluate(input(b,List.of(),List.of(),b.config(),b.versions(),b.referenceAt()));assertEquals(0,r.findings().size());r.checks().values().forEach(c->assertEquals(0,c.eligible()));}
    @Test void freshnessAndUnknownScopeQualifyFrozenResults(){var b=base();var flow=new TreeMap<>(b.config().declarations());flow.put("oneTime",null);var r=evaluate(input(b,b.provider(),b.orders(),new Config(b.config().graceUs(),BigInteger.ONE,flow),b.versions(),b.referenceAt()));assertEquals(0,r.checks().get("state").eligible());assertTrue(r.findings().getFirst().qualifiers().contains("DECLARED_FRESHNESS_EXCEEDED"));}
    @Test void missingAndContradictoryProviderFactsNeverBecomeAppDiscrepancies(){
        var b=base();for(String field:List.of("status","captureMethod","amount","amountReceived","amountCapturable","currency","latestChargeId")){
            var pi=text(b.provider().getFirst(),field,null);var r=evaluate(input(b,List.of(pi,b.provider().get(1)),b.orders(),b.config(),b.versions(),b.referenceAt()));assertEquals(0,r.checks().get("state").eligible(),field);
        }
        var ch=text(b.provider().get(1),"amountCaptured","999");assertEquals(0,evaluate(input(b,List.of(b.provider().getFirst(),ch),b.orders(),b.config(),b.versions(),b.referenceAt())).checks().get("amount").eligible());
        var pi=text(b.provider().getFirst(),"amountReceived","999");assertEquals(1,evaluate(input(b,List.of(pi,ch),b.orders(),b.config(),b.versions(),b.referenceAt())).checks().get("amount").unsupported());
    }
    @Test void semanticOutputCanContainMoreFindingsThanInputRecords(){
        var b=base();var orders=new ArrayList<Fact>();var template=text(b.orders().getFirst(),"paymentReference",null);
        for(int n=0;n<4000;n++)orders.add(new Fact("order","order_"+n,template.values(),template.flags(),template.present(),template.timing(),n));
        var r=evaluate(input(b,List.of(),orders,b.config(),b.versions(),b.referenceAt()));assertEquals(12000,r.findings().size());assertEquals(4000,r.checks().get("state").moreEvidenceNeeded());assertNotNull(EvidenceComparison.semanticDigest(r));
    }
    @Test void derivedOutOfRangeTimesBecomeUnknownWithoutAffectingMoney(){
        var b=base();var pi=b.provider().getFirst();pi=new Fact(pi.kind(),pi.id(),pi.values(),pi.flags(),pi.present(),new Timing(pi.timing().start(),Instant.parse("9999-12-31T23:59:59.999999Z"),BigInteger.ONE),pi.index());var r=evaluate(input(b,List.of(pi,b.provider().get(1)),b.orders(),b.config(),b.versions(),b.referenceAt()));assertEquals(0,r.checks().get("state").eligible());assertEquals(1,r.checks().get("amount").consistent());
    }
    @Test void purePackageHasNoFrameworkStorageNetworkOrLiveClock()throws Exception{
        for(String file:List.of("QualifiedEvaluator.java","ComparisonModel.java")){String source=Files.readString(Path.of("src/main/java/dev/reconrail/investigations/domain/"+file));for(String forbidden:List.of("org.springframework","java.sql","java.net","java.io","Instant.now(","System.currentTimeMillis(","Clock.system"))assertFalse(source.contains(forbidden),forbidden);}
    }
    @Test void frozenConfigAndTimingRetainAbsentVersusExplicitNull(){
        var c=new LinkedHashMap<>(cases().getFirst());var config=new LinkedHashMap<>(StrictJson.object(c.get("config")));config.remove("graceUs");c.put("config",config);var absent=freeze(c);config.put("graceUs",null);var explicit=freeze(c);assertFalse(absent.config().present().contains("graceUs"));assertTrue(explicit.config().present().contains("graceUs"));assertEquals(EvidenceComparison.semanticDigest(evaluate(absent)),EvidenceComparison.semanticDigest(evaluate(explicit)));
        assertTrue(base().provider().getFirst().timing().present().contains("clockErrorUs"));
    }
}
