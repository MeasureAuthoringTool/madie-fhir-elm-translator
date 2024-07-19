package gov.cms.mat.cql_elm_translation.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.mat.cql_elm_translation.ResourceFileUtil;
import gov.cms.mat.cql_elm_translation.data.RequestData;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CqlConversionServicePropertyTest implements ResourceFileUtil {
  CqlConversionService cqlConversionService = new CqlConversionService(null);

  String cqlData;
  LibraryBuilder.SignatureLevel signatureLevel;
  Boolean annotations;
  Boolean locators;
  Boolean disableListDemotion;
  Boolean disableListPromotion;
  Boolean disableMethodInvocation;
  Boolean validateUnits;
  Boolean resultTypes;

  @BeforeEach
  public void setUp() {
    cqlData = getData("/fhir.cql");
    annotations = Boolean.TRUE;
    locators = Boolean.TRUE;
    disableListDemotion = Boolean.TRUE;
    disableListPromotion = Boolean.TRUE;
    disableMethodInvocation = Boolean.TRUE;
    validateUnits = Boolean.TRUE;
    resultTypes = Boolean.TRUE;

    cqlConversionService = new CqlConversionService(null);
  }

  @Test
  void process_Good() {
    CqlTranslator cqlTranslator = buildCqlTranslator();
    assertTrue(cqlTranslator.getErrors().isEmpty());
    assertFalse(cqlTranslator.toJson().contains("CqlToElmError"));
  }

  @Test
  void process_SignatureLevelOverloads() {
    String jsonDefault = getJson();

    signatureLevel = LibraryBuilder.SignatureLevel.Overloads;

    String jsonSignatureLevel = getJson();

    assertEquals(jsonDefault, jsonSignatureLevel);
  }

  @Test
  void process_annotations() {
    String jsonDefault = getJson();

    annotations = Boolean.FALSE;

    String jsonAnnotations = getJson();

    assertNotEquals(jsonDefault, jsonAnnotations); // data changed
  }

  @Test
  void process_locators() {
    String locatorTag = "\"locator\" : ";

    String jsonDefault = getJson();
    assertTrue(jsonDefault.contains(locatorTag));

    locators = Boolean.FALSE;

    String jsonSignatureLevelNone = getJson();
    assertFalse(jsonSignatureLevelNone.contains(locatorTag));

    assertNotEquals(jsonDefault, jsonSignatureLevelNone); // data changed
  }

  @Test
  void process_validateUnits() {
    String jsonDefault = getJson();

    validateUnits = Boolean.FALSE;

    String jsonAnnotations = getJson();

    assertEquals(jsonDefault, jsonAnnotations); // NO change TODO not expected
  }

  @Test
  void testProcessCqlDataWithErrors() throws JsonProcessingException {
    cqlData = getData("/cv_populations.cql");
    RequestData requestData = buildRequestData();
    CqlConversionPayload cqlConversionPayload =
        cqlConversionService.processCqlDataWithErrors(requestData);
    String elmJson = cqlConversionPayload.getJson();
    ObjectMapper objectMapper = new ObjectMapper();
    JsonNode rootNode = objectMapper.readTree(elmJson);
    ArrayNode defines = (ArrayNode) rootNode.get("library").get("statements").get("def");

    // initial population
    JsonNode ipNode = findCqlDefinitionNode("Initial Population", defines);
    assertEquals(ipNode.get("resultTypeSpecifier").get("type").asText(), "ListTypeSpecifier");
    assertEquals(
        ipNode.get("resultTypeSpecifier").get("elementType").get("name").asText(),
        "{http://hl7.org/fhir}Encounter");

    // Measure Population Exclusions
    JsonNode mpeNode = findCqlDefinitionNode("Measure Population Exclusions", defines);
    assertEquals(mpeNode.get("resultTypeSpecifier").get("type").asText(), "ListTypeSpecifier");
    assertEquals(
        mpeNode.get("resultTypeSpecifier").get("elementType").get("name").asText(),
        "{http://hl7.org/fhir}Encounter");

    // Boolean define
    JsonNode booleanNode = findCqlDefinitionNode("Unused Boolean Definition", defines);
    assertEquals(booleanNode.get("resultTypeName").asText(), "{urn:hl7-org:elm-types:r1}Boolean");

    // Integer type for function
    JsonNode moNode = findCqlDefinitionNode("Measure Observation", defines);
    assertEquals(moNode.get("resultTypeName").asText(), "{urn:hl7-org:elm-types:r1}Integer");
  }

  private JsonNode findCqlDefinitionNode(String cqlDefinition, ArrayNode defines) {
    Iterator<JsonNode> definitionIterator = defines.iterator();
    while (definitionIterator.hasNext()) {
      JsonNode node = definitionIterator.next();
      if (node.get("name").asText().contains(cqlDefinition)) {
        return node;
      }
    }
    return null;
  }

  private String getJson() {
    CqlTranslator cqlTranslator = buildCqlTranslator();

    assertTrue(cqlTranslator.getErrors().isEmpty());

    return cqlTranslator.toJson();
  }

  private CqlTranslator buildCqlTranslator() {
    RequestData requestData = buildRequestData();
    return cqlConversionService.processCqlData(requestData);
  }

  private RequestData buildRequestData() {
    return RequestData.builder()
        .cqlData(cqlData)
        .signatures(signatureLevel)
        .annotations(annotations)
        .locators(locators)
        .disableListDemotion(disableListDemotion)
        .disableListPromotion(disableListPromotion)
        .disableMethodInvocation(disableMethodInvocation)
        .validateUnits(validateUnits)
        .resultTypes(resultTypes)
        .build();
  }
}
