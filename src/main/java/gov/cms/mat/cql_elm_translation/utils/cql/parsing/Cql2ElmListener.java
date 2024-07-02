package gov.cms.mat.cql_elm_translation.utils.cql.parsing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.UUID;

import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLCodeSystem;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLFunctionArgument;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLIncludeLibrary;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLParameter;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.DefinitionContent;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.misc.NotNull;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.apache.commons.collections4.CollectionUtils;
import org.cqframework.cql.cql2elm.LibraryBuilder;
import org.cqframework.cql.cql2elm.model.CompiledLibrary;
import org.cqframework.cql.cql2elm.preprocessor.CqlPreprocessorElmCommonVisitor;
import org.cqframework.cql.elm.IdObjectFactory;
import org.cqframework.cql.gen.cqlBaseListener;
import org.cqframework.cql.gen.cqlLexer;
import org.cqframework.cql.gen.cqlParser;
import org.cqframework.cql.gen.cqlParser.AliasedQuerySourceContext;
import org.cqframework.cql.gen.cqlParser.FunctionDefinitionContext;
import org.cqframework.cql.gen.cqlParser.LetClauseContext;
import org.cqframework.cql.gen.cqlParser.OperandDefinitionContext;
import org.cqframework.cql.gen.cqlParser.QualifiedFunctionContext;
import org.cqframework.cql.gen.cqlParser.QualifiedIdentifierExpressionContext;
import org.cqframework.cql.gen.cqlParser.ReferentialIdentifierContext;
import org.cqframework.cql.gen.cqlParser.ReturnClauseContext;
import org.cqframework.cql.gen.cqlParser.SortClauseContext;
import org.cqframework.cql.gen.cqlParser.WhereClauseContext;
import org.cqframework.cql.gen.cqlParser.WithClauseContext;
import org.cqframework.cql.gen.cqlParser.WithoutClauseContext;
import org.hl7.elm.r1.CodeDef;
import org.hl7.elm.r1.CodeSystemDef;
import org.hl7.elm.r1.Element;
import org.hl7.elm.r1.ExpressionDef;
import org.hl7.elm.r1.IncludeDef;
import org.hl7.elm.r1.ParameterDef;
import org.hl7.elm.r1.ValueSetDef;

import gov.cms.mat.cql_elm_translation.cql_translator.TranslationResource;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLCode;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLGraph;
import gov.cms.mat.cql_elm_translation.utils.cql.parsing.model.CQLValueSet;
import lombok.Getter;

@Slf4j
public class Cql2ElmListener extends cqlBaseListener {

  private static final List<String> CQL_DATA_TYPES =
      List.of(
          "Boolean",
          "Date",
          "DateTime",
          "Decimal",
          "Integer",
          "QDM Datatype",
          "Ratio",
          "String",
          "Time",
          "Others");

  /** The child CQL strings */
  private Map<String, String> childrenLibraries = new HashMap<>();

  /**
   * The identifier of the current library, relative to the library that brought us here. Will be in
   * the form of libraryName|alias
   */
  private final String libraryIdentifier;

  /**
   * The include def object which we are current parsing, relative to the library that brought us
   * here.
   */
  IncludeDef libraryAccessor = null;

  /** The current library object from the parser */
  private final CompiledLibrary library;

  /** The map of the other libraries in the current library */
  Map<String, CompiledLibrary> translatedLibraryMap;

  /** The current context, aka which expression are we currently in. */
  private String currentContext;

  @Getter private final Set<CQLIncludeLibrary> libraries = new HashSet<>();
  @Getter private final Set<String> valuesets = new HashSet<>();
  @Getter private final Set<CQLValueSet> cqlValuesets = new HashSet<>();
  @Getter private final Set<String> codes = new HashSet<>();
  @Getter private final Set<String> codesystems = new HashSet<>();
  @Getter private final Set<CQLParameter> parameters = new HashSet<>();
  @Getter private final Set<String> definitions = new HashSet<>();
  @Getter private final Set<DefinitionContent> definitionContents = new HashSet<>();
  @Getter private final Set<String> functions = new HashSet<>();
  @Getter private final HashMap<String, String> valueSetOids = new HashMap<>();
  @Getter private final HashMap<String, CQLCode> drcs = new HashMap<>();
  @Getter private final Map<String, Map<String, Set<String>>> valueSetDataTypeMap = new HashMap<>();
  @Getter private final Map<String, Map<String, Set<String>>> codeDataTypeMap = new HashMap<>();

