package gov.cms.mat.cql_elm_translation.utils.cql;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLIncludeLibrary;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLParameter;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.DefinitionContent;
import lombok.Getter;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.cql2elm.preprocessor.CqlPreprocessorElmCommonVisitor;
import org.cqframework.cql.elm.IdObjectFactory;
import org.cqframework.cql.gen.cqlLexer;
import org.cqframework.cql.gen.cqlParser;
import org.hl7.elm.r1.ExpressionDef;
import org.hl7.elm.r1.IncludeDef;
import org.hl7.elm.r1.Library;
import org.hl7.elm.r1.ParameterDef;

import gov.cms.mat.cql.CqlTextParser;
import gov.cms.mat.cql.elements.UsingProperties;
import gov.cms.mat.cql_elm_translation.cql_translator.TranslationResource;
import gov.cms.mat.cql_elm_translation.data.DataCriteria;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.Cql2ElmListener;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLCode;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLGraph;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLValueSet;

@Getter
public class CQLTools {

  private String parentLibraryString;
  private Map<String, String> childrenLibraries;
  private CompiledLibrary library;
  private CqlTranslator translator;

  /** Maps a valueset identifier to the datatypes it is using. */
  private Map<String, Set<String>> valuesetDataTypeMap = new HashMap<>();

  /** Maps a code identifier to the datatypes it is using */
  private Map<String, Set<String>> codeDataTypeMap = new HashMap<>();

  /** Maps an expression, to it's internal valueset - datatype map */
  private Map<String, Map<String, Set<String>>> expressionNameToValuesetDataTypeMap =
      new HashMap<>();

  /** Maps an expression, to its internal code - datatype map */
  private Map<String, Map<String, Set<String>>> expressionNameToCodeDataTypeMap = new HashMap<>();

  /** Maps an expression name to its return type (only function and definitions) */
  private Map<String, String> nameToReturnTypeMap = new HashMap<>();

  /**
   * The list of parent expressions. Often times, these are populations from MAT. Anything that can
   * be reached from this node in the graph should be considered used.
   */
  private Set<String> parentExpressions = new HashSet<>();

  private Map<String, String> qdmTypeInfoMap = new HashMap<>();

  private Map<String, CompiledLibrary> CompiledLibraryMap;

  /** Map in the form of <LibraryName-x.x.xxx, <ExpressionName, ReturnType>>. */
  private Map<String, Map<String, String>> allNamesToReturnTypeMap = new HashMap<>();

  private Map<String, String> expressionToReturnTypeMap = new HashMap<>();

  // used expression sets
  private Set<CQLIncludeLibrary> usedLibraries = new HashSet<>();
  private Set<CQLCode> usedCodes = new HashSet<>();
  private Set<String> usedValuesets = new HashSet<>();
  private Set<CQLValueSet> usedCQLValuesets = new HashSet<>();
  private Set<CQLParameter> usedParameters = new HashSet<>();
  private Map<String, Set<String>> usedDefinitions = new HashMap<>();
  private Map<String, Set<String>> usedFunctions = new HashMap<>();
  private Set<String> usedCodeSystems = new HashSet<>();
  private DataCriteria dataCriteria = new DataCriteria();
  private Set<DefinitionContent> definitionContents = new HashSet<>();
  private Map<String, Set<String>> callstack = new HashMap<>();
  private UsingProperties usingProperties;

  public CQLTools(
      String parentLibraryString,
      Map<String, String> childrenLibraries,
      Set<String> parentExpressions,
      CqlTranslator translator,
      Map<String, CompiledLibrary> translatedLibraries) {

    this.parentLibraryString = parentLibraryString;
    this.translator = translator;
    this.library = translator.getTranslatedLibrary();
    this.childrenLibraries = childrenLibraries;
    this.CompiledLibraryMap = translatedLibraries;
    this.parentExpressions = parentExpressions;
  }

