package fi.vm.sade.authentication.cas.httpsessionbased;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.*;

/**
 * @author Antti Salonen
 */
public class BlockingAuthCookieCacheTest {

    private int counterST = 0;
    private String targetService1 = "http://thehost/targetService1";
    private String casUrl = "casUrlX";

    @Test
    public void test_getAuthenticatedCookiesForServiceUser() {
        BlockingAuthCookieCache bacc = initCache();

        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForServiceUser("test1", "test1", null, targetService1).get(0));

        // tokalla pyynnöllä sama ticket
        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForServiceUser("test1", "test1", null, targetService1).get(0));

        // jos ticket jo cachessa, salasanalla ei väliä
        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForServiceUser("test1", "ihansama", null, targetService1).get(0));

        // uusi user, uusi ticket
        Assert.assertEquals("CASST=ST-2", bacc.getAuthenticatedCookiesForServiceUser("test2", "test2", null, targetService1).get(0));

        // eri service, uusi ticket
        Assert.assertEquals("CASST=ST-3", bacc.getAuthenticatedCookiesForServiceUser("test2", "test2", null, targetService1+"_2").get(0));

        // null user failaa järkevästi
        try {
            bacc.getAuthenticatedCookiesForServiceUser(null, "xxx", null, targetService1);
            Assert.fail("should throw error");
        } catch (NullPointerException npe) {}
    }

    @Test
    public void test_getAuthenticatedCookiesForProxyAuth() {
        BlockingAuthCookieCache bacc = initCache();


        Authentication auth = new TestingAuthenticationToken("proxy1", "none");
        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForProxyAuth(auth, "session1", targetService1).get(0));

        // toimii vaikka spring securitycontextissa ei mitään
        // toimii vaikka auth objektin luo uusiksi
        // jos ticket jo cachessa, salasanalla ei väliä
        auth = new TestingAuthenticationToken("proxy1", "ihansama");
        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForProxyAuth(auth, "session1", targetService1).get(0));
        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForProxyAuth(auth, "session1", targetService1).get(0));

        // uusi currentsessio, uusi ticket
        Assert.assertEquals("CASST=ST-2", bacc.getAuthenticatedCookiesForProxyAuth(auth, "session1.2", targetService1).get(0));

        // uusi user, uusi ticket
        auth = new TestingAuthenticationToken("proxy2", "none");
        Assert.assertEquals("CASST=ST-3", bacc.getAuthenticatedCookiesForProxyAuth(auth, "session3", targetService1).get(0));

        // null auth failaa järkevästi
        try {
            bacc.getAuthenticatedCookiesForProxyAuth(null, "jokusessio", targetService1);
            Assert.fail("should throw error");
        } catch (NullPointerException npe) {}
    }

    @Test
    public void test_expiring() {
        BlockingAuthCookieCache bacc = initCache();

        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForServiceUser("test1", "test1", null, targetService1).get(0));
        Assert.assertEquals("CASST=ST-1", bacc.getAuthenticatedCookiesForServiceUser("test1", "test1", null, targetService1).get(0));
        sleep(1500);
        // expiroitunut, haetaan uusi
        Assert.assertEquals("CASST=ST-2", bacc.getAuthenticatedCookiesForServiceUser("test1", "test1", null, targetService1).get(0));
    }

    @Test
    public void test_expiringMultiThread() {
        final BlockingAuthCookieCache bacc = initCache();

        // X säiettä ampuu cachea Y ms välein
        // 1 sekunnin kohdalla 1.cookie vanhenee cachesta
        // 1,5 sekunnin kohdalla, säikeet lopetetaan
        // 2 sekunnin kohdalla 2.cookie vanhenee cachesta (muttei poistu koska kukaan ei koske cacheen)
        // 3 sekunnin kohdalla tehdään assertoinnit
        // ...

        int threads = 100;
        final long t0 = System.currentTimeMillis();
        final List<Exception> errors = new ArrayList<Exception>();
        final Set<String> results = Collections.synchronizedSet(new HashSet<String>());
        final int[] finished = {0};
        for (int t = 0; t < threads; t++) {
            new Thread(){
                @Override
                public void run() {
                    try {
                        while (true) {
                            List<String> res = bacc.getAuthenticatedCookiesForServiceUser("USER", "PASS", "CC", "TARGET");
                            if (res == null) throw new NullPointerException("res is null");
                            results.add(res.toString());
                            sleep(10);
                            if (System.currentTimeMillis()-t0 > 1500) break;
                        }
                    } catch (Exception e) {
                        errors.add(e);
                    }
                    synchronized (BlockingAuthCookieCacheTest.this) { finished[0]++; }

                }
            }.start();
        }

        sleep(3000);

        // ...
        // tällöin cachessa on 1, ja cachesta on poistettu 1 cookiet (cache poistaa vain putin yhteydessä, ei taustalla)
        Assert.assertEquals(1, bacc.size());
        Assert.assertEquals(1, bacc.removed());
        // jokainen threadi on joka kerralla pitänyt saada vastaus, ja valmis nyt
        Assert.assertEquals("errors!: "+errors, 0, errors.size());
        Assert.assertEquals(threads, finished[0]);
        // ja cookie vastauksia on ollut yhteensä 2 erilaista
        Assert.assertEquals(2, results.size());
    }

    @Test
    public void test_blocking() throws InterruptedException {
        final BlockingAuthCookieCache bacc = initCache();

        final List<String> res = Collections.synchronizedList(new ArrayList<String>());
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                String cookie = bacc.getAuthenticatedCookiesForServiceUser("test1", "test1", null, targetService1).get(0);
                System.out.println(cookie);
                res.add(cookie);
            }
        };
        for (int i = 0; i < 10; i++) {
            new Thread(runnable).start();
        }
        sleep(2000);

        // assertoi että samanaikaiset pyynnöt antoivat saman tuloksen
        for (int i = 0; i < 10; i++) {
            Assert.assertEquals("CASST=ST-1", res.get(i));
        }
    }

    private BlockingAuthCookieCache initCache() {
        // prepare mock BlockingAuthCookieCache that takes 100 ms to getticket + authenticate session
        return new BlockingAuthCookieCache(casUrl, 1, 10){
            @Override
            public String obtainTicket(CacheKey cachekey) {
                sleep(50);
                return "ST-"+(++counterST);
            }
            @Override
            protected List<String> openSessionAndGetCookies(CacheKey cachekey, String ticket) {
                sleep(50);
                List<String> cookies = new ArrayList<String>();
                cookies.add("CASST="+ticket);
                return cookies;
            }
        };
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
