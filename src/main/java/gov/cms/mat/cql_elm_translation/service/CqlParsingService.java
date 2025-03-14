package gov.cms.mat.cql_elm_translation.service;

import gov.cms.madie.cql_elm_translator.dto.CqlBuilderLookup;
import gov.cms.madie.cql_elm_translator.dto.CqlBuilderLookupComparator;
import gov.cms.madie.cql_elm_translator.utils.cql.CQLTools;
import gov.cms.madie.cql_elm_translator.utils.cql.parsing.model.CQLDefinition;
import gov.cms.madie.cql_elm_translator.utils.cql.parsing.model.CQLParameter;
import gov.cms.madie.cql_elm_translator.utils.cql.parsing.model.DefinitionContent;
import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static java.util.stream.Collectors.toSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class CqlParsingService extends CqlTooling {
  private final CqlLibraryService cqlLibraryService;

  /**
   * Parses the CQL and collect all CQL building blocks irrespective of used or unused(including for
   * the CQL of the included Libraries)
   *
   * @param cql- measure cql
   * @param accessToken Requesting User's Okta Bearer token
   * @return CqlBuilderLookup -> building blocks for CQL Definition UI builder
   */
  public CqlBuilderLookup getCqlBuilderLookups(
      String cql, String accessToken, CqlCompilerException.ErrorSeverity errorSeverity) {
    if (StringUtils.isBlank(cql)) {
      return null;
    }
    log.info("Preparing CqlBuilder Lookups");
    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, null, errorSeverity);
    // all parameters
    Set<CqlBuilderLookup.Lookup> parameters =
        cqlTools.getAllParameters().stream().map(this::buildParameterLookup).collect(toSet());

    // get all CQLDefinitions including functions
    Set<CQLDefinition> allCqlDefinitions = buildCqlDefinitions(cqlTools);

    // prepare lookups for definitions, functions and fluent functions from CQLDefinitions
    Set<CqlBuilderLookup.Lookup> definitions =
        new TreeSet<CqlBuilderLookup.Lookup>(
            new CqlBuilderLookupComparator<CqlBuilderLookup.Lookup>());
    Set<CqlBuilderLookup.Lookup> functions = new HashSet<>();
    Set<CqlBuilderLookup.Lookup> fluentFunctions = new HashSet<>();
    for (CQLDefinition cqlDefinition : allCqlDefinitions) {

      CqlBuilderLookup.Lookup lookup =
          buildCqlBuilderLookup(
              cqlDefinition.getName(),
              cqlDefinition.getLogic(),
              cqlDefinition.getParentLibrary(),
              cqlDefinition.getLibraryDisplayName(),
              cqlDefinition.getStartLine());
      if (cqlDefinition.isFunction()) {
        if (StringUtils.startsWith(cqlDefinition.getLogic(), "define fluent function")) {
          fluentFunctions.add(lookup);
        } else {
          functions.add(lookup);
        }
      } else {
        definitions.add(lookup);
      }
    }
    log.info("Preparing CqlBuilder Lookup completed");
    return CqlBuilderLookup.builder()
        .parameters(parameters)
        .definitions(definitions)
        .functions(functions)
        .fluentFunctions(fluentFunctions)
        .build();
  }

  private CqlBuilderLookup.Lookup buildParameterLookup(CQLParameter parameter) {
    String[] parts = parameter.getParameterName().split("\\|");
    String name = parameter.getParameterName();
    String libraryName = null;
    String libraryAlias = null;
    if (parts.length == 3) {
      libraryName = parts[0].split("-")[0];
      libraryAlias = parts[1];
      name = parts[2];
    }
    return buildCqlBuilderLookup(
        name,
        parameter.getParameterLogic(),
        libraryName,
        libraryAlias,
        0); // MAT-7897 - no need for this value yet..
  }

  private CqlBuilderLookup.Lookup buildCqlBuilderLookup(
      String name, String logic, String libraryName, String libraryAlias, int startLine) {

    return CqlBuilderLookup.Lookup.builder()
        .startLine(startLine)
        .name(name)
        .logic(logic)
        .libraryName(libraryName)
        .libraryAlias(libraryAlias)
        .build();
  }

  /**
   * Maps the references between CQL Definitions. In other words, which CQL Definitions and
   * Functions are called by which other CQL Definition.
   *
   * <p>Note that the resulting Map will most likely not contain all CQL Definitions and Function in
   * the provided CQL, and should be used when needing to work with all CQL Definitions and/or
   * Functions.
   *
   * @param cql CQL to parse.
   * @param accessToken Application user's Okta Bearer token.
   * @return Map representation of the callstack for CQL Definitions that call at least 1 other CQL
   *     Definition and/or Function.
   *     <p>Keys: Strings of CQL Definitions that reference/call 1 or more other CQL Definitions and
   *     Functions. CQL Definitions that do not reference any other CQL Definition and/or Function
   *     will not appear as a Key.
   *     <p>Values: Set of CQL Definition Objects that are referenced in the Key CQL Definition.
   */
  public Map<String, Set<CQLDefinition>> getDefinitionCallstacks(
      String cql, String accessToken, CqlCompilerException.ErrorSeverity errorSeverity) {
    CQLTools cqlTools = parseCql(cql, accessToken, cqlLibraryService, null, errorSeverity);
    Map<String, Set<String>> nodeGraph = cqlTools.getCallstack();
    Set<String> keys = nodeGraph.keySet();
    Set<CQLDefinition> cqlDefinitions =
        cqlTools.getDefinitionContents().stream()
            .filter(definitionContent -> keys.contains(definitionContent.getName()))
            .map(this::buildCqlDefinition)
            .collect(toSet());
    // remove null key, only contains included library references
    nodeGraph.remove(null);

    // Remove if a def is referring to its parent and creating an infinite loop
    // This happens if any function or definition uses System level Types (ex: System.Quantity {
    // value: value, unit: unit })
    nodeGraph.forEach((key, value) -> value.removeIf(def -> def.equals(key)));

    // remove nodes that don't reference any other Definition
    nodeGraph.keySet().removeIf(def -> nodeGraph.get(def).isEmpty());

    Map<String, Set<CQLDefinition>> callstack = new HashMap<>();

    for (String parentDefinition : nodeGraph.keySet()) {
      Set<String> defNames = nodeGraph.get(parentDefinition);
      Set<CQLDefinition> calledDefinitions = new HashSet<>(defNames.size());

      for (String defName : defNames) {
        Optional<CQLDefinition> calledDefinition =
            cqlDefinitions.stream().filter(d -> d.getId().equals(defName)).findFirst();
        calledDefinition.ifPresent(calledDefinitions::add);
      }

      if (!calledDefinitions.isEmpty()) {
        callstack.putIfAbsent(parentDefinition, calledDefinitions);
      }
    }
    return callstack;
  }

  private Set<CQLDefinition> buildCqlDefinitions(CQLTools cqlTools) {
    Set<DefinitionContent> definitions = cqlTools.getDefinitionContents();

    Set<CQLDefinition> results =
        definitions.stream().map(this::buildCqlDefinition).collect(toSet());

    return results;
  }

  private CQLDefinition buildCqlDefinition(DefinitionContent definitionContent) {
    // Graph includes retrieves, functions, and included library references.
    // Filter out any node that does not have CQL Definition text content.
    if (StringUtils.isBlank(definitionContent.getContent())) {
      return null;
    }

    CQLDefinition definition = new CQLDefinition();
    definition.setId(definitionContent.getName());
    definition.setDefinitionLogic(definitionContent.getContent());
    definition.setFunctionArguments(definitionContent.getFunctionArguments());
    definition.setStartLine(definitionContent.getStartLine());
    // Included Lib Define: AHAOverall-2.5.000|AHA|Has Left Ventricular Assist Device
    // Main Lib Define: Numerator
    String[] parts = definitionContent.getName().split("\\|");
    if (parts.length == 1) { // Define from Main Library
      definition.setDefinitionName(definitionContent.getName());
    } else if (parts.length >= 3) { // Define from some included Library
      definition.setLibraryDisplayName(parts[1]);
      definition.setDefinitionName(parts[2]);
      String[] libraryParts = parts[0].split("-");
      if (libraryParts.length == 2) {
        definition.setParentLibrary(libraryParts[0]);
        definition.setLibraryVersion(libraryParts[1]);
      }
    }
    definition.setFunction(definitionContent.isFunction());

    return definition;
  }
}
