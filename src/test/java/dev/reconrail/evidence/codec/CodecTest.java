package dev.reconrail.evidence.codec;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CodecTest {
    byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    @Test void strictJsonRejectsAdversarialInputs(){
        for(String s:List.of("{\"x\":1,\"x\":2}","{\"x\":1,\"\\u0078\":2}","{} {}","[1.5]","[1e2]","[\"\\ud800\"]","[\"\\u0000\"]","\ufeff{}","[NaN]","[".repeat(17)+"]".repeat(17))){assertThrows(EvidenceException.class,()->StrictJson.parse(bytes(s)),s);}
        assertThrows(EvidenceException.class,()->StrictJson.parse(new byte[]{(byte)0xc0,(byte)0x80}));
        assertThrows(EvidenceException.class,()->StrictJson.parse(new byte[StrictJson.MAX_FILE+1]));
    }
    @Test void scalarsRejectCoercionAndInvalidDates(){
        for(String s:List.of("-0","01","+1","1e2","1.1"," 1","1\n"))assertThrows(EvidenceException.class,()->StrictJson.integer(s));
        for(String s:List.of("2025-02-29T00:00:00Z","0000-01-01T00:00:00Z","2026-01-01T00:00:60Z","2026-01-01T00:00:00.0000001Z"))assertThrows(EvidenceException.class,()->StrictJson.instant(s));
        assertEquals(-1,StrictJson.micros("1969-12-31T23:59:59.999999Z"));
    }
    @Test void existingGoldenCanonicalVectorsMatch()throws Exception{
        var vectors=StrictJson.object(StrictJson.parse(Files.readAllBytes(Path.of("docs/04-apis-integrations/codec-golden-vectors.json"))));
        for(Object item:(List<?>)vectors.get("vectors")){
            var v=StrictJson.object(item);byte[] encoded=CanonicalCodec.encode((String)v.get("domain"),(String)v.get("layout"),StrictJson.object(v.get("input")));
            assertEquals(v.get("canonicalHex"),HexFormat.of().formatHex(encoded),String.valueOf(v.get("label")));
            assertEquals(v.get("sha256"),EvidencePair.hash(encoded));
        }
    }
    @Test void literalIndependentPresenceVector(){
        assertEquals("72722e636f6e6669672e763100000000010000000100",HexFormat.of().formatHex(CanonicalCodec.encode("rr.config.v1","PurposeContext",Map.of())));
        assertEquals("72722e636f6e6669672e7631000000000100000001020100000000",HexFormat.of().formatHex(CanonicalCodec.encode("rr.config.v1","PurposeContext",Map.of("purpose",""))));
    }
    @Test void fullSyntheticPairValidatesAndHashes()throws Exception{
        byte[] p=Files.readAllBytes(Path.of("docs/04-apis-integrations/examples/provider.json")),a=Files.readAllBytes(Path.of("docs/04-apis-integrations/examples/application.json"));
        var root=StrictJson.object(StrictJson.parse(p));var pair=EvidencePair.validate(p,a,StrictJson.object(root.get("providerNamespace")),StrictJson.object(root.get("appNamespace")));
        assertEquals(EvidencePair.hash(p),pair.provider().sha256());
        String changed=new String(a,StandardCharsets.UTF_8).replace("\"observation\":", "\"secret\":\"DO_NOT_ECHO\",\"observation\":");
        var error=assertThrows(EvidenceException.class,()->EvidencePair.validate(p,bytes(changed),StrictJson.object(root.get("providerNamespace")),StrictJson.object(root.get("appNamespace"))));
        assertEquals("INVALID_EVIDENCE",error.getMessage());assertNull(error.getCause());
    }
    @Test void accountingHasIndependentByteOracle(){
        var row=new LinkedHashMap<String,Object>();row.put("account_id","00000000-0000-0000-0000-000000000001");row.put("state","ACTIVE");row.put("generation","1");row.put("closed_at",null);row.put("created_at","1970-01-01T00:00:00Z");row.put("updated_at","1970-01-01T00:00:00Z");row.put("row_version","1");
        var charge=AccountingCodec.encode("rr.account",row);
        assertEquals("0200000000000000000000000000000001020000000641435449564502000000000000000101020000000000000000020000000000000000",HexFormat.of().formatHex(charge.structured()));
        assertEquals(56,charge.structured().length); // row_version is explicitly excluded by the accounting manifest.
        assertEquals(184,charge.totalBytes());
    }
    @Test void rejectMalformedEvidenceAcrossEveryRecord()throws Exception{
        var source=StrictJson.object(StrictJson.parse(Files.readAllBytes(Path.of("docs/04-apis-integrations/examples/provider.json"))));
        for(String scenario:List.of("duplicate","unknown","numeric","live","interval","unicode","empty-id","overlong-status")){
            var envelope=new LinkedHashMap<String,Object>(source);var records=new ArrayList<Object>((List<?>)source.get("records"));var record=new LinkedHashMap<>(StrictJson.object(records.getFirst()));
            switch(scenario){
                case "duplicate" -> records.add(records.getFirst());
                case "unknown" -> record.put("apiKey","DO_NOT_ECHO");
                case "numeric" -> record.put("amount",java.math.BigInteger.ONE);
                case "live" -> {var ns=new LinkedHashMap<>(StrictJson.object(source.get("providerNamespace")));ns.put("livemode",true);envelope.put("providerNamespace",ns);}
                case "interval" -> record.put("observation",Map.of("readStart","2026-01-02T00:00:00Z","readEnd","2026-01-01T00:00:00Z"));
                case "unicode" -> record.put("id","\u0000");
                case "empty-id" -> record.put("id","");
                case "overlong-status" -> record.put("status","x".repeat(65));
            }
            records.set(0,record);envelope.put("records",records);
            assertThrows(EvidenceException.class,()->EvidencePair.artifact(bytes(AccountingCodec.json(envelope)),"provider"),scenario);
        }
    }
    @Test void emptyEvidenceIsLimitedAndLocatorsAreAllowlisted()throws Exception{
        var root=new LinkedHashMap<>(StrictJson.object(StrictJson.parse(Files.readAllBytes(Path.of("docs/04-apis-integrations/examples/provider.json")))));
        var original=EvidencePair.artifact(bytes(AccountingCodec.json(root)),"provider");
        assertEquals("/records/0/amount",EvidencePair.locator(original,0,"amount").pointer());
        assertThrows(EvidenceException.class,()->EvidencePair.locator(original,0,"secret"));
        root.put("records",List.of());assertEquals(List.of(),EvidencePair.artifact(bytes(AccountingCodec.json(root)),"provider").envelope().get("records"));
    }
    @Test void allLayoutsHaveExplicitRequiredAndUnknownFieldValidation(){
        var layouts=StrictJson.object(CanonicalCodec.load("codec-manifest.json").get("layouts"));assertEquals(33,layouts.size());
        for(String name:layouts.keySet())assertThrows(EvidenceException.class,()->CanonicalCodec.checkLayout(name,Map.of("notAllowed",true)));
    }
    @Test void packagedManifestsMatchApprovedContracts()throws Exception{
        for(String file:List.of("codec-manifest.json","accounting-manifest.json")){
            var expected=new LinkedHashMap<>(StrictJson.object(StrictJson.parse(Files.readAllBytes(Path.of("docs/04-apis-integrations/"+file)))));expected.remove("status");
            assertEquals(expected,CanonicalCodec.load(file));
        }
    }
    @Test void unsignedTransactionMarkerAddsPresenceAndEightBytes(){
        var schema=CanonicalCodec.load("accounting-manifest.json");var table=((List<?>)schema.get("tables")).stream().map(StrictJson::object).filter(t->t.get("table").equals("rr.input_version")).findFirst().orElseThrow();
        var row=new LinkedHashMap<String,Object>();
        for(Object field:(List<?>)table.get("fields")){var f=StrictJson.object(field);Object value=switch((String)f.get("type")){case "u" -> "00000000-0000-0000-0000-000000000001";case "i" -> "0";case "s" -> "1970-01-01T00:00:00Z";case "j" -> Map.of();case "h" -> new byte[32];case "x" -> "18446744073709551615";default -> "x";};row.put((String)f.get("name"),value);}
        byte[] encoded=AccountingCodec.encode("rr.input_version",row).structured();
        assertEquals("02ffffffffffffffff",HexFormat.of().formatHex(Arrays.copyOfRange(encoded,encoded.length-9,encoded.length)));
        row.put("accepted_transaction","18446744073709551616");assertThrows(EvidenceException.class,()->AccountingCodec.encode("rr.input_version",row));
    }
    @Test void allAccountingLayoutsHaveIndependentLengthChecks(){
        var tables=(List<?>)CanonicalCodec.load("accounting-manifest.json").get("tables");assertEquals(24,tables.size());
        for(Object item:tables){var t=StrictJson.object(item);var row=new LinkedHashMap<String,Object>();long expected=128;
            for(Object field:(List<?>)t.get("fields")){var f=StrictJson.object(field);String type=(String)f.get("type");Object value=switch(type){case "u" -> "00000000-0000-0000-0000-000000000001";case "i","x" -> "1";case "s" -> "1970-01-01T00:00:00Z";case "m" -> "123";case "b" -> true;case "h" -> new byte[]{1,2,3};case "j" -> Map.of("a",java.math.BigInteger.ONE);default -> "\u00e9";};row.put((String)f.get("name"),value);
                if(Boolean.TRUE.equals(f.get("excludeFromPreimage")))continue;
                if(Boolean.TRUE.equals(f.get("rawByteCharge"))){expected+=3;continue;}
                expected+=1+switch(type){case "u" -> 16;case "i","x","s" -> 8;case "m","h" -> 7;case "b" -> 1;case "j" -> 11;default -> 6;};
            }
            assertEquals(expected,AccountingCodec.encode((String)t.get("table"),row).totalBytes(),(String)t.get("table"));
        }
    }
    @Test void byteScalarAndRecordLimitsAreEnforced(){
        byte[] exact=new byte[StrictJson.MAX_FILE];Arrays.fill(exact,(byte)' ');exact[0]='{';exact[1]='}';assertEquals(Map.of(),StrictJson.parse(exact));
        assertThrows(EvidenceException.class,()->StrictJson.parse(bytes("["+"0,".repeat(10000)+"0]")));
        assertThrows(EvidenceException.class,()->StrictJson.parse(bytes("[\""+"x".repeat(65537)+"\"]")));
    }
    @Test void presenceAndPairIdentityRemainDistinct()throws Exception{
        byte[] absent=CanonicalCodec.encode("rr.config.v1","Observation",Map.of());var nullable=new HashMap<String,Object>();nullable.put("readStart",null);
        assertFalse(Arrays.equals(absent,CanonicalCodec.encode("rr.config.v1","Observation",nullable)));
        byte[] p=Files.readAllBytes(Path.of("docs/04-apis-integrations/examples/provider.json")),a=Files.readAllBytes(Path.of("docs/04-apis-integrations/examples/application.json"));var root=StrictJson.object(StrictJson.parse(p));
        var changed=new LinkedHashMap<>(StrictJson.object(StrictJson.parse(a)));changed.put("captureId","00000000-0000-4000-8000-000000000099");
        assertThrows(EvidenceException.class,()->EvidencePair.validate(p,bytes(AccountingCodec.json(changed)),StrictJson.object(root.get("providerNamespace")),StrictJson.object(root.get("appNamespace"))));
    }
    @Test void configAndContextNeverInventDefaults(){
        var flow=new LinkedHashMap<String,Object>();for(String key:List.of("oneTime","fullOriginal","automaticCapture","noAdjustments","noSubscription","noConnect","noSplit"))flow.put(key,null);
        EvidencePair.validateConfig(Map.of("flowDeclarations",flow));
        assertThrows(EvidenceException.class,()->EvidencePair.validateConfig(Map.of("flowDeclarations",flow,"graceUs","-1")));
        assertThrows(EvidenceException.class,()->EvidencePair.validateConfig(Map.of("flowDeclarations",flow,"freshnessLimitUs","315576000000000001")));
        assertThrows(EvidenceException.class,()->EvidencePair.validatePurpose(Map.of("purpose","x".repeat(1025))));
    }
}
