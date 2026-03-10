package gov.cms.mat.cql_elm_translation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.madie.models.dto.TranslatedLibrary;
import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.TranslationResource;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.exceptions.InternalServerException;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;

import gov.cms.mat.cql_elm_translation.utils.cql.FhirUtil;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryContentType;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.cql.model.NamespaceManager;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestTemplate;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import org.cqframework.cql.cql2elm.ModelManager;
import org.hl7.cql.model.ModelIdentifier;
import gov.cms.mat.cql.elements.UsingProperties;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;

@SpringBootTest
class CqlConversionServiceTest implements ResourceFileUtil {

  @Mock RestTemplate restTemplate;
  @Mock private CqlLibraryService cqlLibraryService;
  @Mock private FhirUtil fhirUtil;
  @Mock private ModelManagerFactory modelManagerFactory;
  @Mock private NamespaceManager namespaceManager;
  @InjectMocks private CqlConversionService service;

  private static RequestData requestData;
  private String supplementalDataElements;

  @BeforeAll
  static void setUp() {

    requestData =
        RequestData.builder()
            .errorSeverity(CqlCompilerException.ErrorSeverity.Info)
            .annotations(true)
            .locators(true)
            .disableListDemotion(true)
            .disableListPromotion(true)
            .disableMethodInvocation(false)
            .validateUnits(true)
            .resultTypes(true)
            .build();
  }

  @BeforeEach
  void setUpFhirDefaults() {
    UsingProperties defaultUsing = createUsingProperties("FHIR", "7.0.0");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(defaultUsing);
    lenient()
        .when(modelManagerFactory.getModelManager(any(ModelIdentifier.class)))
        .thenReturn(mock(ModelManager.class));
  }

  @Test
  void testGetTranslationResourceReturnsNullForNullRequestData() {
    // given
    RequestData nullRequestData = null;

    // when
    TranslationResource result = service.getTranslationResource(nullRequestData);

    // then
    assertThat(result, is(equalTo(null)));
  }

  @Test
  void testGetTranslationResourceReturnsNullForNullCql() {
    // given
    RequestData requestDataWithNullCql = requestData.toBuilder().cqlData(null).build();

    // when
    TranslationResource result = service.getTranslationResource(requestDataWithNullCql);

    // then
    assertThat(result, is(equalTo(null)));
  }

  @Test
  void testGetTranslationResourceReturnsNullForEmptyCql() {
    // given
    RequestData requestDataWithEmptyCql = requestData.toBuilder().cqlData("").build();

    // when
    TranslationResource result = service.getTranslationResource(requestDataWithEmptyCql);

    // then
    assertThat(result, is(equalTo(null)));
  }

  @Test
  void testGetTranslationResourceWithFhirVersion7OrAbove() {
    // given
    String cqlData = "using FHIR version '7.0.0'";
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties usingProperties = createUsingProperties("FHIR", "7.0.0");
    ModelManager modelManager = mock(ModelManager.class);
    when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);
    when(modelManagerFactory.getModelManager(any(ModelIdentifier.class))).thenReturn(modelManager);
    when(modelManager.getNamespaceManager()).thenReturn(namespaceManager);

    // when
    TranslationResource result = service.getTranslationResource(data);

