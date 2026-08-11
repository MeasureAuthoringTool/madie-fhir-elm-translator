package gov.cms.mat.cql_elm_translation.clients;

import gov.cms.mat.cql_elm_translation.dto.NamespaceDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LibraryServiceClientTest {

  @Mock private RestTemplate restTemplate;

  private LibraryServiceClient libraryServiceClient;
  private final String LIBRARY_SERVICE_BASE_URL = "https://example.com";

  @BeforeEach
  void setUp() {
    libraryServiceClient = new LibraryServiceClient(restTemplate);
    ReflectionTestUtils.setField(
        libraryServiceClient, "madieLibraryServiceBaseUrl", LIBRARY_SERVICE_BASE_URL);
    ReflectionTestUtils.setField(
        libraryServiceClient, "libraryNamespacesUri", "/cql-libraries/namespaces");
    ReflectionTestUtils.setField(libraryServiceClient, "madieApiKey", "test-madie-api-key");
    ReflectionTestUtils.setField(libraryServiceClient, "madieApiKeyHeader", "x-madie-api-key");
  }

  @Test
  void getNamespacesReturnsBody() {
    List<NamespaceDto> expected =
        List.of(
            NamespaceDto.builder()
                .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                .namespacePrefix("hl7.fhir.us.qicore")
                .build());

    when(restTemplate.exchange(
            eq(LIBRARY_SERVICE_BASE_URL + "/cql-libraries/namespaces"),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(org.springframework.core.ParameterizedTypeReference.class)))
        .thenReturn(new ResponseEntity<>(expected, HttpStatus.OK));

    List<NamespaceDto> actual = libraryServiceClient.getNamespaces();

    assertEquals(1, actual.size());
    assertEquals("hl7.fhir.us.qicore", actual.get(0).getNamespacePrefix());

    ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            eq(LIBRARY_SERVICE_BASE_URL + "/cql-libraries/namespaces"),
            eq(HttpMethod.GET),
            entityCaptor.capture(),
            any(org.springframework.core.ParameterizedTypeReference.class));
    HttpHeaders headers = entityCaptor.getValue().getHeaders();
    assertEquals("test-madie-api-key", headers.getFirst("x-madie-api-key"));
  }

  @Test
  void getNamespacesReturnsEmptyListOnError() {
    doThrow(new RuntimeException("boom"))
        .when(restTemplate)
        .exchange(
            any(String.class),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            any(org.springframework.core.ParameterizedTypeReference.class));

    List<NamespaceDto> actual = libraryServiceClient.getNamespaces();

    assertTrue(actual.isEmpty());
  }
}
