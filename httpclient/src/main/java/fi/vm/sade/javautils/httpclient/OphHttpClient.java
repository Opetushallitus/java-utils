package fi.vm.sade.javautils.httpclient;

import fi.vm.sade.properties.OphProperties;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public class OphHttpClient extends OphRequestParameterStorage<OphHttpClient> {
    private final OphProperties urlProperties;
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
    public static final List<String> CSRF_SAFE_VERBS = Arrays.asList(Method.GET, Method.HEAD, Method.OPTIONS);

    public OphHttpClient(OphHttpClientProxy httpAdapter, String clientSubsystemCode, OphProperties urlProperties) {
        this.httpAdapter = httpAdapter;
        this.urlProperties = urlProperties;
        setThisForRequestParamSetters(this);
        setClientSubSystemCode(clientSubsystemCode);
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
        requestParameters.urlKey = urlKey;
        requestParameters.urlParams = params;
        urlProperties.require(urlKey);
        return new OphHttpRequest(urlProperties, requestParameters, httpAdapter);
    }

    public static String toString(InputStream stream) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(stream);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int result;
        result = bis.read();
        while(result != -1) {
            buf.write((byte) result);
            result = bis.read();
        }
        return buf.toString();
    }

}