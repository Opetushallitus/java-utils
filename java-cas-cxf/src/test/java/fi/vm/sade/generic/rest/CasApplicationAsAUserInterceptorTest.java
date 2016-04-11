package fi.vm.sade.generic.rest;

import fi.vm.sade.authentication.cas.CasApplicationAsAUserInterceptor;
import fi.vm.sade.authentication.cas.DefaultTicketCachePolicy;
import fi.vm.sade.authentication.cas.TicketCachePolicy;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Antti Salonen
 */
public class CasApplicationAsAUserInterceptorTest extends RestWithCasTestSupport {

    private WebClient webClient;
    private CasApplicationAsAUserInterceptor appAsUserInterceptor;
    private String targetService;
    private String user;
    private String pass = "pass";

    @Test
    public void testCasApplicationAsAUserInterceptor() throws Exception {
        // prepare & mock the client
        webClient = createClient();

        // kutsutaan resurssia
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());

        // assertoidaan: ticket haettu kerran
        assertCas(0, 1, 1, 1, 1);

        // kutsutaan resurssia
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());

        // assertoidaan: ticket haettu kerran (ei autentikoida uudestaan vaan ticket cachetettu), mutta validoitu kaksi kertaa
        assertCas(0, 1, 1, 2, 2);

        // simuloidaan: cas restart, server ticket cache tyhjäys -> ticket ei enää validi
        TestParams.instance.failNextBackendAuthentication = true;

        // kutsutaan resurssia -> virhe
        Assert.assertEquals(HttpStatus.SC_UNAUTHORIZED, webClient.get().getStatus());
        assertCas(0, 1, 1, 3, 2); // autentikointi kutsuttiin kerran mutta epäonnistuneesti

        // simuloidaan: käyttäjä joutuu kirjautumaan uudelleen sisään, jonka jälkeen resurssi taas toimii
        appAsUserInterceptor.getTicketCachePolicy().clearTicket(targetService, user); // oikeassa ympäristössä ticket kakutettu käyttäjän http sessioon
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());

        // assertoidaan: ticket haettu ja validoitu nyt uusiksi
        assertCas(0, 2, 2, 4, 3);
    }

    @Test
    public void testTicketCacheIsLoadBlocked() throws Exception {
        // prepare & mock the client
        webClient = createClient();

        // call X times with threads
        Thread threads[] = new Thread[2];
        final String ticketsForThreads[] = new String[threads.length];
        for (int i = 0; i < threads.length; i++) {
            final int finalI = i;
            Thread thread = threads[i] = new Thread(){
                @Override
                public void run() {
                    ticketsForThreads[finalI] = appAsUserInterceptor.getTicketCachePolicy().getCachedTicket(targetService, user, new TicketCachePolicy.TicketLoader() {
                        @Override
                        public String loadTicket() {
                            System.out.println("CasApplicationAsAUserInterceptorTest.loadTicket1");
                            try {
                                // ...wait 100ms when getting new ticket for threading test purposes
                                Thread.sleep(100);
                                System.out.println("CasApplicationAsAUserInterceptorTest.loadTicket2");
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                            return "ticket_"+finalI;
                        }
                    });
                }
            };
            thread.start();
        }

        // wait for threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // assert threads got the same ticket = ticket cache blocks while loading values
        for (String ticket : ticketsForThreads) {
            System.out.println("ticket: "+ticket);
            Assert.assertEquals(ticketsForThreads[0], ticket);
        }
    }

    @Test
    public void testGlobalTicketCacheExpires() throws Exception {
        // prepare & mock the client
        webClient = createClient();

        // set ttl to 1 second
        ((DefaultTicketCachePolicy)appAsUserInterceptor.getTicketCachePolicy()).setGlobalTicketsTimeToLiveSeconds(1);

        // call 1st time -> create ticket
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 1, 1, 1, 1);

        // call 2nd time -> use ticket from cache
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 1, 1, 2, 2);

        // wait 1,5 secs -> ticket expires
        Thread.sleep(1500);

        // call 3rd time -> create ticket
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 2, 2, 3, 3);
    }

    // todo: nämä testit kuuluisi ehkä johonkin muualle huom ticket client refaktoroinnin jälkeen

    @Test
    public void testSameClientForDifferentServicesAndUsers() throws Exception {
        webClient = createClient();

        // testataan 2 eri käyttäjällä ja 2 eri kohdepalvelulla että jokaiselle syntyy omat tgt+tiketit

        changeUserAndService("user1", "target1");
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 1, 1, 1, 1);

        changeUserAndService("user1", "target2");
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 2, 2, 2, 2);

        changeUserAndService("user2", "target1");
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 3, 3, 3, 3);

        changeUserAndService("user2", "target2");
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 4, 4, 4, 4);

        // tämän jälkeen käytetään cachetettua tikettiä onnistuneesti

        changeUserAndService("user1", "target1");
        Assert.assertEquals(HttpStatus.SC_OK, webClient.get().getStatus());
        assertCas(0, 4, 4, 5, 5);
    }

    private void changeUserAndService(String u, String s) {
        user = u;
        pass = "pass";
        targetService = s;
        appAsUserInterceptor.setAppClientUsername(user);
        appAsUserInterceptor.setAppClientPassword(pass);
        appAsUserInterceptor.setTargetService(targetService);
    }

    private WebClient createClient() {
        appAsUserInterceptor = new CasApplicationAsAUserInterceptor();
        changeUserAndService("user", "target");
        appAsUserInterceptor.setWebCasUrl(getUrl("/mock_cas/cas"));
        WebClient c = WebClient.create(getUrl("/httptest/testMethod"));
        WebClient.getConfig(c).getOutInterceptors().add(appAsUserInterceptor);
        return c;
    }

}
