package gov.cms.mat.cql_elm_translation.controllers;

import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
public class TranslatorVersionControllerTest {

  @Mock Package translatorPackage;
  @Spy private TranslatorVersionController translatorVersionController;

  @Test
  public void testGetTranslatorVersionIsDraft() {
    // Given
    when(translatorVersionController.getTranslatorPackage()).thenReturn(translatorPackage);
    when(translatorPackage.getImplementationVersion()).thenReturn("1.2.3");

    // When
    ResponseEntity<String> results = translatorVersionController.getTranslatorVersion(true);

    // Then
    assertThat(results.getStatusCode(), is(equalTo(HttpStatus.OK)));
    assertThat(results.getBody(), is(equalTo("1.2.3")));
  }

  @Test
  public void testGetTranslatorVersionWithVersionLookupFailure() {
    // Given
    when(translatorVersionController.getTranslatorPackage()).thenReturn(null);

    // When
    ResponseEntity<String> results = translatorVersionController.getTranslatorVersion(true);

    // Then
    assertThat(results.getStatusCode(), is(equalTo(HttpStatus.FAILED_DEPENDENCY)));
  }

  @Test
  public void testGetTranslatorVersionForNullImplementationVersion() {
    // Given
    when(translatorVersionController.getTranslatorPackage()).thenReturn(translatorPackage);
    when(translatorPackage.getImplementationVersion()).thenReturn(null);

    // When
    ResponseEntity<String> results = translatorVersionController.getTranslatorVersion(true);

    // Then
    assertThat(results.getStatusCode(), is(equalTo(HttpStatus.FAILED_DEPENDENCY)));
  }

  @Test
  public void testGetTranslatorVersionForBlankImplementationVersion() {
    // Given
    when(translatorVersionController.getTranslatorPackage()).thenReturn(translatorPackage);
    when(translatorPackage.getImplementationVersion()).thenReturn("");

    // When
    ResponseEntity<String> output = translatorVersionController.getTranslatorVersion(true);

    // Then
    assertThat(output.getStatusCode(), is(equalTo(HttpStatus.FAILED_DEPENDENCY)));
  }

  @Test
  public void testGetTranslatorVersionForDraftFalseThrowsException() {
    // When
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> translatorVersionController.getTranslatorVersion(false));

    // Then
    assertThat(exception.getStatusCode(), is(equalTo(HttpStatus.BAD_REQUEST)));
    assertThat(exception.getReason(), is(equalTo("Non-draft version is no longer supported.")));
  }

  @Test
  public void testGetTranslatorPackageReturnsNonNull() {
    // When
    Package output = translatorVersionController.getTranslatorPackage();

    // Then
    assertThat(output, is(notNullValue()));
  }
}
