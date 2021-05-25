module fi.vm.sade.javautils.cas {
    requires java.net.http;
    requires async.http.client;
    requires io.netty.codec.http;
    requires slf4j.api;
    requires java.xml;
    requires org.apache.commons.lang3;
    exports fi.vm.sade.javautils.nio.cas;
}
