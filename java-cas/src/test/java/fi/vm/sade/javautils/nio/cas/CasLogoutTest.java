package fi.vm.sade.javautils.nio.cas;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;

public class CasLogoutTest {
    String logoutXML = "<samlp:LogoutRequest><saml:NameID>It-ankka</saml:NameID><samlp:SessionIndex>123456789</samlp:SessionIndex></samlp:LogoutRequest>";
    CasLogout casLogout = new CasLogout();

    @Test
    public void shouldParseNameFromXMLSuccessfully() {
        Optional<String> logoutName = casLogout.parseTicketFromLogoutRequest(logoutXML);
        assertEquals("It-ankka", logoutName.get());
    }

    @Test
    public void shouldReturnEmptyIfMalformedXML() {
        String logoutXML = "<samlp:t><saml:NameID>It-ankka</saml:NameID><samlp:SessionIndex>123456789</samlp:SessionIndex></samlp:LogoutRequest>";
        Optional<String> logoutName = casLogout.parseTicketFromLogoutRequest(logoutXML);
        assertEquals(Optional.empty(), logoutName);
    }
}

