package fi.vm.sade.generic.rest;

import fi.vm.sade.jetty.JettyJersey;
import junit.framework.Assert;
import org.apache.commons.io.IOUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * @author Antti Salonen
 */
public class CacheableJerseyFilterAndAnnotationTest {

    CachingRestClient client = new CachingRestClient();

    @Test
    public void testCacheableJerseyFilterAndAnnotation() throws Exception {
        // lue resurssi, jossa maxAge 2 sek (huomaa että max-agella, age on aluksi 1 sec)
        Assert.assertEquals("cacheable 1", get("/httptest/cacheableAnnotatedResource"));

        // lue resurssi uudestaan, assertoi että tuli cachesta, eikä serveriltä asti
        Assert.assertEquals("cacheable 1", get("/httptest/cacheableAnnotatedResource"));

        // odota 1 sek
        Thread.sleep(2000);

        // lue resurssi uudestaan, assertoi että haettiin serveriltä koska cache vanheni
        Assert.assertEquals("cacheable 2", get("/httptest/cacheableAnnotatedResource"));
    }

    @Test
    public void testHttpClientThreading() throws InterruptedException, IOException {
        get("/httptest/oneSecondResource"); // first force init jersey stuff
        Thread.sleep(1000);
        long t0 = System.currentTimeMillis();

        // call 1-second resource in 100 threads
        final int threads = 100;
        final int[] done = {0};
        final int[] errors = {0};
        for (int i=0; i < threads; i++) {
            final int order = i + 1;
            new Thread(){
                @Override
                public void run() {
                    try {
                        String result = get("/httptest/oneSecondResource");
                        Assert.assertEquals("OK", result);
                    } catch (Exception e) {
                        errors[0]++;
                        throw new RuntimeException(e);
                    } finally {
                        done[0]++;
                        System.out.println("testHttpClientThreading done "+done[0]+"/"+threads + ". Started as " + order + "");
                    }
                }
            }.start();
        }

        // wait until threads finished
        while (true) {
            if (done[0] + errors[0] == threads) {
                break;
            }
            Thread.sleep(100);
        }

        // assert all ok and calls were simultaneous
        long took = System.currentTimeMillis() - t0;
        System.out.println("took: "+ took +" ms");
        Assert.assertEquals(0, errors[0]);
        Assert.assertTrue("http conns too slow", took < 15000); // because 100 max threads/connections per route in CachingRestClient, and calling resource takes 1000 ms
    }

    @Before
    public void start() throws Exception {
        JettyJersey.startServer("fi.vm.sade.generic.rest", "fi.vm.sade.generic.rest.CacheableJerseyFilter");
    }

    @After
    public void stop() throws Exception {
        JettyJersey.stopServer();
    }

    private String get(String url) throws IOException {
        return IOUtils.toString(client.get("http://localhost:"+JettyJersey.getPort()+url));
    }

}
