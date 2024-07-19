package gov.cms.mat.cql_elm_translation.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import gov.cms.mat.cql_elm_translation.dto.CqlLibraryDetails;
import gov.cms.mat.cql_elm_translation.cql_translator.TranslationResource;
import gov.cms.mat.cql_elm_translation.data.RequestData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.cqframework.cql.cql2elm.CqlCompilerOptions;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.elm.requirements.fhir.DataRequirementsProcessor;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EffectiveDataRequirementService {
  private final FhirContext fhirContextForR5;
  private final CqlLibraryService cqlLibraryService;

  private RequestData createDefaultRequestData(String cql) {
    return RequestData.builder()
        .cqlData(cql)
        .showWarnings(false)
        .annotations(true)
        .locators(true)
        .disableListDemotion(true)
        .disableListPromotion(true)
        .disableMethodInvocation(false)
        .validateUnits(true)
        .resultTypes(true)
        .build();
  }

  /**
   * @param libraryDetails - instance of CqlLibraryDetails
   * @param recursive- indicates if the data requirements gathered should be recursive
   * @param accessToken- used by MadieLibrarySourceProvider to make calls to cql-library-services
   * @return effective data requirement of type R5 library
   */
  public org.hl7.fhir.r5.model.Library getEffectiveDataRequirements(
      CqlLibraryDetails libraryDetails, boolean recursive, String accessToken) {

    // setting up the librarySourceProvider to fetch included libraries
    cqlLibraryService.setUpLibrarySourceProvider(libraryDetails.getCql(), accessToken);

    var translationResource = TranslationResource.getInstance(true);
    RequestData requestData = createDefaultRequestData(libraryDetails.getCql());
    CqlTranslator cqlTranslator = translationResource.buildTranslator(requestData);
    CompiledLibrary translatedLibrary = cqlTranslator.getTranslatedLibrary();
    LibraryManager libraryManager = translationResource.getLibraryManager();

    // providing compiled measureLibrary, as it cannot be fetched using
    // LibrarySourceProvider ( we are not storing measure libraries in MADiE cql-library-service)
    libraryManager.getCompiledLibraries().put(translatedLibrary.getIdentifier(), translatedLibrary);

    var dqReqTrans = new DataRequirementsProcessor();
    CqlCompilerOptions options = CqlCompilerOptions.defaultOptions();
    options.setCollapseDataRequirements(true); // removing duplicate data requirements
    options.setSignatureLevel(LibraryBuilder.SignatureLevel.Overloads);

    org.hl7.fhir.r5.model.Library effectiveDataRequirements =
        dqReqTrans.gatherDataRequirements(
            libraryManager,
            translatedLibrary,
            options,
            libraryDetails.getExpressions(),
            true,
            recursive);

    effectiveDataRequirements.setId("effective-data-requirements");
    return effectiveDataRequirements;
  }

  public String getEffectiveDataRequirementsStr(org.hl7.fhir.r5.model.Library r5Library) {
    return getR5Parser().setPrettyPrint(true).encodeResourceToString(r5Library);
  }

  protected IParser getR5Parser() {
    return fhirContextForR5.newJsonParser();
  }
}
