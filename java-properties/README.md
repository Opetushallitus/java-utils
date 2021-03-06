# OPH properties and url configuration

* Works for Java, Scala, Javascript
* Simple syntax which is the same across languages
  * Javascript: `window.url("organisaatio-service.soap", param1, param2)`
  * Java and Scala: `ophProperties.url("organisaatio-service.soap", param1, param2)`
  * .properties: `organisaatio-service.soap=/organisaatio-service/soap/$1/$2`
  * .json: `{"organisaatio-service.soap": "/organisaatio-service/soap/$1/$2"}`
* Different property resolve methods:
  * url(key, param1, param2, ...) property resolving with additional url features: baseUrl override (see below) and unused named parameters are added to querystring
  * require(key, param1, param2, ...) throws an exception if key is not defined
  * getProperty(key, param1, param2, ...) returns null if key is not defined
  * getOrElse(key, defaultValue, param1, param2, ...) returns defaultValue if key is not defined
  * resolveFor(key).url(param1) makes it easy to ensure at startup time that the key is defined. url(), require(), getProperty() and getOrElse() use original key parameter and take parameters
* All property resolve methods support parameter lookup: by index ($1), by parameter name ($id) and by property key ${key:defaultValue}
  * .properties: `organisaatio-service.info=${publicLB}/organisaatio-service/info/$id/$user`
  * Javascript: `window.url("organisaatio-service.info", {id: oid, user: user.id})`
  * Parameter name supports two syntaxes: ${key} throws an exception if key is not defined, ${key:defaultValue} uses defaultValue if key not defined
  * Java supports Maps, Scala implementation supports Maps and case classes
* Supports development, property values can be overridden
  * Property values can be overriden with command line parameters: `-Dorganisaatio-service.soap=https://testserver/soap/123/456`
  * Frontend can be instructed with: `-Dfront.organisaatio-service.soap=https://testserver/soap/123/456`
  * Prefixing a key with `front.` makes it available to front without the prefix
  * Prefixing a key with `url.` makes it available to both front and backend without the prefix
  * note: You'll need to add a properties servlet to your application to serve the override the properties from backend
* Backend override properties can be loaded from files with command line. These will override values loaded with code
  * `-Doph-properties=file1.properties,file2.properties` - for properties (url. and front. filtering is applied to get front properties)
  * `-Doph-front=file3.properties,file4.properties` - for front only properties
* Easily redirect urls generated with `url()` by defining baseUrl:
  * After the url is created from properties URL resolving looks for "*service*.baseUrl" and "baseUrl".
  *  Base url is appended to the beginning of the resolved url. If the original url contains protocol, server and port they are removed.
  * `-Dsuoritusrekisteri.baseUrl=https://testserver/suoritusrekisteri` - for suoritusrekisteri urls
  * `-DbaseUrl=https://testserver/suoritusrekisteri` - for all urls

            /path/123 + baseUrl /foo -> /foo/path/123
            https://www.oph.fi/path/123 + baseUrl http://localhost -> http://localhost/path/123

