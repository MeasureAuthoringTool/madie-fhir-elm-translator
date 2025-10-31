package gov.cms.mat.cql_elm_translation.service;

import ca.uhn.fhir.context.FhirContext;
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
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.cql2elm.model.Model;
import org.cqframework.fhir.npm.LibraryLoader;
import org.cqframework.fhir.npm.NpmModelInfoProvider;
import org.cqframework.fhir.npm.NpmPackageManager;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_40_50;
import org.hl7.fhir.convertors.conv40_50.VersionConvertor_40_50;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.model.ImplementationGuide;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@Slf4j
public abstract class CqlTooling implements ILoggingService {

  private final Logger logger = LoggerFactory.getLogger(CqlTooling.class);

  public void logMessage(String message) {
    logger.info(message);
  }

  public void logDebugMessage(ILoggingService.LogCategory category, String message) {
    logger.debug(message);
  }

  public void logWarningMessage(String message) {
    logger.warn(message);
  }

  public void logErrorMessage(String message) {
    logger.error(message);
  }

  public abstract ModelManagerFactory getModelManagerFactory();

  @Override
  public boolean isDebugLogging() {
    return false;
  }

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
    log.info("Getting included libraries CQL from translator");
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
    List<UsingProperties> allUsingProperties = cqlTextParser.getAllUsings();

    //    return TranslationResource.getInstance(
    //            usingProperties != null
    //                && ("FHIR".equals(usingProperties.getLibraryType())
    //                    || "QICore".equals(usingProperties.getLibraryType())))
    //        .buildTranslator(requestData);

    //    ModelManager qiCore =
    //        getModelManagerFactory()
    //            .getModelManager(new ModelIdentifier().withId("QICore").withVersion("7.0.0"));

    return TranslationResource.getInstance(buildModelManager(allUsingProperties), true)
        .buildTranslator(requestData);
    //    return TranslationResource.getInstance(qiCore).buildTranslator(requestData);
  }

  protected ModelManager buildModelManager(List<UsingProperties> usingProperties) {
    log.info("Building ModelManager with UsingProperties: {}", usingProperties);

    // TODO: load appropriate IG based on usingProperties
    Resource igResource =
            (Resource)
                    FhirContext.forR4Cached()
                            .newJsonParser()
                            .parseResource(
                                    CqlTooling.class
                                            .getClassLoader()
                                            .getResourceAsStream("packages/madie-ig.json"));



//    Resource igResource =
//        (Resource)
//            FhirContext.forR4Cached()
//                .newXmlParser()
//                .parseResource(
//                    CqlTooling.class
//                        .getClassLoader()
//                         .getResourceAsStream("packages/ImplementationGuide-hl7.fhir.us.qicore.xml"));
//                        .getResourceAsStream("packages/ImplementationGuide-gov.cms.madie-stu7.xml"));
    //
    // .getResourceAsStream("packages/ImplementationGuide-hl7.fhir.us.qicore.xml"));

    VersionConvertor_40_50 convertor = new VersionConvertor_40_50(new BaseAdvisor_40_50());
    ImplementationGuide implementationGuide =
        (ImplementationGuide) convertor.convertResource(igResource);

    //    // create our own FilesystemPackageCacheManager to manually load the IG package
    //    FilesystemPackageCacheManager fspcm = new FilesystemPackageCacheManager.Builder().build();
    //    // Load the IG package, which will be cached to the filesystem
    //    NpmPackage igNpmPackage = fspcm.loadPackage(implementationGuide.getPackageId(),
    // implementationGuide.getVersion());
    //    // Add the IG package to the list of known NPM Packages for this IG
    //    final List<NpmPackage> npmList = new ArrayList<>();
    //    npmList.add(igNpmPackage);
    //
    //    NpmPackageManager packageManager = new NpmPackageManager(implementationGuide, fspcm,
    // npmList);

    NpmPackageManager packageManager = new NpmPackageManager(implementationGuide);

    LibraryLoader reader = new LibraryLoader("5.0");
    NpmModelInfoProvider modelInfoProvider =
        new NpmModelInfoProvider(packageManager.getNpmList(), reader, this);

    ModelManager modelManager = new ModelManager();
    modelManager.getModelInfoLoader().registerModelInfoProvider(modelInfoProvider, true);
//    Model usCore = modelManager.resolveModel("USCore", "7.0.0"); // This works fine

    // TODO: only apply the workaround if usingProperties contains QICore v7
    //    ModelIdentifier workaroundUsCoreModelIdentifier =
    //        new ModelIdentifier()
    //            .withSystem(NamespaceManager.getUriPart(usCore.getModelInfo().getUrl()))
    //            .withId(usCore.getModelInfo().getName())
    //            .withVersion(usCore.getModelInfo().getVersion());
    //    modelManager.getGlobalCache().put(workaroundUsCoreModelIdentifier, usCore);

    Model qiCore = modelManager.resolveModel("QICore", "7.0.1"); // This fails to load USCore
//    log.info("got USCore model: {}", usCore);
    log.info("got QICore model: {}", qiCore);

    return modelManager;

    //    modelInfoProvider.load(new
    // ModelIdentifier().withSystem("http://hl7.org/fhir/us/qicore").withId("QICore"));
    //    ModelInfo qiCore = modelInfoProvider.load(new
    // ModelIdentifier().withSystem("http://hl7.org/fhir/us/qicore").withId("QICore"));
    //    modelManager.getModelInfoLoader().clearModelInfoProviders();
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
