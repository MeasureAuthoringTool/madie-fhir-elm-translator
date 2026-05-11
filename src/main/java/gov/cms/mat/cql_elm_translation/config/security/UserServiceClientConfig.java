package gov.cms.mat.cql_elm_translation.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

@Configuration
public class UserServiceClientConfig {

  @Bean
  public RestTemplate userServiceRestTemplate(ObjectMapper objectMapper) {
    MappingJackson2HttpMessageConverter messageConverter =
        new MappingJackson2HttpMessageConverter();
    messageConverter.setObjectMapper(objectMapper);

    return new RestTemplateBuilder().additionalMessageConverters(messageConverter).build();
  }
}