* Debug-mode for showing how application works: `-DOphProperties.debug=true`
* [project_info_server](https://github.com/Opetushallitus/dokumentaatio/tree/master/project_info)
parses the configuration files and is able to generate reports from the data.

    note: The files should be named according to the pattern:
    `*url.properties|*url_properties.json|*oph.properties|*oph_properties.json`, for example `suoritusrekisteri_oph.properties`.
    project_info_server takes "suoritusrekisteri" from the filename and adds dependencies for each service derived from the property key,
    for example "organisaatio-service".

See implementation and usage in following projects
* [Java](https://github.com/Opetushallitus/java-utils/tree/master/java-properties)
* [Javascript](https://github.com/Opetushallitus/java-utils/tree/master/java-properties/javascript)
* [Scala](https://github.com/Opetushallitus/scala-utils/tree/master/scala-properties_2.11)
* [project_info_server](https://github.com/Opetushallitus/dokumentaatio/tree/master/project_info)
* [suoritusrekisteri](https://github.com/Opetushallitus/hakurekisteri) - uses scala-properties and oph_urls.js

## Configuration for JVM based languages

### Maven, for Java @ [github.com/Opetushallitus/java-utils](https://github.com/Opetushallitus/java-utils/tree/master/java-properties)

    <dependency>
        <groupId>fi.vm.sade.java-utils</groupId>
        <artifactId>java-properties</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

### Maven, for Scala @ [github.com/Opetushallitus/scala-utils](https://github.com/Opetushallitus/scala-utils/tree/master/scala-properties_2.11)

    <dependency>
        <groupId>fi.vm.sade</groupId>
        <artifactId>scala-properties_2.11</artifactId>
        <version>0.0.1-SNAPSHOT</version>
    </dependency>

### SBT

    "fi.vm.sade" %% "scala-properties" % "0.0.1-SNAPSHOT"
    
### Clojure / Leiningen

    [fi.vm.sade.java-utils/java-properties "0.1.0-SNAPSHOT"]

See an example of using this for url.properties in a [Component](https://github.com/stuartsierra/component)
architecture: [url_helper.clj](https://github.com/Opetushallitus/oti/blob/master/src/clj/oti/component/url_helper.clj)

### Java and Scala

    import fi.vm.sade.properties.OphProperties // java
    // import fi.vm.sade.scalaproperties.OphProperties // scala
    
    // load properties by default from /suoritusrekisteri-web-oph.properties (which should be placed in class path)
    OphProperties properties = new OphProperties("/suoritusrekisteri-web-oph.properties");
    properties.url("organisaatio-service.soap");


## Configuration for Javascript

note: Add the file oph_urls.js to the javascript build process or refer to it in the main page with a script tag.

### NPM @ [github.com/Opetushallitus/oph-urls-js](https://github.com/Opetushallitus/oph-urls-js)

Add to _package.json_:

```json
{
    "dependencies": {
        "oph-urls-js": "Opetushallitus/oph-urls-js#master"
    }
}
```

### Bower, example from suoritusrekisteri

* [.bowerrc](https://github.com/Opetushallitus/hakurekisteri/blob/master/.bowerrc) - define the directory where bower saves dependencies
* `bower init` - creates bower.json with user defined values, example: [bower.json](https://github.com/Opetushallitus/hakurekisteri/blob/master/bower.json) 
* `bower install --save https://raw.githubusercontent.com/Opetushallitus/oph-urls-js/master/oph_urls.js`

### ES6 projects
```javascript
import 'oph-urls-js'  // functions (urls, url, etc.) attached to window
```
### HTML

    <script type="text/javascript" src="static/js/oph_urls.js/index.js"></script>

### Javascript, Angular

    // load properties from a static file and a rest resource which returns override properties
    // start application after resources are loaded
    window.urls.loadFromUrls("suoritusrekisteri-web-frontend-url_properties.json", "rest/v1/properties").success(function() {
      // bootstrap angular application manually after properties are loaded
      angular.element(document).ready(function() {
        angular.bootstrap(document, ['myApp'])
      })
    })
    window.url("organisaatio-service.soap")


### Supported property file formats

* .properties (java, scala)

        service.key=value

* .json, nested and flat structure (front)

        {
            "service": {
                "key": "value"
            }
        }

        {
            "service.key": "value"
        }

* javascript file can use `window.urls.addProperties()`.
  `window.urls.addOverride()` and `window.urls.AddDefaults()` are available but should not be used

        // window.urls.properties should contain application's front urls
        window.urls.addProperties({
            "service.key": "value"
        })
        // available but should not be used
        window.urls.addOverride({
        }) 
        // available but should not be used
        window.urls.AddDefaults({
        })

* .js es6 (front)

        export default {
            hakuperusteetadmin: {
                paymentUpdateUrl: "/hakuperusteetadmin/api/v1/admin/payment"
            }
        }

# Steps for converting existing applications

1. Add maven or sbt dependency
2. Add <service-name>-oph.properties file

        url-cas=https://${host.cas}
        url-haku=https://${host.haku}
        url-virkailija=https://${host.virkailija}

        sijoittelu-service.hakija=${url-virkailija}/sijoittelu-service/resources/sijoittelu/$1/sijoitteluajo/latest/hakemus/$2

3. Add a helper class to the project
  * Java / Spring

          import fi.vm.sade.properties.OphProperties;
          import org.springframework.context.annotation.Configuration;

          import java.nio.file.Paths;

          @Configuration
          public class UrlConfiguration extends OphProperties {
              public UrlConfiguration() {
                  addFiles("/hakemus-api-oph.properties");
                  addOptionalFiles(Paths.get(System.getProperties().getProperty("user.home"), "/oph-configuration/common.properties").toString());
              }
          }
  * Scala

          object OphUrlProperties {
            val ophProperties = new OphProperties("/suoritusrekisteri-web-oph.properties").addOptionalFiles(Paths.get(sys.props.getOrElse("user.home", ""), "/oph-configuration/common.properties").toString)
          }
4. Replace all external links
  * Take care that all parameters are handled correctly
  * Remove all manual link generation code, no more baseUrl + "resource/" + "oid"
  * Extra parameters are appended to the querystring, remove that code too
  * If some urls need different hosts, create url key for each language

        hakuperusteet.tokenUrl.en=${url-haku-en}/hakuperusteet/app/$1#/token/
        hakuperusteet.tokenUrl.fi=${url-haku}/hakuperusteet/app/$1#/token/
        hakuperusteet.tokenUrl.sv=${url-haku-sv}/hakuperusteet/app/$1#/token/

  * If there is a common http client, consider if that should take in url key + params

        get(String key, Object... params) // java
        get(key: String, args: AnyRef*)) // scala

5. Fix all backend tests

6. For front-apps you'll need to include oph_urls.js. Just add .bowerrc and use `bower install` according to instructions above.

7. Decide on how front app's url configuration is loaded
  * If there is a single .js file which is built with webpack (etc) include oph_urls.js and a properties file with contents `window.urls.properties={key: "url"}`
  * It's a good practice to add a REST endpoint to the backend application that returns the override urls

          import org.springframework.beans.factory.annotation.Autowired;
          import org.springframework.stereotype.Component;
          import javax.ws.rs.GET;
          import javax.ws.rs.Path;
          import javax.ws.rs.Produces;
          import javax.ws.rs.core.MediaType;

          @Path("/rest/frontProperties")
          @Component
          public class FrontPropertiesResource {

            @Autowired
            UrlConfiguration urlConfiguration;

            @GET
            @Produces("application/javascript" + ";charset=UTF-8")
            public String frontProperties() {
              return "window.urls.addOverrides(" + urlConfiguration.frontPropertiesToJson() + ")";
            }
          }

  * If the application can be started with a method call, you can delay startup until properties are loaded with

          window.urls.load("test.json", {overrides: ["test_overrides.json"]}).then(function() {
              // ..
          }, function(error) {})

   * If the application has many starting points it's better to include the oph_urls.js first with js script and then load the url configurations.
   This prevents the application from starting before urls are loaded.

            <script src="${contextPath}/resources/javascript/oph_urls.js/index.js" type="text/javascript"></script>
            <script src="${contextPath}/rest/frontProperties" type="text/javascript"></script>
            <script src="${contextPath}/resources/javascript/haku-app-web-url_properties.js" type="text/javascript"></script>

8. Fix all front tests and test manually in real use
