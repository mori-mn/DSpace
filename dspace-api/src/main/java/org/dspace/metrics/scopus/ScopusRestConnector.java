/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.metrics.scopus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.logging.log4j.Logger;

/**
 * This class deals with logic management to connect to the SCOPUS external service
 * Used {@link CloseableHttpClient} can be injected. i.e. for testing purposes.
 * Please note that {@link CloseableHttpClient} instance connection is eventually closed after performing operation.
 *
 * @author Mykhaylo Boychuk (mykhaylo.boychuk at 4science.it)
 * @author Corrado Lombardi (corrado.lombardi at 4science.it)
 */
public class ScopusRestConnector {

    private static final Logger log = org.apache.logging.log4j.LogManager.getLogger(ScopusRestConnector.class);

    private String apiKey;
    private String insttoken;
    private String scopusUrl;

    private CloseableHttpClient httpClient;


    public String get(String id) {
        try {
            return sendRequestToScopus(id);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String sendRequestToScopus(String id)
            throws IOException {
        try (CloseableHttpClient httpClient = Optional.ofNullable(this.httpClient)
            .orElseGet(HttpClients::createDefault)) {

            HttpGet httpGet = new HttpGet(scopusUrl + id);
            httpGet.setHeader("Accept-Encoding", "gzip, deflate, br");
            httpGet.setHeader("Connection", "keep-alive");
            httpGet.setHeader("X-ELS-APIKey", apiKey);
            httpGet.setHeader("X-ELS-Insttoken", insttoken);
            httpGet.setHeader("Accept", "application/xml");

            try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                if (statusCode != HttpStatus.SC_OK) {
                    log.error("Error connecting to server! The Server answered with: " + statusCode);
                    throw new RuntimeException();
                }
                return IOUtils.toString(response.getEntity().getContent(),
                    StandardCharsets.UTF_8);
            }
        }
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * sets a custom {@link CloseableHttpClient} instance. Please make sure that
     * this instance is not closed.
     * @param httpClient
     */
    public void setHttpClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public String getScopusUrl() {
        return scopusUrl;
    }

    public void setScopusUrl(String scopusUrl) {
        this.scopusUrl = scopusUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getInsttoken() {
        return insttoken;
    }

    public void setInsttoken(String insttoken) {
        this.insttoken = insttoken;
    }
}
