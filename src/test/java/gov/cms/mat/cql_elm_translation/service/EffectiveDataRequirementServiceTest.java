package gov.cms.mat.cql_elm_translation.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.JsonParser;
import gov.cms.madie.cql_elm_translator.dto.CqlLibraryDetails;
import gov.cms.mat.cql.CqlTextParser;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.TranslationResource;
import gov.cms.madie.cql_elm_translator.utils.ResourceUtils;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.hl7.elm.r1.ExpressionDef;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EffectiveDataRequirementServiceTest {

  @Mock FhirContext fhirContextForR5;
  @Mock CqlLibraryService cqlLibraryService;
  @Mock JsonParser r5Parser;
  @Mock JsonParser jsonParserPrettier;
  @Mock CqlConversionService cqlConversionService;
  @Mock TranslationResource translationResource;
  @Mock CqlTranslator cqlTranslator;
  @Mock LibraryManager libraryManager;
  @Mock CompiledLibrary compiledLibrary;
  @Mock Library elmLibrary;
  @Mock VersionedIdentifier versionedIdentifier;
  @Mock Library.Usings usings;
  @Mock Library.Statements statements;
  @Mock ExpressionDef expressionDef;

  @InjectMocks EffectiveDataRequirementService effectiveDataRequirementService;

  private final String testAccessToken = "test_access_token";
  private static final String CQL_LIBRARY_NAME = "test_cql_library_name";
  private CqlLibraryDetails cqlLibraryDetails;

  @BeforeEach
  void setUp() {
    String cqlData = ResourceUtils.getData("/cv_populations.cql");
    cqlLibraryDetails =
        CqlLibraryDetails.builder()
            .libraryName(CQL_LIBRARY_NAME)
            .cql(cqlData)
            .expressions(Set.of("Initial Population"))
            .build();
  }

  @Test
  public void testGetEffectiveDataRequirementsSuccess() {
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlLibraryDetails.getCql()).getUsing());

    String fhirHelperString = ResourceUtils.getData("/fhirhelpers.cql");
    String suppDataString = ResourceUtils.getData("/SupplementalDataElements.cql");
    String cqlLibrary = ResourceUtils.getData("/cv_populations.cql");

    doReturn(fhirHelperString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("FHIRHelpers"), eq("4.0.001"), nullable(String.class));

    doReturn(suppDataString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("SupplementalDataElementsFHIR4"), eq("4.0.001"), nullable(String.class));

    doReturn(cqlLibrary)
        .when(cqlLibraryService)
        .getLibraryCql(eq("TestCVPopulations"), nullable(String.class), nullable(String.class));

    // Mock TranslationResource dependencies
    when(cqlConversionService.getTranslationResource(any())).thenReturn(translationResource);
    when(translationResource.buildTranslator(any())).thenReturn(cqlTranslator);
    when(translationResource.getLibraryManager()).thenReturn(libraryManager);
    when(cqlTranslator.getTranslatedLibrary()).thenReturn(compiledLibrary);
    when(libraryManager.getCompiledLibraries()).thenReturn(new ConcurrentHashMap<>());
    when(compiledLibrary.getIdentifier()).thenReturn(versionedIdentifier);
    when(libraryManager.getCompiledLibraries()).thenReturn(new ConcurrentHashMap<>());
    when(compiledLibrary.getLibrary()).thenReturn(elmLibrary);

    // Mock Library internals
    when(elmLibrary.getUsings()).thenReturn(usings);
    when(usings.getDef()).thenReturn(java.util.List.of());
    when(elmLibrary.getStatements()).thenReturn(statements);
    when(statements.getDef()).thenReturn(java.util.List.of());
    when(elmLibrary.getIncludes()).thenReturn(null);

    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    org.hl7.fhir.r5.model.Library r5Library =
        effectiveDataRequirementService.getEffectiveDataRequirements(
            cqlLibraryDetails, false, testAccessToken);
    assertEquals("effective-data-requirements", r5Library.getId());
  }

  @Test
  public void testGetEffectiveDataRequirementsStr() {
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlLibraryDetails.getCql()).getUsing());

    String fhirHelperString = ResourceUtils.getData("/fhirhelpers.cql");
    String suppDataString = ResourceUtils.getData("/SupplementalDataElements.cql");
    String cqlLibrary = ResourceUtils.getData("/cv_populations.cql");

    doReturn(fhirHelperString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("FHIRHelpers"), eq("4.0.001"), nullable(String.class));

    doReturn(suppDataString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("SupplementalDataElementsFHIR4"), eq("4.0.001"), nullable(String.class));

    doReturn(cqlLibrary)
        .when(cqlLibraryService)
        .getLibraryCql(eq("TestCVPopulations"), nullable(String.class), nullable(String.class));

    // Mock TranslationResource dependencies
    when(cqlConversionService.getTranslationResource(any())).thenReturn(translationResource);
    when(translationResource.buildTranslator(any())).thenReturn(cqlTranslator);
    when(translationResource.getLibraryManager()).thenReturn(libraryManager);
    when(cqlTranslator.getTranslatedLibrary()).thenReturn(compiledLibrary);
    when(libraryManager.getCompiledLibraries()).thenReturn(new ConcurrentHashMap<>());
    when(compiledLibrary.getIdentifier()).thenReturn(versionedIdentifier);
    when(libraryManager.getCompiledLibraries()).thenReturn(new ConcurrentHashMap<>());
    when(compiledLibrary.getLibrary()).thenReturn(elmLibrary);

    // Mock Library internals
    when(elmLibrary.getUsings()).thenReturn(usings);
    when(usings.getDef()).thenReturn(java.util.List.of());
    when(elmLibrary.getStatements()).thenReturn(statements);
    when(statements.getDef()).thenReturn(java.util.List.of());
    when(elmLibrary.getIncludes()).thenReturn(null);

    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    org.hl7.fhir.r5.model.Library r5Library =
        effectiveDataRequirementService.getEffectiveDataRequirements(
            cqlLibraryDetails, false, testAccessToken);

    when(fhirContextForR5.newJsonParser()).thenReturn(r5Parser);
    when(r5Parser.setPrettyPrint(true)).thenReturn(jsonParserPrettier);
    when(jsonParserPrettier.encodeResourceToString(any())).thenReturn("test");

    String r5LibraryStr =
        effectiveDataRequirementService.getEffectiveDataRequirementsStr(r5Library);
    assertEquals("test", r5LibraryStr);
  }

  @Test
  public void testGetEffectiveDataRequirementsSuccessExpressionsNull() {
    String cqlData = ResourceUtils.getData("/CMS135.cql");
    cqlLibraryDetails = CqlLibraryDetails.builder().libraryName("CMS135").cql(cqlData).build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cqlLibraryDetails.getCql()).getUsing());

    String fhirHelperString = ResourceUtils.getData("/fhirhelpers_4-4-000.cql");
    String suppDataString = ResourceUtils.getData("/SupplementalDataElements_3-5-000.cql");
    String qiCoreCommonString = ResourceUtils.getData("/QiCoreCommon_2-1-000.cql");
    String ahaOverallString = ResourceUtils.getData("/AHAOverall_2-8-000.cql");

    doReturn(fhirHelperString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("FHIRHelpers"), eq("4.4.000"), nullable(String.class));

    doReturn(suppDataString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("SupplementalDataElements"), eq("3.5.000"), nullable(String.class));

    doReturn(qiCoreCommonString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("QICoreCommon"), eq("2.1.000"), nullable(String.class));

    doReturn(ahaOverallString)
        .when(cqlLibraryService)
        .getLibraryCql(eq("AHAOverall"), eq("2.8.000"), nullable(String.class));

    // Mock TranslationResource dependencies
    when(cqlConversionService.getTranslationResource(any())).thenReturn(translationResource);
    when(translationResource.buildTranslator(any())).thenReturn(cqlTranslator);
    when(translationResource.getLibraryManager()).thenReturn(libraryManager);
    when(cqlTranslator.getTranslatedLibrary()).thenReturn(compiledLibrary);
    when(libraryManager.getCompiledLibraries()).thenReturn(new ConcurrentHashMap<>());
    when(compiledLibrary.getIdentifier()).thenReturn(versionedIdentifier);
    when(libraryManager.getCompiledLibraries()).thenReturn(new ConcurrentHashMap<>());
    when(compiledLibrary.getLibrary()).thenReturn(elmLibrary);

    // Mock Library internals
    when(elmLibrary.getUsings()).thenReturn(usings);
    when(usings.getDef()).thenReturn(java.util.List.of());
    when(elmLibrary.getStatements()).thenReturn(statements);
    when(statements.getDef()).thenReturn(java.util.List.of(expressionDef));
    when(expressionDef.getName()).thenReturn("TestExpression");
    when(elmLibrary.getIncludes()).thenReturn(null);

    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    org.hl7.fhir.r5.model.Library r5Library =
        effectiveDataRequirementService.getEffectiveDataRequirements(
            cqlLibraryDetails, false, testAccessToken);
    assertEquals("effective-data-requirements", r5Library.getId());
  }
}
