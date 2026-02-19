package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.elements.UsingProperties;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.TranslationResource;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.utils.cql.CQLTools;
import gov.cms.madie.cql_elm_translator.utils.cql.parsing.model.CQLModel;
import gov.cms.mat.cql_elm_translation.utils.cql.FhirUtil;
import gov.cms.mat.cql_elm_translation.utils.cql.VersionUtil;
import kotlinx.io.Source;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections4.CollectionUtils;
import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.cql.model.ModelIdentifier;
import org.hl7.elm.r1.VersionedIdentifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
@Slf4j
public abstract class CqlTooling {

  private final ModelManagerFactory modelManagerFactory;
  private final FhirUtil fhirUtil;
  private static final String MIN_FHIR_VERSION = "7.0.0";

  protected CQLTools parseCql(
      String cql,
      String accessToken,
      CqlLibraryService cqlLibraryService,
      Set<String> parentExpressions,
      CqlCompilerException.ErrorSeverity errorSeverity) {
    // Run Translator to compile libraries
    RequestData requestData = buildRequestData(cql, accessToken, cqlLibraryService, errorSeverity);
    TranslationResource translationResource = getTranslationResource(requestData);
    CqlTranslator cqlTranslator = translationResource.buildTranslator(requestData);
    Map<String, CompiledLibrary> translatedLibraries = new HashMap<>();
    translationResource
        .getLibraryManager()
        .getCompiledLibraries()
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
            getIncludedLibrariesCql(
                new MadieLibrarySourceProvider(),
                translationResource.getLibraryManager().getCompiledLibraries()),
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
      MadieLibrarySourceProvider librarySourceProvider,
      Map<VersionedIdentifier, CompiledLibrary> compiledLibraryMap) {
    Map<String, String> includedLibrariesCql = new HashMap<>();
    for (CompiledLibrary l : compiledLibraryMap.values()) {
      try {

        Source librarySource =
            librarySourceProvider.getLibrarySource(l.getLibrary().getIdentifier());
        String libraryCql = "";
        try {
          libraryCql = readSourceToString(librarySource);
        } finally {
          librarySource.close();
        }

        includedLibrariesCql.putIfAbsent(
            l.getIdentifier().getId() + "-" + l.getIdentifier().getVersion(), libraryCql);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return includedLibrariesCql;
  }

  private String readSourceToString(Source source) throws IOException {
    try {
      StringBuilder content = new StringBuilder();
      byte[] buffer = new byte[8192]; // 8KB buffer

      // Read from source in chunks using readAtMostTo
      int bytesRead;
      while ((bytesRead = source.readAtMostTo(buffer, 0, buffer.length)) > 0) {
        content.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
      }

      return content.toString();
    } finally {
      source.close();
    }
  }

  protected RequestData buildRequestData(
      String cql,
      String accessToken,
      CqlLibraryService cqlLibraryService,
      CqlCompilerException.ErrorSeverity errorSeverity) {
    cqlLibraryService.setUpLibrarySourceProvider(cql, accessToken);
    return RequestData.builder()
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
  }

  protected CqlTranslator processCqlData(RequestData requestData) {
    TranslationResource translationResource = getTranslationResource(requestData);
    return translationResource.buildTranslator(requestData);
  }

  protected TranslationResource getTranslationResource(RequestData requestData) {
    CqlTextParser cqlTextParser = new CqlTextParser(requestData.getCqlData());
    UsingProperties usingProperties =
        fhirUtil.getMostSpecificFhirModel(cqlTextParser.getAllUsings());
    // Treat any QICore/FHIR version >= baseline (MIN_FHIR_VERSION)
    if (usingProperties != null
        && usingProperties.getVersion() != null
        && VersionUtil.isVersionAtLeast(usingProperties.getVersion(), MIN_FHIR_VERSION)) {
      ModelIdentifier modelIdentifier =
          new ModelIdentifier(usingProperties.getLibraryType(), null, usingProperties.getVersion());
      ModelManager modelManager = modelManagerFactory.getModelManager(modelIdentifier);
      return new TranslationResource(modelManager, true);
    } else {
      return new TranslationResource(true);
    }
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
