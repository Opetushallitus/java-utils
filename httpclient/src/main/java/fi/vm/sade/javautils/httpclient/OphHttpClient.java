package fi.vm.sade.javautils.httpclient;

import fi.vm.sade.properties.OphProperties;

import java.io.*;
import java.util.Arrays;
import java.util.List;

public class OphHttpClient extends OphRequestParameterAccessors<OphHttpClient> implements AutoCloseable {
    private OphProperties urlProperties;
    private OphHttpClientProxy httpAdapter;

    public static final class Method {
        public static final String GET = "GET";
        public static final String HEAD = "HEAD";
        public static final String OPTIONS = "OPTIONS";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
        public static final String PATCH = "PATCH";
        public static final String DELETE = "DELETE";
    }

    public static final class Header {
        public static final String CONTENT_TYPE="Content-Type";
        public static final String ACCEPT="Accept";
        public static final String CSRF="CSRF";

    }
    public static final String UTF8 = "UTF-8";
    public static final String JSON = "application/json";
    public static final String HTML = "text/html";
    public static final String TEXT = "text/plain";
    public static final String FORM_URLENCODED = "application/x-www-form-urlencoded";

    public static final List<String> CSRF_SAFE_VERBS = Arrays.asList(Method.GET, Method.HEAD, Method.OPTIONS);

    public OphHttpClient(OphHttpClientProxy httpAdapter) {
        this.httpAdapter = httpAdapter;
    }

    /**
     * Resolve urls through urlProperties
     * @param httpAdapter
     * @param callerId
     * @param urlProperties
     */
    public OphHttpClient(OphHttpClientProxy httpAdapter, String callerId, OphProperties urlProperties) {
        this.httpAdapter = httpAdapter;
        this.urlProperties = urlProperties;
        setThisForRequestParamSetters(this);
        setCallerId(callerId);
    }

    /**
     * Use urls without any parameter resolving
     * @param httpAdapter
     * @param callerId
     */
    public OphHttpClient(OphHttpClientProxy httpAdapter, String callerId) {
        this.httpAdapter = httpAdapter;
        this.urlProperties = null;
        setThisForRequestParamSetters(this);
        setCallerId(callerId);
    }

    /*
    note: if you pass in an array as the only params parameter, you need to cast it to (Object) if you want it to be handled correctly.
     */
    public OphHttpRequest get(String key, Object... params) {
        return createRequest(Method.GET, key, params);
    }
    public OphHttpRequest head(String key, Object... params) {
        return createRequest(Method.HEAD, key, params);
    }
    public OphHttpRequest options(String key, Object... params) {
        return createRequest(Method.OPTIONS, key, params);
    }
    public OphHttpRequest post(String key, Object... params) {
        return createRequest(Method.POST, key, params);
    }
    public OphHttpRequest put(String key, Object... params) {
        return createRequest(Method.PUT, key, params);
    }
    public OphHttpRequest patch(String key, Object... params) {
        return createRequest(Method.PATCH, key, params);
    }
    public OphHttpRequest delete(String key, Object... params) {
        return createRequest(Method.DELETE, key, params);
    }

    private OphHttpRequest createRequest(String method, String urlKey, Object[] params) {
        OphRequestParameters requestParameters = cloneRequestParameters();
        requestParameters.method = method;
        if(urlProperties != null) {
            requestParameters.urlKey = urlKey;
            requestParameters.urlParams = params;
            urlProperties.requireWithoutDebugPrint(urlKey);
        } else {
            requestParameters.url = urlKey;
        }
        return new OphHttpRequest(urlProperties, requestParameters, httpAdapter);
    }

    public static FormUrlEncodedWriter formUrlEncodedWriter(Writer outstream) {
        return new FormUrlEncodedWriter(outstream);
    }

    /**
     * Release all resources
     */
    public void close() {
        try {
            httpAdapter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public OphHttpClient setUrlProperties(OphProperties urlProperties) {
        this.urlProperties = urlProperties;
        return this;
    }
}
