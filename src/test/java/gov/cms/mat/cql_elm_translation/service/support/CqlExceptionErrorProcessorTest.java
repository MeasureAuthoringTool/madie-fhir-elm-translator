package gov.cms.mat.cql_elm_translation.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import org.cqframework.cql.cql2elm.CqlCompilerException;

import org.cqframework.cql.cql2elm.CqlSemanticException;
import org.cqframework.cql.cql2elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

class CqlExceptionErrorProcessorTest implements ResourceFileUtil {
  private static final ObjectMapper mapper = new ObjectMapper();

  private final List<CqlCompilerException> cqlErrors = new ArrayList<>();
  private final List<CqlCompilerException> cqlTranslatorExternalErrors = new ArrayList<>();
  private String inputJson;

  @BeforeEach
  void setUp() {
    inputJson = getData("/library-elm.json");

    VersionedIdentifier versionedIdentifier = new VersionedIdentifier();
    versionedIdentifier.setVersion("0.0.000");
    versionedIdentifier.setId("Library767878");
    cqlErrors.add(
        new CqlSemanticException(
            "Member relevantDatetime not found for type null.",
            new TrackBack(versionedIdentifier, 2, 2, 2, 12),
            CqlCompilerException.ErrorSeverity.Error,
            null));
    cqlErrors.add(
        new CqlSemanticException(
            "just warning",
            new TrackBack(versionedIdentifier, 3, 3, 3, 13),
            CqlCompilerException.ErrorSeverity.Warning,
            null));

    VersionedIdentifier includedLibraryIdentifier = new VersionedIdentifier();
    includedLibraryIdentifier.setVersion("7.0.000");
    includedLibraryIdentifier.setId("IncludedLibrary258");
    cqlTranslatorExternalErrors.add(
        new CqlSemanticException(
            "This is an External Error",
            new TrackBack(includedLibraryIdentifier, 7, 7, 7, 17),
            CqlCompilerException.ErrorSeverity.Error,
            null));
  }

  @Test
  void testProcessNoErrorsReturnsOriginalJson() {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(List.of(), List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();
    assertEquals(inputJson, resultJson);
  }

  @Test
  void testProcessWithErrorsFormatsCorrectly() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(cqlErrors, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    assertTrue(rootNode.has("errorExceptions"));
    assertEquals(2, rootNode.get("errorExceptions").size());
    assertEquals(
        cqlErrors.get(0).getMessage(),
        rootNode.get("errorExceptions").get(0).get("message").asText());
    assertEquals(
        cqlErrors.get(1).getMessage(),
        rootNode.get("errorExceptions").get(1).get("message").asText());

    assertEquals(0, rootNode.get("externalErrors").size());
  }

  @Test
  void testProcessWithExternalErrorsFormatsCorrectly() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(List.of(), cqlTranslatorExternalErrors, inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);

    assertEquals(0, rootNode.get("errorExceptions").size());

    assertTrue(rootNode.has("externalErrors"));
    assertEquals(1, rootNode.get("externalErrors").size());
    assertEquals(
        cqlTranslatorExternalErrors.get(0).getMessage(),
        rootNode.get("externalErrors").get(0).get("message").asText());
  }

  @Test
  void addExceptionsToJsonReturnsOriginalJsonForEmptyErrors() {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(new ArrayList<>(), new ArrayList<>(), inputJson);
    String resultJson = processor.addExceptionsToJson();
    assertEquals(inputJson, resultJson);
  }

  @Test
  void addExceptionsToJsonReturnsOriginalJsonForNullErrors() {
    CqlExceptionErrorProcessor processor = new CqlExceptionErrorProcessor(null, null, inputJson);
    String resultJson = processor.addExceptionsToJson();
    assertEquals(inputJson, resultJson);
  }

  @Test
  void addExceptionsToJsonIncludesLocatorData() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(cqlErrors, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    JsonNode firstError = rootNode.get("errorExceptions").get(0);

    assertEquals(2, firstError.get("startLine").asInt());
    assertEquals(2, firstError.get("startChar").asInt());
    assertEquals(2, firstError.get("endLine").asInt());
    assertEquals(12, firstError.get("endChar").asInt());
    assertEquals("0.0.000", firstError.get("targetIncludeLibraryVersionId").asText());
    assertEquals("Library767878", firstError.get("targetIncludeLibraryId").asText());
  }

