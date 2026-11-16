package dev.reconrail.evidence.codec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Validates a complete pair before returning any accepted evidence. No persistence or I/O. */
public final class EvidencePair {
    private EvidencePair() {}
    public record Artifact(Map<String,Object> envelope, String sha256) {}
    public record Pair(Artifact provider, Artifact application) {}
    private static final Set<String> ROOT=Set.of("schemaVersion","role","captureId","providerNamespace","appNamespace","sourceVersion","exporterVersion","capturedAt","coverage","records");
    public static Pair validate(byte[] provider,byte[] application,Map<String,Object> expectedProvider,Map<String,Object> expectedApp) {
        Artifact p=artifact(provider,"provider"),a=artifact(application,"application");
        for (String name:List.of("captureId","providerNamespace","appNamespace"))
            if (!p.envelope.get(name).equals(a.envelope.get(name))) throw new EvidenceException();
        if (!p.envelope.get("providerNamespace").equals(expectedProvider) || !p.envelope.get("appNamespace").equals(expectedApp)) throw new EvidenceException();
        return new Pair(p,a);
    }
    public static Artifact artifact(byte[] bytes,String role) {
        try { return checkedArtifact(bytes.clone(),role); } catch (RuntimeException error) { throw new EvidenceException(); }
    }
    private static Artifact checkedArtifact(byte[] bytes,String role) {
        Map<String,Object> root=StrictJson.object(StrictJson.parse(bytes));
        if (!root.keySet().equals(ROOT) || !"rr-evidence-v1".equals(root.get("schemaVersion")) || !role.equals(root.get("role"))) throw new EvidenceException();
        uuid(root.get("captureId"));
        var provider=StrictJson.object(root.get("providerNamespace"));
        if (!provider.keySet().equals(Set.of("provider","accountId","sandboxId","livemode")) || !"stripe".equals(provider.get("provider")) || !Boolean.FALSE.equals(provider.get("livemode"))) throw new EvidenceException();
        bounded(provider.get("accountId"),255);bounded(provider.get("sandboxId"),255);
        var app=StrictJson.object(root.get("appNamespace"));
        if (!app.keySet().equals(Set.of("systemId","environmentId"))) throw new EvidenceException();
        bounded(app.get("systemId"),255);bounded(app.get("environmentId"),255);
        if (!(role.equals("provider") ? "2025-09-30.clover" : "rr-app-export-v1").equals(root.get("sourceVersion"))) throw new EvidenceException();
        bounded(root.get("exporterVersion"),128);StrictJson.instant(root.get("capturedAt"));
        if (!Set.of("selected","complete","unknown").contains(root.get("coverage"))) throw new EvidenceException();
        if (!(root.get("records") instanceof List<?> records)) throw new EvidenceException();
        Set<String> identities=new HashSet<>();
        for (Object row:records) {
            var record=StrictJson.object(row);Object kind=record.get("kind");
            String layout=switch(String.valueOf(kind)) {case "payment_intent" -> "PaymentIntent";case "charge" -> "Charge";case "order" -> "Order";default -> throw new EvidenceException();};
            if (role.equals("provider")==layout.equals("Order")) throw new EvidenceException();
            CanonicalCodec.checkLayout(layout,record);
            bounded(record.get("id"),255);
            if (!identities.add(kind+"\u0000"+record.get("id"))) throw new EvidenceException();
            for (var field:record.entrySet()) {
                String name=field.getKey();Object value=field.getValue();if(value==null)continue;
                if (Set.of("amount","amountReceived","amountCapturable","amountCaptured","amountRefunded","expectedAmount").contains(name)) StrictJson.integer(value);
                else if(name.equals("currency")||name.equals("expectedCurrency")) { if(!(value instanceof String s)||!s.matches("[A-Za-z]{3}"))throw new EvidenceException(); }
                else if(Set.of("status","captureMethod","actualMethodType","paymentState","rawPaymentState").contains(name)) bounded(value,64);
                else if(Set.of("latestChargeId","paymentIntentId","paymentReference","expectationRevision").contains(name))bounded(value,255);
            }
            var observation=StrictJson.object(record.get("observation"));
            if(observation.get("readStart")!=null && observation.get("readEnd")!=null && StrictJson.instant(observation.get("readEnd")).isBefore(StrictJson.instant(observation.get("readStart"))))throw new EvidenceException();
            if(observation.get("clockErrorUs")!=null){var d=StrictJson.integer(observation.get("clockErrorUs"));if(d.signum()<0||d.compareTo(new java.math.BigInteger("315576000000000000"))>0)throw new EvidenceException();}
        }
        return new Artifact(root,hash(bytes));
    }
    public static void validateConfig(Map<String,Object> config){
        CanonicalCodec.checkLayout("RunConfig",config);
        for(String key:List.of("graceUs","freshnessLimitUs"))if(config.get(key)!=null){var duration=StrictJson.integer(config.get(key));if(duration.signum()<0||duration.compareTo(new java.math.BigInteger("315576000000000000"))>0)throw new EvidenceException();}
        if(CanonicalCodec.encode("rr.config.v1","RunConfig",config).length>65536)throw new EvidenceException();
    }
    public static void validatePurpose(Map<String,Object> purpose){
        CanonicalCodec.checkLayout("PurposeContext",purpose);
        if(purpose.containsKey("purpose")&&((String)purpose.get("purpose")).getBytes(StandardCharsets.UTF_8).length>1024)throw new EvidenceException();
    }
    public record Locator(String role,int recordIndex,String pointer) {}
    public static Locator locator(Artifact artifact,int index,String field){
        var records=(List<?>)artifact.envelope().get("records");
        if(index<0||index>=records.size())throw new EvidenceException();
        var record=StrictJson.object(records.get(index));
        String layout=switch((String)record.get("kind")){case "payment_intent" -> "PaymentIntent";case "charge" -> "Charge";case "order" -> "Order";default -> throw new EvidenceException();};
        if(CanonicalCodec.fields(layout).stream().noneMatch(f->field.equals(StrictJson.object(f).get("name"))))throw new EvidenceException();
        return new Locator((String)artifact.envelope().get("role"),index,"/records/"+index+"/"+field);
    }
    static void bounded(Object value,int max){if(!(value instanceof String s)||s.isEmpty()||StrictJson.validText(s).getBytes(StandardCharsets.UTF_8).length>max)throw new EvidenceException();}
    static UUID uuid(Object value){if(!(value instanceof String s)||!s.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new EvidenceException();return UUID.fromString(s);}
    public static String hash(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
