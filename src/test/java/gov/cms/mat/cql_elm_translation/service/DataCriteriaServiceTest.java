package gov.cms.mat.cql_elm_translation.service;

import gov.cms.madie.models.measure.Group;
import gov.cms.madie.models.measure.Measure;
import gov.cms.madie.models.measure.MeasureObservation;
import gov.cms.madie.models.measure.Population;
import gov.cms.madie.models.measure.Stratification;
import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.MadieLibrarySourceProvider;
import gov.cms.madie.cql_elm_translator.utils.cql.cql_translator.TranslationResource;
import gov.cms.madie.cql_elm_translator.utils.cql.data.RequestData;
import gov.cms.madie.cql_elm_translator.dto.SourceDataCriteria;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DataCriteriaServiceTest implements ResourceFileUtil {
  @Mock private CqlLibraryService cqlLibraryService;
  @InjectMocks private DataCriteriaService dataCriteriaService;

  private final String token = "token";
  private String cql;
  private String matGlobalCql;
  private RequestData requestData;

  @BeforeEach
  void setup() {
    matGlobalCql = getData("/mat_global_common_functions.cql");
    cql = getData("/qdm_data_criteria_retrieval_test.cql");

    requestData =
        RequestData.builder()
            .cqlData(cql)
            .errorSeverity(CqlCompilerException.ErrorSeverity.Info)
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

  @Test
  void testGetRelevantElements() {
    Population population = Population.builder().definition("Qualifying Encounters").build();
    MeasureObservation observation =
        MeasureObservation.builder().definition("Test Observation").build();
    Stratification stratification = new Stratification();
    stratification.setCqlDefinition("test stratification");
    Group group =
        Group.builder()
            .populations(Collections.singletonList(population))
            .measureObservations(Collections.singletonList(observation))
            .stratifications(Collections.singletonList(stratification))
            .build();
    Measure measure = Measure.builder().cql(cql).groups(Collections.singletonList(group)).build();
    MadieLibrarySourceProvider.setUsing(new CqlTextParser(cql).getUsing());
    MadieLibrarySourceProvider.setCqlLibraryService(cqlLibraryService);
    doReturn(matGlobalCql).when(cqlLibraryService).getLibraryCql(any(), any(), any());

    CqlTranslator translator = TranslationResource.getInstance(false).buildTranslator(requestData);
    verify(cqlLibraryService).getLibraryCql(any(), any(), any());
    Mockito.doNothing()
        .when(cqlLibraryService)
        .setUpLibrarySourceProvider(anyString(), anyString());

    Set<SourceDataCriteria> relevantElements =
        dataCriteriaService.getRelevantElements(
            measure, token, CqlCompilerException.ErrorSeverity.Info);

    // source data criteria for value set
    assertThat(relevantElements.size(), is(equalTo(2)));
    SourceDataCriteria firstData = ((TreeSet<SourceDataCriteria>) relevantElements).first();
    assertThat(firstData.getOid(), is(equalTo("2.16.840.1.113883.3.666.5.307")));
    assertThat(firstData.getTitle(), is(equalTo("Encounter Inpatient")));
    assertThat(firstData.getType(), is(equalTo("EncounterPerformed")));
    assertThat(
        firstData.getDescription(), is(equalTo("Encounter, Performed: Encounter Inpatient")));
    assertFalse(firstData.isDrc());

    // source data criteria for direct reference code
    SourceDataCriteria secondData = ((TreeSet<SourceDataCriteria>) relevantElements).last();
    assertTrue(secondData.isDrc());
    assertThat(secondData.getTitle(), is(equalTo("Clinical Examples")));
    assertThat(secondData.getType(), is(equalTo("EncounterPerformed")));
    assertThat(secondData.getDescription(), is(equalTo("Encounter, Performed: Clinical Examples")));
  }

  @Test
  void testGetRelevantElementsWhenNoSourceCriteriaFound() {
    String cql =
        "library DataCriteriaRetrivalTest version '0.0.000'\n"
            + "using QDM version '5.6'\n"
            + "valueset \"Encounter Inpatient\": 'urn:oid:2.16.840.1.113883.3.666.5.307'\n"
            + "parameter \"Measurement Period\" Interval<DateTime>\n"
            + "context Patient\n"
            + "define \"Qualifying Encounters\":\n true";

    Population population = Population.builder().definition("Qualifying Encounters").build();
    MeasureObservation observation =
        MeasureObservation.builder().definition("Test Observation").build();
    Stratification stratification = new Stratification();
    stratification.setCqlDefinition("test stratification");
    Group group =
        Group.builder()
            .populations(Collections.singletonList(population))
            .measureObservations(Collections.singletonList(observation))
            .stratifications(Collections.singletonList(stratification))
            .build();
    Measure measure = Measure.builder().cql(cql).groups(Collections.singletonList(group)).build();

    RequestData data = requestData.toBuilder().cqlData(cql).build();
    CqlTranslator translator =
        TranslationResource.getInstance(false)
            .buildTranslator(data.getCqlDataInputStream(), data.createMap(), data.getSourceInfo());

    Mockito.doNothing()
        .when(cqlLibraryService)
        .setUpLibrarySourceProvider(anyString(), anyString());

    Set<SourceDataCriteria> sourceDataCriteria =
        dataCriteriaService.getRelevantElements(
            measure, token, CqlCompilerException.ErrorSeverity.Info);

    assertThat(sourceDataCriteria.size(), is(equalTo(0)));
  }

  @Test
  void testGetRelevantElementsWhenNoCqlProvided() {
    Population population = Population.builder().definition("Qualifying Encounters").build();
    Group group = Group.builder().populations(Collections.singletonList(population)).build();
    Measure measure = Measure.builder().cql("").groups(Collections.singletonList(group)).build();
    Set<SourceDataCriteria> sourceDataCriteria =
        dataCriteriaService.getRelevantElements(
            measure, token, CqlCompilerException.ErrorSeverity.Info);
    assertThat(sourceDataCriteria.size(), is(equalTo(0)));
  }
}
