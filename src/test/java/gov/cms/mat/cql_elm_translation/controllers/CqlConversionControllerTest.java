package gov.cms.mat.cql_elm_translation.controllers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;

import java.io.UncheckedIOException;
import java.util.Optional;

import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.hl7.cql.model.NamespaceInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import gov.cms.mat.cql_elm_translation.service.NamespaceService;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.mat.cql_elm_translation.service.CqlConversionService;

@ExtendWith(MockitoExtension.class)
class CqlConversionControllerTest implements ResourceFileUtil {
  private static final String translatorOptionsTag = "\"translatorOptions\"";

  @Mock private CqlConversionService cqlConversionService;
  @Mock private CqlLibraryService cqlLibraryService;
  @Mock private NamespaceService namespaceService;
  @InjectMocks private CqlConversionController cqlConversionController;

  @Test
  void cqlToElmJson() {
    // given
    String cqlData = getData("/cv_populations.cql");
    String result = getData("/cv_populations.json");
    String namespaceCanonical = "http://hl7.org/fhir/us/qicore";
    NamespaceInfo namespaceInfo = new NamespaceInfo("hl7.fhir.us.qicore", namespaceCanonical);
    CqlConversionPayload payload = CqlConversionPayload.builder().json(result).build();
    Mockito.when(namespaceService.getNamespaceInfo(namespaceCanonical)).thenReturn(namespaceInfo);
    Mockito.when(cqlConversionService.translateCqlToElm(any(RequestData.class), anyBoolean()))
        .thenReturn(payload);

    // when
    CqlConversionPayload cqlConversionPayload =
        cqlConversionController.cqlToElmJson(
            cqlData,
            null,
            CqlCompilerException.ErrorSeverity.Info,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            true,
            Optional.of(namespaceCanonical),
            "test");

    // then
    assertEquals(result, cqlConversionPayload.getJson());
    ArgumentCaptor<RequestData> requestDataCaptor = ArgumentCaptor.forClass(RequestData.class);
    Mockito.verify(cqlConversionService)
        .translateCqlToElm(requestDataCaptor.capture(), anyBoolean());
    assertSame(namespaceInfo, requestDataCaptor.getValue().getNsInfo());
  }

  @Test
  void translatorOptionsRemoverNoErrors() {
    String json = getData("/fhir4_std_lib_no_errors.json");

    assertTrue(json.contains(translatorOptionsTag));

    CqlConversionController.TranslatorOptionsRemover translatorOptionsRemover =
        new CqlConversionController.TranslatorOptionsRemover(json);

    String cleaned = translatorOptionsRemover.clean();

    assertFalse(cleaned.contains(translatorOptionsTag));
  }

  @Test
  void translatorOptionsRemoverErrors() {

    String json = getData("/fhir4_std_lib_errors.json");

    assertTrue(json.contains(translatorOptionsTag));

    CqlConversionController.TranslatorOptionsRemover translatorOptionsRemover =
        new CqlConversionController.TranslatorOptionsRemover(json);

    String cleaned = translatorOptionsRemover.clean();
    assertFalse(cleaned.contains(translatorOptionsTag));
  }

  @Test
  void translatorOptionsRemoverNoAnnotations() {

    String json = getData("/fhir4_std_lib_no_annotations.json");

    assertFalse(json.contains(translatorOptionsTag));

    CqlConversionController.TranslatorOptionsRemover translatorOptionsRemover =
        new CqlConversionController.TranslatorOptionsRemover(json);

    String cleaned = translatorOptionsRemover.clean();

    assertFalse(json.contains(translatorOptionsTag));
    assertEquals(json, cleaned);
  }

  @Test
  void translatorOptionsRemoverEmptyAnnotations() {

    String json = getData("/fhir4_std_lib_empty_array_annotations.json");

    assertFalse(json.contains(translatorOptionsTag));

    CqlConversionController.TranslatorOptionsRemover translatorOptionsRemover =
        new CqlConversionController.TranslatorOptionsRemover(json);

    String cleaned = translatorOptionsRemover.clean();

    assertFalse(cleaned.contains(translatorOptionsTag));
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(cleaned);
    JsonNode libraryNode = rootNode.get("library");
    JsonNode annotationNode = libraryNode.get("annotation");
    assertNull(annotationNode);
  }

  @Test
  void translatorOptionsRemoverBadJson() {

    String json = "{this isn't json/>";
    CqlConversionController.TranslatorOptionsRemover translatorOptionsRemover =
        new CqlConversionController.TranslatorOptionsRemover(json);

    assertThrows(UncheckedIOException.class, () -> translatorOptionsRemover.clean());
  }
}
