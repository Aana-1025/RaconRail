package dev.reconrail.access;

import jakarta.servlet.http.*;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import java.time.*;
import java.util.*;

/** Single-use browser-bound states, at most two pending requests and five-minute expiry. */
public final class BoundedAuthorizationRequests implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {
    private final Clock clock;
    public BoundedAuthorizationRequests(Clock clock){this.clock=clock;}
    @Override public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request){
        var session=request.getSession(false);if(session==null)return null;PendingLogins pending=(PendingLogins)session.getAttribute(SessionAttributeCodec.PENDING);if(pending==null)return null;
        String state=request.getParameter("state");if(state==null||state.length()>512)return null;
        for(var entry:pending.requests())if(entry.request().getState().equals(state)&&!clock.instant().isBefore(entry.createdAt())&&clock.instant().isBefore(entry.createdAt().plusSeconds(300)))return entry.request();return null;
    }
    @Override public void saveAuthorizationRequest(OAuth2AuthorizationRequest value,HttpServletRequest request,HttpServletResponse response){
        if(value==null){removeAuthorizationRequest(request,response);return;}
        HttpSession session=request.getSession();PendingLogins existing=(PendingLogins)session.getAttribute(SessionAttributeCodec.PENDING);List<PendingLogins.Entry> entries=new ArrayList<>();
        if(existing!=null)for(var e:existing.requests())if(clock.instant().isBefore(e.createdAt().plusSeconds(300))&&!clock.instant().isBefore(e.createdAt()))entries.add(e);
        if(entries.size()>=2)throw new IllegalStateException("LOGIN_TRANSACTION_CAP");entries.add(new PendingLogins.Entry(value,clock.instant()));session.setAttribute(SessionAttributeCodec.PENDING,new PendingLogins(entries));
    }
    @Override public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,HttpServletResponse response){
        var match=loadAuthorizationRequest(request);if(match==null)return null;var session=request.getSession(false);PendingLogins pending=(PendingLogins)session.getAttribute(SessionAttributeCodec.PENDING);
        var remaining=pending.requests().stream().filter(e->!e.request().getState().equals(match.getState())).toList();if(remaining.isEmpty())session.removeAttribute(SessionAttributeCodec.PENDING);else session.setAttribute(SessionAttributeCodec.PENDING,new PendingLogins(remaining));return match;
    }
}
