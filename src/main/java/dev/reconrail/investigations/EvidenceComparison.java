package dev.reconrail.investigations;

import dev.reconrail.evidence.codec.*;
import dev.reconrail.investigations.domain.*;
import static dev.reconrail.investigations.domain.ComparisonModel.*;
import java.util.*;
import java.math.BigInteger;

/** Boundary adapter: validate both projections before constructing frozen domain facts. */
public final class EvidenceComparison {
    private EvidenceComparison() {}
    public static FrozenInput freeze(String inputId,byte[] provider,byte[] application,
            Map<String,Object> providerNamespace,Map<String,Object> appNamespace,
            Map<String,Object> config,Versions versions,String referenceAt){
        if(!inputId.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"))throw new EvidenceException();
        var pair=EvidencePair.validate(provider,application,providerNamespace,appNamespace);
        EvidencePair.validateConfig(config);
        var declarations=new TreeMap<String,Boolean>();StrictJson.object(config.get("flowDeclarations")).forEach((k,v)->declarations.put(k,(Boolean)v));
        var frozenConfig=new Config(config.get("graceUs")==null?null:StrictJson.integer(config.get("graceUs")),config.get("freshnessLimitUs")==null?null:StrictJson.integer(config.get("freshnessLimitUs")),declarations,config.keySet());
        return new FrozenInput(inputId,pair.provider().sha256(),pair.application().sha256(),facts(pair.provider()),facts(pair.application()),frozenConfig,versions,StrictJson.instant(referenceAt),(String)pair.provider().envelope().get("coverage"),(String)pair.application().envelope().get("coverage"));
    }
    private static List<Fact> facts(EvidencePair.Artifact artifact){
        var rows=(List<?>)artifact.envelope().get("records");var output=new ArrayList<Fact>();
        for(int index=0;index<rows.size();index++){
            var row=StrictJson.object(rows.get(index));Map<String,String> values=new TreeMap<>();Map<String,Boolean> flags=new TreeMap<>();
            for(var field:row.entrySet())if(!Set.of("kind","id","observation").contains(field.getKey())){
                if(Set.of("paid","captured","disputed").contains(field.getKey()))flags.put(field.getKey(),(Boolean)field.getValue());else values.put(field.getKey(),(String)field.getValue());
            }
            var timing=StrictJson.object(row.get("observation"));
            output.add(new Fact((String)row.get("kind"),(String)row.get("id"),values,flags,row.keySet(),new Timing(timing.get("readStart")==null?null:StrictJson.instant(timing.get("readStart")),timing.get("readEnd")==null?null:StrictJson.instant(timing.get("readEnd")),timing.get("clockErrorUs")==null?null:StrictJson.integer(timing.get("clockErrorUs")),timing.keySet()),index));
        }
        return List.copyOf(output);
    }
    public static Map<String,Object> semantic(Result result){
        var findings=new ArrayList<Map<String,Object>>();for(var f:result.findings())findings.add(Map.of("ruleId",f.ruleId(),"classification",f.classification().name(),"subjectKind",f.subjectKind(),"subjectId",f.subjectId(),"explanationCode",f.explanationCode(),"qualifiers",f.qualifiers(),"observedValues",f.observedValues()));
        Map<String,Object> checks=new TreeMap<>();result.checks().forEach((name,c)->checks.put(name,Map.of("eligible",Long.toString(c.eligible()),"consistent",Long.toString(c.consistent()),"discrepancy",Long.toString(c.discrepancy()),"moreEvidenceNeeded",Long.toString(c.moreEvidenceNeeded()),"unsupported",Long.toString(c.unsupported()),"ambiguous",Long.toString(c.ambiguous()))));
        return Map.of("inputCounts",Map.of("paymentIntents",Long.toString(result.paymentIntents()),"charges",Long.toString(result.charges()),"orders",Long.toString(result.orders())),"checks",checks,"qualifiers",result.qualifiers(),"findings",List.copyOf(findings));
    }
    public static String semanticDigest(Result result){return EvidencePair.hash(CanonicalCodec.encode("rr.semantic.v1","SemanticResult",semantic(result)));}
}
