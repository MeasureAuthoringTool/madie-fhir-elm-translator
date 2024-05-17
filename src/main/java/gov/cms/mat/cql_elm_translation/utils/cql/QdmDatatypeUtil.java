package gov.cms.mat.cql_elm_translation.utils.cql;

import gov.cms.mat.cql_elm_translation.data.DataElementDescriptor;

import java.util.Map;

public class QdmDatatypeUtil {
  private static final Map<String, DataElementDescriptor> negationRationale;

  static {
    negationRationale =
        Map.ofEntries(
            Map.entry(
                "Assessment, Not Ordered",
                new DataElementDescriptor("AssessmentOrder", "Assessment, Order")),
            Map.entry(
                "Assessment, Not Performed",
                new DataElementDescriptor("AssessmentPerformed", "Assessment, Performed")),
            Map.entry(
                "Assessment, Not Recommended",
                new DataElementDescriptor("AssessmentRecommended", "Assessment, Recommended")),
            Map.entry(
                "Communication, Not Performed",
                new DataElementDescriptor("CommunicationPerformed", "Communication, Performed")),
            Map.entry(
                "Device, Not Ordered", new DataElementDescriptor("DeviceOrder", "Device, Order")),
            Map.entry(
                "Device, Not Recommended",
                new DataElementDescriptor("DeviceRecommended", "Device, Recommended")),
            Map.entry(
                "Diagnostic Study, Not Ordered",
                new DataElementDescriptor("DiagnosticStudyOrder", "Diagnostic Study, Order")),
            Map.entry(
                "Diagnostic Study, Not Performed",
                new DataElementDescriptor(
                    "DiagnosticStudyPerformed", "Diagnostic Study, Performed")),
            Map.entry(
                "Diagnostic Study, Not Recommended",
                new DataElementDescriptor(
                    "DiagnosticStudyRecommended", "Diagnostic Study, Recommended")),
            Map.entry(
                "Encounter, Not Ordered",
                new DataElementDescriptor("EncounterOrder", "Encounter, Order")),
            Map.entry(
                "Encounter, Not Recommended",
                new DataElementDescriptor("EncounterRecommended", "Encounter Recommended")),
            Map.entry(
                "Intervention, Not Ordered",
                new DataElementDescriptor("InterventionOrder", "Intervention, Order")),
            Map.entry(
                "Intervention, Not Performed",
                new DataElementDescriptor("InterventionPerformed", "Intervention, Performed")),
            Map.entry(
                "Intervention, Not Recommended",
                new DataElementDescriptor("InterventionRecommended", "Intervention, Recommended")),
            Map.entry(
                "Immunization, Not Ordered",
                new DataElementDescriptor("ImmunizationOrder", "Immunization, Order")),
            Map.entry(
                "Immunization, Not Administered",
                new DataElementDescriptor(
                    "ImmunizationAdministered", "Immunization, Administered")),
            Map.entry(
                "Laboratory Test, Not Ordered",
                new DataElementDescriptor("LaboratoryTestOrder", "Laboratory Test, Order")),
            Map.entry(
                "Laboratory Test, Not Performed",
                new DataElementDescriptor("LaboratoryTestPerformed", "Laboratory Test, Performed")),
            Map.entry(
                "Laboratory Test, Not Recommended",
                new DataElementDescriptor(
                    "LaboratoryTestRecommended", "Laboratory Test, Recommended")),
            Map.entry(
                "Medication, Not Administered",
                new DataElementDescriptor("MedicationAdministered", "Medication, Administered")),
            Map.entry(
                "Medication, Not Discharged",
                new DataElementDescriptor("MedicationDischarge", "Medication, Discharge")),
            Map.entry(
                "Medication, Not Dispensed",
                new DataElementDescriptor("MedicationDispensed", "Medication, Dispensed")),
            Map.entry(
                "Medication, Not Ordered",
                new DataElementDescriptor("MedicationOrder", "Medication, Order")),
            Map.entry(
                "Physical Exam, Not Ordered",
                new DataElementDescriptor("PhysicalExamOrder", "Physical Exam, Order")),
            Map.entry(
                "Physical Exam, Not Performed",
                new DataElementDescriptor("PhysicalExamPerformed", "Physical Exam, Performed")),
            Map.entry(
                "Physical Exam, Not Recommended",
                new DataElementDescriptor("PhysicalExamRecommended", "Physical Exam, Recommended")),
            Map.entry(
                "Procedure, Not Ordered",
                new DataElementDescriptor("ProcedureOrder", "Procedure, Order")),
            Map.entry(
                "Procedure, Not Performed",
                new DataElementDescriptor("ProcedurePerformed", "Procedure, Performed")),
            Map.entry(
                "Procedure, Not Recommended",
                new DataElementDescriptor("ProcedureRecommended", "Procedure, Recommended")),
            Map.entry(
                "Substance, Not Administered",
                new DataElementDescriptor("SubstanceAdministered", "Substance, Administered")),
            Map.entry(
                "Substance, Not Ordered",
                new DataElementDescriptor("SubstanceOrder", "Substance, Order")),
            Map.entry(
                "Substance, Not Recommended",
                new DataElementDescriptor("SubstanceRecommended", "Substance, Recommended")));
  }

  public static boolean isValidNegation(final String negation) {
    return negationRationale.containsKey(negation);
  }

  public static DataElementDescriptor getDescriptorForNegation(final String negation) {
    return negationRationale.get(negation);
  }
}
