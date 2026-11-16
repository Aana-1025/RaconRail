package dev.reconrail.evidence.codec;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Logical row charge, never a measurement of PostgreSQL physical storage. */
public final class AccountingCodec {
    private AccountingCodec() {}
    private static final Map<String,Object> MANIFEST=CanonicalCodec.load("accounting-manifest.json");
    public record Charge(byte[] structured,long totalBytes){public Charge{structured=structured.clone();} @Override public byte[] structured(){return structured.clone();}}
    public static Charge encode(String table,Map<String,Object> row){
        Map<String,Object> schema=null;for(Object t:(List<?>)MANIFEST.get("tables")){var candidate=StrictJson.object(t);if(table.equals(candidate.get("table")))schema=candidate;}
        if(schema==null)throw new EvidenceException();
        try{
            var bytes=new CanonicalCodec.BoundedOutput();var out=new DataOutputStream(bytes);long raw=0;Set<String> names=new HashSet<>();
            for(Object field:(List<?>)schema.get("fields")){
                var f=StrictJson.object(field);String name=(String)f.get("name");names.add(name);
                if(!row.containsKey(name))throw new EvidenceException();Object value=row.get(name);
                if(value==null&&!Boolean.TRUE.equals(f.get("nullable")))throw new EvidenceException();
                if(Boolean.TRUE.equals(f.get("excludeFromPreimage")))continue;
                if(Boolean.TRUE.equals(f.get("rawByteCharge"))){if(!(value instanceof byte[] b))throw new EvidenceException();raw=Math.addExact(raw,b.length);continue;}
                out.writeByte(value==null?1:2);if(value==null)continue;
                switch((String)f.get("type")){
                    case "u" -> {var u=EvidencePair.uuid(value);out.writeLong(u.getMostSignificantBits());out.writeLong(u.getLeastSignificantBits());}
                    case "i" -> out.writeLong(decimal(value).longValueExact());
                    case "x" -> {var n=decimal(value);if(n.signum()<0||n.bitLength()>64)throw new EvidenceException();out.writeLong(n.longValue());}
                    case "s" -> out.writeLong(StrictJson.micros(value));
                    case "b" -> {if(!(value instanceof Boolean b))throw new EvidenceException();out.writeBoolean(b);}
                    case "m" -> sized(out,StrictJson.integer(value).toString().getBytes(StandardCharsets.US_ASCII));
                    case "t" -> {if(!(value instanceof String s))throw new EvidenceException();sized(out,StrictJson.validText(s).getBytes(StandardCharsets.UTF_8));}
                    case "h" -> {if(!(value instanceof byte[] b))throw new EvidenceException();sized(out,b);}
                    case "j" -> sized(out,json(value).getBytes(StandardCharsets.UTF_8));
                    default -> throw new EvidenceException();
                }
            }
            if(!names.containsAll(row.keySet()))throw new EvidenceException();
            return new Charge(bytes.toByteArray(),Math.addExact(128,Math.addExact(bytes.size(),raw)));
        }catch(IOException|ArithmeticException e){throw new EvidenceException();}
    }
    private static BigInteger decimal(Object value){if(!(value instanceof String s)||!s.matches("0|-?[1-9][0-9]{0,38}"))throw new EvidenceException();return new BigInteger(s);}
    private static void sized(DataOutputStream out,byte[] value)throws IOException{if(value.length>65536)throw new EvidenceException();out.writeInt(value.length);out.write(value);}
    public static String json(Object value){return json(value,0);}
    private static String json(Object value,int depth){
        if(depth>16)throw new EvidenceException();
        if(value==null)return "null";
        if(value instanceof Boolean||value instanceof BigInteger)return value.toString();
        if(value instanceof String s){StrictJson.validText(s);StringBuilder b=new StringBuilder("\"");for(char c:s.toCharArray())switch(c){case '"' -> b.append("\\\"");case '\\' -> b.append("\\\\");case '\b' -> b.append("\\b");case '\f' -> b.append("\\f");case '\n' -> b.append("\\n");case '\r' -> b.append("\\r");case '\t' -> b.append("\\t");default -> {if(c<32)b.append(String.format(Locale.ROOT,"\\u%04x",(int)c));else b.append(c);}}return b.append('"').toString();}
        if(value instanceof Map<?,?>){var map=StrictJson.object(value);if(map.size()>32768)throw new EvidenceException();List<String> keys=new ArrayList<>(map.keySet());keys.sort((a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8)));var parts=new ArrayList<String>();int size=2;for(String k:keys){String part=json(k,depth+1)+":"+json(map.get(k),depth+1);size+=part.getBytes(StandardCharsets.UTF_8).length+1;if(size>65537)throw new EvidenceException();parts.add(part);}return "{"+String.join(",",parts)+"}";}
        if(value instanceof List<?> list){if(list.size()>10000)throw new EvidenceException();var parts=new ArrayList<String>();int size=2;for(Object item:list){String part=json(item,depth+1);size+=part.getBytes(StandardCharsets.UTF_8).length+1;if(size>65537)throw new EvidenceException();parts.add(part);}return "["+String.join(",",parts)+"]";}
        throw new EvidenceException();
    }
}
