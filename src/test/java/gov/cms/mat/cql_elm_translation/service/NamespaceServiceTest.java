package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql_elm_translation.clients.LibraryServiceClient;
import gov.cms.mat.cql_elm_translation.dto.NamespaceDto;
import org.hl7.cql.model.NamespaceInfo;
import org.hl7.cql.model.NamespaceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NamespaceServiceTest {

  @Mock private LibraryServiceClient libraryServiceClient;
  @InjectMocks private NamespaceService namespaceService;

  @Test
  void loadNamespacesMapsAndRegistersDefinitions() {
    when(libraryServiceClient.getNamespaces())
        .thenReturn(
            List.of(
                NamespaceDto.builder()
                    .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                    .namespacePrefix("hl7.fhir.us.qicore")
                    .build()));

    namespaceService.loadNamespaces();

    NamespaceManager namespaceManager = org.mockito.Mockito.mock(NamespaceManager.class);
    namespaceService.registerNamespaces(namespaceManager);

    ArgumentCaptor<NamespaceInfo> captor = ArgumentCaptor.forClass(NamespaceInfo.class);
    verify(namespaceManager, times(1)).ensureNamespaceRegistered(captor.capture());
    assertEquals("hl7.fhir.us.qicore", captor.getValue().getName());
    assertEquals("http://hl7.org/fhir/us/qicore", captor.getValue().getUri());
  }

  @Test
  void loadNamespacesSkipsInvalidDefinitions() {
    when(libraryServiceClient.getNamespaces())
        .thenReturn(
            List.of(
                NamespaceDto.builder()
                    .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                    .namespacePrefix("")
                    .build()));

    namespaceService.loadNamespaces();

    NamespaceManager namespaceManager = org.mockito.Mockito.mock(NamespaceManager.class);
    namespaceService.registerNamespaces(namespaceManager);

    verify(namespaceManager, never()).ensureNamespaceRegistered(org.mockito.ArgumentMatchers.any());
  }
}
