package dev.reconrail.access;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import java.security.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Session-bound AEAD envelope. Fresh process keys require session invalidation before readiness. */
public final class SessionEnvelope {
    private String active;
    private final Map<String,SecretKey> keys;
    private final Map<String,java.time.Instant> retireAfter=new HashMap<>();
    private final AtomicLong uses=new AtomicLong();
    private final SecureRandom random=new SecureRandom();
    public SessionEnvelope(String active,Map<String,SecretKey> keys){
        if(keys.size()>32||active==null||!active.matches("[A-Za-z0-9_-]{1,32}")||!keys.containsKey(active))throw invalid();
        for(SecretKey key:keys.values())if(!"AES".equals(key.getAlgorithm())||key.getEncoded().length!=32)throw invalid();
        this.active=active;this.keys=new HashMap<>(keys);for(String id:keys.keySet())if(!id.equals(active))retireAfter.put(id,java.time.Instant.now().plusSeconds(28800));
    }
    public static SessionEnvelope ephemeral(){try{var generator=KeyGenerator.getInstance("AES");generator.init(256);String id=UUID.randomUUID().toString().replace("-","");return new SessionEnvelope(id,Map.of(id,generator.generateKey()));}catch(GeneralSecurityException e){throw invalid();}}
    public synchronized byte[] encrypt(String session,String attribute,byte[] plaintext){
        try{
            // Reserve the maximum envelope overhead so the encoded attribute is <=64KiB.
            if(plaintext.length>65474)throw invalid();
            if(uses.get()>=999998)rotate();
            uses.incrementAndGet();
            byte[] nonce=new byte[12];random.nextBytes(nonce);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,keys.get(active),new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(session,attribute));
            byte[] id=active.getBytes(StandardCharsets.US_ASCII),encrypted=cipher.doFinal(plaintext);
            return ByteBuffer.allocate(2+id.length+12+encrypted.length).put((byte)1).put((byte)id.length).put(id).put(nonce).put(encrypted).array();
        }catch(GeneralSecurityException|RuntimeException e){throw invalid();}
    }
    public synchronized byte[] decrypt(String session,String attribute,byte[] encoded){
        try{
            if(encoded.length>65536||encoded.length<31)throw invalid();var input=ByteBuffer.wrap(encoded);
            if(input.get()!=1)throw invalid();int length=Byte.toUnsignedInt(input.get());if(length<1||length>32||input.remaining()<length+28)throw invalid();
            byte[] id=new byte[length];input.get(id);String keyId=new String(id,StandardCharsets.US_ASCII);SecretKey key=keys.get(keyId);if(key==null||(retireAfter.containsKey(keyId)&&!java.time.Instant.now().isBefore(retireAfter.get(keyId))))throw invalid();
            byte[] nonce=new byte[12];input.get(nonce);byte[] encrypted=new byte[input.remaining()];input.get(encrypted);
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,nonce));cipher.updateAAD(aad(session,attribute));return cipher.doFinal(encrypted);
        }catch(GeneralSecurityException|RuntimeException e){throw invalid();}
    }
    private void rotate()throws GeneralSecurityException{
        var now=java.time.Instant.now();for(String id:new ArrayList<>(retireAfter.keySet()))if(!now.isBefore(retireAfter.get(id))){retireAfter.remove(id);keys.remove(id);}
        if(keys.size()>=32)throw invalid();
        var generator=KeyGenerator.getInstance("AES");generator.init(256);retireAfter.put(active,now.plusSeconds(28800));active=UUID.randomUUID().toString().replace("-","");keys.put(active,generator.generateKey());uses.set(0);
    }
    private static byte[] aad(String session,String attribute){
        if(session==null||session.length()>128||attribute==null||attribute.length()>200)throw invalid();
        byte[] s=session.getBytes(StandardCharsets.UTF_8),a=attribute.getBytes(StandardCharsets.UTF_8);
        return ByteBuffer.allocate(16+s.length+a.length).putInt(0x52525331).putInt(1).putInt(s.length).put(s).putInt(a.length).put(a).array();
    }
    static IllegalArgumentException invalid(){return new IllegalArgumentException("INVALID_SESSION");}
}
