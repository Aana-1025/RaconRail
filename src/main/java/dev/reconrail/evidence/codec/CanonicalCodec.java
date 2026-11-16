package dev.reconrail.evidence.codec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Fixed manifest ordering and explicit presence markers; independent of JSON map order. */
public final class CanonicalCodec {
    private CanonicalCodec() {}
    private static final Map<String,Object> MANIFEST=load("codec-manifest.json");
    static Map<String,Object> load(String name){try(var in=CanonicalCodec.class.getResourceAsStream("/codec/"+name)){if(in==null)throw new IllegalStateException("Missing codec schema");return StrictJson.object(StrictJson.parse(in.readAllBytes()));}catch(IOException e){throw new IllegalStateException(e);}}
    public static byte[] encode(String domain,String layout,Map<String,Object> value){
        if(!StrictJson.object(MANIFEST.get("domains")).containsValue(domain))throw new EvidenceException();
        try{var bytes=new BoundedOutput();var out=new DataOutputStream(bytes);out.write(domain.getBytes(StandardCharsets.US_ASCII));out.writeByte(0);out.writeInt(1);object(out,layout,value,0);return bytes.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}
    }
    static List<?> fields(String layout){Object value=StrictJson.object(MANIFEST.get("layouts")).get(layout);if(!(value instanceof List<?> l))throw new EvidenceException();return l;}
    public static void checkLayout(String layout,Map<String,Object> value){try{object(new DataOutputStream(new BoundedOutput()),layout,value,0);}catch(IOException e){throw new IllegalStateException(e);}}
    private static void object(DataOutputStream out,String layout,Map<String,Object> value,int depth)throws IOException{
        if(depth>16)throw new EvidenceException();
        List<?> fields=fields(layout);Set<String> names=new HashSet<>();for(Object f:fields)names.add((String)StrictJson.object(f).get("name"));if(!names.containsAll(value.keySet()))throw new EvidenceException();
        out.writeInt(fields.size());for(Object field:fields){var f=StrictJson.object(field);String name=(String)f.get("name");if(!value.containsKey(name)){if(Boolean.TRUE.equals(f.get("required")))throw new EvidenceException();out.writeByte(0);}else if(value.get(name)==null){if(!Boolean.TRUE.equals(f.get("nullable")))throw new EvidenceException();out.writeByte(1);}else{out.writeByte(2);typed(out,(String)f.get("type"),value.get(name),depth+1);}}
    }
    private static void sized(DataOutputStream out,byte[] bytes)throws IOException{if(bytes.length>65536)throw new EvidenceException();out.writeInt(bytes.length);out.write(bytes);}
    private static void typed(DataOutputStream out,String type,Object value,int depth)throws IOException{
        if(depth>16)throw new EvidenceException();
        if(type.startsWith("object:")){out.writeByte(8);object(out,type.substring(7),StrictJson.object(value),depth);return;}
        if(type.startsWith("list:")){if(!(value instanceof List<?> list)||list.size()>(type.equals("list:object:SemanticFinding")?60000:10000))throw new EvidenceException();out.writeByte(7);out.writeInt(list.size());for(Object item:list){if(item==null)throw new EvidenceException();typed(out,type.substring(5),item,depth+1);}return;}
        switch(type){
            case "text" -> {if(!(value instanceof String s))throw new EvidenceException();out.writeByte(1);sized(out,StrictJson.validText(s).getBytes(StandardCharsets.UTF_8));}
            case "integer" -> {out.writeByte(2);sized(out,StrictJson.integer(value).toString().getBytes(StandardCharsets.US_ASCII));}
            case "uuid" -> {var u=EvidencePair.uuid(value);out.writeByte(3);out.writeLong(u.getMostSignificantBits());out.writeLong(u.getLeastSignificantBits());}
            case "timestamp" -> {out.writeByte(4);out.writeLong(StrictJson.micros(value));}
            case "boolean" -> {if(!(value instanceof Boolean b))throw new EvidenceException();out.writeByte(5);out.writeBoolean(b);}
            case "hexbytes" -> {if(!(value instanceof String s)||(s.length()>131072 || (s.length()&1)!=0 || s.chars().anyMatch(c->!((c>='0'&&c<='9')||(c>='a'&&c<='f')))))throw new EvidenceException();out.writeByte(6);sized(out,HexFormat.of().parseHex(s));}
            default -> throw new EvidenceException();
        }
    }
    static final class BoundedOutput extends ByteArrayOutputStream{
        private void bound(int add){if(add<0||count>64*1024*1024-add)throw new EvidenceException();}
        @Override public synchronized void write(int value){bound(1);super.write(value);}
        @Override public synchronized void write(byte[] b,int off,int len){bound(len);super.write(b,off,len);}
    }
}
