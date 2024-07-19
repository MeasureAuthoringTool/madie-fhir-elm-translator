package gov.cms.mat.cql_elm_translation.cql_translator;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.fhir.ucum.UcumService;
import org.hl7.cql.model.NamespaceInfo;
import org.hl7.elm.r1.VersionedIdentifier;

import gov.cms.mat.cql_elm_translation.data.RequestData;

public class TranslationResource {
  public enum ModelType {
    FHIR,
    QICore
  }

  private static final MultivaluedMap<String, CqlCompilerOptions.Options> PARAMS_TO_OPTIONS_MAP =
      new MultivaluedHashMap<>() {
        {
          putSingle(
              "date-range-optimization", CqlCompilerOptions.Options.EnableDateRangeOptimization);
          putSingle("annotations", CqlCompilerOptions.Options.EnableAnnotations);
          putSingle("locators", CqlCompilerOptions.Options.EnableLocators);
          putSingle("result-types", CqlCompilerOptions.Options.EnableResultTypes);
          putSingle("detailed-errors", CqlCompilerOptions.Options.EnableDetailedErrors);
          putSingle("disable-list-traversal", CqlCompilerOptions.Options.DisableListTraversal);
          putSingle("disable-list-demotion", CqlCompilerOptions.Options.DisableListDemotion);
          putSingle("disable-list-promotion", CqlCompilerOptions.Options.DisableListPromotion);
          putSingle("enable-interval-demotion", CqlCompilerOptions.Options.EnableIntervalDemotion);
          putSingle(
              "enable-interval-promotion", CqlCompilerOptions.Options.EnableIntervalPromotion);
          putSingle(
              "disable-method-invocation", CqlCompilerOptions.Options.DisableMethodInvocation);
          putSingle("require-from-keyword", CqlCompilerOptions.Options.RequireFromKeyword);

          // Todo Do we even use these consolidated options ?
          put(
              "strict",
              Arrays.asList(
                  CqlCompilerOptions.Options.DisableListTraversal,
                  CqlCompilerOptions.Options.DisableListDemotion,
                  CqlCompilerOptions.Options.DisableListPromotion,
                  CqlCompilerOptions.Options.DisableMethodInvocation));
          put(
              "debug",
              Arrays.asList(
                  CqlCompilerOptions.Options.EnableAnnotations,
                  CqlCompilerOptions.Options.EnableLocators,
                  CqlCompilerOptions.Options.EnableResultTypes));
          put(
              "mat",
              Arrays.asList(
                  CqlCompilerOptions.Options.EnableAnnotations,
                  CqlCompilerOptions.Options.EnableLocators,
                  CqlCompilerOptions.Options.DisableListDemotion,
                  CqlCompilerOptions.Options.DisableListPromotion,
                  CqlCompilerOptions.Options.DisableMethodInvocation));
        }
      };

  private ModelManager modelManager;
  private LibraryManager libraryManager;

  static TranslationResource instance = null;

  public LibraryManager getLibraryManager() {
    return libraryManager;
  }

  private TranslationResource(boolean isFhir) {
    modelManager = new ModelManager();

    if (isFhir) {
      modelManager.resolveModel("FHIR", "4.0.1");
    } else {
      modelManager.resolveModel("QDM", "5.6");
    }
    // MAT-6240: Upgrading to cqframework 3.2.0 introduced a reliance on default options that would
    // include locator even if locator was false
    //   org.cqframework.cql.cql2elm.CqlCompilerOptions.setOptions only adds new options, it doesn't
    // remove
    this.libraryManager = new LibraryManager(modelManager, new CqlCompilerOptions());
  }

  public static TranslationResource getInstance(boolean isFhir) {
    instance = new TranslationResource(isFhir);
    // returns the singleton object
    return instance;
  }

  public CqlTranslator buildTranslator(RequestData requestData) {
    return buildTranslator(
        requestData.getCqlDataInputStream(), requestData.createMap(), requestData.getSourceInfo());
  }

  /*sets the options and calls cql-elm-translator using MatLibrarySourceProvider,
  which helps the translator to fetch the CQL of the included libraries from HAPI FHIR Server*/
  public CqlTranslator buildTranslator(
      InputStream cqlStream,
      MultivaluedMap<String, String> params,
      VersionedIdentifier sourceInfo) {
    try {
      UcumService ucumService = null;
      LibraryBuilder.SignatureLevel signatureLevel = LibraryBuilder.SignatureLevel.Overloads;
      List<CqlCompilerOptions.Options> optionsList = new ArrayList<>();

      for (String key : params.keySet()) {
        if (PARAMS_TO_OPTIONS_MAP.containsKey(key) && Boolean.parseBoolean(params.getFirst(key))) {
          optionsList.addAll(PARAMS_TO_OPTIONS_MAP.get(key));
        } else if (key.equals("validate-units") && Boolean.parseBoolean(params.getFirst(key))) {
          ucumService = getUcumService();
        } else if (key.equals("signatures")) {
          signatureLevel = LibraryBuilder.SignatureLevel.valueOf(params.getFirst("signatures"));
        }
      }

      CqlCompilerOptions.Options[] options = optionsList.toArray(new CqlCompilerOptions.Options[0]);

      libraryManager.getLibrarySourceLoader().registerProvider(new MadieLibrarySourceProvider());
      // this was the old code for version 2.11.0 of cqframework.. the constructor changed
      // drastically, so want to save this until we get past any regressions problems
      /*public static CqlTranslator fromStream(NamespaceInfo namespaceInfo,
       * VersionedIdentifier sourceInfo, InputStream cqlStream,
      LibraryManager libraryManager) throws IOException {*/
      //      return CqlTranslator.fromStream(
      //          cqlStream,
      //          modelManager,
      //          libraryManager,
      //          ucumService,
      //          CqlCompilerException.ErrorSeverity.Error,
      //          signatureLevel,
      //          options);

      NamespaceInfo nsInfo = null;

      libraryManager.setUcumService(ucumService);
      libraryManager.getCqlCompilerOptions().setSignatureLevel(signatureLevel);
      libraryManager.getCqlCompilerOptions().setOptions(options);
      CqlTranslator translator =
          CqlTranslator.fromStream(nsInfo, sourceInfo, cqlStream, libraryManager);
      return translator;

    } catch (Exception e) {
      throw new TranslationFailureException("Unable to read request", e);
    }
  }

  UcumService getUcumService() throws UcumException {
    return new UcumEssenceService(
        UcumEssenceService.class.getResourceAsStream("/ucum-essence.xml"));
  }
}