  @Getter private final Map<String, CQLCodeSystem> codeSystemMap = new HashMap<>();
  @Getter private final Set<CQLCode> declaredCodes = new HashSet<>();

  private final Stack<String> namespace = new Stack<>();

  @Getter private final CQLGraph graph;

  public Cql2ElmListener(
      CQLGraph graph,
      CompiledLibrary library,
      Map<String, CompiledLibrary> translatedLibraryMap,
      Map<String, String> childrenLibraries) {
    this.graph = graph;
    this.library = library;
    this.translatedLibraryMap = translatedLibraryMap;
    this.libraryIdentifier = "";
    this.childrenLibraries = childrenLibraries;
  }

  public Cql2ElmListener(
      String libraryIdentifier,
      CQLGraph graph,
      CompiledLibrary library,
      Map<String, CompiledLibrary> translatedLibraryMap,
      Map<String, String> childrenLibraries) {
    this.graph = graph;
    this.library = library;
    this.translatedLibraryMap = translatedLibraryMap;
    this.libraryIdentifier = libraryIdentifier;
    this.childrenLibraries = childrenLibraries;
  }

  private CompiledLibrary getCurrentLibraryContext() {
    if (libraryAccessor != null) {
      //            return this.translatedLibraryMap.get(libraryAccessor.getPath() + "-" +
      // libraryAccessor.getVersion());
      return this.translatedLibraryMap.get(libraryAccessor.getPath());
    }

    return this.library;
  }

  @Override
  public void enterCodesystemDefinition(cqlParser.CodesystemDefinitionContext ctx) {
    String identifier = parseString(ctx.identifier().getText());

    if (library.resolve(identifier) instanceof CodeSystemDef csDef) {
      CQLCodeSystem codeSystem = new CQLCodeSystem();
      codeSystem.setId(csDef.getId());
      codeSystem.setOID(csDef.getId());
      codeSystem.setCodeSystemName(csDef.getName());

      codeSystemMap.putIfAbsent(identifier, codeSystem);
    }
  }

  @Override
  public void enterQualifiedFunction(QualifiedFunctionContext ctx) {
    if (ctx.identifierOrFunctionIdentifier() != null
        && ctx.identifierOrFunctionIdentifier().identifier() != null) {
      String identifier = parseString(ctx.identifierOrFunctionIdentifier().identifier().getText());

      if (shouldResolve(identifier)) {
        resolve(identifier, getCurrentLibraryContext());
      }
    }
  }

  @Override
  public void enterQualifiedIdentifierExpression(QualifiedIdentifierExpressionContext ctx) {
    String identifier = parseString(ctx.referentialIdentifier().getText());
    String qualifier = "";

    if (shouldResolve(identifier)) {
      // a qualified identifier can take on the form (qualifier '.')* identifier. If there is only
      // one qualifier,
      // then that could be a library. Resolve the qualifier to check if it's a library.
      if (CollectionUtils.isNotEmpty(ctx.qualifierExpression())
          && ctx.qualifierExpression().get(0) != null) {
        qualifier = parseString(ctx.qualifierExpression().get(0).getText());
        if (shouldResolve(qualifier)) {
          resolve(qualifier, getCurrentLibraryContext());
        }
      }
      resolve(identifier, getCurrentLibraryContext());
    }
  }

  @Override
  public void enterReferentialIdentifier(ReferentialIdentifierContext ctx) {
    String identifier = parseString(ctx.getText());
    if (shouldResolve(identifier)) {
      resolve(identifier, getCurrentLibraryContext());
    }
  }