    // then
    assertNotNull(result);
    assertThat(result.getLibraryManager(), is(notNullValue()));
  }

  @Test
  void testGetTranslationResourceWithFhirVersionBelow7() {
    // given
    String cqlData = "using FHIR version '4.0.1'";
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties usingProperties = createUsingProperties("FHIR", "4.0.1");
    when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);

    // when
    TranslationResource result = service.getTranslationResource(data);

    // then
    assertNotNull(result);
    assertThat(result.getLibraryManager(), is(notNullValue()));
  }

  @Test
  void testGetTranslationResourceWithNullUsingProperties() {
    // given
    String cqlData = "library Test version '1.0.0'";
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(null);

    // when
    TranslationResource result = service.getTranslationResource(data);

    // then
    assertNotNull(result);
    assertThat(result.getLibraryManager(), is(notNullValue()));
  }

  @Test
  void testGetTranslationResourceWithNullVersion() {
    // given
    String cqlData = "using FHIR";
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties usingProperties = createUsingProperties("FHIR", null);
    when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);

    // when
    TranslationResource result = service.getTranslationResource(data);

    // then
    assertNotNull(result);
    assertThat(result.getLibraryManager(), is(notNullValue()));
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

    // Override default mock for FHIR 4.0.1
    UsingProperties fhir401Using = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(fhir401Using);

    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    CqlConversionPayload payload = service.translateCqlToElm(data, true);
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
  void testProcessCqlDataWithErrorsQICore() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/qicore.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    // Override default mock for QICore 4.1.1
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);

    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    CqlConversionPayload payload = service.translateCqlToElm(data, false);
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
  void testProcessCqlDataWithDuplicateIncludes() {

    String supplementalDataElement;
    File inputCqlFile =
        new File(this.getClass().getResource("/SupplementalDataElements.cql").getFile());

    try {
      supplementalDataElement = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    String supplementalDataElement3;
    inputCqlFile =
        new File(this.getClass().getResource("/SupplementalDataElements_3.cql").getFile());

    try {
      supplementalDataElement3 = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    doReturn(supplementalDataElement)
        .when(cqlLibraryService)
        .getLibraryCql(any(String.class), eq("4.0.000"), any(String.class));
    doReturn(supplementalDataElement3)
        .when(cqlLibraryService)
        .getLibraryCql(any(String.class), eq("3.0.000"), any(String.class));

    String cqlData;
    inputCqlFile = new File(this.getClass().getResource("/fhir_duplicate_includes.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    // Override default mock for QICore 4.1.1
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);

    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");
    CqlConversionPayload payload = service.translateCqlToElm(data, true);
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

      final AtomicBoolean foundMessage = new AtomicBoolean(false);
      libraryNodeEx.forEach(
          node -> {
            if (node.get("message")
                .asText()
                .contains("Library SupplementalDataElements is already in use in this library.")) {
              foundMessage.set(true);
            }
          });
      assertTrue(foundMessage.get());
    } catch (JsonProcessingException e) {
      fail(e.getMessage());
    }
  }

  @Test
  void testProcessCqlDataWithDuplicateIncludeSameVersions() {

    String supplementalDataElement;
    File inputCqlFile =
        new File(this.getClass().getResource("/SupplementalDataElements.cql").getFile());

    try {
      supplementalDataElement = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    doReturn(supplementalDataElement)
        .when(cqlLibraryService)
        .getLibraryCql(any(String.class), any(String.class), any(String.class));

    String cqlData;
    inputCqlFile =
        new File(this.getClass().getResource("/fhir_duplicate_includes_sameversion.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    // Override default mock for QICore 4.1.1
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);

    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");
    CqlConversionPayload payload = service.translateCqlToElm(data, false);
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
      JsonNode finalNode = libraryNodeEx.get(libraryNodeEx.size() - 1);
      assertThat(
          finalNode.get("message").textValue(),
          is(
              equalTo(
                  "Library SupplementalDataElements Version 4.0.000 is already in use in this library.")));
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

  @Test
  void testProcessNoContextError() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/fhir_noContext.cql").getFile());
    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    // Override default mock for QICore 4.1.1
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);

    VersionedIdentifier identifier = new VersionedIdentifier();
    identifier.setId("FHIRHelpers");
    identifier.setVersion("4.1.000");

    RequestData requestData =
        RequestData.builder()
            .cqlData(cqlData)
            .errorSeverity(CqlCompilerException.ErrorSeverity.Info)
            .signatures(LibraryBuilder.SignatureLevel.Overloads)
            .annotations(Boolean.TRUE)
            .locators(Boolean.TRUE)
            .disableListDemotion(Boolean.TRUE)
            .disableListPromotion(Boolean.TRUE)
            .disableMethodInvocation(Boolean.TRUE)
            .validateUnits(Boolean.TRUE)
            .resultTypes(Boolean.TRUE)
            .sourceInfo(identifier)
            .build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(requestData);

    service.processNoContextError(cqlTranslator, cqlData);

    List<CqlCompilerException> exceptions = cqlTranslator.getExceptions();
    assertNotNull(exceptions);
    assertTrue(
        "Measure CQL must contain a Context.".equalsIgnoreCase(exceptions.get(1).getMessage()));
  }

  @Test
  void testProcessNoContextErrorCqlNull() {
    String cqlData;
    File inputCqlFile = new File(this.getClass().getResource("/fhir_noContext.cql").getFile());
    try {
      cqlData = new String(Files.readAllBytes(inputCqlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    // Override default mock for QICore 4.1.1
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);

    VersionedIdentifier identifier = new VersionedIdentifier();
    identifier.setId("FHIRHelpers");
    identifier.setVersion("4.1.000");

    RequestData requestData =
        RequestData.builder()
            .cqlData(cqlData)
            .errorSeverity(CqlCompilerException.ErrorSeverity.Info)
            .signatures(LibraryBuilder.SignatureLevel.Overloads)
            .annotations(Boolean.TRUE)
            .locators(Boolean.TRUE)
            .disableListDemotion(Boolean.TRUE)
            .disableListPromotion(Boolean.TRUE)
            .disableMethodInvocation(Boolean.TRUE)
            .validateUnits(Boolean.TRUE)
            .resultTypes(Boolean.TRUE)
            .sourceInfo(identifier)
            .build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(requestData);

    service.processNoContextError(cqlTranslator, null);

    List<CqlCompilerException> exceptions = cqlTranslator.getExceptions();
    assertNotNull(exceptions);
    assertFalse(
        "Measure CQL must contain a Context.".equalsIgnoreCase(exceptions.get(0).getMessage()));
  }

  @Test
  void givenFhirModelWhenProcessCqlDataThenReturnsTranslatorWithModelManager() {
    // given
    String cql = "using FHIR version '7.0.0'";
    RequestData data = requestData.toBuilder().cqlData(cql).build();
    ModelManager modelManager = mock(ModelManager.class);
    UsingProperties usingProperties = createUsingProperties("FHIR", "7.0.0");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);
    lenient()
        .when(modelManagerFactory.getModelManager(any(ModelIdentifier.class)))
        .thenReturn(modelManager);
    lenient().when(modelManager.getNamespaceManager()).thenReturn(namespaceManager);
    // when
    CqlTranslator translator = service.processCqlData(data);
    // then
    assertThat(translator, is(notNullValue()));
    assertThat(translator.getTranslatedLibrary(), is(notNullValue()));
  }

  @Test
  void givenNullCqlWhenProcessCqlDataThenReturnsNull() {
    // given
    RequestData data = requestData.toBuilder().cqlData(null).build();
    // when
    CqlTranslator translator = service.processCqlData(data);
    // then
    assertNull(translator);
  }

  @Test
  void givenBlankCqlWhenProcessCqlDataThenReturnsNull() {
    // given
    RequestData data = requestData.toBuilder().cqlData("").build();
    // when
    CqlTranslator translator = service.processCqlData(data);
    // then
    assertNull(translator);
  }

  @Test
  void givenNullRequestDataWhenProcessCqlDataThenReturnsNull() {
    // when
    CqlTranslator translator = service.processCqlData(null);
    // then
    assertNull(translator);
  }

  @Test
  void givenFhirModelWithOldVersionWhenProcessCqlDataThenReturnsTranslatorWithoutModelManager() {
    // given
    String cql = "using FHIR version '4.0.1'";
    RequestData data = requestData.toBuilder().cqlData(cql).build();
    UsingProperties usingProperties = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);
    // when
    CqlTranslator translator = service.processCqlData(data);
    // then
    assertThat(translator, is(notNullValue()));
    assertThat(translator.getTranslatedLibrary(), is(notNullValue()));
  }

  @Test
  void givenModelManagerFactoryThrowsWhenProcessCqlDataThenThrowsOrHandlesException() {
    // given
    String cql = "using FHIR version '7.0.0'";
    RequestData data = requestData.toBuilder().cqlData(cql).build();
    UsingProperties usingProperties = createUsingProperties("FHIR", "7.0.0");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);
    lenient()
        .when(modelManagerFactory.getModelManager(any(ModelIdentifier.class)))
        .thenThrow(new RuntimeException("error"));
    // when/then
    try {
      service.processCqlData(data);
      fail("Expected exception");
    } catch (RuntimeException ex) {
      assertThat(ex.getMessage(), is("error"));
    }
  }

  private UsingProperties createUsingProperties(String libraryType, String version) {
    return UsingProperties.builder().libraryType(libraryType).version(version).build();
  }

  @Test
  void translateCqlToElmReturnsPayloadWithJsonAndXml() {
    String cqlData = getData("/qicore_define_callstack.cql");
    String helperCql = getData("/qicore_included_lib.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);
    doReturn(helperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlConversionPayload payload = service.translateCqlToElm(data, false);

    assertNotNull(payload);
    assertNotNull(payload.getJson());
    assertNotNull(payload.getXml());
    assertTrue(payload.getJson().contains("library"));
    assertTrue(payload.getXml().contains("library"));
  }

  @Test
  void translateCqlToElmReportsModelAndVersionMissingError() {
    String cqlData = "library Test version '1.0.0'\ndefine \"Test\": true";
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties usingProperties = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(usingProperties);

    CqlConversionPayload payload = service.translateCqlToElm(data, false);

    assertNotNull(payload);
    assertNotNull(payload.getJson());
  }

  @Test
  void processForLibraryRulesExceptionsDoesNotAddErrorForFhirHelpers() {
    String cqlData = getData("/fhirhelpers.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties fhirUsing = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(fhirUsing);
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    int initialExceptionCount = cqlTranslator.getExceptions().size();
    service.processForLibraryRulesExceptions(cqlTranslator, cqlData);

    assertThat(cqlTranslator.getExceptions().size(), is(initialExceptionCount));
  }

  @Test
  void processForLibraryRulesExceptionsHandlesNullCql() {
    String cqlData = getData("/fhir.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties fhirUsing = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(fhirUsing);
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    int initialExceptionCount = cqlTranslator.getExceptions().size();
    service.processForLibraryRulesExceptions(cqlTranslator, null);

    assertThat(cqlTranslator.getExceptions().size(), is(initialExceptionCount));
  }

  @Test
  void processForLibraryRulesExceptionsHandlesBlankCql() {
    String cqlData = getData("/fhir.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties fhirUsing = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(fhirUsing);
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    int initialExceptionCount = cqlTranslator.getExceptions().size();
    service.processForLibraryRulesExceptions(cqlTranslator, "");

    assertThat(cqlTranslator.getExceptions().size(), is(initialExceptionCount));
  }

  @Test
  void convertToJsonReturnsJsonString() throws IOException {
    Library library = new Library();
    VersionedIdentifier identifier = new VersionedIdentifier();
    identifier.setId("TestLib");
    identifier.setVersion("1.0.0");
    library.setIdentifier(identifier);

    String json = service.convertToJson(library, LibraryContentType.JSON);

    assertNotNull(json);
    assertTrue(json.contains("TestLib"));
    assertTrue(json.contains("1.0.0"));
  }

  @Test
  void convertToXmlReturnsXmlString() throws IOException {
    Library library = new Library();
    VersionedIdentifier identifier = new VersionedIdentifier();
    identifier.setId("TestLib");
    identifier.setVersion("1.0.0");
    library.setIdentifier(identifier);

    String xml = service.convertToJson(library, LibraryContentType.XML);

    assertNotNull(xml);
    assertTrue(xml.contains("TestLib"));
    assertTrue(xml.contains("1.0.0"));
  }

  @Test
  void processNoContextErrorDoesNotAddErrorWhenContextExists() {
    String cqlData = getData("/qicore_define_callstack.cql");
    String helperCql = getData("/qicore_included_lib.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);
    doReturn(helperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    int initialExceptionCount = cqlTranslator.getExceptions().size();
    service.processNoContextError(cqlTranslator, cqlData);

    assertThat(cqlTranslator.getExceptions().size(), is(initialExceptionCount));
  }

  @Test
  void processNoContextErrorDoesNotAddErrorWhenCqlIsBlank() {
    String cqlData = getData("/fhir.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties fhirUsing = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(fhirUsing);
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    int initialExceptionCount = cqlTranslator.getExceptions().size();
    service.processNoContextError(cqlTranslator, "");

    assertThat(cqlTranslator.getExceptions().size(), is(initialExceptionCount));
  }

  @Test
  void getLinesReturnsEmptyListForNullInput() {
    List<Integer> lines = service.getLines(null, "start");

    assertTrue(lines.isEmpty());
  }

  @Test
  void getLinesReturnsEmptyListForEmptyInput() {
    List<Integer> lines = service.getLines(List.of(), "start");

    assertTrue(lines.isEmpty());
  }

  @Test
  void getUsingEndLinesReturnsLinesForValidTranslator() {
    String cqlData = getData("/fhir.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties fhirUsing = createUsingProperties("FHIR", "4.0.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(fhirUsing);
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    List<Integer> lines = service.getUsingEndLines(cqlTranslator);

    assertNotNull(lines);
  }

  @Test
  void getParameterEndLinesReturnsLinesForTranslatorWithParameters() {
    String cqlData = getData("/qicore_define_callstack.cql");
    String helperCql = getData("/qicore_included_lib.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);
    doReturn(helperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    List<Integer> lines = service.getParameterEndLines(cqlTranslator);

    assertNotNull(lines);
  }

  @Test
  void getValueSetsEndLinesReturnsEmptyForTranslatorWithoutValueSets() {
    String cqlData = getData("/qicore_define_callstack.cql");
    String helperCql = getData("/qicore_included_lib.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);
    doReturn(helperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    List<Integer> lines = service.getValueSetsEndLines(cqlTranslator);

    assertNotNull(lines);
    assertTrue(lines.isEmpty());
  }

  @Test
  void getCodeSystemEndLinesReturnsEmptyListForTranslatorWithoutCodeSystems() {
    String cqlData = getData("/qicore_define_callstack.cql");
    String helperCql = getData("/qicore_included_lib.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);
    doReturn(helperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    List<Integer> lines = service.getCodeSystemEndLines(cqlTranslator);

    assertNotNull(lines);
    assertTrue(lines.isEmpty());
  }

  @Test
  void getCodeEndLinesReturnsEmptyListForTranslatorWithoutCodes() {
    String cqlData = getData("/qicore_define_callstack.cql");
    String helperCql = getData("/qicore_included_lib.cql");
    RequestData data = requestData.toBuilder().cqlData(cqlData).build();
    UsingProperties qicoreUsing = createUsingProperties("QICore", "4.1.1");
    lenient().when(fhirUtil.getMostSpecificFhirModel(any())).thenReturn(qicoreUsing);
    doReturn(helperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlData).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    MadieLibrarySourceProvider.setAccessToken("access token");

    CqlTranslator cqlTranslator = service.processCqlData(data);
    List<Integer> lines = service.getCodeEndLines(cqlTranslator);

    assertNotNull(lines);
    assertTrue(lines.isEmpty());
  }
}
