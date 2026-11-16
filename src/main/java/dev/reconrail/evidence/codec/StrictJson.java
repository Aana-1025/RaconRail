package dev.reconrail.evidence.codec;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.*;
import java.util.*;
import tools.jackson.core.*;
import tools.jackson.core.json.JsonFactory;

/** Strict bounded JSON model preserving absent versus explicit null. */
public final class StrictJson {
    private StrictJson() {}
    public static final int MAX_FILE = 10 * 1024 * 1024;
    private static final JsonFactory FACTORY = JsonFactory.builder().streamReadConstraints(
        StreamReadConstraints.builder().maxNestingDepth(16).maxStringLength(65536)
            .maxNameLength(65536).maxNumberLength(64).build()).build();
    public static Object parse(byte[] bytes) {
        try {
            if (bytes.length > MAX_FILE || bytes.length == 0) throw new EvidenceException();
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            if (text.charAt(0) == '\ufeff') throw new EvidenceException();
            try (JsonParser parser = FACTORY.createParser(text)) {
                parser.nextToken(); Object value = read(parser);
                if (parser.nextToken() != null) throw new EvidenceException();
                return value;
            }
        } catch (Exception error) { throw new EvidenceException(); }
    }
    private static Object read(JsonParser p) {
        return switch (p.currentToken()) {
            case START_OBJECT -> {
                Map<String,Object> map = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    if (p.currentToken() != JsonToken.PROPERTY_NAME) throw new EvidenceException();
                    String name = validText(p.currentName());
                    if (map.containsKey(name)) throw new EvidenceException();
                    p.nextToken(); map.put(name, read(p));
                }
                yield Collections.unmodifiableMap(map);
            }
            case START_ARRAY -> {
                List<Object> list = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    if (list.size() >= 10000) throw new EvidenceException();
                    list.add(read(p));
                }
                yield Collections.unmodifiableList(list);
            }
            case VALUE_STRING -> validText(p.getString());
            case VALUE_NUMBER_INT -> p.getBigIntegerValue();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_NULL -> null;
            default -> throw new EvidenceException();
        };
    }
    public static String validText(String value) {
        for (int n=0;n<value.length();n++) {
            char c=value.charAt(n);
            if (c==0 || Character.isLowSurrogate(c)) throw new EvidenceException();
            if (Character.isHighSurrogate(c) && (++n>=value.length() || !Character.isLowSurrogate(value.charAt(n)))) throw new EvidenceException();
        }
        if (value.getBytes(StandardCharsets.UTF_8).length>65536) throw new EvidenceException();
        return value;
    }
    public static BigInteger integer(Object value) {
        if (!(value instanceof String s) || !s.matches("0|-?[1-9][0-9]{0,37}")) throw new EvidenceException();
        return new BigInteger(s);
    }
    public static Instant instant(Object value) {
        if (!(value instanceof String s) || !s.matches("(?!0000)[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-5][0-9](\\.[0-9]{1,6})?Z")) throw new EvidenceException();
        try { return LocalDateTime.parse(s.substring(0,s.length()-1)).toInstant(ZoneOffset.UTC); }
        catch (DateTimeException error) { throw new EvidenceException(); }
    }
    public static long micros(Object value) {
        Instant i=instant(value);
        return Math.addExact(Math.multiplyExact(i.getEpochSecond(),1000000),i.getNano()/1000);
    }
    @SuppressWarnings("unchecked")
    public static Map<String,Object> object(Object value) {
        if (!(value instanceof Map<?,?>)) throw new EvidenceException();
        return (Map<String,Object>)value;
    }
}
