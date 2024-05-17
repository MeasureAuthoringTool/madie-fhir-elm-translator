package gov.cms.mat.cql_elm_translation.service;

import gov.cms.madie.models.measure.Measure;
import gov.cms.mat.cql_elm_translation.data.DataCriteria;
import gov.cms.mat.cql_elm_translation.data.DataElementDescriptor;
import gov.cms.mat.cql_elm_translation.dto.SourceDataCriteria;
import gov.cms.mat.cql_elm_translation.utils.cql.CQLTools;
import gov.cms.mat.cql_elm_translation.utils.cql.QdmDatatypeUtil;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLCode;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLValueSet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataCriteriaService extends CqlTooling {

  private final CqlLibraryService cqlLibraryService;

  public DataCriteria parseDataCriteriaFromCql(String cql, String accessToken) {
    return parseCql(cql, accessToken, cqlLibraryService, null).getDataCriteria();
  }

  public Set<SourceDataCriteria> getRelevantElements(Measure measure, String accessToken) {
    if (StringUtils.isBlank(measure.getCql())) {
      log.info("Data criteria not found as cql is blank");
      return Collections.emptySet();
    }

    Set<String> measureDefinitions = getUsedDefinitionsFromMeasure(measure);
    CQLTools cqlTools =
        parseCql(measure.getCql(), accessToken, cqlLibraryService, measureDefinitions);
    List<SourceDataCriteria> sourceDataCriteria = getSourceDataCriteria(cqlTools);

    Set<String> allUsedDefinitions = new HashSet<>(measureDefinitions);
    // add used definitions
    for (var entry : cqlTools.getUsedDefinitions().entrySet()) {
      allUsedDefinitions.add(entry.getKey());
      allUsedDefinitions.addAll(entry.getValue());
    }
    // add used functions
    for (var entry : cqlTools.getUsedFunctions().entrySet()) {
      allUsedDefinitions.add(entry.getKey());
      allUsedDefinitions.addAll(entry.getValue());
    }

    // Combines explicitly called definitions with any in the tree
    Set<String> values = new HashSet<>();
    allUsedDefinitions.forEach(
        def -> {
          if (!MapUtils.isEmpty(cqlTools.getExpressionNameToValuesetDataTypeMap())
              && !MapUtils.isEmpty(cqlTools.getExpressionNameToValuesetDataTypeMap().get(def))) {
            cqlTools
                .getExpressionNameToValuesetDataTypeMap()
                .get(def)
                .forEach((expression, valueSet) -> values.add(expression));
          }
          if (!MapUtils.isEmpty(cqlTools.getExpressionNameToCodeDataTypeMap())
              && !MapUtils.isEmpty(cqlTools.getExpressionNameToCodeDataTypeMap().get(def))) {
            cqlTools
                .getExpressionNameToCodeDataTypeMap()
                .get(def)
                .forEach((expression, valueSet) -> values.add(expression));
          }
        });

    Set<SourceDataCriteria> relevantSet = new TreeSet<>();
    sourceDataCriteria.stream()
        .filter(sourceDataCriteria1 -> values.contains(sourceDataCriteria1.getName()))
        .forEach(
            src -> {
              relevantSet.add(src);
            });
    return relevantSet;
  }

  private Set<String> getUsedDefinitionsFromMeasure(Measure measure) {
    if (measure == null || org.springframework.util.CollectionUtils.isEmpty(measure.getGroups())) {
      return Set.of();
    }
    Set<String> usedDefinitions = new HashSet<>();
    measure
        .getGroups()
        .forEach(
            group -> {
              group
                  .getPopulations()
                  .forEach(
                      population -> {
                        if (!population.getDefinition().isEmpty()) {
                          usedDefinitions.add(population.getDefinition());
                        }
                      });
              if (!CollectionUtils.isEmpty(group.getMeasureObservations())) {
                group
                    .getMeasureObservations()
                    .forEach(
                        measureObservation -> {
                          if (!measureObservation.getDefinition().isEmpty()) {
                            usedDefinitions.add(measureObservation.getDefinition());
                          }
                        });
              }
              if (!CollectionUtils.isEmpty(group.getStratifications())) {
                group
                    .getStratifications()
                    .forEach(
                        stratification -> {
                          if (!stratification.getCqlDefinition().isEmpty()) {
                            usedDefinitions.add(stratification.getCqlDefinition());
                          }
                        });
              }
            });
    measure
        .getSupplementalData()
        .forEach(defDescPair -> usedDefinitions.add(defDescPair.getDefinition()));
    measure
        .getRiskAdjustments()
        .forEach(defDescPair -> usedDefinitions.add(defDescPair.getDefinition()));
    return usedDefinitions;
  }

  public List<SourceDataCriteria> getSourceDataCriteria(CQLTools cqlTools) {
    DataCriteria dataCriteria = cqlTools.getDataCriteria();
    Map<CQLValueSet, Set<String>> criteriaWithValueSet =
        dataCriteria.getDataCriteriaWithValueSets();

    Map<CQLCode, Set<String>> criteriaWithCodes = dataCriteria.getDataCriteriaWithCodes();
    if (MapUtils.isEmpty(criteriaWithValueSet) && MapUtils.isEmpty(criteriaWithCodes)) {
      log.info("Data criteria not found for given cql");
      return Collections.emptyList();
    }
    // data criteria from value sets
    List<SourceDataCriteria> valueSetCriteria =
        criteriaWithValueSet.entrySet().stream()
            .map(
                criteria ->
                    buildSourceDataCriteriasForValueSet(criteria.getKey(), criteria.getValue()))
            .flatMap(Collection::stream)
            .collect(Collectors.toList());

    // data criteria from direct reference codes
    List<SourceDataCriteria> codeCriteria =
        criteriaWithCodes.entrySet().stream()
            .map(
                criteria -> buildSourceDataCriteriasForCode(criteria.getKey(), criteria.getValue()))
            .flatMap(Collection::stream)
            .toList();

    valueSetCriteria.addAll(codeCriteria);
    return valueSetCriteria;
  }

  public List<SourceDataCriteria> getSourceDataCriteria(String cql, String accessToken) {
    if (StringUtils.isBlank(cql)) {
      return Collections.emptyList();
    }

    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, null);
    return getSourceDataCriteria(cqlTools);
  }

  private Set<SourceDataCriteria> buildSourceDataCriteriasForCode(
      CQLCode code, Set<String> dataTypes) {
    // return nothing if datatype is missing..otherwise we'll get an NPE in buildCriteriaType
    if (CollectionUtils.isEmpty(dataTypes)) {
      return Set.of();
    }
    return dataTypes.stream()
        .map(
            dataType -> {
              DataElementDescriptor descriptor = getCriteriaType(dataType);
              String name = splitByPipeAndGetLast(code.getName());
              return SourceDataCriteria.builder()
                  // generate fake oid for drc, as it doesn't have one: e.g.id='71802-3',
                  // codeSystemName='LOINC', codeSystemVersion='null'
                  .oid(
                      "drc-"
                          + DigestUtils.md5Hex(
                              code.getCodeSystemName()
                                  + code.getId()
                                  + code.getCodeSystemVersion()))
                  .title(name)
                  .description(descriptor.title() + ": " + name)
                  .type(descriptor.dataType())
                  .drc(true)
                  .codeId(code.getId())
                  .name(code.getName())
                  .build();
            })
        .collect(Collectors.toSet());
  }

  private Set<SourceDataCriteria> buildSourceDataCriteriasForValueSet(
      CQLValueSet valueSet, Set<String> dataTypes) {
    // return nothing if datatype is missing..otherwise we'll get an NPE in buildCriteriaType
    if (CollectionUtils.isEmpty(dataTypes)) {
      return Set.of();
    }
    return dataTypes.stream()
        .map(
            dataType -> {
              DataElementDescriptor descriptor = getCriteriaType(dataType);
              String name = splitByPipeAndGetLast(valueSet.getName());
              String oid = valueSet.getOid();
              return SourceDataCriteria.builder()
                  .oid(oid)
                  .title(name)
                  .description(descriptor.title() + ": " + name)
                  .type(descriptor.dataType())
                  .name(valueSet.getName())
                  .build();
            })
        .collect(Collectors.toSet());
  }

  private String splitByPipeAndGetLast(String criteria) {
    String[] parts = criteria.split("\\|");
    // get last part
    return parts[parts.length - 1];
  }

  DataElementDescriptor getCriteriaType(String dataType) {
    return QdmDatatypeUtil.isValidNegation(dataType)
        ? QdmDatatypeUtil.getDescriptorForNegation(dataType)
        : new DataElementDescriptor(
            dataType.replace(",", "").replace(" ", "").replace("Not", ""), dataType);
  }
}
