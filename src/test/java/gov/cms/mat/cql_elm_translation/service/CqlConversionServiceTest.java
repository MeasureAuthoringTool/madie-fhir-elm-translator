package gov.cms.mat.cql_elm_translation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.dto.TranslatedLibrary;
import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.exceptions.InternalServerException;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;

import org.cqframework.cql.cql2elm.LibraryContentType;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@SpringBootTest
class CqlConversionServiceTest implements ResourceFileUtil {

  @Mock private CqlLibraryService cqlLibraryService;
  @InjectMocks private CqlConversionService service;

  private static RequestData requestData;

  @BeforeAll
  static void setUp() {
    requestData =
        RequestData.builder()
            .showWarnings(true)
            .annotations(true)
            .locators(true)
            .disableListDemotion(true)
            .disableListPromotion(true)
            .disableMethodInvocation(false)
            .validateUnits(true)
            .resultTypes(true)
            .build();
  }

  @Test
  void testProcessCqlDataWithErrors() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/fhir.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    CqlConversionPayload payload = service.processCqlDataWithErrors(data);
    assertNotNull(payload);
    String resultJson = payload.getJson();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      JsonNode jsonNode = objectMapper.readTree(resultJson);
      assertNotNull(jsonNode);

      JsonNode libraryNodeEx = jsonNode.at("/errorExceptions");
      assertNotNull(libraryNodeEx);
      assertFalse(libraryNodeEx.isMissingNode());
      assertThat(libraryNodeEx.isArray(), is(true));
      assertThat(
          libraryNodeEx.get(0).get("message").textValue(),
          is(
              equalTo(
                  "FHIRHelpers is required as an included library for QI-Core. Please add the appropriate version of FHIRHelpers to your CQL.")));
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  void testProcessCqlDataWithErrorsNonSupportedModel() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/non_supported_model.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    CqlConversionPayload payload = service.processCqlDataWithErrors(data);
    assertNotNull(payload);
    String resultJson = payload.getJson();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      JsonNode jsonNode = objectMapper.readTree(resultJson);
      assertNotNull(jsonNode);
      JsonNode libraryNode = jsonNode.at("/errorExceptions");
      assertNotNull(libraryNode);
      assertFalse(libraryNode.isMissingNode());
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  void testProcessCqlDataWithErrorsQICore() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/qicore.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    CqlConversionPayload payload = service.processCqlDataWithErrors(data);
    assertNotNull(payload);
    String resultJson = payload.getJson();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      JsonNode jsonNode = objectMapper.readTree(resultJson);
      assertNotNull(jsonNode);

      JsonNode libraryNodeEx = jsonNode.at("/errorExceptions");
      assertNotNull(libraryNodeEx);
      assertFalse(libraryNodeEx.isMissingNode());
      assertThat(libraryNodeEx.isArray(), is(true));
      assertThat(
          libraryNodeEx.get(0).get("message").textValue(),
          is(
              equalTo(
                  "FHIRHelpers is required as an included library for QI-Core. Please add the appropriate version of FHIRHelpers to your CQL.")));

    } catch (JsonProcessingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  void testProcessCqlDataWithErrorsMissingModel() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/missing-model.cql").getFile());
    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");
    CqlConversionPayload payload = service.processCqlDataWithErrors(data);
    assertNotNull(payload);
    String resultJson = payload.getJson();
    ObjectMapper objectMapper = new ObjectMapper();
    try {
      JsonNode jsonNode = objectMapper.readTree(resultJson);
      assertNotNull(jsonNode);
      JsonNode libraryNode = jsonNode.at("/errorExceptions");
      assertNotNull(libraryNode);

      assertFalse(libraryNode.isMissingNode());
      final AtomicBoolean foundMessage = new AtomicBoolean(Boolean.FALSE);
      libraryNode.forEach(
          node ->
              foundMessage.set(
                  foundMessage.get()
                      || node.get("message")
                          .asText()
                          .contains("Model Type and version are required")));
      assertTrue(foundMessage.get());
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  void testBuildTranslatedLibrary() {
    Library library = new Library();
    VersionedIdentifier identifier = new VersionedIdentifier();
    identifier.setId("test");
    identifier.setVersion("0.0.000");
    library.setIdentifier(identifier);
    CompiledLibrary compiledLibrary = new CompiledLibrary();
    compiledLibrary.setLibrary(library);
    TranslatedLibrary translatedLibrary =
        service.buildTranslatedLibrary(compiledLibrary, Map.of("test-0.0.000", "test cql"));
    assertThat(translatedLibrary.getName(), is(equalTo(identifier.getId())));
    assertThat(translatedLibrary.getVersion(), is(equalTo(identifier.getVersion())));
    assertThat(translatedLibrary.getCql(), is(equalTo("test cql")));
  }

  @Test
  void testBuildTranslatedLibraryWhenExceptionThrown() throws IOException {
    Library library = new Library();
    VersionedIdentifier identifier = new VersionedIdentifier();
    identifier.setId("test");
    identifier.setVersion("0.0.000");
    library.setIdentifier(identifier);
    CompiledLibrary compiledLibrary = new CompiledLibrary();
    compiledLibrary.setLibrary(library);
    CqlConversionService conversionService = spy(service);
    doThrow(new IOException("Failed to build the library"))
        .when(conversionService)
        .convertToJson(library, LibraryContentType.JSON);
    assertThrows(
        InternalServerException.class,
        () ->
            conversionService.buildTranslatedLibrary(
                compiledLibrary, Map.of("test-0.0.000", "test cql")),
        "An error occurred while building translated artifacts for library test");
  }

  @Test
  void testBuildTranslatedLibraryWhenCompiledLibraryIsNull() {
    TranslatedLibrary library = service.buildTranslatedLibrary(null, null);
    assertNull(library);
  }
}
