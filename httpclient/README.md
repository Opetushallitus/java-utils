# Common HTTP interface for OPH

    Koulutus koulutus = client.get("tarjonta-service.koulutus", koulutusId).expectStatus(200).
                            execute( r -> mapper.readValue(r.asInputStream(), Koulutus.class) );

    Koulutus newKoulutus = new Koulutus()
    Koulutus savedKoulutus = client.post("tarjonta-service.koulutus", koulutusId).expectStatus(200).
                            data("application/json", "UTF-8", out -> mapper.writeValue(out, newKoulutus) ).
                            execute( r -> mapper.readValue(r.asInputStream(), Koulutus.class) );

# Features

* Simple fluent API
* Easy streaming for request and response -> one liner handlers and small memory use by default
* Built in assertions for response status `expectStatus(200,...)` and content type `accept(JSON)`.
By default the client expects the status code to be 2xx.
* Built in retry: `retryOnError(times, delayMs)`
* Uses Apache Httpclient 4.5.2, but you can write an adapter for other http client libraries.
  Just implement your own OphHttpClientProxy, OphHttpClientProxyRequest and OphHttpResponse

# Usage

## Maven

    <dependency>
        <groupId>fi.vm.sade.java-utils</groupId>
        <artifactId>httpclient</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>

## SBT

    "fi.vm.sade.java-utils" %% "httpclient" % "0.0.1-SNAPSHOT"

## Initializing a client

Initialize a non-caching http client that pools connections.

    OphHttpClient client = ApacheOphHttpClient.createDefaultOphHttpClient("tester", properties, 10000, 600);

* `tester` is clientSubSystemCode header, which identifies this client by adding the header with the specified value to the request
* `properties` is a OphProperties instance. Urls are resolved through it, see: https://github.com/Opetushallitus/java-utils/tree/master/java-properties
* 10000ms is a common timeout for various timeout values
* 600s is the how long each connection is up

`ApacheHttpClientBuilder.createCustomBuilder()` can be used to configure your own HttpClient.
See available methods in `ApacheHttpClientBuilder`

    ApacheHttpClientBuilder builder = ApacheOphHttpClient.createCustomBuilder().
                            createCachingClient( 50 * 1000, 10 * 1024 * 1024).
                            setDefaultConfiguration(10000, 60);
    builder.httpBuilder.setProxy(...); // Accessing the original HttpClientBuilder's method
    OphHttpClient cachingClient = new OphHttpClient(builder.build(), "tester", properties)

By default initialized clients:
* accept the response if the response status code is between 200 and 299. Otherwise an exception is thrown.
* follow redirects automatically
* don't retry automatically

## Making requests

A regular GET for JSON

1. Resolve url. Url is resolved from OphProperties instances and koulutusId is filled in to the url.
2. Make the request. Request is made with header: Accept: application/json
3. Verify that response code is 200 and Content-Type matches Accept
4. Handle the response with a OphHttpResponseHandler instance or lambda. The handler gets an instance of OphHttpResponse which
provides methods related to the response.
5. Connection and any related resources are released after handler is finished.


    Koulutus koulutus = client.get("tarjonta-service.koulutus", koulutusId).expectStatus(200).accept(JSON).
        execute(r -> mapper.readValue(r.asInputStream(), Koulutus.class));

Make a POST and verify that the response code is 200. You can use the plain execute() method without writing a handler.

    client.post("tarjonta-service.koulutus").expectStatus(200).
        data("application/json", "UTF-8", out -> mapper.writeValue(out, koulutus) )
        execute();

There is also `handleManually()` which doesn't release anything. It returns an

## Retrying

Make the request and handle the response. Retry 3 times (and wait 2000ms between requests) if anything throws an exception during the process.
Warning: The HTTP request is also repeated, so you might get multiple requests even if the original request succeeds but the assertions or
the handler throw an exception.

    Koulutus koulutus = client.get("tarjonta-service.koulutus", koulutusId).
        expectStatus(200).accept(JSON).
        retryOnError(3, 2000).
        execute(r -> mapper.readValue(r.asInputStream(), Koulutus.class));