  /**
   * The CQL Filter Entry Point.
   *
   * <p>This function will find all the used CQL expressions, create a valueset - datatype map and
   * code - datatype map, and find return types for each expression.
   *
   * @throws IOException
   */
  public void generate() throws IOException {
    InputStream stream =
        new ByteArrayInputStream(this.parentLibraryString.getBytes(StandardCharsets.UTF_8));
    cqlLexer lexer = new cqlLexer(CharStreams.fromStream(stream));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    cqlParser parser = new cqlParser(tokens);

    CQLGraph graph = new CQLGraph();

    Cql2ElmListener listener =
        new Cql2ElmListener(graph, library, CompiledLibraryMap, childrenLibraries);

    ParseTree tree = parser.library();
    CqlTextParser cqlTextParser = new CqlTextParser(this.parentLibraryString);
    UsingProperties usingProperties = cqlTextParser.getUsing();
    this.usingProperties = usingProperties;

    TranslationResource translationResource =
        TranslationResource.getInstance(
            usingProperties.getLibraryType() == "FHIR"); // <-- BADDDDD!!!! Defaults to fhir

    CqlPreprocessorElmCommonVisitor preprocessor =
        new CqlPreprocessorElmCommonVisitor(
            new LibraryBuilder(translationResource.getLibraryManager(), new IdObjectFactory()),
            tokens);

    preprocessor.visit(tree);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, tree);

    definitionContents.addAll(listener.getDefinitionContents());
    callstack = graph.getAdjacencyList();

    Set<CQLIncludeLibrary> librariesSet = new HashSet<>(listener.getLibraries());
    Set<String> valuesetsSet = new HashSet<>(listener.getValuesets());
    Set<CQLValueSet> cqlValuesetsSet = new HashSet<>(listener.getCqlValuesets());
    Set<String> codesSet = new HashSet<>(listener.getCodes());
    Set<String> codesystemsSet = new HashSet<>(listener.getCodesystems());
    Set<CQLParameter> parametersSet = new HashSet<>(listener.getParameters());
    Set<String> definitionsSet = new HashSet<>(listener.getDefinitions());
    Set<String> functionsSet = new HashSet<>(listener.getFunctions());
    Map<String, Map<String, Set<String>>> valuesetMap =
        new HashMap<>(listener.getValueSetDataTypeMap());
    Map<String, Map<String, Set<String>>> codeMap = new HashMap<>(listener.getCodeDataTypeMap());
    Map<String, String> valueSetOids = new HashMap<>(listener.getValueSetOids());
    Map<String, CQLCode> drcs = new HashMap<>(listener.getDrcs());
    Set<CQLCode> declaredCodes = new HashSet<>(listener.getDeclaredCodes());

