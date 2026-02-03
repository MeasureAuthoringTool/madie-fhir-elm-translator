package gov.cms.mat.cql_elm_translation.exceptions;

import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;

public class MissingLibraryCqlCompilerException extends CqlCompilerException {
  private static final String MESSAGE =
      "%s is required as an included library for QI-Core. "
          + "Please add the appropriate version of %s to your CQL.";

  public MissingLibraryCqlCompilerException(
      final String library, VersionedIdentifier identifier, int lineNumber) {
    super(
        String.format(MESSAGE, library, library),
        new TrackBack(identifier, lineNumber, 0, lineNumber, 0),
        CqlCompilerException.ErrorSeverity.Error,
        null);
  }
}
