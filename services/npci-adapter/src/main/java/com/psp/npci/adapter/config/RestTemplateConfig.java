package com.psp.npci.adapter.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactoryBuilder;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.security.KeyStore;

/**
 * RestTemplate configuration for outbound NPCI calls.
 *
 * <h2>mTLS design</h2>
 * <p>
 * The flag {@code npci.mtls.enabled} controls which RestTemplate is wired:
 * <ul>
 * <li>{@code false} (default) — plain HTTP RestTemplate backed by Apache
 * HttpClient 5.
 * Zero SSL configuration. Works for local demo.</li>
 * <li>{@code true} — same Apache HttpClient 5 client but with an
 * {@link SSLContext}
 * loaded from the keystore/truststore paths supplied via environment variables.
 * Switch from demo → production by flipping ONE property. No code refactor
 * needed.</li>
 * </ul>
 *
 * <p>
 * The {@link RestTemplate} bean is used exclusively in
 * {@code NpciAdapterService}
 * for outbound NPCI calls. Inbound webhook handling never uses this bean.
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    @Value("${npci.mtls.enabled:false}")
    private boolean mtlsEnabled;

    @Value("${npci.mtls.keystore-path:}")
    private String keystorePath;

    @Value("${npci.mtls.keystore-password:}")
    private String keystorePassword;

    @Value("${npci.mtls.truststore-path:}")
    private String truststorePath;

    @Value("${npci.mtls.truststore-password:}")
    private String truststorePassword;

    /**
     * Builds a {@link RestTemplate} backed by Apache HttpClient 5.
     *
     * <p>
     * When {@code npci.mtls.enabled=false}, a plain connection manager is used.
     * When {@code npci.mtls.enabled=true}, an {@link SSLContext} is built from the
     * configured keystore and truststore, enabling mutual TLS with NPCI.
     */
    @Bean
    public RestTemplate npciRestTemplate() {
        try {
            CloseableHttpClient httpClient;

            if (mtlsEnabled) {
                log.info("[NPCI-ADAPTER] mTLS ENABLED — loading keystore from {}", keystorePath);

                // Load keystore (client certificate + private key)
                KeyStore keyStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream ksStream = new FileInputStream(keystorePath)) {
                    keyStore.load(ksStream, keystorePassword.toCharArray());
                }

                // Load truststore (NPCI CA certificate)
                KeyStore trustStore = KeyStore.getInstance("PKCS12");
                try (FileInputStream tsStream = new FileInputStream(truststorePath)) {
                    trustStore.load(tsStream, truststorePassword.toCharArray());
                }

                SSLContext sslContext = SSLContextBuilder.create()
                        .loadKeyMaterial(keyStore, keystorePassword.toCharArray())
                        .loadTrustMaterial(trustStore, null)
                        .build();

                var sslSocketFactory = SSLConnectionSocketFactoryBuilder.create()
                        .setSslContext(sslContext)
                        .build();

                var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                        .setSSLSocketFactory(sslSocketFactory)
                        .build();

                httpClient = HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .build();

                log.info("[NPCI-ADAPTER] mTLS RestTemplate built successfully");

            } else {
                log.info("[NPCI-ADAPTER] mTLS DISABLED — using plain HTTP RestTemplate");

                // Plain HTTP — no SSL configuration; same code path as mTLS, just no SSLContext
                var connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                        .build();

                httpClient = HttpClients.custom()
                        .setConnectionManager(connectionManager)
                        .build();
            }

            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);

            return new RestTemplate(factory);

        } catch (Exception ex) {
            throw new IllegalStateException(
                    "[NPCI-ADAPTER] Failed to build RestTemplate (mTLS=" + mtlsEnabled + ")", ex);
        }
    }
}
