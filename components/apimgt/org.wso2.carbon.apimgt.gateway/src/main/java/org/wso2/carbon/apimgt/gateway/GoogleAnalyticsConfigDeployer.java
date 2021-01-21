package org.wso2.carbon.apimgt.gateway;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.wso2.carbon.apimgt.api.APIManagementException;
import org.wso2.carbon.apimgt.gateway.internal.ServiceReferenceHolder;
import org.wso2.carbon.apimgt.impl.APIConstants;
import org.wso2.carbon.apimgt.impl.dto.EventHubConfigurationDto;
import org.wso2.carbon.apimgt.impl.gatewayartifactsynchronizer.exception.ArtifactSynchronizerException;
import org.wso2.carbon.apimgt.impl.utils.APIUtil;
import org.wso2.carbon.apimgt.impl.utils.LocalEntryAdminClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class GoogleAnalyticsConfigDeployer {

    private static final Log log = LogFactory.getLog(GoogleAnalyticsConfigDeployer.class);
    private String tenantDomain;
    private final EventHubConfigurationDto eventHubConfigurationDto =
            ServiceReferenceHolder.getInstance().getAPIManagerConfiguration().getEventHubConfigurationDto();
    private String baseURL = eventHubConfigurationDto.getServiceUrl() + APIConstants.INTERNAL_WEB_APP_EP;

    public GoogleAnalyticsConfigDeployer(String tenantDomain) {

        this.tenantDomain = tenantDomain;

    }

    public void deploy() throws APIManagementException {

        try {
            LocalEntryAdminClient localEntryAdminClient = new LocalEntryAdminClient(tenantDomain);

            String endpoint = baseURL + APIConstants.GA_CONFIG_RETRIEVAL_ENDPOINT;

            try (CloseableHttpResponse closeableHttpResponse = invokeService(endpoint, tenantDomain)) {
                deployAsLocalEntry(closeableHttpResponse, localEntryAdminClient);
            }
        } catch (IOException | ArtifactSynchronizerException e) {
            throw new APIManagementException("Error while deploying Google analytics configuration", e);
        }

    }

    private void deployAsLocalEntry(CloseableHttpResponse closeableHttpResponse,
                                    LocalEntryAdminClient localEntryAdminClient)
            throws IOException, ArtifactSynchronizerException {

        if (closeableHttpResponse.getStatusLine().getStatusCode() == 200) {
            try (InputStream content = closeableHttpResponse.getEntity().getContent()) {
                String resourceContent = IOUtils.toString(content);
                if (localEntryAdminClient.localEntryExists(APIConstants.GA_CONF_KEY)){
                    localEntryAdminClient.deleteEntry(APIConstants.GA_CONF_KEY);
                }
                localEntryAdminClient.addLocalEntry("<localEntry key=\"" + APIConstants.GA_CONF_KEY + "\">"
                        + resourceContent + "</localEntry>");
            }
        } else {
            throw new ArtifactSynchronizerException("Error while deploying localEntry status code : " +
                    closeableHttpResponse.getStatusLine().getStatusCode());
        }
    }

    private CloseableHttpResponse invokeService(String endpoint, String tenantDomain) throws IOException,
            ArtifactSynchronizerException {

        HttpGet method = new HttpGet(endpoint);
        URL url = new URL(endpoint);
        String username = eventHubConfigurationDto.getUsername();
        String password = eventHubConfigurationDto.getPassword();
        byte[] credentials = Base64.encodeBase64((username + APIConstants.DELEM_COLON + password).
                getBytes(APIConstants.DigestAuthConstants.CHARSET));
        int port = url.getPort();
        String protocol = url.getProtocol();
        method.setHeader(APIConstants.AUTHORIZATION_HEADER_DEFAULT, APIConstants.AUTHORIZATION_BASIC
                + new String(credentials, APIConstants.DigestAuthConstants.CHARSET));
        if (tenantDomain != null) {
            method.setHeader(APIConstants.HEADER_TENANT, tenantDomain);
        }

        HttpClient httpClient = APIUtil.getHttpClient(port, protocol);
        try {
            return APIUtil.executeHTTPRequest(method, httpClient);
        } catch (APIManagementException e) {
            throw new ArtifactSynchronizerException(e);
        }
    }

}
