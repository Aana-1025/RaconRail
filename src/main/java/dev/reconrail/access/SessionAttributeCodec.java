package dev.reconrail.access;

import dev.reconrail.evidence.codec.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.springframework.security.core.context.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.*;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Explicit allowlist. No class names, object serialization or polymorphic object construction. */
public final class SessionAttributeCodec {
    public static final String CONTEXT="SPRING_SECURITY_CONTEXT";
    public static final String CSRF="org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository.CSRF_TOKEN";
    public static final String PENDING="rr.pending";
    public static final String INDEX=org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;
    private final SessionEnvelope envelope;
    public SessionAttributeCodec(SessionEnvelope envelope){this.envelope=envelope;}
    public byte[] encode(String id,String name,Object value){
        try{
            Map<String,Object> model;
            if(name.equals(CONTEXT)&&value instanceof SecurityContext context&&context.getAuthentication()!=null&&context.getAuthentication().isAuthenticated()&&context.getAuthentication().getPrincipal() instanceof AppPrincipal p&&context.getAuthentication().getCredentials()==null){
                model=new TreeMap<>();model.put("account",p.accountId().toString());model.put("workspace",p.workspaceId().toString());model.put("generation",Long.toString(p.generation()));model.put("login",p.originalLogin().toString());model.put("activity",p.lastActivity().toString());model.put("authTime",p.authTime()==null?null:p.authTime().toString());
            }else if(name.equals(CSRF)&&value instanceof CsrfToken token){
                if(!token.getHeaderName().equals("X-CSRF-TOKEN")||!token.getParameterName().equals("_csrf"))throw SessionEnvelope.invalid();
                model=Map.of("token",token.getToken());
            }else if(name.equals(INDEX)&&value instanceof String principal&&principal.matches("[0-9a-f-]{36}:[1-9][0-9]{0,18}")){model=Map.of("principal",principal);}
            else if(name.equals(PENDING)&&value instanceof PendingLogins pending){
                List<Map<String,Object>> requests=new ArrayList<>();for(var entry:pending.requests()){
                    var request=entry.request();if(!request.getScopes().equals(Set.of("openid")))throw SessionEnvelope.invalid();
                    var attributes=strings(request.getAttributes(),Set.of("registration_id","nonce","code_verifier"));var parameters=strings(request.getAdditionalParameters(),Set.of("nonce","code_challenge","code_challenge_method","max_age"));
                    requests.add(Map.of("created",entry.createdAt().toString(),"authorizationUri",request.getAuthorizationUri(),"client",request.getClientId(),"redirect",request.getRedirectUri(),"state",request.getState(),"uri",request.getAuthorizationRequestUri(),"attributes",attributes,"parameters",parameters));
                }model=Map.of("requests",requests);
            }else throw SessionEnvelope.invalid();
            return envelope.encrypt(id,name,AccountingCodec.json(model).getBytes(StandardCharsets.UTF_8));
        }catch(RuntimeException e){throw SessionEnvelope.invalid();}
    }
    public Object decode(String id,String name,byte[] encoded){
        try{
            var model=StrictJson.object(StrictJson.parse(envelope.decrypt(id,name,encoded)));
            if(name.equals(INDEX)){exact(model,"principal");String p=string(model,"principal");if(!p.matches("[0-9a-f-]{36}:[1-9][0-9]{0,18}"))throw SessionEnvelope.invalid();return p;}
            return switch(name){
                case CONTEXT -> {
                    exact(model,"account","workspace","generation","login","activity","authTime");
                    var p=new AppPrincipal(UUID.fromString(string(model,"account")),UUID.fromString(string(model,"workspace")),Long.parseLong(string(model,"generation")),Instant.parse(string(model,"login")),Instant.parse(string(model,"activity")),model.get("authTime")==null?null:Instant.parse(string(model,"authTime")));
                    yield new SecurityContextImpl(UsernamePasswordAuthenticationToken.authenticated(p,null,List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                }
                case CSRF -> {exact(model,"token");yield new DefaultCsrfToken("X-CSRF-TOKEN","_csrf",string(model,"token"));}
                case PENDING -> {
                    exact(model,"requests");var entries=new ArrayList<PendingLogins.Entry>();for(Object item:(List<?>)model.get("requests")){
                        var request=StrictJson.object(item);exact(request,"created","authorizationUri","client","redirect","state","uri","attributes","parameters");
                        var attributes=strings(StrictJson.object(request.get("attributes")),Set.of("registration_id","nonce","code_verifier"));var parameters=strings(StrictJson.object(request.get("parameters")),Set.of("nonce","code_challenge","code_challenge_method","max_age"));
                        var auth=OAuth2AuthorizationRequest.authorizationCode().authorizationUri(string(request,"authorizationUri")).clientId(string(request,"client")).redirectUri(string(request,"redirect")).scopes(Set.of("openid")).state(string(request,"state")).authorizationRequestUri(string(request,"uri")).attributes(a->a.putAll(attributes)).additionalParameters(a->a.putAll(parameters)).build();
                        entries.add(new PendingLogins.Entry(auth,Instant.parse(string(request,"created"))));
                    }yield new PendingLogins(entries);
                }
                default -> throw SessionEnvelope.invalid();
            };
        }catch(RuntimeException e){throw SessionEnvelope.invalid();}
    }
    private static Map<String,Object> strings(Map<String,Object> map,Set<String> allowed){
        if(!allowed.containsAll(map.keySet()))throw SessionEnvelope.invalid();var copy=new TreeMap<String,Object>();for(var e:map.entrySet()){if(!(e.getValue() instanceof String value)||value.length()>16384)throw SessionEnvelope.invalid();copy.put(e.getKey(),value);}return copy;
    }
    private static String string(Map<String,Object> map,String key){Object value=map.get(key);if(!(value instanceof String s)||s.length()>16384)throw SessionEnvelope.invalid();return s;}
    private static void exact(Map<String,Object> map,String...keys){if(!map.keySet().equals(Set.of(keys)))throw SessionEnvelope.invalid();}
}
