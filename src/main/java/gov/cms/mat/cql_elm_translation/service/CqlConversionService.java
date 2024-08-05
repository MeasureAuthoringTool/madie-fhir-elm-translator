package gov.cms.mat.cql_elm_translation.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import gov.cms.madie.models.dto.TranslatedLibrary;
import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.exceptions.InternalServerException;
import gov.cms.mat.cql_elm_translation.exceptions.MissingLibraryCqlCompilerException;
import gov.cms.mat.cql_elm_translation.service.filters.AnnotationErrorFilter;
import gov.cms.mat.cql_elm_translation.service.filters.CqlTranslatorExceptionFilter;
import gov.cms.mat.cql_elm_translation.service.support.CqlExceptionErrorProcessor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryContentType;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.elm.serializing.ElmLibraryWriterFactory;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.VersionedIdentifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class CqlConversionService extends CqlTooling {

  private static final String LOG_MESSAGE_TEMPLATE = "ErrorSeverity: %s, Message: %s";
  private final CqlLibraryService cqlLibraryService;

  public CqlConversionPayload processCqlDataWithErrors(RequestData requestData) {
    // verify the presence of ^using .*version '[0-9]\.[0-9]\.[0-9]'$ on the cql
    Pattern pattern = Pattern.compile("using .*version '[0-9]\\.[0-9](\\.[0-9])?'");
    Matcher matcher = pattern.matcher(requestData.getCqlData());
    boolean noModelVersion = false;
    if (!matcher.find()) {
      log.debug("cqlTranslatorException: Model and version don't exist");
      log.debug("cqlTranslatorException: \n{}", requestData.getCqlData());
      noModelVersion = true;
    }
    // Gets the translator results
    CqlTranslator cqlTranslator = processCqlData(requestData);

    // QI-Core measures require FHIRHelpers...enforce this validation only for measure CQL
    processForMissingFhirHelpersLibrary(cqlTranslator, requestData.getCqlData());

    List<CqlCompilerException> cqlTranslatorExceptions =
        processErrors(
            requestData.getCqlData(), requestData.isShowWarnings(), cqlTranslator.getExceptions());

    AnnotationErrorFilter annotationErrorFilter =
        new AnnotationErrorFilter(
            requestData.getCqlData(), requestData.isShowWarnings(), cqlTranslator.toJson());

    String processedJson = annotationErrorFilter.filter();

    String jsonWithErrors =
        new CqlExceptionErrorProcessor(cqlTranslatorExceptions, processedJson).process();
    if (noModelVersion) {
      // Does jsonWithErrors contain "Model and version don't exist"
      // Looking for both the original error in cqlTranslatorException
      //  and the 'Model and version' error in jsonWithErrors

      DocumentContext jsonContext = JsonPath.parse(jsonWithErrors);
      try {
        JSONArray errorFound =
            jsonContext.read(
                "$.errorExceptions[?(@.message==\"Model Type and version are required\")]");
        if (errorFound.size() == 0) {
          log.error(
              "cqlTranslatorException: There was a problem finding Model and version, "
                  + "but the error wasn't correctly reported by cqlTranslator?");
          log.warn("Error list {}", cqlTranslatorExceptions);
        }
      } catch (Exception e) {
        log.info("Model missing, but likely an empty CQL file");
      }
    }
    return CqlConversionPayload.builder().json(jsonWithErrors).xml(cqlTranslator.toXml()).build();
  }

  /**
   * MODIFIES INPUT PARAMETER Checks for FHIRHelpers library and adds an exception on the
   * CqlTranslator object if missing. Exception is not added if the CQL is for the FHIRHelpers
   * library itself.
   *
   * @param cqlTranslator
   * @param cql
   */
  public void processForMissingFhirHelpersLibrary(CqlTranslator cqlTranslator, String cql) {
    VersionedIdentifier identifier =
        cqlTranslator.getTranslatedLibrary().getLibrary().getIdentifier();
    if (StringUtils.isNotBlank(cql)
        && identifier != null
        && !identifier.getId().contains("FHIRHelpers")) {
      Library.Includes includes = cqlTranslator.getTranslatedLibrary().getLibrary().getIncludes();
      if (includes == null
          || includes.getDef() == null
          || includes.getDef().isEmpty()
          || !includes.getDef().stream()
              .anyMatch(includeDef -> includeDef.getPath().contains("FHIRHelpers"))) {
        cqlTranslator
            .getExceptions()
            .add(
                new MissingLibraryCqlCompilerException(
                    "FHIRHelpers", cqlTranslator.getTranslatedLibrary().getIdentifier(), 1));
      }
    }
  }

  public TranslatedLibrary buildTranslatedLibrary(
      CompiledLibrary compiledLibrary, Map<String, String> cqlMap) {
    if (compiledLibrary == null) {
      return null;
    }
    Library library = compiledLibrary.getLibrary();
    String name = library.getIdentifier().getId();
    String version = library.getIdentifier().getVersion();
    try {
      return buildTranslatedLibrary(library, cqlMap.get(name + "-" + version));
    } catch (IOException e) {
      log.error("Error occurred while building the translated library artifacts: ", e);
      throw new InternalServerException(
          "An error occurred while building translated artifacts for library " + name);
    }
  }

  private TranslatedLibrary buildTranslatedLibrary(Library library, String cql) throws IOException {
    VersionedIdentifier identifier = library.getIdentifier();
    String elmJson = convertToJson(library, LibraryContentType.JSON);
    String elmXml = convertToJson(library, LibraryContentType.XML);
    String name = identifier.getId();
    String version = identifier.getVersion();
    return TranslatedLibrary.builder()
        .name(name)
        .version(version)
        .cql(cql)
        .elmJson(elmJson)
        .elmXml(elmXml)
        .build();
  }

  public String convertToJson(Library library, LibraryContentType contentType) throws IOException {
    StringWriter writer = new StringWriter();
    ElmLibraryWriterFactory.getWriter(contentType.mimeType()).write(library, writer);
    return writer.getBuffer().toString();
  }

  private List<CqlCompilerException> processErrors(
      String cqlData, boolean showWarnings, List<CqlCompilerException> cqlTranslatorExceptions) {
    logErrors(cqlTranslatorExceptions);
    return new CqlTranslatorExceptionFilter(cqlData, showWarnings, cqlTranslatorExceptions)
        .filter();
  }

  private void logErrors(List<CqlCompilerException> exceptions) {
    exceptions.forEach(e -> log.debug(formatMessage(e)));
  }

  private String formatMessage(CqlCompilerException e) {
    return String.format(
        LOG_MESSAGE_TEMPLATE,
        e.getSeverity() != null ? e.getSeverity().name() : null,
        e.getMessage());
  }
}
