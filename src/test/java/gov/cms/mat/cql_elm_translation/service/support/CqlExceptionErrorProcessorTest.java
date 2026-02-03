package gov.cms.mat.cql_elm_translation.service.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import org.cqframework.cql.cql2elm.CqlCompilerException;

import org.cqframework.cql.cql2elm.CqlSemanticException;
import org.cqframework.cql.cql2elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
        new CqlCompilerException(
            "This is an External Error",
            new TrackBack(includedLibraryIdentifier, 7, 7, 7, 17),
            CqlCompilerException.ErrorSeverity.Error,
            null) {});
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
}
