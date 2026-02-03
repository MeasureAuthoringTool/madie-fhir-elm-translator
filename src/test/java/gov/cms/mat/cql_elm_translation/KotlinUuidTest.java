package gov.cms.mat.cql_elm_translation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Test to verify that Kotlin UUID experimental API is accessible */
public class KotlinUuidTest {

  @Test
  public void testKotlinUuidAccessible() {
    try {
      // Try to load the Kotlin UUID class
      Class<?> uuidClass = Class.forName("kotlin.uuid.Uuid");
      assertNotNull(uuidClass, "Kotlin UUID class should be accessible");
    } catch (ClassNotFoundException e) {
      fail("Kotlin UUID class not found: " + e.getMessage());
    }
  }
}
