package gov.cms.mat.cql_elm_translation.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.cms.mat.fhir.rest.dto.MatCqlConversionException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.tracking.TrackBack;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class CqlExceptionErrorProcessor {

  private static final ObjectMapper mapper = new ObjectMapper();

  private final List<CqlCompilerException> cqlErrors;
  private final List<CqlCompilerException> cqlTranslatorExternalErrors;
  private final String json;

  /**
   * Transforms CqlTranslatorException to MatCqlConversionException and prepend with
   * "errorExceptions" object to the translator.json
   */
  public CqlExceptionErrorProcessor(
      List<CqlCompilerException> cqlErrors,
      List<CqlCompilerException> cqlTranslatorExternalErrors,
      String json) {
    this.cqlErrors = cqlErrors;
    this.cqlTranslatorExternalErrors = cqlTranslatorExternalErrors;
    this.json = json;
  }

  public String addExceptionsToJson() {
    try {
      if (CollectionUtils.isEmpty(cqlErrors)
          && CollectionUtils.isEmpty(cqlTranslatorExternalErrors)) {
        return json;
      } else {
        return addErrorsToJson();
      }
    } catch (Exception e) {
      log.error("Cannot parse json.", e);
      log.trace(json);
      return json;
    }
  }

  private String addErrorsToJson() throws JsonProcessingException {
    JsonNode rootNode = mapper.readTree(json);

    ObjectNode updatedNode = (ObjectNode) rootNode;
    updatedNode.set("errorExceptions", mapper.valueToTree(buildMatErrors(cqlErrors)));
    updatedNode.set(
        "externalErrors", mapper.valueToTree(buildMatErrors(cqlTranslatorExternalErrors)));

    return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(updatedNode);
  }

  private List<MatCqlConversionException> buildMatErrors(
      List<CqlCompilerException> cqlCompilerExceptions) {
    return cqlCompilerExceptions.stream().map(this::createDto).collect(Collectors.toList());
  }

  private MatCqlConversionException createDto(CqlCompilerException cqlException) {
    MatCqlConversionException matCqlConversionException = buildMatError(cqlException);

    if (cqlException.getLocator() == null) {
      log.warn("Locator is null");
    } else {
      addLocatorData(cqlException.getLocator(), matCqlConversionException);
    }

    return matCqlConversionException;
  }

  private MatCqlConversionException buildMatError(CqlCompilerException cqlTranslatorException) {
    MatCqlConversionException matCqlConversionException = new MatCqlConversionException();
    matCqlConversionException.setErrorSeverity(cqlTranslatorException.getSeverity().name());

    if (cqlTranslatorException
        .toString()
        .contains("org.cqframework.cql.cql2elm.CqlSyntaxException")) {
      matCqlConversionException.setType("parsing");
    }

    log.debug("cqlTranslatorException:" + cqlTranslatorException.getMessage());
    try {
      String payload = escape(cqlTranslatorException.getMessage());
      // UsingProperties.getVersion should be an indicate that the CQL error was a result of Model
      // and version not found in the CQL
      if (StringUtils.contains(payload, "UsingProperties.getVersion")) {
        log.info("cqlTranslatorException: Payload" + payload);
        String rawPayload = clean(payload);
        log.info("cqlTranslatorException: RawPayload" + rawPayload);
        if (rawPayload.equals(
            "CannotinvokegovcmsmatcqlelementsUsingProperties"
                + "getVersionbecausethereturnvalueofjavalangThreadLocalgetisnull")) {
          payload = "Model Type and version are required";
        }
      }

      matCqlConversionException.setMessage(payload);
    } catch (Exception e) {
      log.info("Error building MADiEError message", e.getMessage());
      log.debug("Error building MADiEError", e);
      matCqlConversionException.setMessage("Exception");
    }

    return matCqlConversionException;
  }

  private void addLocatorData(
      TrackBack locator, MatCqlConversionException matCqlConversionException) {
    matCqlConversionException.setStartLine(locator.getStartLine());
    matCqlConversionException.setStartChar(locator.getStartChar());
    matCqlConversionException.setEndLine(locator.getEndLine());
    matCqlConversionException.setEndChar(locator.getEndChar());
    matCqlConversionException.setTargetIncludeLibraryVersionId(locator.getLibrary().getVersion());
    matCqlConversionException.setTargetIncludeLibraryId(locator.getLibrary().getId());
  }

  private String escape(String raw) {
    String escaped = raw;
    escaped = escaped.replace("\\", "\\\\");
    escaped = escaped.replace("\"", "\\\"");
    escaped = escaped.replace("\b", "\\b");
    escaped = escaped.replace("\f", "\\f");
    escaped = escaped.replace("\n", "\\n");
    escaped = escaped.replace("\r", "\\r");
    escaped = escaped.replace("\t", "\\t");
    // TODO: escape other non-printing characters using uXXXX notation
    return escaped;
  }

  private String clean(String raw) {
    return raw.replaceAll("[^a-zA-Z0-9]", "");
  }
}
