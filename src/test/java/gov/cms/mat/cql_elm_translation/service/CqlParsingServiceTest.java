package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.dto.CqlBuilderLookup;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.utils.cql.parsing.model.CQLDefinition;

import static org.hamcrest.CoreMatchers.nullValue;

import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CqlParsingServiceTest implements ResourceFileUtil {
  @Mock private CqlLibraryService cqlLibraryService;
  @InjectMocks private CqlParsingService cqlParsingService;

  private static final String TOKEN = "John Doe";
  private String qiCoreHelperCql;
  private String qiCoreMeasureCql;

  @BeforeEach
  void setup() {
    qiCoreHelperCql = getData("/qicore_included_lib.cql");
    qiCoreMeasureCql = getData("/qicore_define_callstack.cql");
  }

  @Test
  void testCallstack() {
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(qiCoreMeasureCql).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    doReturn(qiCoreHelperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    doNothing().when(cqlLibraryService).setUpLibrarySourceProvider(anyString(), anyString());
    Map<String, Set<CQLDefinition>> definitionCallstacks =
        cqlParsingService.getDefinitionCallstacks(
            qiCoreMeasureCql, TOKEN, CqlCompilerException.ErrorSeverity.Info);

    CQLDefinition define1 =
        CQLDefinition.builder()
            .id("define 1")
            .definitionName("define 1")
            .definitionLogic("define \"define 1\":\n" + "    true")
            .build();

    CQLDefinition define2 =
        CQLDefinition.builder()
            .id("define 2")
            .definitionName("define 2")
            .definitionLogic("define \"define 2\":\n" + "    \"define 1\"")
            .build();

    CQLDefinition function =
        CQLDefinition.builder()
            .id("func")
            .definitionName("func")
            .definitionLogic("define function \"func\":\n" + "    true")
            .build();

    CQLDefinition helperDefine =
        CQLDefinition.builder()
            .id("HelperLibrary-0.0.000|Helper|Inpatient Encounter")
            .definitionName("Inpatient Encounter")
            .definitionLogic(
                "define \"Inpatient Encounter\":\n"
                    + "  [Encounter: \"Encounter Inpatient\"] EncounterInpatient\n"
                    + "\t\twhere EncounterInpatient.status = 'finished'\n"
                    + "\t\tand EncounterInpatient.period ends during day of \"Measurement Period\"")
            .parentLibrary("HelperLibrary")
            .libraryDisplayName("Helper")
            .libraryVersion("0.0.000")
            .build();

    CQLDefinition helperQuantity =
        CQLDefinition.builder()
            .id("HelperLibrary-0.0.000|Helper|Quantity")
            .definitionName("Quantity")
            .definitionLogic(
                "define function Quantity(value Decimal, unit String):\n"
                    + "  if value is not null then\n"
                    + "    System.Quantity { value: value, unit: unit }\n"
                    + "  else\n"
                    + "    null")
            .parentLibrary("HelperLibrary")
            .libraryDisplayName("Helper")
            .libraryVersion("0.0.000")
            .build();

    assertThat(definitionCallstacks.keySet().size(), is(4));
    assertThat(definitionCallstacks.get("define 3"), containsInAnyOrder(define1, define2));
    assertThat(definitionCallstacks.get("define 2"), contains(define1));
    assertThat(definitionCallstacks.get("define 4"), containsInAnyOrder(helperDefine, function));
    assertThat(definitionCallstacks.get("Testing Quantity"), contains(helperQuantity));
  }

  void testGetCqlBuilderLookups() {
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(qiCoreMeasureCql).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    doReturn(qiCoreHelperCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());
    doNothing().when(cqlLibraryService).setUpLibrarySourceProvider(anyString(), anyString());
    CqlBuilderLookup lookup =
        cqlParsingService.getCqlBuilderLookups(
            qiCoreMeasureCql, TOKEN, CqlCompilerException.ErrorSeverity.Info);
    assertThat(lookup.getParameters().size(), is(2));
    assertThat(lookup.getDefinitions().size(), is(5));
    assertThat(lookup.getFunctions().size(), is(1));
    assertThat(lookup.getFluentFunctions().size(), is(1));
  }

  @Test
  void testGetCqlBuilderLookupsForEmptyCql() {
    CqlBuilderLookup lookup =
        cqlParsingService.getCqlBuilderLookups(
            null, TOKEN, CqlCompilerException.ErrorSeverity.Info);
    assertThat(lookup, is(nullValue()));
  }
}