  @Override
  public void enterQualifiedIdentifier(@NotNull cqlParser.QualifiedIdentifierContext ctx) {
    String identifier = parseString(ctx.identifier().getText());
    String qualifier = "";

    if (shouldResolve(identifier)) {
      // a qualified identifier can take on the form (qualifier) '.')* identifier. If there is only
      // one qualifier,
      // then that could be a library. Resolve the qualifier to check if it's a library.
      if (ctx.qualifier(0) != null) {
        qualifier = parseString(ctx.qualifier(0).getText());
        if (shouldResolve(qualifier)) {
          resolve(qualifier, getCurrentLibraryContext());
        }
      }

      resolve(identifier, getCurrentLibraryContext());
    }
  }

  private boolean shouldResolve(String identifier) {
    // if the namespace contains the identifier, that means it's a local identifier and should not
    // be treated
    // as an identifier for an expression.

    // an identifier can also not be equal to patient.
    return !namespace.contains(identifier) && !identifier.equalsIgnoreCase("patient");
  }

  @Override
  public void enterFunction(@NotNull cqlParser.FunctionContext ctx) {
    String identifier = parseString(ctx.referentialIdentifier().getText());
    resolve(identifier, getCurrentLibraryContext());
    libraryAccessor = null;
  }

  @Override
  public void enterExpressionDefinition(@NotNull cqlParser.ExpressionDefinitionContext ctx) {
    String identifier = parseString(ctx.identifier().getText());
    this.currentContext = libraryIdentifier + identifier;
    String content =
        ctx.getStart()
            .getInputStream()
            .getText(new Interval(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
    definitionContents.add(
        DefinitionContent.builder().name(currentContext).content(content).build());
    graph.addNode(currentContext);
  }

  @Override
  public void enterFunctionDefinition(@NotNull cqlParser.FunctionDefinitionContext ctx) {
    String identifier = parseString(ctx.identifierOrFunctionIdentifier().getText());
    this.currentContext = libraryIdentifier + identifier;
    for (cqlParser.OperandDefinitionContext operand : ctx.operandDefinition()) {
      namespace.push(operand.referentialIdentifier().getText());
    }
    String content =
        ctx.getStart()
            .getInputStream()
            .getText(new Interval(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()));
    List<CQLFunctionArgument> functionArguments = getFunctionArguments(ctx);
    definitionContents.add(
        DefinitionContent.builder()
            .name(currentContext)
            .content(content)
            .functionArguments(functionArguments)
            .build());
    graph.addNode(currentContext);
  }

  @Override
  public void exitFunctionDefinition(@NotNull cqlParser.FunctionDefinitionContext ctx) {
    for (cqlParser.OperandDefinitionContext operand : ctx.operandDefinition()) {
      namespace.pop();
    }
  }

  static List<CQLFunctionArgument> getFunctionArguments(FunctionDefinitionContext ctx) {
    List<CQLFunctionArgument> functionArguments = new ArrayList<>();
    if (ctx.operandDefinition() != null) {
      for (OperandDefinitionContext operand : ctx.operandDefinition()) {
        String name = "";
        String type = "";
        if (operand.referentialIdentifier() != null) {
          name = getFullText(operand.referentialIdentifier());
        }

        if (operand.typeSpecifier() != null) {
          type = getFullText(operand.typeSpecifier());
        }

        CQLFunctionArgument functionArgument = new CQLFunctionArgument();
        functionArgument.setId(UUID.nameUUIDFromBytes(name.getBytes()).toString());
        functionArgument.setArgumentName(name);

        if (QDMUtil.getQDMContainer().getDatatypes().contains(CQLParserUtil.parseString(type))) {
          functionArgument.setArgumentType("QDM Datatype");
          functionArgument.setQdmDataType(CQLParserUtil.parseString(type));
        } else if (CQL_DATA_TYPES.contains(type)) {
          functionArgument.setArgumentType(type);
        } else {
          functionArgument.setArgumentType("Others");
          functionArgument.setOtherType(type);
        }
        functionArguments.add(functionArgument);
      }
    }
    return functionArguments;
  }

  private static String getFullText(ParserRuleContext context) {
    if (context.start == null
        || context.stop == null
        || context.start.getStartIndex() < 0
        || context.stop.getStopIndex() < 0) return context.getText();
    return context
        .start
        .getInputStream()
        .getText(Interval.of(context.start.getStartIndex(), context.stop.getStopIndex()));
  }

  @Override
  public void enterParameterDefinition(@NotNull cqlParser.ParameterDefinitionContext ctx) {
    String identifier = parseString(ctx.identifier().getText());
    this.currentContext = libraryIdentifier + identifier;
    graph.addNode(currentContext);
  }

  @Override
  public void enterRetrieve(@NotNull cqlParser.RetrieveContext ctx) {

    // we only care about entering a retrieve if it has a terminology
    if (ctx.terminology() == null || ctx.codePath() != null) {
      return;
    }

    // if the valueset is in the form alias.name, get the alias and resolve it so we can switch to
    // the other
    // libraries context
    String identifier = "";
    if (CollectionUtils.isNotEmpty(
            ctx.terminology().qualifiedIdentifierExpression().qualifierExpression())
        && ctx.terminology().qualifiedIdentifierExpression().qualifierExpression().get(0) != null) {
      resolve(
          ctx.terminology().qualifiedIdentifierExpression().qualifierExpression().get(0).getText(),
          getCurrentLibraryContext());
      identifier =
          parseString(
              ctx.terminology().qualifiedIdentifierExpression().referentialIdentifier().getText());
    } else {
      identifier = parseString(ctx.terminology().getText());
    }

    String formattedIdentifier = formatIdentifier(identifier);

    // we need to resolve based on the identifier since it will be looking in the proper library but
    // we need
    // to put the formatted identifier into the maps since this is the format MAT is looking for
    String dataType =
        parseString(ctx.namedTypeSpecifier().referentialOrTypeNameIdentifier().getText());
    Element element = resolve(identifier, getCurrentLibraryContext());
    if (element instanceof ValueSetDef) {
      Map<String, Set<String>> current = valueSetDataTypeMap.get(currentContext);
      if (current == null) {
        valueSetDataTypeMap.put(currentContext, new HashMap<>());
      }

      current = valueSetDataTypeMap.get(currentContext);
      Set<String> currentSet = current.get(formattedIdentifier);
      if (currentSet == null) {
        currentSet = new HashSet<>();
        current.put(formattedIdentifier, currentSet);
      }

      current.get(formattedIdentifier).add(dataType);
      valueSetOids.putIfAbsent(
          formattedIdentifier, ((ValueSetDef) element).getId().substring("urn:oid:".length()));

    } else if (element instanceof CodeDef codeDef) {
      Map<String, Set<String>> current = codeDataTypeMap.get(currentContext);
      if (current == null) {
        codeDataTypeMap.put(currentContext, new HashMap<>());
      }

      current = codeDataTypeMap.get(currentContext);
      Set<String> currentSet = current.get(formattedIdentifier);
      if (currentSet == null) {
        currentSet = new HashSet<>();
        current.put(formattedIdentifier, currentSet);
      }

      current.get(formattedIdentifier).add(dataType);
      drcs.putIfAbsent(
          formattedIdentifier,
          CQLCode.builder()
              .id(codeDef.getId())
              .codeName(formattedIdentifier)
              .codeSystemName(codeDef.getCodeSystem().getName())
              .build());
    }
  }

  private void pushAliasesOntoStackForQueries(cqlParser.QueryContext ctx) {
    for (AliasedQuerySourceContext source : ctx.sourceClause().aliasedQuerySource()) {
      namespace.push(source.alias().getText());
    }
  }

  private void popAliasesOffStackForQueries(cqlParser.QueryContext ctx) {
    for (AliasedQuerySourceContext source : ctx.sourceClause().aliasedQuerySource()) {
      namespace.pop();
    }
  }

  @Override
  public void enterWhereClause(WhereClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      pushAliasesOntoStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void exitWhereClause(WhereClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      popAliasesOffStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void enterWithClause(WithClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      pushAliasesOntoStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }

    namespace.push(ctx.aliasedQuerySource().alias().getText());
  }

  @Override
  public void exitWithClause(WithClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      popAliasesOffStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }

    namespace.pop();
  }

  @Override
  public void enterWithoutClause(WithoutClauseContext ctx) {
    namespace.push(ctx.aliasedQuerySource().alias().getText());
  }

  @Override
  public void exitWithoutClause(WithoutClauseContext ctx) {
    namespace.pop();
  }

  @Override
  public void enterLetClause(LetClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      pushAliasesOntoStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void exitLetClause(LetClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      popAliasesOffStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void enterReturnClause(ReturnClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      pushAliasesOntoStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void exitReturnClause(ReturnClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      popAliasesOffStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void enterSortClause(SortClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      pushAliasesOntoStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  @Override
  public void exitSortClause(SortClauseContext ctx) {
    if (ctx.parent instanceof cqlParser.QueryContext) {
      popAliasesOffStackForQueries(((cqlParser.QueryContext) ctx.parent));
    }
  }

  public String parseString(String s) {
    return s.replace("\"", "");
  }

  /**
   * Formats the identifier based on where it is relative to the parent library
   *
   * @param identifier the identifier to format
   * @return
   */
  private String formatIdentifier(String identifier) {
    String formattedIdentifier = "";

    // if we are looking at an expression from some child library (aka an expression alias.name)
    // build the
    // formatted expression based on the library it came from.
    if (libraryAccessor != null) {
      String path = libraryAccessor.getPath() + "-" + libraryAccessor.getVersion();
      String alias = libraryAccessor.getLocalIdentifier();
      formattedIdentifier = path + "|" + alias + "|" + identifier;
    } else {
      // if the expression is in a child library or grandchild library (relative to the parent) then
      // format and
      // then format that identifier with the details of the current library
      if (this.libraryIdentifier != null) {
        formattedIdentifier = libraryIdentifier + identifier;
      } else {
        // if the expression is in the parent library, then make the formatted identifier the
        // current identifier.
        formattedIdentifier = identifier;
      }
    }

    return formattedIdentifier;
  }

  private Element resolve(String identifier, CompiledLibrary library) {
    Element element = library.resolve(identifier);
    String formattedIdentifier = formatIdentifier(identifier);
    libraryAccessor =
        null; // we've done all we need to do with the accessor, so set it equal to null so it can
    // be
    // updated again if need be.
    if (element instanceof IncludeDef def) {
      graph.addEdge(
          currentContext, def.getPath() + "-" + def.getVersion() + "|" + def.getLocalIdentifier());
      libraryAccessor = def;
      try {
        parseChildLibraries(def);
        libraries.add(
            CQLIncludeLibrary.builder()
                .cqlLibraryName(def.getPath())
                .aliasName(def.getLocalIdentifier())
                .version(def.getVersion())
                // TODO: should be taken from librarySetId
                .id(def.getTrackerId().toString())
                .setId(def.getTrackerId().toString())
                .build());
      } catch (IOException e) {
        e.printStackTrace();
      }
    } else if (element instanceof CodeDef codeDef) {
      codes.add(formattedIdentifier);
      CQLCodeSystem cqlCodeSystem = codeSystemMap.get(codeDef.getCodeSystem().getName());
      CQLCode declaredCode =
          CQLCode.builder()
              .id(codeDef.getId())
              .codeName(codeDef.getDisplay())
              .codeSystemName(codeDef.getCodeSystem().getName())
              .codeSystemOID(cqlCodeSystem == null ? null : cqlCodeSystem.getOID())
              .codeIdentifier(formattedIdentifier)
              .build();
      declaredCodes.add(declaredCode);
      graph.addEdge(currentContext, formattedIdentifier);
    } else if (element instanceof CodeSystemDef) {
      codesystems.add(identifier);
      graph.addEdge(currentContext, formattedIdentifier);
    } else if (element instanceof ValueSetDef vsDef) {
      valuesets.add(formattedIdentifier);
      graph.addEdge(currentContext, formattedIdentifier);

      CQLValueSet declaredValueSet =
          CQLValueSet.builder()
              .oid(vsDef.getId().replace("urn:oid:", ""))
              .name(vsDef.getName())
              .version(vsDef.getVersion())
              .identifier(formattedIdentifier)
              .build();
      cqlValuesets.add(declaredValueSet);

    } else if (element instanceof ParameterDef parameterDef) {
      CQLParameter parameter =
          CQLParameter.builder()
              .parameterName(formattedIdentifier)
              .parameterLogic(parameterDef.getResultType().toString())
              .build();
      parameters.add(parameter);
      graph.addEdge(currentContext, formattedIdentifier);
    } else if (element instanceof ExpressionDef) {
      definitions.add(formattedIdentifier);
      graph.addEdge(currentContext, formattedIdentifier);
    } else {
      final String finalFormattedIdentifier = formattedIdentifier;
      library
          .getLibrary()
          .getStatements()
          .getDef()
          .forEach(
              (def) -> {
                if (def.getName().equals(identifier)) {
                  functions.add(finalFormattedIdentifier);
                  graph.addEdge(currentContext, finalFormattedIdentifier);
                }
              });
    }

    return element;
  }

  private void parseChildLibraries(IncludeDef def) throws IOException {
    String childCQLString = this.childrenLibraries.get(def.getPath() + "-" + def.getVersion());

    InputStream stream = new ByteArrayInputStream(childCQLString.getBytes(StandardCharsets.UTF_8));
    cqlLexer lexer = new cqlLexer(CharStreams.fromStream(stream));
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    cqlParser parser = new cqlParser(tokens);

    //        CompiledLibrary childLibrary = this.translatedLibraryMap.get(def.getPath() + "-" +
    // def.getVersion());
    CompiledLibrary childLibrary = this.translatedLibraryMap.get(def.getPath());
    Cql2ElmListener listener =
        new Cql2ElmListener(
            def.getPath() + "-" + def.getVersion() + "|" + def.getLocalIdentifier() + "|",
            graph,
            childLibrary,
            translatedLibraryMap,
            childrenLibraries);
    ParseTree tree = parser.library();

    TranslationResource translationResource = TranslationResource.getInstance(true);

    // Add CqlCompilerOptions from LibraryManager to prevent NPE while walking through CQL
    //  The next two lines were added as a fix for the NPE while using Translator version 3.2.0..
    // but after upgrading they have been deprecated
    //    LibraryBuilder libraryBuilder = new
    // LibraryBuilder(translationResource.getLibraryManager());
    //    libraryBuilder.setCompilerOptions(
    //        translationResource.getLibraryManager().getCqlCompilerOptions());

    CqlPreprocessorElmCommonVisitor preprocessor =
        new CqlPreprocessorElmCommonVisitor(
            new LibraryBuilder(translationResource.getLibraryManager(), new IdObjectFactory()),
            tokens);

    // qlPreprocessorVisitor preprocessor = new CqlPreprocessorVisitor(libraryBuilder, tokens);
    preprocessor.visit(tree);
    ParseTreeWalker walker = new ParseTreeWalker();
    walker.walk(listener, tree);

    libraries.addAll(listener.getLibraries());
    valuesets.addAll(listener.getValuesets());
    cqlValuesets.addAll(listener.getCqlValuesets());
    codes.addAll(listener.getCodes());
    declaredCodes.addAll(listener.getDeclaredCodes());
    codesystems.addAll(listener.getCodesystems());
    parameters.addAll(listener.getParameters());
    definitions.addAll(listener.getDefinitions());
    functions.addAll(listener.getFunctions());
    valueSetDataTypeMap.putAll(listener.getValueSetDataTypeMap());
    codeDataTypeMap.putAll(listener.getCodeDataTypeMap());
    valueSetOids.putAll(listener.getValueSetOids());
    drcs.putAll(listener.getDrcs());
    definitionContents.addAll(listener.getDefinitionContents());
  }
}
