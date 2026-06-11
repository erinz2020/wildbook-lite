package com.wildme.wildbook_lite.search.opensearch;

import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildme.wildbook_lite.config.AppProperties;

/**
 * OpenSearch Java client wiring.
 *
 * Spring Boot bits worth calling out:
 *
 *  - @ConditionalOnProperty("app.opensearch.enabled")
 *      The whole config (and therefore the client bean) is skipped when
 *      OS is disabled. App boots fine without OpenSearch running.
 *      Anything else in the codebase that wants the client uses
 *      ObjectProvider<OpenSearchClient> / @Autowired(required=false) so
 *      it can no-op when the bean is missing.
 *
 *  - ApacheHttpClient5TransportBuilder is the modern transport (vs the
 *    deprecated RestClient bridge). It's lazy — no socket opens until
 *    the first call. That means startup never fails because the OS
 *    container isn't up yet.
 *
 *  - JacksonJsonpMapper reuses Spring's ObjectMapper. Without this the
 *    client would build its own ObjectMapper without our custom
 *    settings (e.g., Java 8 time module, Instant serialization).
 */
@Configuration
@ConditionalOnProperty(value = "app.opensearch.enabled", havingValue = "true")
public class OpenSearchConfig {

    @Bean
    public OpenSearchClient openSearchClient(AppProperties props, ObjectMapper objectMapper) {
        AppProperties.OpenSearch cfg = props.opensearch();

        HttpHost host = new HttpHost(cfg.scheme(), cfg.host(), cfg.port());

        var builder = ApacheHttpClient5TransportBuilder.builder(host);

        // Optional basic auth — used in prod, skipped in dev.
        boolean hasAuth = cfg.username() != null && !cfg.username().isBlank();
        if (hasAuth) {
            BasicCredentialsProvider creds = new BasicCredentialsProvider();
            creds.setCredentials(
                new AuthScope(host),
                new UsernamePasswordCredentials(
                    cfg.username(),
                    cfg.password() == null ? new char[0] : cfg.password().toCharArray()
                )
            );
            builder.setHttpClientConfigCallback(httpBuilder ->
                httpBuilder.setDefaultCredentialsProvider(creds));
        }

        OpenSearchTransport transport = builder
            .setMapper(new JacksonJsonpMapper(objectMapper))
            .build();
        return new OpenSearchClient(transport);
    }
}
