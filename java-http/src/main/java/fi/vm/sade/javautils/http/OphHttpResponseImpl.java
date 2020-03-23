package fi.vm.sade.javautils.http;

import java.util.HashSet;
import java.util.Set;

import org.apache.http.client.methods.CloseableHttpResponse;

public class OphHttpResponseImpl<T> implements OphHttpResponse<T> {

    private final CloseableHttpResponse response;

    private Set<OphHttpOnErrorCallBackImpl<T>> ophHttpCallBackSet;

    public OphHttpResponseImpl(CloseableHttpResponse response) {
        this.response = response;
        this.ophHttpCallBackSet = new HashSet<>();
    }

    @Override
    public OphHttpOnErrorCallBack<T> handleErrorStatus(int... statusArray) {
        if (this.ophHttpCallBackSet == null) {
            this.ophHttpCallBackSet = new HashSet<>();
        }
        OphHttpOnErrorCallBackImpl<T> ophHttpCallBack = new OphHttpOnErrorCallBackImpl<>(statusArray, this);
        this.ophHttpCallBackSet.add(ophHttpCallBack);
        return ophHttpCallBack;
    }

    @Override
    public OphHttpResponseHandler<T> expectedStatus(int... statusArray) {
        return new OphHttpResponseHandlerImpl<>(this.response, statusArray, this.ophHttpCallBackSet);
    }

}
