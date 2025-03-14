package gov.cms.mat.cql_elm_translation.controllers;

import gov.cms.madie.cql_elm_translator.dto.CqlBuilderLookup;
import gov.cms.mat.cql_elm_translation.service.CqlConversionService;
import gov.cms.mat.cql_elm_translation.service.CqlParsingService;
import gov.cms.mat.cql_elm_translation.service.DataCriteriaService;
import org.apache.http.HttpStatus;
import org.cqframework.cql.cql2elm.CqlCompilerException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Set;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

@WebMvcTest({CqlToolsController.class})
public class CqlToolsControllerMvcTest {
  private static final String TEST_USER_ID = "john_doe";
  @MockBean private DataCriteriaService dataCriteriaService;
  @MockBean private CqlConversionService cqlConversionService;
  @MockBean private CqlParsingService cqlParsingService;

  @Autowired private MockMvc mockMvc;

  @Test
  void testGetCqlBuilderLookups() throws Exception {
    var p = CqlBuilderLookup.Lookup.builder().name("Parameter").logic("abc").build();
    var d = CqlBuilderLookup.Lookup.builder().name("Definition").logic("abcd").build();
    var f = CqlBuilderLookup.Lookup.builder().name("Function").logic("abcdef").build();
    when(cqlParsingService.getCqlBuilderLookups(
            anyString(), anyString(), any(CqlCompilerException.ErrorSeverity.class)))
        .thenReturn(
            CqlBuilderLookup.builder()
                .parameters(Set.of(p))
                .definitions(Set.of(d))
                .functions(Set.of(f))
                .build());

    var results =
        mockMvc
            .perform(
                MockMvcRequestBuilders.put("/cql-builder-lookups")
                    .with(user(TEST_USER_ID))
                    .with(csrf())
                    .header(HttpHeaders.AUTHORIZATION, TEST_USER_ID)
                    .content("test cql")
                    .contentType(MediaType.TEXT_PLAIN_VALUE))
            .andReturn();
    assertThat(results.getResponse().getStatus(), is(equalTo(HttpStatus.SC_OK)));
    String response = results.getResponse().getContentAsString();
    assertThat(response, containsString(p.getName()));
    assertThat(response, containsString(d.getName()));
    assertThat(response, containsString(f.getName()));
  }
}
