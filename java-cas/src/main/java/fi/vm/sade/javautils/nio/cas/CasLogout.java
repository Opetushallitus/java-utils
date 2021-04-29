package fi.vm.sade.javautils.nio.cas;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

public class CasLogout {
    private static final Logger logger = LoggerFactory.getLogger(CasLogout.class);

    public Optional<String> parseTicketFromLogoutRequest(String logoutRequest) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(logoutRequest)));
            return Optional.of(document.getElementsByTagName("saml:NameID").item(0).getTextContent());
        } catch (Exception e) {
            logger.error("CAS Logout request parsing failed: ", e);
            return Optional.empty();
        }
    }
}