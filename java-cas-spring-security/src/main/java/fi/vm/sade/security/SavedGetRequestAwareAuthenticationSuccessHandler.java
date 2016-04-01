package fi.vm.sade.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SavedGetRequestAwareAuthenticationSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private RequestCache requestCache = new HttpSessionRequestCache();

    public SavedGetRequestAwareAuthenticationSuccessHandler() {
        super();
        super.setRequestCache(requestCache);
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws ServletException, IOException {
        // Do redirection to original URL after succesfull auhtentication
        super.onAuthenticationSuccess(request, response, authentication);
        SavedRequest savedRequest = requestCache.getRequest(request, response);
        if (savedRequest != null && !"GET".equals(savedRequest.getMethod())) {
            // Do not try to replay original POST or PUT request after redirect
            // because request body (and so also form parameters) went missing (see BUG-773)
            requestCache.removeRequest(request, response);
        }
    }

    @Override
    public void setRequestCache(RequestCache requestCache) {
        super.setRequestCache(requestCache);
        this.requestCache = requestCache;
    }

}
