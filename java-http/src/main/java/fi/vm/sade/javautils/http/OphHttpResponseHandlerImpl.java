package fi.vm.sade.javautils.http;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.apache.http.HttpStatus.SC_NO_CONTENT;

import fi.vm.sade.javautils.http.exceptions.UnhandledHttpStatusCodeException;
import org.apache.http.client.methods.CloseableHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class OphHttpResponseHandlerImpl<T> implements OphHttpResponseHandler<T> {
    private CloseableHttpResponse response;
    private int[] statusArray;
    private Set<OphHttpOnErrorCallBackImpl<T>> ophHttpCallBackSet;

    OphHttpResponseHandlerImpl(CloseableHttpResponse response, int[] statusArray, Set<OphHttpOnErrorCallBackImpl<T>> ophHttpCallBackSet) {
        this.response = response;
        this.statusArray = statusArray;
        this.ophHttpCallBackSet = ophHttpCallBackSet;
    }

    @Override
    public Optional<T> mapWith(Function<String, T> handler) {
        // Expected status code received
        if (Arrays.stream(statusArray).anyMatch(status -> status == this.response.getStatusLine().getStatusCode()) ) {
            return Optional.ofNullable(handler.apply(this.asTextAndClose()));
        }
        return this.notExpectedStatusCodeHandling(true);
    }

    @Override
    public void ignoreResponse() {
        if (Arrays.stream(statusArray).noneMatch(status -> status == this.response.getStatusLine().getStatusCode()) ) {
            notExpectedStatusCodeHandling(false);
        }
        this.close();
    }

    private Optional<T> notExpectedStatusCodeHandling(boolean acceptEmptyResponse) {
        try {
            // Handled error code received
            return this.ophHttpCallBackSet.stream()
                    .filter(ophHttpCallBack -> ophHttpCallBack.getStatusCode()
                            .contains(this.response.getStatusLine().getStatusCode()))
                    .map(OphHttpOnErrorCallBackImpl::getCallBack)
                    .map(callback -> callback.apply(this.asTextAndClose()))
                    .findFirst()
                    .orElseThrow(() -> new UnhandledHttpStatusCodeException(this.asTextAndClose(), this.response.getStatusLine().getStatusCode()));
        } catch (UnhandledHttpStatusCodeException e) {
            // If user has not handled 404 NOT_FOUND or 204 NO_CONTENT assume it means empty resource content.
            if (acceptEmptyResponse && (this.response.getStatusLine().getStatusCode() == SC_NOT_FOUND || this.response.getStatusLine().getStatusCode() == SC_NO_CONTENT)) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void consumeStreamWith(Consumer<InputStream> handler) {
        if (Arrays.stream(statusArray).anyMatch(status -> status == this.response.getStatusLine().getStatusCode()) ) {
            try (InputStream inputStream = this.response.getEntity().getContent()) {
                handler.accept(inputStream);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            } finally {
                this.close();
            }
        }
        else {
            this.notExpectedStatusCodeHandling(false);
        }
    }

    private String asTextAndClose() {
        try (InputStream inputStream = this.response.getEntity().getContent()) {
            return OphHttpResponseImpl.toString(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            this.close();
        }
    }

    private void close() {
        try {
            this.response.close();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }


}