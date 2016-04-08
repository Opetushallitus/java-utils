package fi.vm.sade.generic.healthcheck;

/**
 * Healthcheckiin kuuluva tarkastaja, jonka roolina on tarkastaa yksi kohde healthcheckin yhteydessä.
 * SpringAwareHealthCheckServlet kutsuu spring application contextista löytyviä tämän HealthChecker -interfacen toteuttavia beaneja.
 * checkHealth -metodin palauttama objekti serialisoidaan JSON:ksi, ja liitetään healthcheckin checks -osioon kentäksi [beanName].
 * Mikäli tarkastuksessa on virhe, checkHealth -metodin tulee heittää sitä poikkeus (jonka message kuvaa virhetilannetta).
 * Tällöin poikkeuksen message liitetään healthcheck tulokseen, ja koko healthcheckin tila on ERREOR.
 *
 * Esim:
 *
 *  @Component("solrIndexed")
 *  public class SolrIndexedCheck implements HealthChecker {
 *      Object checkHealth() throws Throwable {
 *          // tarkastetaan tässä onko solr indeksoitu
 *          return new LinkedHashMap(){{ put("status", "OK"); put("previouslyIndexed", timestamp); }}
 *      }
 *  }
 *
 *  ...johtaa tällaiseen healthcheck tulokseen...
 *
 *  {
 *      "status": "OK",
 *      "checks": {
 *          "solrIndexed": {"status": "OK", "timestamp": [timestamp]}
 *      }
 *  }
 *
 * @see SpringAwareHealthCheckServlet
 * @author Antti Salonen
 */
public interface HealthChecker {
    /**
     * @return something json-serializable that describes the state of this checker
     * @throws Throwable if there is health check error
     */
    Object checkHealth() throws Throwable;
}
