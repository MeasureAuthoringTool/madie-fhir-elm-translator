package gov.cms.mat.cql_elm_translation.service;

import ca.uhn.fhir.context.FhirContext;
import gov.cms.mat.cql_elm_translation.config.ModelManagerProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.fhir.npm.LibraryLoader;
import org.cqframework.fhir.npm.NpmModelInfoProvider;
import org.cqframework.fhir.npm.NpmPackageManager;
import org.hl7.cql.model.ModelIdentifier;
import org.hl7.cql.model.ModelInfoProvider;
import org.hl7.fhir.convertors.advisors.impl.BaseAdvisor_40_50;
import org.hl7.fhir.convertors.conv40_50.VersionConvertor_40_50;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r5.context.ILoggingService;
import org.hl7.fhir.r5.model.ImplementationGuide;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ModelManagerFactory implements ILoggingService {

  private final Map<ModelIdentifier, ModelManager> modelManagers;

  private final Logger logger = LoggerFactory.getLogger(CqlTooling.class);

  public void logMessage(String message) {
    logger.info(message);
  }

  public void logDebugMessage(ILoggingService.LogCategory category, String message) {
    logger.debug(message);
  }

  public void logWarningMessage(String message) {
    logger.warn(message);
  }

  public void logErrorMessage(String message) {
    logger.error(message);
  }

  @Override
  public boolean isDebugLogging() {
    return false;
  }

  public ModelManagerFactory() {
    modelManagers = new ConcurrentHashMap<>();
    log.info("Initializing ModelManagerFactory with default settings");

    try {
      // List all XML files in resources/igs
      var igsDir = CqlTooling.class.getClassLoader().getResource("igs");
      if (igsDir != null) {
        var uri = igsDir.toURI();
        var igsPath = java.nio.file.Paths.get(uri);
        java.nio.file.Files.list(igsPath)
                .filter(path -> path.toString().endsWith(".xml"))
                .forEach(path -> {
                  String fileName = "igs/" + path.getFileName().toString();
                  ImplementationGuide ig = loadImplementationGuide(fileName);
                  if (ig != null && ig.hasDependsOn()) {
                    ig.getDependsOn().forEach(dep -> {
                      ModelIdentifier identifier = new ModelIdentifier()
                              .withId(dep.getUri())
                              .withVersion(dep.getVersion());
                      ModelManager manager = buildModelManager(identifier, ModelManagerProperties.ModelManagerOptions.builder().preResolve(true).useNpmModelInfoProvider(true).build());
                      modelManagers.put(identifier, manager);
                      log.info("Added ModelManager for dependsOn: {} version: {}", dep.getUri(), dep.getVersion());
                    });
                  }
                });
      }
    } catch (Exception e) {
      log.error("Error initializing ModelManagerFactory IGs", e);
    }
  }

//  @Autowired
//  public ModelManagerFactory(ModelManagerProperties properties) {
//    modelManagers = new ConcurrentHashMap<>();
//    log.info("Initializing ModelManagerFactory with properties: {}", properties);
//
//    for (Map.Entry<String, ModelManagerProperties.ModelGroup> entry :
//        properties.getModelManagers().entrySet()) {
//      log.info("Initializing ModelManagerFactory with model group: {}", entry.getValue());
//      for (ModelManagerProperties.ModelConfig config : entry.getValue().getModels()) {
//        ModelIdentifier key =
//            new ModelIdentifier().withId(config.getId()).withVersion(config.getVersion());
//        ModelManager manager =
//            modelManagers.computeIfAbsent(
//                key, k -> buildModelManager(k, entry.getValue().getOptions()));
//        ModelIdentifier identifier =
//            new ModelIdentifier().withId(config.getId()).withVersion(config.getVersion());
//        modelManagers.put(identifier, manager);
//      }
//    }
//  }

  //
  //    private ModelManager buildModelManager(ModelIdentifier identifier) {
  //        if (identifier == null || StringUtils.isEmpty(identifier.getId())) {
  //            log.error("Model name cannot be null or empty");
  //            throw new IllegalArgumentException("Model name cannot be null or empty");
  //        }
  //
  //        // Create a new ModelManager with the NpmModelInfoProvider
  //        log.info("Creating new ModelManager for model: {}", identifier);
  //        return new ModelManager();
  //    }

  private ImplementationGuide loadImplementationGuide(String resourcePath) {
    Resource igResource =
            (Resource)
                    FhirContext.forR4Cached()
                            .newXmlParser()
                            .parseResource(
                                    CqlTooling.class
                                            .getClassLoader()
                                            .getResourceAsStream(resourcePath));
    //
    // .getResourceAsStream("packages/ImplementationGuide-hl7.fhir.us.qicore.xml"));

    VersionConvertor_40_50 convertor = new VersionConvertor_40_50(new BaseAdvisor_40_50());
    return (ImplementationGuide) convertor.convertResource(igResource);
  }

  private ModelInfoProvider buildNpmModelInfoProvider(String igPath) {
    // TODO: load appropriate IG based on usingProperties
    ImplementationGuide implementationGuide = loadImplementationGuide(igPath);

    //    // create our own FilesystemPackageCacheManager to manually load the IG package
    //    FilesystemPackageCacheManager fspcm = new FilesystemPackageCacheManager.Builder().build();
    //    // Load the IG package, which will be cached to the filesystem
    //    NpmPackage igNpmPackage = fspcm.loadPackage(implementationGuide.getPackageId(),
    // implementationGuide.getVersion());
    //    // Add the IG package to the list of known NPM Packages for this IG
    //    final List<NpmPackage> npmList = new ArrayList<>();
    //    npmList.add(igNpmPackage);
    //
    //    NpmPackageManager packageManager = new NpmPackageManager(implementationGuide, fspcm,
    // npmList);

    NpmPackageManager packageManager = new NpmPackageManager(implementationGuide);

    LibraryLoader reader = new LibraryLoader("5.0");
    return new NpmModelInfoProvider(packageManager.getNpmList(), reader, this);
  }

  protected ModelManager buildModelManager(
      ModelIdentifier identifier, ModelManagerProperties.ModelManagerOptions options) {
    ModelManager modelManager = new ModelManager();
    if (options.isUseNpmModelInfoProvider()) {
      modelManager
          .getModelInfoLoader()
          .registerModelInfoProvider(buildNpmModelInfoProvider("igs/ImplementationGuide-gov.cms.madie-stu7.xml"), true);
    } else {
      return getModelManager(identifier);
    }

    if (options.isPreResolve()) {
      modelManager.resolveModel(identifier);
    }

    return modelManager;

    //    modelInfoProvider.load(new
    // ModelIdentifier().withSystem("http://hl7.org/fhir/us/qicore").withId("QICore"));
    //    ModelInfo qiCore = modelInfoProvider.load(new
    // ModelIdentifier().withSystem("http://hl7.org/fhir/us/qicore").withId("QICore"));
    //    modelManager.getModelInfoLoader().clearModelInfoProviders();
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
}
