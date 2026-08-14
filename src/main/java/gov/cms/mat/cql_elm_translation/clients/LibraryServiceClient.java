package gov.cms.mat.cql_elm_translation.clients;

import gov.cms.mat.cql_elm_translation.dto.NamespaceDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class LibraryServiceClient {

  private final RestTemplate restTemplate;

  @Value("${madie.library.service.baseUrl}")
  private String madieLibraryServiceBaseUrl;

  @Value("${madie.library.service.namespaces.uri}")
  private String libraryNamespacesUri;

  @Value("${security.system-api-key}")
  private String madieApiKey;

  @Value("${security.api-key-header}")
  private String madieApiKeyHeader;

  public LibraryServiceClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public List<NamespaceDto> getNamespaces() {
    String url = madieLibraryServiceBaseUrl + libraryNamespacesUri;

    HttpHeaders headers = new HttpHeaders();
    if (StringUtils.hasText(madieApiKey)) {
      headers.set(madieApiKeyHeader, madieApiKey);
    }

    try {
      ResponseEntity<List<NamespaceDto>> response =
          restTemplate.exchange(
              url,
              HttpMethod.GET,
              new HttpEntity<>(headers),
              new ParameterizedTypeReference<>() {});

      return response.getBody() == null ? Collections.emptyList() : response.getBody();
    } catch (Exception ex) {
      log.error(
          "Unable to retrieve namespace definitions from library service: {}", ex.getMessage());
      return Collections.emptyList();
    }
  }
}
