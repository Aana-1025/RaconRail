package dev.reconrail.investigations.domain;

import java.math.BigInteger;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.reconrail.investigations.domain.ComparisonModel.*;
import static dev.reconrail.investigations.domain.ComparisonModel.Classification.*;

/** D-019 comparisons over one frozen, validated namespace. Never infers source authenticity. */
public final class QualifiedEvaluator {
    public static final Versions SUPPORTED=new Versions("stripe-card-order-v1","rr-currency-v1","stripe-card-order-normalizer-v1");
    private static final List<String> FLOW=List.of("oneTime","fullOriginal","automaticCapture","noAdjustments","noSubscription","noConnect","noSplit");
    private static final Set<String> CURRENCIES=Set.of("USD","INR","JPY");
    private static final BigInteger LIMIT=new BigInteger("315576000000000000");
    private record Gate(Classification classification,String reason) {}
    public Result evaluate(FrozenInput input) {
        if(!SUPPORTED.equals(input.versions())) throw new IllegalArgumentException("UNSUPPORTED_EVALUATOR_VERSION");
        for(BigInteger duration:Arrays.asList(input.config().graceUs(),input.config().freshnessUs()))
            if(duration!=null&&(duration.signum()<0||duration.compareTo(LIMIT)>0)) throw new IllegalArgumentException("INVALID_FROZEN_CONFIG");
        if(!input.config().declarations().keySet().equals(new HashSet<>(FLOW))) throw new IllegalArgumentException("INVALID_FROZEN_CONFIG");
        if(input.provider().size()>10000||input.orders().size()>10000)throw new IllegalArgumentException("INPUT_LIMIT");
        Map<String,Fact> payments=new HashMap<>(),charges=new HashMap<>(),orders=new HashMap<>();
        for(Fact f:input.provider()) { if(f.kind().equals("payment_intent")) unique(payments,f);else if(f.kind().equals("charge"))unique(charges,f);else throw new IllegalArgumentException("INVALID_FACT_KIND"); }
        for(Fact f:input.orders()){if(!f.kind().equals("order"))throw new IllegalArgumentException("INVALID_FACT_KIND");unique(orders,f);}
        Map<String,Integer> claims=new HashMap<>();for(Fact order:input.orders())if(order.text("paymentReference")!=null)claims.merge(order.text("paymentReference"),1,Integer::sum);
        List<Finding> findings=new ArrayList<>();
        List<String> base=new ArrayList<>(List.of("DECLARED_NONATOMIC_CAPTURE","EXPORTER_AUTHENTICITY_UNVERIFIED"));
        if(!"complete".equals(input.providerCoverage())||!"complete".equals(input.applicationCoverage()))base.add("LIMITED_EXPORT_COVERAGE");
        Collections.sort(base);
        for(Fact order:orders.values()) {
            String ref=order.text("paymentReference");Fact pi=payments.get(ref);Fact charge=pi==null?null:charges.get(pi.text("latestChargeId"));
            List<String> qualifiers=qualifiers(base,input,order,pi,charge);
            Gate gate=ref==null?new Gate(MORE_EVIDENCE_NEEDED,"MISSING_EXPLICIT_BINDING"):
                claims.get(ref)>1?new Gate(AMBIGUOUS,"MULTIPLE_ORDERS_CLAIM_PAYMENT"):
                pi==null?new Gate(MORE_EVIDENCE_NEEDED,"MISSING_PAYMENT_TARGET"):providerGate(input.config(),pi,charge);
            Map<String,String> values=values(order,pi); List<SourceRef> refs=refs(order,pi,charge);
            if(gate!=null){for(int rule=1;rule<=3;rule++)findings.add(finding(rule,gate.classification(),order,gate.reason(),qualifiers,values,refs));continue;}
            Map<String,String> stateValues=new TreeMap<>(values);BigInteger elapsed=elapsed(order,pi,charge);
            if(elapsed!=null)stateValues.put("elapsedLowerBoundUs",elapsed.toString());
            if(input.config().graceUs()!=null)stateValues.put("graceUs",input.config().graceUs().toString());
            String state=order.text("paymentState");Classification stateClass;String stateReason;
            if(state==null){stateClass=MORE_EVIDENCE_NEEDED;stateReason="MISSING_APPLICATION_STATE";}
            else if(!Set.of("paid","awaiting_payment").contains(state)){stateClass=UNSUPPORTED;stateReason="UNSUPPORTED_APPLICATION_STATE";}
            else if(elapsed==null||elapsed.signum()<0){stateClass=MORE_EVIDENCE_NEEDED;stateReason="UNKNOWN_TEMPORAL_ORDER";}
            else if(state.equals("paid")){stateClass=CONSISTENT;stateReason="PAID_IN_COMPARED_OBSERVATIONS";}
            else if(input.config().graceUs()==null){stateClass=MORE_EVIDENCE_NEEDED;stateReason="MISSING_DECLARED_GRACE";}
            else if(elapsed.compareTo(input.config().graceUs())>=0){stateClass=DISCREPANCY;stateReason="APP_AWAITING_AFTER_DECLARED_GRACE";}
            else{stateClass=MORE_EVIDENCE_NEEDED;stateReason="DECLARED_GRACE_NOT_ELAPSED";}
            findings.add(finding(1,stateClass,order,stateReason,qualifiers,stateValues,refs));
            String pc=currency(pi.text("currency")),ac=currency(order.text("expectedCurrency"));
            if(order.text("expectationRevision")==null||ac==null){money(findings,order,MORE_EVIDENCE_NEEDED,"MISSING_STABLE_EXPECTATION",qualifiers,values,refs);}
            else if(!CURRENCIES.contains(pc)||!CURRENCIES.contains(ac)){money(findings,order,UNSUPPORTED,"UNSUPPORTED_MONETARY_CURRENCY",qualifiers,values,refs);}
            else {
                boolean same=pc.equals(ac);
                findings.add(finding(2,same?CONSISTENT:DISCREPANCY,order,same?"CURRENCY_AGREES":"CURRENCY_DIFFERS",qualifiers,values,refs));
                BigInteger expected=order.amount("expectedAmount");
                Classification amountClass;String reason;
                if(!same){amountClass=MORE_EVIDENCE_NEEDED;reason="DIFFERENT_UNITS_NOT_COMPARED";}
                else if(expected==null){amountClass=MORE_EVIDENCE_NEEDED;reason="MISSING_EXPECTED_AMOUNT";}
                else if(expected.signum()<=0){amountClass=UNSUPPORTED;reason="NONPOSITIVE_ORIGINAL_EXPECTATION";}
                else {boolean equal=expected.equals(pi.amount("amountReceived"));amountClass=equal?CONSISTENT:DISCREPANCY;reason=equal?"ORIGINAL_AMOUNT_AGREES":"ORIGINAL_AMOUNT_DIFFERS";}
                findings.add(finding(3,amountClass,order,reason,qualifiers,values,refs));
            }
        }
        for(Fact pi:payments.values())if(!claims.containsKey(pi.id())) {
            Fact charge=charges.get(pi.text("latestChargeId"));Gate gate=providerGate(input.config(),pi,charge);
            Classification cl=gate!=null?gate.classification():MORE_EVIDENCE_NEEDED;
            String reason=gate!=null?gate.reason():"NO_APPLICATION_BINDING_OBSERVED";
            for(int r=1;r<=3;r++)findings.add(finding(r,cl,pi,reason,qualifiers(base,input,pi,charge),Map.of(),refs(null,pi,charge)));
        }
        Set<String> latestIds=new HashSet<>();for(Fact pi:payments.values())if(pi.text("latestChargeId")!=null)latestIds.add(pi.text("latestChargeId"));
        for(Fact charge:charges.values())if(!payments.containsKey(charge.text("paymentIntentId"))&&!latestIds.contains(charge.id()))
            for(int r=1;r<=3;r++)findings.add(finding(r,MORE_EVIDENCE_NEEDED,charge,"MISSING_PAYMENT_FOR_CHARGE",qualifiers(base,input,charge),Map.of(),refs(null,null,charge)));
        findings.sort((a,b)->{int c=utf8(a.subjectKind(),b.subjectKind());if(c==0)c=utf8(a.subjectId(),b.subjectId());if(c==0)c=a.ruleId().compareTo(b.ruleId());return c;});
        if(findings.size()>60000)throw new IllegalArgumentException("OUTPUT_LIMIT");
        Map<String,CheckCounts> counts=new TreeMap<>();for(int n=1;n<=3;n++){
            String rule="DOM-F-00"+n;long consistent=0,discrepancy=0,more=0,unsupported=0,ambiguous=0;
            for(Finding f:findings)if(f.ruleId().equals(rule))switch(f.classification()){case CONSISTENT->consistent++;case DISCREPANCY->discrepancy++;case MORE_EVIDENCE_NEEDED->more++;case UNSUPPORTED->unsupported++;case AMBIGUOUS->ambiguous++;}
            counts.put(List.of("state","currency","amount").get(n-1),new CheckCounts(consistent+discrepancy,consistent,discrepancy,more,unsupported,ambiguous));
        }
        return new Result(input,payments.size(),charges.size(),orders.size(),counts,findings,base);
    }
    private static Gate providerGate(Config config,Fact pi,Fact charge){
        if(config.declarations().containsValue(false))return new Gate(UNSUPPORTED,"UNSUPPORTED_DECLARED_FLOW");
        String status=pi.text("status"),capture=pi.text("captureMethod");
        if(status!=null&&!status.equals("succeeded"))return new Gate(UNSUPPORTED,"UNSUPPORTED_PROVIDER_STATE");
        if(capture!=null&&!Set.of("automatic","automatic_async").contains(capture))return new Gate(UNSUPPORTED,"UNSUPPORTED_CAPTURE_FLOW");
        if(charge==null)return new Gate(MORE_EVIDENCE_NEEDED,"MISSING_LATEST_CHARGE");
        if(Boolean.TRUE.equals(charge.flags().get("disputed"))||(charge.amount("amountRefunded")!=null&&charge.amount("amountRefunded").signum()!=0))return new Gate(UNSUPPORTED,"UNSUPPORTED_REVERSAL_LIFECYCLE");
        if(charge.text("status")!=null&&!Set.of("succeeded","failed","pending").contains(charge.text("status")))return new Gate(UNSUPPORTED,"UNSUPPORTED_CHARGE_STATE");
        if(charge.text("actualMethodType")!=null&&!charge.text("actualMethodType").equals("card"))return new Gate(UNSUPPORTED,"UNSUPPORTED_ACTUAL_METHOD");
        for(BigInteger amount:Arrays.asList(pi.amount("amount"),pi.amount("amountReceived"),charge.amount("amount"),charge.amount("amountCaptured")))if(amount!=null&&amount.signum()<=0)return new Gate(UNSUPPORTED,"NONPOSITIVE_ORIGINAL_COLLECTION");
        if(charge.text("paymentIntentId")!=null&&!pi.id().equals(charge.text("paymentIntentId")))return new Gate(MORE_EVIDENCE_NEEDED,"PROVIDER_EVIDENCE_CONFLICT");
        if(status==null||capture==null||config.declarations().containsValue(null)||charge.text("paymentIntentId")==null||charge.text("status")==null||charge.text("actualMethodType")==null||charge.flags().get("paid")==null||charge.flags().get("captured")==null||charge.flags().get("disputed")==null||charge.amount("amountRefunded")==null)return new Gate(MORE_EVIDENCE_NEEDED,"MISSING_PROVIDER_CORROBORATION");
        if(!charge.text("status").equals("succeeded")||!Boolean.TRUE.equals(charge.flags().get("paid"))||!Boolean.TRUE.equals(charge.flags().get("captured")))return new Gate(MORE_EVIDENCE_NEEDED,"PROVIDER_EVIDENCE_CONFLICT");
        BigInteger amount=pi.amount("amount"),received=pi.amount("amountReceived"),charged=charge.amount("amount"),captured=charge.amount("amountCaptured"),capturable=pi.amount("amountCapturable");
        if(amount==null||received==null||charged==null||captured==null||capturable==null||pi.text("currency")==null||charge.text("currency")==null)return new Gate(MORE_EVIDENCE_NEEDED,"MISSING_PROVIDER_CORROBORATION");
        if(!received.equals(captured))return new Gate(MORE_EVIDENCE_NEEDED,"PROVIDER_EVIDENCE_CONFLICT");
        if(amount.equals(charged)&&received.compareTo(amount)<0)return new Gate(UNSUPPORTED,"UNSUPPORTED_PARTIAL_CAPTURE");
        if(!amount.equals(received)||!amount.equals(charged)||capturable.signum()!=0||!currency(pi.text("currency")).equals(currency(charge.text("currency"))))return new Gate(MORE_EVIDENCE_NEEDED,"PROVIDER_EVIDENCE_CONFLICT");
        return null;
    }
    private static BigInteger elapsed(Fact order,Fact...provider){
        if(order.timing().start()==null||order.timing().errorUs()==null)return null;
        BigInteger latest=null;for(Fact fact:provider){if(fact==null||fact.timing().end()==null||fact.timing().errorUs()==null)return null;BigInteger bound=micros(fact.timing().end()).add(fact.timing().errorUs());if(!representable(bound))return null;if(latest==null||bound.compareTo(latest)>0)latest=bound;}
        BigInteger earliest=micros(order.timing().start()).subtract(order.timing().errorUs());if(!representable(earliest))return null;return earliest.subtract(latest);
    }
    private static boolean representable(BigInteger value){return value.compareTo(BigInteger.valueOf(-62135596800000000L))>=0&&value.compareTo(BigInteger.valueOf(253402300799999999L))<=0;}
    private static BigInteger micros(Instant i){return BigInteger.valueOf(i.getEpochSecond()).multiply(BigInteger.valueOf(1000000)).add(BigInteger.valueOf(i.getNano()/1000));}
    private static List<String> qualifiers(List<String> base,FrozenInput input,Fact...facts){var q=new TreeSet<>(base);for(Fact fact:facts)if(fact!=null){if(input.config().freshnessUs()!=null&&fact.timing().errorUs()==null)q.add("FRESHNESS_UNCERTAINTY_UNKNOWN");if(fact.timing().end()==null)q.add("OBSERVATION_AGE_UNKNOWN");else{BigInteger age=micros(input.referenceAt()).subtract(micros(fact.timing().end()));if(age.signum()<0)q.add("OBSERVATION_AFTER_REFERENCE");if(input.config().freshnessUs()!=null&&age.compareTo(input.config().freshnessUs())>0)q.add("DECLARED_FRESHNESS_EXCEEDED");}}return List.copyOf(q);}
    private static Finding finding(int rule,Classification cl,Fact subject,String reason,List<String> qualifiers,Map<String,String> values,List<SourceRef> refs){return new Finding("DOM-F-00"+rule,cl,switch(subject.kind()){case "order"->"ORDER";case "charge"->"CHARGE";default->"PAYMENT_INTENT";},subject.id(),reason,qualifiers,values,refs);}
    private static void money(List<Finding> out,Fact subject,Classification cl,String reason,List<String> q,Map<String,String> values,List<SourceRef> refs){for(int n=2;n<=3;n++)out.add(finding(n,cl,subject,reason,q,values,refs));}
    private static boolean positive(BigInteger n){return n!=null&&n.signum()>0;}
    private static String currency(String s){return s==null?null:s.toUpperCase(Locale.ROOT);}
    private static void unique(Map<String,Fact> map,Fact f){if(map.putIfAbsent(f.id(),f)!=null)throw new IllegalArgumentException("DUPLICATE_SCOPED_FACT");}
    private static int utf8(String a,String b){return Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
    private static Map<String,String> values(Fact order,Fact pi){Map<String,String> v=new TreeMap<>();for(String key:List.of("paymentReference","expectedAmount","expectedCurrency"))if(order.present().contains(key))v.put(key,key.equals("expectedCurrency")?currency(order.text(key)):order.text(key));if(order.present().contains("paymentState"))v.put("applicationState",order.text("paymentState"));if(pi!=null){if(pi.present().contains("amountReceived"))v.put("providerAmount",pi.text("amountReceived"));if(pi.present().contains("currency"))v.put("providerCurrency",currency(pi.text("currency")));if(pi.present().contains("status"))v.put("providerState",pi.text("status"));}return v;}
    private static List<SourceRef> refs(Fact order,Fact pi,Fact charge){List<SourceRef> out=new ArrayList<>();if(order!=null)for(String field:List.of("paymentReference","paymentState","expectedAmount","expectedCurrency","expectationRevision","observation"))out.add(new SourceRef("application",order.index(),"/"+field));if(pi!=null)for(String field:List.of("status","amountReceived","currency","latestChargeId","observation"))out.add(new SourceRef("provider",pi.index(),"/"+field));if(charge!=null)for(String field:List.of("paymentIntentId","captured","amountCaptured","observation"))out.add(new SourceRef("provider",charge.index(),"/"+field));return out;}
}
