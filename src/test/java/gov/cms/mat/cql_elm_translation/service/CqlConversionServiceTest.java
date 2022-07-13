package gov.cms.mat.cql_elm_translation.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import gov.cms.mat.cql.dto.CqlConversionPayload;
import gov.cms.mat.cql_elm_translation.data.RequestData;

@SpringBootTest
class CqlConversionServiceTest {

  @Autowired private CqlConversionService service;

  @Mock RequestData requestData;

  @Test
  void testProcessCqlDataWithErrors() {
    String cqlData = StringUtils.EMPTY;
    File inputXmlFile = new File(this.getClass().getResource("/fhir.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputXmlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    when(requestData.getCqlData()).thenReturn(cqlData);

    when(requestData.getCqlDataInputStream())
        .thenReturn(new ByteArrayInputStream(cqlData.getBytes()));
    MultivaluedMap<String, String> map = new MultivaluedHashMap<String, String>();
    List<String> trueList = new ArrayList<String>(Arrays.asList("true"));
    map.put("disable-method-invocation", trueList);
    map.put("validate-units", trueList);

    when(requestData.createMap()).thenReturn(map);
    CqlConversionPayload payload = service.processCqlDataWithErrors(requestData);
    assertNotNull(payload);
  }
  
  @Test
  void testProcessCqlDataWithErrors_NonSupportedModel() {
    String cqlData = StringUtils.EMPTY;
    File inputXmlFile = new File(this.getClass().getResource("/non_supported_model.cql").getFile());

    try {
      cqlData = new String(Files.readAllBytes(inputXmlFile.toPath()));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    when(requestData.getCqlData()).thenReturn(cqlData);

    when(requestData.getCqlDataInputStream())
        .thenReturn(new ByteArrayInputStream(cqlData.getBytes()));
    MultivaluedMap<String, String> map = new MultivaluedHashMap<String, String>();
    List<String> trueList = new ArrayList<String>(Arrays.asList("true"));
    map.put("disable-method-invocation", trueList);
    map.put("validate-units", trueList);

    when(requestData.createMap()).thenReturn(map);
    CqlConversionPayload payload = service.processCqlDataWithErrors(requestData);
    assertNotNull(payload);
  }
}