  @Test
  void addExceptionsToJsonIncludesErrorSeverity() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(cqlErrors, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    assertEquals("Error", rootNode.get("errorExceptions").get(0).get("errorSeverity").asText());
    assertEquals("Warning", rootNode.get("errorExceptions").get(1).get("errorSeverity").asText());
  }

  @Test
  void addExceptionsToJsonHandlesBothErrorsAndExternalErrors() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(cqlErrors, cqlTranslatorExternalErrors, inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    assertEquals(2, rootNode.get("errorExceptions").size());
    assertEquals(1, rootNode.get("externalErrors").size());
  }

  @Test
  void addExceptionsToJsonHandlesInvalidJsonGracefully() {
    String invalidJson = "not valid json {{{";
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(cqlErrors, List.of(), invalidJson);
    String resultJson = processor.addExceptionsToJson();
    assertEquals(invalidJson, resultJson);
  }

  @Test
  void addExceptionsToJsonHandlesExceptionWithNullLocator() throws Exception {
    List<CqlCompilerException> errorsWithNullLocator = new ArrayList<>();
    errorsWithNullLocator.add(
        new CqlSemanticException(
            "Error without locator", null, CqlCompilerException.ErrorSeverity.Error, null));

    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(errorsWithNullLocator, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    assertEquals(1, rootNode.get("errorExceptions").size());
    assertEquals(
        "Error without locator", rootNode.get("errorExceptions").get(0).get("message").asText());
    assertTrue(rootNode.get("errorExceptions").get(0).get("startLine").isNull());
  }

  @Test
  void addExceptionsToJsonEscapesSpecialCharactersInMessage() throws Exception {
    List<CqlCompilerException> errorsWithSpecialChars = new ArrayList<>();
    VersionedIdentifier versionedIdentifier = new VersionedIdentifier();
    versionedIdentifier.setVersion("1.0.0");
    versionedIdentifier.setId("TestLib");
    errorsWithSpecialChars.add(
        new CqlSemanticException(
            "Error with \"quotes\" and\nnewlines\tand\ttabs",
            new TrackBack(versionedIdentifier, 1, 1, 1, 10),
            CqlCompilerException.ErrorSeverity.Error,
            null));

    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(errorsWithSpecialChars, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    String message = rootNode.get("errorExceptions").get(0).get("message").asText();
    assertTrue(message.contains("quotes"));
  }

  @Test
  void addExceptionsToJsonHandlesModelAndVersionError() throws Exception {
    List<CqlCompilerException> modelVersionErrors = new ArrayList<>();
    VersionedIdentifier versionedIdentifier = new VersionedIdentifier();
    versionedIdentifier.setVersion("1.0.0");
    versionedIdentifier.setId("TestLib");
    modelVersionErrors.add(
        new CqlSemanticException(
            "Cannot invoke \"gov.cms.mat.cql.elements.UsingProperties.getVersion()\" because the return value of \"java.lang.ThreadLocal.get()\" is null",
            new TrackBack(versionedIdentifier, 1, 1, 1, 10),
            CqlCompilerException.ErrorSeverity.Error,
            null));

    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(modelVersionErrors, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    String message = rootNode.get("errorExceptions").get(0).get("message").asText();
    assertEquals("Model Type and version are required", message);
  }

  @Test
  void addExceptionsToJsonPreservesOriginalJsonContent() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(cqlErrors, List.of(), inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    assertTrue(rootNode.has("library"));
  }

  @Test
  void addExceptionsToJsonIncludesExternalErrorLocatorData() throws Exception {
    CqlExceptionErrorProcessor processor =
        new CqlExceptionErrorProcessor(List.of(), cqlTranslatorExternalErrors, inputJson);
    String resultJson = processor.addExceptionsToJson();

    JsonNode rootNode = mapper.readTree(resultJson);
    JsonNode externalError = rootNode.get("externalErrors").get(0);

    assertEquals(7, externalError.get("startLine").asInt());
    assertEquals(7, externalError.get("startChar").asInt());
    assertEquals(7, externalError.get("endLine").asInt());
    assertEquals(17, externalError.get("endChar").asInt());
    assertEquals("7.0.000", externalError.get("targetIncludeLibraryVersionId").asText());
    assertEquals("IncludedLibrary258", externalError.get("targetIncludeLibraryId").asText());
  }
}
