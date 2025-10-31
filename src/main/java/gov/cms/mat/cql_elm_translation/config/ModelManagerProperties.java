package gov.cms.mat.cql_elm_translation.config;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
@ToString
@Configuration
@ConfigurationProperties(prefix = "model-managers")
public class ModelManagerProperties {

  private Map<String, ModelGroup> modelManagers = new HashMap<>();

  @Setter
  @Getter
  @ToString
  public static class ModelGroup {
    private List<ModelConfig> models;
    private ModelManagerOptions options;
  }

  @Setter
  @Getter
  @ToString
  public static class ModelConfig {
    private String id;
    private String version;
  }

  @Getter
  @Setter
  @ToString
  @Builder
  public static class ModelManagerOptions {
    private boolean preResolve;
    private boolean useNpmModelInfoProvider;
  }
}
