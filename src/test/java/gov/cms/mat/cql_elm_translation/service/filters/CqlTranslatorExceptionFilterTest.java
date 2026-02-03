// package gov.cms.mat.cql_elm_translation.service.filters;
//
// import static org.junit.jupiter.api.Assertions.assertEquals;
// import static org.junit.jupiter.api.Assertions.assertTrue;
// import java.io.File;
// import java.io.IOException;
// import java.io.UncheckedIOException;
// import java.nio.file.Files;
// import java.util.ArrayList;
// import java.util.List;
// import java.util.Objects;
//
// import org.cqframework.cql.cql2elm.CqlCompilerException;
// import org.cqframework.cql.cql2elm.CqlCompilerException.ErrorSeverity;
// import org.cqframework.cql.cql2elm.CqlSemanticException;
// import org.cqframework.cql.cql2elm.tracking.TrackBack;
// import org.hl7.elm.r1.VersionedIdentifier;
// import org.junit.jupiter.api.BeforeEach;
// import org.junit.jupiter.api.Test;
// import org.springframework.boot.test.context.SpringBootTest;
//
// @SpringBootTest
// public class CqlTranslatorExceptionFilterTest {
//
//  private String cqlData;
//  private final List<CqlCompilerException> cqlTranslatorExceptions = new ArrayList<>();
//
//  @BeforeEach
//  void setUp() {
//    File cqlFile =
//        new
// File(Objects.requireNonNull(this.getClass().getResource("/syntaxError.cql")).getFile());
//    try {
//      cqlData = new String(Files.readAllBytes(cqlFile.toPath()));
//    } catch (IOException e) {
//      throw new UncheckedIOException(e);
//    }
//
//    VersionedIdentifier versionedIdentifier = new VersionedIdentifier();
//    versionedIdentifier.setVersion("0.0.000");
//    versionedIdentifier.setId("Library767878");
//
//    cqlTranslatorExceptions.add(
//        new CqlSemanticException(
//            "Member relevantDatetime not found for type null.",
//            ErrorSeverity.Error,
//            new TrackBack(versionedIdentifier, 2, 2, 2, 12)));
//    cqlTranslatorExceptions.add(
//        new CqlSemanticException(
//            "just warning",
//            ErrorSeverity.Warning,
//            new TrackBack(versionedIdentifier, 3, 3, 3, 13)));
//    cqlTranslatorExceptions.add(
//        new CqlCompilerException(
//            "org.cqframework.cql.cql2elm.CqlSyntaxException: extraneous input ')' expecting
// {<EOF>, 'using', 'include', 'public', 'private', 'parameter', 'codesystem', 'valueset', 'code',
// 'concept', 'define', 'context'}",
//            new TrackBack(versionedIdentifier, 4, 4, 4, 14)));
//    cqlTranslatorExceptions.add(
//        new CqlCompilerException(
//            "no viable alternative at input 'define xyz'",
//            ErrorSeverity.Error,
//            new TrackBack(versionedIdentifier, 5, 5, 5, 15)));
//    cqlTranslatorExceptions.add(
//        new CqlCompilerException(
//            "mismatched input 'display' expecting 'from'",
//            ErrorSeverity.Error,
//            new TrackBack(versionedIdentifier, 6, 6, 6, 16)));
//
//    VersionedIdentifier includedLibraryIdentifier = new VersionedIdentifier();
//    includedLibraryIdentifier.setVersion("7.0.000");
//    includedLibraryIdentifier.setId("IncludedLibrary258");
//    cqlTranslatorExceptions.add(
//        new CqlCompilerException(
//            "This is an External Error",
//            ErrorSeverity.Error,
//            new TrackBack(includedLibraryIdentifier, 7, 7, 7, 17)));
//  }
//
//  @Test
//  public void testFilterWithNoExceptions() {
//    CqlTranslatorExceptionFilter filter =
//        new CqlTranslatorExceptionFilter(cqlData, ErrorSeverity.Info, new ArrayList<>());
//    filter.generateCqlExceptions();
//    assertTrue(filter.getErrorExceptions().isEmpty());
//    assertTrue(filter.getExternalErrors().isEmpty());
//  }
//
//  @Test
//  public void testFilterOutWarnings() {
//    CqlTranslatorExceptionFilter filter =
//        new CqlTranslatorExceptionFilter(cqlData, ErrorSeverity.Error, cqlTranslatorExceptions);
//    filter.generateCqlExceptions();
//    assertEquals(1, filter.getErrorExceptions().size());
//    assertEquals(1, filter.getExternalErrors().size());
//  }
//
//  @Test
//  public void testFilterSyntaxAndSpecificMessages() {
//    CqlTranslatorExceptionFilter filter =
//        new CqlTranslatorExceptionFilter(cqlData, ErrorSeverity.Info, cqlTranslatorExceptions);
//    filter.generateCqlExceptions();
//    assertEquals(2, filter.getErrorExceptions().size());
//    assertEquals(1, filter.getExternalErrors().size());
//    assertTrue(
//        filter.getErrorExceptions().stream()
//            .noneMatch(
//                ex ->
//                    ex.getMessage().contains("no viable alternative at input 'define")
//                        || ex.getMessage().contains("mismatched input 'display' expecting 'from'")
//                        || ex.toString()
//                            .contains("org.cqframework.cql.cql2elm.CqlSyntaxException:")));
//  }
// }
