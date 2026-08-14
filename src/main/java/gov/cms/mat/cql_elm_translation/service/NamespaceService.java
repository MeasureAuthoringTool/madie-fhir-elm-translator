package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql_elm_translation.clients.LibraryServiceClient;
import gov.cms.mat.cql_elm_translation.dto.NamespaceDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.cql.model.NamespaceInfo;
import org.hl7.cql.model.NamespaceManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NamespaceService {

  private final LibraryServiceClient libraryServiceClient;
  private volatile List<NamespaceInfo> namespaceInfos = Collections.emptyList();

  @EventListener(ApplicationReadyEvent.class)
  public void loadNamespaces() {
    namespaceInfos =
        libraryServiceClient.getNamespaces().stream()
            .filter(this::isValidNamespace)
            .map(dto -> new NamespaceInfo(dto.getNamespacePrefix(), dto.getNamespaceCanonical()))
            .toList();
    log.info("Loaded [{}] namespace definitions", namespaceInfos.size());
  }

  public void registerNamespaces(NamespaceManager namespaceManager) {
    if (namespaceManager == null) {
      return;
    }
    // there is a possibility of cql library service being down or rebooting during deployment while
    // translator is being
    // rebooted. Make sure to reattempt the namespace loading
    if (namespaceInfos.isEmpty()) {
      loadNamespaces();
    }

    namespaceInfos.forEach(namespaceManager::ensureNamespaceRegistered);
  }

  private boolean isValidNamespace(NamespaceDto dto) {
    return dto != null
        && StringUtils.isNotBlank(dto.getNamespaceCanonical())
        && StringUtils.isNotBlank(dto.getNamespacePrefix());
  }
}
