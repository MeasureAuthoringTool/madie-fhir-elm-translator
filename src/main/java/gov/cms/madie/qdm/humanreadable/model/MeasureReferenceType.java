package gov.cms.madie.qdm.humanreadable.model;

public enum MeasureReferenceType {
  CITATION("Citation"),
  DOCUMENTATION("Documentation"),
  JUSTIFICATION("Justification"),
  UNKNOWN("Unknown");
  private final String displayName;

  MeasureReferenceType(String displayName) {
    this.displayName = displayName;
  }

  public String getDisplayName() {
    return displayName;
  }
}
