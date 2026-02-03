package gov.cms.mat.cql_elm_translation.exceptions;

import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.cqframework.cql.cql2elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;

public class MissingContextException extends CqlCompilerException {
  private static final long serialVersionUID = -8499197087358679644L;
  private static final String MESSAGE = "Measure CQL must contain a Context.";

  public MissingContextException(VersionedIdentifier identifier, int lineNumber) {
    super(
        MESSAGE,
        new TrackBack(identifier, lineNumber, 0, lineNumber, 0),
        CqlCompilerException.ErrorSeverity.Error,
        null);
  }
}
