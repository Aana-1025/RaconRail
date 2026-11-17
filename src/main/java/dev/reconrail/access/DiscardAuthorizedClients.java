package dev.reconrail.access;

import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.core.Authentication;
import jakarta.servlet.http.*;

/** No downstream OAuth calls: authorized clients and their tokens are never retained. */
public final class DiscardAuthorizedClients implements OAuth2AuthorizedClientRepository {
    @Override public <T extends OAuth2AuthorizedClient>T loadAuthorizedClient(String id,Authentication principal,HttpServletRequest request){return null;}
    @Override public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication principal,HttpServletRequest request,HttpServletResponse response){}
    @Override public void removeAuthorizedClient(String id,Authentication principal,HttpServletRequest request,HttpServletResponse response){}
}
