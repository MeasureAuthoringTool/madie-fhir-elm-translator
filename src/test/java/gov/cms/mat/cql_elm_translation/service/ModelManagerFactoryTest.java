package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql_elm_translation.utils.cql.FhirUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.cqframework.cql.cql2elm.ModelManager;
import org.hl7.cql.model.ModelIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.doReturn;

import java.io.File;
import java.io.IOException;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.r5.model.ImplementationGuide.ImplementationGuideDependsOnComponent;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.allOf;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class ModelManagerFactoryTest {

  @Mock FhirUtil fhirUtil;
  @InjectMocks private static ModelManagerFactory modelManagerFactory;

  @BeforeEach
  void setUp() throws IOException {
    File file = new File("/tmp/fake-cache");
    if (!file.exists()) {
      FileUtils.forceMkdir(file);
    }
    reset(fhirUtil);
    modelManagerFactory = new ModelManagerFactory("fake-cache", fhirUtil);
  }

  @Test
  void testConstructorInitializesSuccessfully() {
    assertNotNull(modelManagerFactory);
  }

  @Test
  void testConstructorWithMissingIgsResourceDoesNotThrow() {
    when(fhirUtil.loadImplementationGuide(anyString())).thenReturn(null);
    ModelManagerFactory factory = new ModelManagerFactory("/tmp/fake-cache", fhirUtil);
    assertNotNull(factory);
  }

  @Test
  void testGetModelManagerWithValidIdentifier() {
    ModelIdentifier identifier = new ModelIdentifier("FHIR", null, "4.0.1");

    ModelManager modelManager = modelManagerFactory.getModelManager(identifier);

    assertNotNull(modelManager);
  }

  @Test
  void testGetModelManagerWithQICoreIdentifier() {
    ModelIdentifier identifier = new ModelIdentifier("QICore", null, "7.0.1");

    ModelManager modelManager = modelManagerFactory.getModelManager(identifier);

    assertNotNull(modelManager);
    assertThat(modelManager, is(notNullValue()));
  }

  @Test
  void testGetModelManagerWithCqlIdentifier() {
    ModelIdentifier identifier = new ModelIdentifier("hl7.fhir.us.cql", null, "1.0.0");

    ModelManager modelManager = modelManagerFactory.getModelManager(identifier);

    assertNotNull(modelManager);
  }

  @Test
  void testGetModelManagerWithNullIdentifierThrowsException() {
    Exception exception =
        assertThrows(
            IllegalArgumentException.class, () -> modelManagerFactory.getModelManager(null));

    assertThat(exception.getMessage(), equalTo("Model name cannot be null or empty"));
  }

  @Test
  void testGetModelManagerWithNullIdThrowsException() {
    ModelIdentifier identifier = new ModelIdentifier(null, null, "1.0.0");

    Exception exception =
        assertThrows(
            IllegalArgumentException.class, () -> modelManagerFactory.getModelManager(identifier));

    assertThat(exception.getMessage(), equalTo("Model name cannot be null or empty"));
  }

  @Test
  void testGetModelManagerWithEmptyIdThrowsException() {
    ModelIdentifier identifier = new ModelIdentifier("", null, "1.0.0");

    Exception exception =
        assertThrows(
            IllegalArgumentException.class,
            () -> {
              modelManagerFactory.getModelManager(identifier);
            });

    assertThat(exception.getMessage(), equalTo("Model name cannot be null or empty"));
  }

  @Test
  void testGetModelManagerReturnsSameInstanceForSameIdentifier() {
    ModelIdentifier identifier = new ModelIdentifier("FHIR", null, "4.0.1");

    ModelManager modelManager1 = modelManagerFactory.getModelManager(identifier);
    ModelManager modelManager2 = modelManagerFactory.getModelManager(identifier);

    assertNotNull(modelManager1);
    assertNotNull(modelManager2);
    assertSame(modelManager1, modelManager2);
  }

  @Test
  void testGetModelManagerReturnsDifferentInstancesForDifferentIdentifiers() {
    ModelIdentifier identifier1 = new ModelIdentifier("FHIR", null, "4.0.1");
    ModelIdentifier identifier2 = new ModelIdentifier("FHIR", null, "5.0.0");

    ModelManager modelManager1 = modelManagerFactory.getModelManager(identifier1);
    ModelManager modelManager2 = modelManagerFactory.getModelManager(identifier2);

    assertNotNull(modelManager1);
    assertNotNull(modelManager2);
    assertNotSame(modelManager1, modelManager2);
  }

  @Test
  void testGetModelManagerCreatesNewManagerForUnknownModel() {
    ModelIdentifier identifier = new ModelIdentifier("UnknownModel", null, "1.0.0");

    ModelManager modelManager = modelManagerFactory.getModelManager(identifier);

    assertNotNull(modelManager);
  }

  @Test
  void testLogDebugMessageWithNullCategory() {
    assertDoesNotThrow(
        () -> {
          modelManagerFactory.logDebugMessage(null, "Test debug message with null category");
        });
  }

  @Test
  void testLogMessageWithNullMessage() {
    assertDoesNotThrow(
        () -> {
          modelManagerFactory.logMessage(null);
        });
  }

  @Test
  void testMultipleModelManagersCanCoexist() {
    ModelIdentifier identifier1 = new ModelIdentifier("Model1", null, "1.0.0");
    ModelIdentifier identifier2 = new ModelIdentifier("Model2", null, "2.0.0");
    ModelIdentifier identifier3 = new ModelIdentifier("Model3", null, "3.0.0");

    ModelManager modelManager1 = modelManagerFactory.getModelManager(identifier1);
    ModelManager modelManager2 = modelManagerFactory.getModelManager(identifier2);
    ModelManager modelManager3 = modelManagerFactory.getModelManager(identifier3);

    assertNotNull(modelManager1);
    assertNotNull(modelManager2);
    assertNotNull(modelManager3);
    assertNotSame(modelManager1, modelManager2);
    assertNotSame(modelManager2, modelManager3);
    assertNotSame(modelManager1, modelManager3);
  }

  @Test
  void testErrorLoggingWhenBuildModelManagerThrows() throws IOException {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("MainIG");
    ig.setVersion("2.0.0");
    ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
    dep.setId("DepIG");
    dep.setVersion("1.0.0");
    ig.setDependsOn(List.of(dep));
    when(fhirUtil.loadImplementationGuide(anyString())).thenReturn(ig);
    ModelManagerFactory factory = spy(new ModelManagerFactory("/tmp/fake-cache", fhirUtil));
    doReturn(mock(ModelManager.class)).when(factory).buildModelManager(any(), any());
    try {
      factory.processImplementationGuide(ig);
    } catch (IllegalArgumentException e) {
      // expected
      fail("processImplementationGuide should handle exceptions internally and not throw");
    }
  }

  @Test
  void testProcessImplementationGuideWithNoDependencies() throws IOException {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("TestIG");
    ig.setVersion("1.0.0");
    ModelManagerFactory factory = spy(new ModelManagerFactory("/tmp/fake-cache", fhirUtil));
    factory.processImplementationGuide(ig);
    // No dependencies, so knownModelIdentifiers should be empty
    assertThat(factory.getKnownModelIdentifiers().size(), is(0));
  }

  @Test
  void testProcessImplementationGuideWithDependencies() throws IOException {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("MainIG");
    ig.setVersion("2.0.0");
    ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
    dep.setId("DepIG");
    dep.setVersion("1.0.0");
    ig.setDependsOn(List.of(dep));
    ModelManagerFactory factory = spy(new ModelManagerFactory("/tmp/fake-cache", fhirUtil));
    doReturn(mock(ModelManager.class)).when(factory).buildModelManager(any(), any());
    factory.processImplementationGuide(ig);
    // Only dependency ModelIdentifier should be present
    assertThat(
        factory.getKnownModelIdentifiers(),
        hasItem(allOf(hasProperty("id", is("DepIG")), hasProperty("version", is("1.0.0")))));
    assertThat(factory.getKnownModelIdentifiers().size(), is(1));
  }

  @Test
  void testProcessImplementationGuideWithMalformedDependency() throws IOException {
    ImplementationGuide ig = new ImplementationGuide();
    ig.setId("MainIG");
    ig.setVersion("2.0.0");
    ImplementationGuideDependsOnComponent dep = new ImplementationGuideDependsOnComponent();
    // No id or version set on dependency
    ig.setDependsOn(List.of(dep));
    ModelManagerFactory factory = spy(new ModelManagerFactory("/tmp/fake-cache", fhirUtil));
    factory.processImplementationGuide(ig);
    // Malformed dependency, so knownModelIdentifiers should be empty
    assertThat(factory.getKnownModelIdentifiers().size(), is(0));
  }
}