    collectUsedExpressions(
        graph,
        librariesSet,
        valuesetsSet,
        cqlValuesetsSet,
        codesSet,
        codesystemsSet,
        parametersSet,
        definitionsSet,
        functionsSet,
        declaredCodes);
    collectValueSetCodeDataType(valuesetMap, codeMap);
    collectReturnTypeMap();
    collectDataCriteria(valueSetOids, drcs);
  }

  private void collectDataCriteria(Map<String, String> valueSetOids, Map<String, CQLCode> drcs) {
    valuesetDataTypeMap
        .keySet()
        .forEach(
            vs ->
                dataCriteria
                    .getDataCriteriaWithValueSets()
                    .put(
                        CQLValueSet.builder().name(vs).oid(valueSetOids.get(vs)).build(),
                        valuesetDataTypeMap.get(vs)));

    codeDataTypeMap
        .keySet()
        .forEach(
            code ->
                dataCriteria
                    .getDataCriteriaWithCodes()
                    .put(drcs.get(code), codeDataTypeMap.get(code)));
  }

  private void collectUsedExpressions(
      CQLGraph graph,
      Set<CQLIncludeLibrary> librariesSet,
      Set<String> valuesetsSet,
      Set<CQLValueSet> cqlValuesetsSet,
      Set<String> codesSet,
      Set<String> codesystemsSet,
      Set<CQLParameter> parametersSet,
      Set<String> definitionsSet,
      Set<String> functionsSet,
      Set<CQLCode> declaredCodes) {
    List<CQLIncludeLibrary> libraries = new ArrayList<>(librariesSet);
    List<String> valuesets = new ArrayList<>(valuesetsSet);
    List<CQLValueSet> cqlValuesets = new ArrayList<>(cqlValuesetsSet);
    List<String> codes = new ArrayList<>(codesSet);
    List<String> codesystems = new ArrayList<>(codesystemsSet);
    List<CQLParameter> parameters = new ArrayList<>(parametersSet);
    List<String> definitions = new ArrayList<>(definitionsSet);
    List<String> functions = new ArrayList<>(functionsSet);

    for (String parentExpression : parentExpressions) {
      collectUsedLibraries(graph, libraries, parentExpression);
      collectUsedValuesets(graph, valuesets, cqlValuesets, parentExpression);
      collectUsedCodes(graph, codes, parentExpression, declaredCodes);
      collectUsedCodeSystems(graph, codesystems, parentExpression);
      collectUsedParameters(graph, parameters, parentExpression);
      collectUsedDefinitions(graph, definitions, parentExpression);
      collectUsedFunctions(graph, functions, parentExpression);
    }
  }

  /**
   * For every function reference from the listener, checks if the parent expression and the
   * function make a path. If it does make a path, that means the function is used and should
   * therefore be added to the used functions list.
   *
   * @param graph the graph
   * @param functions the function references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedFunctions(
      CQLGraph graph, List<String> functions, String parentExpression) {
    for (String function : functions) {
      if (graph.isPath(parentExpression, function)) {
        if (usedFunctions.containsKey(function)) {
          usedFunctions.get(function).add(parentExpression);
        } else {
          usedFunctions.putIfAbsent(
              function, new HashSet<>(Collections.singleton(parentExpression)));
        }
      }
    }
  }

  /**
   * For every definition reference from the listener, checks if the parent expression and the
   * definition make a path. If it does make a path, that means the definition is used and should
   * therefore be added to the used definitions list.
   *
   * @param graph the graph
   * @param definitions the definition references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedDefinitions(
      CQLGraph graph, List<String> definitions, String parentExpression) {
    for (String definition : definitions) {
      if (graph.isPath(parentExpression, definition)
          && !definition.equalsIgnoreCase("Patient")
          && !definition.equalsIgnoreCase("Population")) {
        if (usedDefinitions.containsKey(definition)) {
          usedDefinitions.get(definition).add(parentExpression);
        } else {
          usedDefinitions.putIfAbsent(
              definition, new HashSet<>(Collections.singleton(parentExpression)));
        }
      }
    }
  }

  /**
   * For every parameter reference from the listener, checks if the parent expression and the
   * parameter make a path. If it does make a path, that means the parameter is used and should
   * therefore be added to the used parameters list.
   *
   * @param graph the graph
   * @param parameters the parameter references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedParameters(
      CQLGraph graph, List<CQLParameter> parameters, String parentExpression) {
    for (CQLParameter parameter : parameters) {
      if (graph.isPath(parentExpression, parameter.getParameterName())) {
        usedParameters.add(parameter);
      }
    }
  }

  /**
   * For every code reference from the listener, checks if the parent expression and the code make a
   * path. If it does make a path, that means the code is used and should therefore be added to the
   * used codes list.
   *
   * @param graph the graph
   * @param codes the code references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedCodes(
      CQLGraph graph, List<String> codes, String parentExpression, Set<CQLCode> declaredCodes) {
    for (String code : codes) {
      if (graph.isPath(parentExpression, code)) {
        Optional<CQLCode> used =
            declaredCodes.stream().filter(c -> c.getCodeIdentifier().equals(code)).findFirst();
        if (used.isPresent()) {
          usedCodes.add(used.get());
        }
      }
    }
  }

  /**
   * For every codesystem reference from the listener, checks if the parent expression and the
   * codesystem make a path. If it does make a path, that means the codesystem is used and should
   * therefore be added to the used codesystems list.
   *
   * @param graph the graph
   * @param codesystems the code references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedCodeSystems(
      CQLGraph graph, List<String> codesystems, String parentExpression) {
    for (String codesystem : codesystems) {
      if (graph.isPath(parentExpression, codesystem)) {
        usedCodeSystems.add(codesystem);
      }
    }
  }

  /**
   * For every valueset reference from the listener, checks if the parent expression and the
   * valueset make a path. If it does make a path, that means the valueset is used and should
   * therefore be added to the used valuesets list.
   *
   * @param graph the graph
   * @param valuesets the valueset references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedValuesets(
      CQLGraph graph,
      List<String> valuesets,
      List<CQLValueSet> cqlValuesets,
      String parentExpression) {
    for (String valueset : valuesets) {
      if (graph.isPath(parentExpression, valueset)) {
        usedValuesets.add(valueset);
      }
    }

    for (CQLValueSet valueset : cqlValuesets) {
      if (graph.isPath(parentExpression, valueset.getIdentifier())) {
        usedCQLValuesets.add(valueset);
      }
    }
  }

  /**
   * For every library reference from the listener, checks if the parent expression and the library
   * make a path. If it does make a path, that means the library is used and should therefore be
   * added to the used libraries list.
   *
   * @param graph the graph
   * @param libraries the library references from the listener
   * @param parentExpression the parent expression to check
   */
  private void collectUsedLibraries(
      CQLGraph graph, List<CQLIncludeLibrary> libraries, String parentExpression) {
    for (CQLIncludeLibrary library : libraries) {
      String path =
          library.getCqlLibraryName() + "-" + library.getVersion() + "|" + library.getAliasName();
      if (graph.isPath(parentExpression, path)) {
        usedLibraries.add(library);
      }
    }
  }

  /** Collects and creates a mapping of expression names to return types. */
  private void collectReturnTypeMap() {
    // the following makes an assumption that a library can not have any duplicate libraries
    // declared in it.

    // statements contain all function and definitions.
    Library.Statements statements = this.library.getLibrary().getStatements();
    Library.Parameters parameters = this.library.getLibrary().getParameters();
    String libraryName = this.library.getIdentifier().getId();
    String libraryVersion = this.library.getIdentifier().getVersion();
    this.allNamesToReturnTypeMap.put(libraryName + "-" + libraryVersion, new HashMap<>());

    for (ExpressionDef expression : statements.getDef()) {
      this.allNamesToReturnTypeMap
          .get(libraryName + "-" + libraryVersion)
          .put(expression.getName(), expression.getResultType().toString());
      this.nameToReturnTypeMap.put(expression.getName(), expression.getResultType().toString());
      this.expressionToReturnTypeMap.put(
          expression.getName(), expression.getResultType().toString());
    }

    if (parameters != null) {
      for (ParameterDef parameter : parameters.getDef()) {
        this.allNamesToReturnTypeMap
            .get(libraryName + "-" + libraryVersion)
            .put(parameter.getName(), parameter.getResultType().toString());
        this.nameToReturnTypeMap.put(parameter.getName(), parameter.getResultType().toString());
        this.expressionToReturnTypeMap.put(
            parameter.getName(), parameter.getResultType().toString());
      }
    }

    if (null != this.library.getLibrary().getIncludes()) {
      for (IncludeDef include : this.library.getLibrary().getIncludes().getDef()) {
        //                CompiledLibrary lib = this.CompiledLibraryMap.get(include.getPath() + "-"
        // + include.getVersion());
        CompiledLibrary lib = this.CompiledLibraryMap.get(include.getPath());

        Library.Statements statementsFromIncludedLibrary = lib.getLibrary().getStatements();
        Library.Parameters parametersFromIncludedLibrary = lib.getLibrary().getParameters();
        String includedLibraryName = lib.getIdentifier().getId();
        String includedLibraryVersion = lib.getIdentifier().getVersion();
        this.allNamesToReturnTypeMap.put(
            includedLibraryName + "-" + includedLibraryVersion, new HashMap<>());

        for (ExpressionDef expression : statementsFromIncludedLibrary.getDef()) {
          this.allNamesToReturnTypeMap
              .get(includedLibraryName + "-" + includedLibraryVersion)
              .put(expression.getName(), expression.getResultType().toString());
          this.expressionToReturnTypeMap.put(
              include.getLocalIdentifier() + "." + expression.getName(),
              expression.getResultType().toString());
        }

        if (parametersFromIncludedLibrary != null) {
          for (ParameterDef parameter : parametersFromIncludedLibrary.getDef()) {
            this.allNamesToReturnTypeMap
                .get(includedLibraryName + "-" + includedLibraryVersion)
                .put(parameter.getName(), parameter.getResultType().toString());
            this.expressionToReturnTypeMap.put(
                include.getLocalIdentifier() + "." + parameter.getName(),
                parameter.getResultType().toString());
          }
        }
      }
    }
  }

  /**
   * Collects the valueset - datatype map and code - datatype map.
   *
   * <p>It loos through each translator object from the parser, and then for each translator it
   * loops through the retrieves. It then puts the valueset/code and it's corresponding data type
   * into the correct map.
   */
  private void collectValueSetCodeDataType(
      Map<String, Map<String, Set<String>>> valuesetMap,
      Map<String, Map<String, Set<String>>> codeMap) {
    this.expressionNameToValuesetDataTypeMap = valuesetMap;
    this.expressionNameToCodeDataTypeMap = codeMap;
    this.valuesetDataTypeMap = flattenMap(valuesetMap);
    this.codeDataTypeMap = flattenMap(codeMap);
  }

  /**
   * The valueset/code - datatype map will come to us in a format of <ExpressionName, <Valueset/Code
   * Name, [DataType]>>. We want to also have a flattened map which will be in the format of
   * <Valueset/Code Name, [DataType]>
   *
   * @return a map in the above format
   */
  private Map<String, Set<String>> flattenMap(Map<String, Map<String, Set<String>>> mapToFlatten) {
    Map<String, Set<String>> flattenedMap = new HashMap<>();

    Set<String> keys = mapToFlatten.keySet();
    for (String key : keys) {
      Map<String, Set<String>> innerMap = mapToFlatten.get(key);

      Set<String> innerKeys = innerMap.keySet();
      for (String innerKey : innerKeys) {
        flattenedMap
            .computeIfAbsent(innerKey, k -> new HashSet<>(innerMap.get(innerKey)))
            .addAll(innerMap.get(innerKey));
      }
    }

    return flattenedMap;
  }

  public Map<String, List<String>> getValuesetDataTypeMap() {
    Map<String, List<String>> valuesetDataTypeMapWithList = new HashMap<>();

    List<String> keySet = new ArrayList<>(valuesetDataTypeMap.keySet());

    for (String key : keySet) {
      List<String> dataTypes = new ArrayList<>(valuesetDataTypeMap.get(key));
      valuesetDataTypeMapWithList.put(key, dataTypes);
    }

    return valuesetDataTypeMapWithList;
  }

  public Map<String, List<String>> getCodeDataTypeMap() {
    Map<String, List<String>> codeDataTypeMapWithList = new HashMap<>();

    List<String> keySet = new ArrayList<>(codeDataTypeMap.keySet());

    for (String key : keySet) {
      List<String> dataTypes = new ArrayList<>(codeDataTypeMap.get(key));
      codeDataTypeMapWithList.put(key, dataTypes);
    }

    return codeDataTypeMapWithList;
  }

  @Override
  public String toString() {

    StringBuilder builder = new StringBuilder();

    builder.append("RETURN TYPE MAP: " + this.getAllNamesToReturnTypeMap());
    builder.append("\n");
    builder.append("VALUSET-DATATYPE MAP: " + this.getValuesetDataTypeMap());
    builder.append("\n");
    builder.append("CODE-DATATYPE MAP: " + this.getCodeDataTypeMap());
    builder.append("\n");
    builder.append(
        "EXPRESSION NAME - VALUSET-DATATYPE MAP: " + this.getExpressionNameToValuesetDataTypeMap());
    builder.append("\n");
    builder.append(
        "EXPRESSION NAME - CODE-DATATYPE MAP: " + this.getExpressionNameToCodeDataTypeMap());
    builder.append("\n");
    builder.append("USED LIBRARIES: " + this.getUsedLibraries());
    builder.append("\n");
    builder.append("USED VALUESETS: " + this.getUsedValuesets());
    builder.append("\n");
    builder.append("USED CODESYSTEMS: " + this.getUsedCodeSystems());
    builder.append("\n");
    builder.append("USED CODES: " + this.getUsedCodes());
    builder.append("\n");
    builder.append("USED PARAMETERS: " + this.getUsedParameters());
    builder.append("\n");
    builder.append("USED DEFINITIONS: " + this.getUsedDefinitions());
    builder.append("\n");
    builder.append("USED FUNCTIONS " + this.getUsedFunctions());

    return builder.toString();
  }
}
