package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.elements.UsingProperties;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.TranslationResource;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.utils.cql.CQLTools;
import gov.cms.madie.cql_elm_translator.utils.cql.parsing.model.CQLModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Slf4j
public abstract class CqlTooling {
  protected CQLTools parseCql(
      String cql,
      String accessToken,
      CqlLibraryService cqlLibraryService,
      Set<String> parentExpressions,
      CqlCompilerException.ErrorSeverity errorSeverity) {
    // Run Translator to compile libraries
    CqlTranslator cqlTranslator = runTranslator(cql, accessToken, cqlLibraryService, errorSeverity);
    Map<String, CompiledLibrary> translatedLibraries = new HashMap<>();
    cqlTranslator
        .getTranslatedLibraries()
        .forEach((key, value) -> translatedLibraries.put(key.getId(), value));
    // if no parentExpressions provided, consider all expressions from main CQL
    Set<String> topLevelExpressions;
    if (CollectionUtils.isEmpty(parentExpressions)) {
      topLevelExpressions = getParentExpressions(cql);
    } else {
      topLevelExpressions = parentExpressions;
    }

    CQLTools cqlTools =
        new CQLTools(
            cql,
            getIncludedLibrariesCql(new MadieLibrarySourceProvider(), cqlTranslator),
            topLevelExpressions,
            cqlTranslator,
            translatedLibraries);

    try {
      cqlTools.generate();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return cqlTools;
  }

  protected Map<String, String> getIncludedLibrariesCql(
      MadieLibrarySourceProvider librarySourceProvider, CqlTranslator cqlTranslator) {
    Map<String, String> includedLibrariesCql = new HashMap<>();
    for (CompiledLibrary l : cqlTranslator.getTranslatedLibraries().values()) {
      try {
        includedLibrariesCql.putIfAbsent(
            l.getIdentifier().getId() + "-" + l.getIdentifier().getVersion(),
            new String(
                librarySourceProvider
                    .getLibrarySource(l.getLibrary().getIdentifier())
                    .readAllBytes(),
                StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return includedLibrariesCql;
  }

  // we need to default errorSeverity to Error, but also allow for warnings
  protected CqlTranslator runTranslator(
      String cql,
      String accessToken,
      CqlLibraryService cqlLibraryService,
      CqlCompilerException.ErrorSeverity errorSeverity) {
    cqlLibraryService.setUpLibrarySourceProvider(cql, accessToken);
    RequestData requestData =
        RequestData.builder()
            .cqlData(cql)
            .errorSeverity(errorSeverity)
            .signatures(LibraryBuilder.SignatureLevel.All)
            .annotations(true)
            .locators(true)
            .disableListDemotion(true)
            .disableListPromotion(true)
            .disableMethodInvocation(false)
            .validateUnits(true)
            .resultTypes(true)
            .build();

    return processCqlData(requestData);
  }

  protected CqlTranslator processCqlData(RequestData requestData) {
    CqlTextParser cqlTextParser = new CqlTextParser(requestData.getCqlData());
    UsingProperties usingProperties = cqlTextParser.getUsing();
    return TranslationResource.getInstance(
            usingProperties != null
                && ("FHIR".equals(usingProperties.getLibraryType())
                    || "QICore".equals(usingProperties.getLibraryType())))
        .buildTranslator(requestData);
  }

  private Set<String> getParentExpressions(String cql) {

    // CqlParserListener listener = new CqlParserListener(cql);
    CQLModel cqlModel = new CQLModel();
    // GAK MAT-6865 setting to default value because that is how it was when
    // this code was copied from CqlParserListener
    cqlModel.setContext("Patient");

    return cqlModel.getExpressionListFromCqlModel();
  }
}
