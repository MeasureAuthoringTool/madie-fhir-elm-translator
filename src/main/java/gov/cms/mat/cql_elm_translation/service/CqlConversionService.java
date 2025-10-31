package gov.cms.mat.cql_elm_translation.service;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import gov.cms.madie.models.dto.TranslatedLibrary;
import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.madie.cql_elm_translator.utils.MadieCqlValidator;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.utils.cql.data.SimpleIncludeDef;
import gov.cms.madie.cql_elm_translator.exceptions.InternalServerException;
import gov.cms.mat.cql_elm_translation.exceptions.MissingContextException;
import gov.cms.mat.cql_elm_translation.exceptions.MissingLibraryCqlCompilerException;
import gov.cms.mat.cql_elm_translation.service.filters.CqlTranslatorExceptionFilter;
import gov.cms.mat.cql_elm_translation.service.support.CqlExceptionErrorProcessor;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONArray;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryContentType;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.elm.serializing.ElmLibraryWriterFactory;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.elm.r1.CodeDef;
import org.hl7.elm.r1.CodeSystemDef;
import org.hl7.elm.r1.Element;
import org.hl7.elm.r1.ExpressionDef;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.ParameterDef;
import org.hl7.elm.r1.UsingDef;
import org.hl7.elm.r1.ValueSetDef;
import org.hl7.elm.r1.VersionedIdentifier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor
public class CqlConversionService extends CqlTooling {

  private static final String LOG_MESSAGE_TEMPLATE = "ErrorSeverity: %s, Message: %s";
  @Getter @Autowired private ModelManagerFactory modelManagerFactory;

