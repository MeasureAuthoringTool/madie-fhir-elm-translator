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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    NamespaceManager namespaceManager = mock(NamespaceManager.class);
    namespaceService.registerNamespaces(namespaceManager);

    ArgumentCaptor<NamespaceInfo> captor = ArgumentCaptor.forClass(NamespaceInfo.class);
    verify(namespaceManager, times(1)).ensureNamespaceRegistered(captor.capture());
    assertEquals("hl7.fhir.us.qicore", captor.getValue().getName());
    assertEquals("http://hl7.org/fhir/us/qicore", captor.getValue().getUri());
  }

  @Test
  void loadNamespacesSkipsInvalidAndNullDefinitions() {
    List<NamespaceDto> namespaceDtos = new ArrayList<>();
    namespaceDtos.add(null);
    namespaceDtos.add(
        NamespaceDto.builder().namespaceCanonical(null).namespacePrefix("prefix-one").build());
    namespaceDtos.add(
        NamespaceDto.builder().namespaceCanonical(" ").namespacePrefix("prefix-two").build());
    namespaceDtos.add(
        NamespaceDto.builder()
            .namespaceCanonical("http://valid.example/one")
            .namespacePrefix("")
            .build());
    namespaceDtos.add(
        NamespaceDto.builder()
            .namespaceCanonical("http://valid.example/two")
            .namespacePrefix("valid.two")
            .build());

    when(libraryServiceClient.getNamespaces()).thenReturn(namespaceDtos);

    namespaceService.loadNamespaces();

    NamespaceManager namespaceManager = mock(NamespaceManager.class);
    namespaceService.registerNamespaces(namespaceManager);

    ArgumentCaptor<NamespaceInfo> captor = ArgumentCaptor.forClass(NamespaceInfo.class);
    verify(namespaceManager, times(1)).ensureNamespaceRegistered(captor.capture());
    assertEquals("valid.two", captor.getValue().getName());
    assertEquals("http://valid.example/two", captor.getValue().getUri());
  }

  @Test
  void registerNamespacesRetriesLoadingWhenCacheIsEmpty() {
    when(libraryServiceClient.getNamespaces())
        .thenReturn(
            List.of(
                NamespaceDto.builder()
                    .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                    .namespacePrefix("hl7.fhir.us.qicore")
                    .build()));

    NamespaceManager namespaceManager = mock(NamespaceManager.class);
    namespaceService.registerNamespaces(namespaceManager);

    verify(libraryServiceClient, times(1)).getNamespaces();
    verify(namespaceManager, times(1)).ensureNamespaceRegistered(any());
  }

  @Test
  void registerNamespacesReturnsImmediatelyWhenManagerIsNull() {
    namespaceService.registerNamespaces(null);

    verifyNoInteractions(libraryServiceClient);
  }

  @Test
  void registerNamespacesUsesLoadedCacheWithoutReloading() {
    when(libraryServiceClient.getNamespaces())
        .thenReturn(
            List.of(
                NamespaceDto.builder()
                    .namespaceCanonical("http://valid.example/a")
                    .namespacePrefix("valid.a")
                    .build(),
                NamespaceDto.builder()
                    .namespaceCanonical("http://valid.example/b")
                    .namespacePrefix("valid.b")
                    .build()));

    namespaceService.loadNamespaces();

    NamespaceManager firstManager = mock(NamespaceManager.class);
    NamespaceManager secondManager = mock(NamespaceManager.class);

    namespaceService.registerNamespaces(firstManager);
    namespaceService.registerNamespaces(secondManager);

    verify(libraryServiceClient, times(1)).getNamespaces();
    verify(firstManager, times(2)).ensureNamespaceRegistered(any());
    verify(secondManager, times(2)).ensureNamespaceRegistered(any());
    verify(firstManager, never()).ensureNamespaceRegistered(null);
  }

  @Test
  void getNamespaceInfoReturnsLoadedNamespaceForCanonicalUrl() {
    // given
    String namespaceCanonical = "http://hl7.org/fhir/us/qicore";
    when(libraryServiceClient.getNamespaces())
        .thenReturn(
            List.of(
                NamespaceDto.builder()
                    .namespaceCanonical(namespaceCanonical)
                    .namespacePrefix("hl7.fhir.us.qicore")
                    .build()));
    namespaceService.loadNamespaces();

    // when
    NamespaceInfo namespaceInfo = namespaceService.getNamespaceInfo(namespaceCanonical);

    // then
    assertEquals("hl7.fhir.us.qicore", namespaceInfo.getName());
    assertEquals(namespaceCanonical, namespaceInfo.getUri());
  }

  @Test
  void getNamespaceInfoReturnsNullForUnknownCanonicalUrl() {
    // given
    when(libraryServiceClient.getNamespaces())
        .thenReturn(
            List.of(
                NamespaceDto.builder()
                    .namespaceCanonical("http://hl7.org/fhir/us/qicore")
                    .namespacePrefix("hl7.fhir.us.qicore")
                    .build()));
    namespaceService.loadNamespaces();

    // when
    NamespaceInfo namespaceInfo =
        namespaceService.getNamespaceInfo("http://example.com/unknown-namespace");

    // then
    assertNull(namespaceInfo);
  }
}
