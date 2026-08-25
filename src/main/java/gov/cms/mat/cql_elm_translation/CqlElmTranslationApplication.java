package gov.cms.mat.cql_elm_translation;

import gov.cms.madie.cql_elm_translator.service.CqlLibraryService;
import gov.cms.madie.cql_elm_translator.utils.FhirUtil;
import gov.cms.mat.cql_elm_translation.config.logging.LogInterceptor;
import gov.cms.mat.cql_elm_translation.config.security.SecurityFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.github.benmanes.caffeine.cache.Caffeine;

import jakarta.annotation.PostConstruct;

import java.util.TimeZone;

@SpringBootApplication(exclude = {MongoAutoConfiguration.class, DataMongoAutoConfiguration.class})
@Configuration
@Slf4j
@Import({CqlLibraryService.class, FhirUtil.class})
@EnableCaching
@EnableScheduling
public class CqlElmTranslationApplication {

  public static void main(String[] args) {
    SpringApplication.run(CqlElmTranslationApplication.class, args);
  }

  /** Force UTC timezone locally. */
  @PostConstruct
  public void init() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    log.info("Set timezone to UTC.");
  }

  @Bean
  @Primary
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  @Primary
  public CacheManager cacheManager() {
    CaffeineCacheManager cacheManager =
        new CaffeineCacheManager("cqlLibraries", "effectiveDataRequirementsCache");
    cacheManager.setCaffeine(Caffeine.newBuilder().maximumSize(500));
    return cacheManager;
  }

  @Bean(name = "FilterRegistrationBeanSecurityFilter")
  public FilterRegistrationBean<SecurityFilter> securityFilter(SecurityFilter securityFilter) {
    FilterRegistrationBean<SecurityFilter> registrationBean =
        new FilterRegistrationBean<>(securityFilter);
    registrationBean.setFilter(securityFilter);
    registrationBean.addUrlPatterns("/*");
    return registrationBean;
  }

  @Bean
  public WebMvcConfigurer corsConfigurer(@Autowired LogInterceptor logInterceptor) {
    return new WebMvcConfigurer() {

      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        WebMvcConfigurer.super.addInterceptors(registry);
        registry.addInterceptor(logInterceptor);
      }

      @Override
      public void addCorsMappings(CorsRegistry registry) {
        registry
            .addMapping("/**")
            .allowedMethods("PUT", "POST", "GET")
            .allowedOrigins(
                "http://localhost:9000",
                "https://dev-madie.hcqis.org",
                "https://test-madie.hcqis.org",
                "https://impl-madie.hcqis.org",
                "https://dev.madie.internal.cms.gov",
                "https://test.madie.internal.cms.gov",
                "https://impl.madie.internal.cms.gov",
                "https://madie.cms.gov");
      }
    };
  }
}
