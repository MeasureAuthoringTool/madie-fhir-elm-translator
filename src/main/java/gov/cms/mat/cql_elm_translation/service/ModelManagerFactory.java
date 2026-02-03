package gov.cms.mat.cql_elm_translation.service;

import gov.cms.mat.cql_elm_translation.utils.cql.FhirUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.fhir.npm.LibraryLoader;
import org.cqframework.fhir.npm.NpmModelInfoProvider;
import org.cqframework.fhir.npm.NpmPackageManager;
import org.cqframework.fhir.npm.NpmPackageManagerException;
import org.hl7.cql.model.ModelIdentifier;
import org.hl7.cql.model.ModelInfoProvider;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Paths;
import java.nio.file.Files;

@Slf4j
@Service
public class ModelManagerFactory implements ILoggingService {

  private final String fhirCachePath;

  private final Map<ModelIdentifier, ModelManager> modelManagers = new ConcurrentHashMap<>();

  private final Logger logger = LoggerFactory.getLogger(ModelManagerFactory.class);

  public void logMessage(String message) {
    logger.info(message);
  }

  public void logDebugMessage(ILoggingService.LogCategory category, String message) {
    logger.debug(message);
  }

  public ModelManagerFactory(
      @Value("${madie.fhir-cache}") String fhirCachePath, @Autowired final FhirUtil fhirUtil) {
    log.info("Initializing ModelManagerFactory");
    this.fhirCachePath = fhirCachePath;

    try {
      // igs live in resources/igs directory
      var igsDir = ModelManagerFactory.class.getClassLoader().getResource("igs");
      if (igsDir != null) {
        var uri = igsDir.toURI();
        var igsPath = Paths.get(uri);
        Files.list(igsPath)
            // all igs are json files
            .filter(path -> path.toString().endsWith(".json"))
            .forEach(
                path -> {
                  String fileName = "igs/" + path.getFileName().toString();
                  ImplementationGuide ig = fhirUtil.loadImplementationGuide(fileName);
                  processImplementationGuide(ig);
                });
      }
    } catch (Exception e) {
      log.error("Error initializing ModelManagerFactory IGs", e);
    }
  }

  public void processImplementationGuide(ImplementationGuide ig) {
    if (ig != null && ig.hasDependsOn()) {
      ig.getDependsOn().stream()
          .filter(dep -> StringUtils.isNotBlank(dep.getId()))
          .forEach(
              dep -> {
                try {
                  ModelIdentifier identifier =
                      new ModelIdentifier(dep.getId(), null, dep.getVersion());
                  ModelManager modelManager = buildModelManager(identifier, ig);
                  modelManagers.put(identifier, modelManager);
                  if (StringUtils.isNotBlank(identifier.getSystem())) {
                    modelManagers.put(
                        new ModelIdentifier(identifier.getId(), null, identifier.getVersion()),
                        modelManager);
                  }
                  log.info(
                      "ModelManager created for dependsOn: {}#{}", dep.getUri(), dep.getVersion());
                } catch (IOException | NpmPackageManagerException e) {
                  log.info(
                      "Error occurred and failed to created ModelManager created for dependsOn: {}#{}",
                      dep.getUri(),
                      dep.getVersion(),
                      e);
                }
              });
    }
  }

  public ModelManager getModelManager(ModelIdentifier identifier) {
    if (identifier == null || StringUtils.isEmpty(identifier.getId())) {
      log.error("Model name cannot be null or empty");
      throw new IllegalArgumentException("Model name cannot be null or empty");
    }

    // If model is not known, create a new ModelManager without the NpmModelInfoProvider
    return modelManagers.computeIfAbsent(
        identifier,
        key -> {
          log.info("Creating new basic ModelManager for model: {}", key);
          return new ModelManager();
        });
  }

  public List<ModelIdentifier> getKnownModelIdentifiers() {
    return this.modelManagers.keySet().stream()
        .map(
            modelIdentifier ->
                new ModelIdentifier(
                    modelIdentifier.getId(),
                    modelIdentifier.getSystem(),
                    modelIdentifier.getVersion()))
        .toList();
  }

  protected ModelManager buildModelManager(
      ModelIdentifier identifier, ImplementationGuide implementationGuide) throws IOException {
    ModelManager modelManager = new ModelManager();
    modelManager
        .getModelInfoLoader()
        .registerModelInfoProvider(buildNpmModelInfoProvider(implementationGuide), true);
    modelManager.resolveModel(identifier);
    log.info("ModelManager built and model resolved for model: {}", identifier);
    return modelManager;
  }

  private ModelInfoProvider buildNpmModelInfoProvider(ImplementationGuide implementationGuide)
      throws IOException {
    FilesystemPackageCacheManager.Builder fspcmBuilder =
        new FilesystemPackageCacheManager.Builder();
    if (StringUtils.isNotBlank(fhirCachePath)) {
      fspcmBuilder = fspcmBuilder.withCacheFolder(fhirCachePath);
    }
    FilesystemPackageCacheManager fspcm = fspcmBuilder.build();
    NpmPackageManager packageManager = new NpmPackageManager(implementationGuide, fspcm);
    LibraryLoader reader = new LibraryLoader("5.0");
    return new NpmModelInfoProvider(packageManager.getNpmList(), reader, this);
  }
}