  public CqlConversionPayload translateCqlToElm(RequestData requestData, boolean checkContext) {
    // verify the presence of ^using .*version '[0-9]\.[0-9]\.[0-9]'$ on the cql
    Pattern pattern = Pattern.compile("using .*version '[0-9]\\.[0-9](\\.[0-9])?'");
    Matcher matcher = pattern.matcher(requestData.getCqlData());
    boolean noModelVersion = false;
    if (!matcher.find()) {
      log.debug("cqlTranslatorException: Model and version don't exist");
      log.debug("cqlTranslatorException: \n{}", requestData.getCqlData());
      noModelVersion = true;
    }
    log.info("ModelManagerFactory: {}", modelManagerFactory);

    // Gets the translator results
    CqlTranslator cqlTranslator = processCqlData(requestData);

    // QI-Core measures require FHIRHelpers...enforce this validation only for
    // measure CQL
    processForLibraryRulesExceptions(cqlTranslator, requestData.getCqlData());

    if (checkContext) {
      processNoContextError(cqlTranslator, requestData.getCqlData());
    }

    CqlTranslatorExceptionFilter cqlTranslatorExceptionFilter =
        new CqlTranslatorExceptionFilter(
            requestData.getCqlData(),
            requestData.getErrorSeverity(),
            cqlTranslator.getExceptions());
    cqlTranslatorExceptionFilter.generateCqlExceptions();

    String jsonWithErrors =
        new CqlExceptionErrorProcessor(
                cqlTranslatorExceptionFilter.getErrorExceptions(),
                cqlTranslatorExceptionFilter.getExternalErrors(),
                cqlTranslator.toJson())
            .addExceptionsToJson();
    if (noModelVersion) {
      // Does jsonWithErrors contain "Model and version don't exist"
      // Looking for both the original error in cqlTranslatorException
      // and the 'Model and version' error in jsonWithErrors

      DocumentContext jsonContext = JsonPath.parse(jsonWithErrors);
      try {
        JSONArray errorFound =
            jsonContext.read(
                "$.errorExceptions[?(@.message==\"Model Type and version are required\")]");
        if (errorFound.isEmpty()) {
          log.error(
              "cqlTranslatorException: There was a problem finding Model and version, "
                  + "but the error wasn't correctly reported by cqlTranslator?");
          log.warn("Error list {}", cqlTranslatorExceptionFilter.getErrorExceptions());
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
  private SimpleIncludeDef lastInclude = null;

  public void processForLibraryRulesExceptions(CqlTranslator cqlTranslator, String cql) {
    VersionedIdentifier identifier =
        cqlTranslator.getTranslatedLibrary().getLibrary().getIdentifier();
    if (StringUtils.isNotBlank(cql)) {
      if (identifier != null && !identifier.getId().contains("FHIRHelpers")) {
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
        new MadieCqlValidator().checkNoDuplicateIncludes(cqlTranslator, includes);
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

  private void logErrors(List<CqlCompilerException> exceptions) {
    exceptions.forEach(e -> log.debug(formatMessage(e)));
  }

  private String formatMessage(CqlCompilerException e) {
    return String.format(
        LOG_MESSAGE_TEMPLATE,
        e.getSeverity() != null ? e.getSeverity().name() : null,
        e.getMessage());
  }

  public void processNoContextError(CqlTranslator cqlTranslator, String cql) {
    if (StringUtils.isNotBlank(cql)
        && cqlTranslator.getTranslatedLibrary().getLibrary().getContexts() == null) {
      VersionedIdentifier identifier =
          cqlTranslator.getTranslatedLibrary().getLibrary().getIdentifier();

      List<Integer> allLines = new ArrayList<>();
      int startLineForContext = 0;
      Library.Statements statements =
          cqlTranslator.getTranslatedLibrary().getLibrary().getStatements();
      if (statements != null) {
        List<ExpressionDef> defs = statements.getDef();
        allLines = getLines(defs, "start");
        Collections.sort(allLines);
        startLineForContext = allLines.size() > 0 ? allLines.get(0) - 1 : 0;
      } else {
        allLines.addAll(getUsingEndLines(cqlTranslator));
        allLines.addAll(getParameterEndLines(cqlTranslator));
        allLines.addAll(getValueSetsEndLines(cqlTranslator));
        allLines.addAll(getCodeSystemEndLines(cqlTranslator));
        allLines.addAll(getCodeEndLines(cqlTranslator));
        allLines.sort(Comparator.reverseOrder());
        startLineForContext = allLines.size() > 0 ? allLines.get(0) + 1 : 0;
      }
      log.debug("Missing context at line: " + startLineForContext);

      cqlTranslator
          .getExceptions()
          .add(new MissingContextException(identifier, startLineForContext));
    }
  }

  protected List<Integer> getLines(List<? extends Element> defs, String type) {
    List<Integer> lines = new ArrayList<>();
    if (CollectionUtils.isNotEmpty(defs)) {
      for (Element def : defs) {
        List<TrackBack> trackBacks = def.getTrackbacks();
        if (CollectionUtils.isNotEmpty(trackBacks)) {
          for (TrackBack trackBack : trackBacks) {
            if ("start".equalsIgnoreCase(type)) {
              lines.add(trackBack.getStartLine());
            } else {
              lines.add(trackBack.getEndLine());
            }
          }
        }
      }
    }
    return lines;
  }

  protected List<Integer> getUsingEndLines(CqlTranslator cqlTranslator) {
    List<Integer> endLines = new ArrayList<>();
    Library.Usings usings = cqlTranslator.getTranslatedLibrary().getLibrary().getUsings();
    if (usings != null) {
      List<UsingDef> defs = usings.getDef();
      endLines = getLines(defs, "end");
    }
    return endLines;
  }

  protected List<Integer> getParameterEndLines(CqlTranslator cqlTranslator) {
    List<Integer> endLines = new ArrayList<>();
    Library.Parameters parameters =
        cqlTranslator.getTranslatedLibrary().getLibrary().getParameters();
    if (parameters != null) {
      List<ParameterDef> defs = parameters.getDef();
      endLines = getLines(defs, "end");
    }
    return endLines;
  }

  protected List<Integer> getValueSetsEndLines(CqlTranslator cqlTranslator) {
    List<Integer> endLines = new ArrayList<>();
    Library.ValueSets valuesets = cqlTranslator.getTranslatedLibrary().getLibrary().getValueSets();
    if (valuesets != null) {
      List<ValueSetDef> defs = valuesets.getDef();
      endLines = getLines(defs, "end");
    }
    return endLines;
  }

  protected List<Integer> getCodeSystemEndLines(CqlTranslator cqlTranslator) {
    List<Integer> endLines = new ArrayList<>();
    Library.CodeSystems codeSystems =
        cqlTranslator.getTranslatedLibrary().getLibrary().getCodeSystems();
    if (codeSystems != null) {
      List<CodeSystemDef> defs = codeSystems.getDef();
      endLines = getLines(defs, "end");
    }
    return endLines;
  }

  protected List<Integer> getCodeEndLines(CqlTranslator cqlTranslator) {
    List<Integer> endLines = new ArrayList<>();
    Library.Codes codes = cqlTranslator.getTranslatedLibrary().getLibrary().getCodes();
    if (codes != null) {
      List<CodeDef> defs = codes.getDef();
      endLines = getLines(defs, "end");
    }
    return endLines;
  }
}
