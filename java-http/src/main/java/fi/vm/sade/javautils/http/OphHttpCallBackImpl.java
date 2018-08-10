package fi.vm.sade.javautils.http;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

class OphHttpCallBackImpl<T> implements OphHttpCallBack<T> {
    private Set<Integer> statusCodes;

    private OphHttpResponse ophHttpResponse;

    private Function<String, Optional<T>> callBack;

    OphHttpCallBackImpl(int[] statusCodes, OphHttpResponse ophHttpResponse) {
        this.statusCodes = Arrays.stream(statusCodes)
                .boxed()
                .collect(Collectors.toSet());
        this.ophHttpResponse = ophHttpResponse;
    }

    Set<Integer> getStatusCode() {
        return this.statusCodes;
    }

    Function<String, Optional<T>> getCallBack() {
        return this.callBack;
    }

    @Override
    public OphHttpResponse with(Function<String, Optional<T>> callBack) {
        this.callBack = callBack;
        return this.ophHttpResponse;
    }
}