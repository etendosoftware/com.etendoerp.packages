package com.etendoerp.dependencymanager.process;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;
import com.etendoerp.dependencymanager.util.PackageUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Element;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.xml.XMLUtil;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * This process updates the packages and package versions from the GitHub API.
 */
public class GetPackagesFromRepositories extends DalBaseProcess {
  private static final Logger log = LogManager.getLogger();
  private static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String LOCATION_HEADER = "Location";
  private static final String BASIC_AUTH_TOKEN = "Basic ";
  private static final String GITHUB_API_URL = "https://api.github.com/orgs/etendosoftware/packages?package_type=maven&per_page=100&page=";
  private static final String GITHUB_VERSIONS_API_URL = "https://api.github.com/orgs/etendosoftware/packages/maven/";
  private static final String GITHUB_POM_URL = "https://maven.pkg.github.com/etendosoftware/etendo_core/";
  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String GITHUB_USER = "githubUser";
  public static final String GITHUB_TOKEN = "githubToken";
  public static final String NAME = "name";
  public static final String GITHUB_API_URI_VERSIONS = "/versions";
  private String _auth;
  private static final List<String> EXCLUDED_PACKAGE_PREFIXES = Arrays.asList("com.etendorx");
  private static final List<String> EXCLUDED_PACKAGES = Arrays.asList(
    "com.etendoerp.platform.etendo-core", "com.etendoerp.gradleplugin",
    "com.etendoerp.gradleplugin.com.etendoerp.gradleplugin.gradle.plugin");
  private static final List<String> EXCLUDED_REPOSITORIES = Arrays.asList(
    "com.etendoerp.public.jars");

  /**
   * This method is called when the process is executed.
   *
   * @param bundle
   * @throws Exception
   */
  @Override
  protected void doExecute(ProcessBundle bundle) throws Exception {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String githubUser = properties.getProperty(GITHUB_USER, "");
    String githubToken = properties.getProperty(GITHUB_TOKEN, "");
    // Base64 Basic Auth token
    this._auth = BASIC_AUTH_TOKEN + Base64.getEncoder()
      .encodeToString((githubUser + ":" + githubToken).getBytes());
    try {
      processPackagesAndVersions();
      processDependencies();
    } catch (Exception e) {
      log.error("Failed to process packages", e);
      bundle.getLogger().logln("Failed to process packages "+ e.getMessage());
    }
  }

  /**
   * This method processes package dependencies.
   * It fetches packages from the GitHub API in pages, with each page containing multiple packages.
   * For each package, it attempts to process the package for its dependencies.
   * If any exception occurs during the processing of a package, it is caught and logged, and the method continues with the next package.
   * After all packages in a page have been processed, the method checks if there are more packages to fetch.
   * If there are no more packages to fetch (i.e., the fetched list of packages is empty), it breaks the loop.
   * After all packages have been processed, it flushes the session to synchronize with the database.
   * @throws Exception If an error occurs during processing.
   */
  private void processDependencies() throws Exception {
    for (int page = 1; page < 10; page++) {
      List<Map<String, Object>> packages = fetchPackages(page);
      if (packages.isEmpty()) {
        break;
      }
      for (Map<String, Object> pkg : packages) {
        try {
          processPackageDependency(pkg);
        } catch (Exception e) {
          log.error("Failed to process package dependency - ERROR: {}", e.getMessage());
        }
      }
    }
    OBDal.getInstance().flush();
  }

  /**
   * This method processes packages and their versions.
   * It fetches packages from the GitHub API in pages, with each page containing multiple packages.
   * For each package, it attempts to process the package.
   * If any exception occurs during the processing of a package, it is caught and logged, and the method continues with the next package.
   * After all packages in a page have been processed, the method checks if there are more packages to fetch.
   * If there are no more packages to fetch (i.e., the fetched list of packages is empty), it breaks the loop.
   * After all packages have been processed, it flushes the session to synchronize with the database.
   * @throws Exception If an error occurs during processing.
   */
  private void processPackagesAndVersions() throws Exception {
    for (int page = 1; page < 10; page++) {
      List<Map<String, Object>> packages = fetchPackages(page);
      if (packages.isEmpty()) {
        break;
      }
      for (Map<String, Object> pkg : packages) {
        try {
          processPackage(pkg);
        } catch (Exception e) {
          log.error("Failed to process package dependencies", e);
        }
      }
    }
    OBDal.getInstance().flush();
  }

  /**
   * Fetches the packages from the GitHub API.
   *
   * @param page
   * @return
   * @throws Exception
   */
  private List<Map<String, Object>> fetchPackages(int page) throws OBException {
    try {
      String url = GITHUB_API_URL + page;
      String responseBody = sendHttpRequest(url);
      return objectMapper.readValue(responseBody, new TypeReference<>() {
      });
    } catch (Exception e) {
      throw new OBException("Failed to fetch packages", e);
    }
  }

  /**
   * Processes a package from the GitHub API.
   * @param pkg
   * @throws Exception
   */
  private void processPackage(Map<String, Object> pkg) throws Exception {
    String name = (String) pkg.get(NAME);
    log.debug("Processing package: {}", name);

    if (!isPackageExcluded(pkg)) {
      String[] parts = name.split("\\.");
      String group = parts[0] + "." + parts[1];
      String artifact = String.join(".", Arrays.copyOfRange(parts, 2, parts.length));

      Package res = findOrCreatePackage(group, artifact);

      List<Map<String, Object>> versions = fetchPackageVersions(name);
      for (Map<String, Object> version : versions) {
        processPackageVersion(version, res);
      }
    } else {
      log.debug("Skipping excluded package: {}", name);
    }
  }

  /**
   * Processes a package from the GitHub API for its dependencies.
   * If the package is not excluded, it fetches the package versions and processes each version for dependencies.
   * If the package is excluded, it skips the processing and logs a debug message.
   *
   * @param pkg The package map object from the GitHub API.
   * @throws Exception If an error occurs during processing.
   */
  private void processPackageDependency(Map<String, Object> pkg) throws Exception {
    String name = (String) pkg.get(NAME);
    log.debug("Processing package: {}", name);

    if (!isPackageExcluded(pkg)) {
      String[] parts = name.split("\\.");
      String group = parts[0] + "." + parts[1];
      String artifact = String.join(".", Arrays.copyOfRange(parts, 2, parts.length));

      Package res = findOrCreatePackage(group, artifact);

      List<Map<String, Object>> versions = fetchPackageVersions(name);
      for (Map<String, Object> version : versions) {
        processPackageDependencyVersion(version, res, group, artifact);
      }
    } else {
      log.debug("Skipping excluded package: {}", name);
    }
  }

  /**
   * Checks if a given package name or group should be excluded based on its name or prefix.
   * Excluded packages are not processed or shown in the module management window (core, plugins, or rx packages).
   *
   * @param pkg The package to check for exclusion.
   * @return true if the package is to be excluded, false otherwise.
   */
  private boolean isPackageExcluded(Map<String, Object> pkg) {
    String packageName = (String) pkg.get(NAME);
    for (String prefix : EXCLUDED_PACKAGE_PREFIXES) {
      if (StringUtils.startsWith(packageName, prefix)) {
        return true;
      }
    }
    Map<String, Object> repository = (Map<String, Object>) pkg.get("repository");
    return EXCLUDED_PACKAGES.contains(packageName) || EXCLUDED_REPOSITORIES.contains(repository.get(NAME));
  }

  /**
   * Finds or creates a package.
   * @param group
   * @param artifact
   * @return
   */
  private Package findOrCreatePackage(String group, String artifact) {
    Package pkg = OBDal.getInstance()
      .createQuery(Package.class, "e where e.group = :group and e.artifact = :artifact")
      .setNamedParameter(DependencyManagerConstants.GROUP, group)
      .setNamedParameter(DependencyManagerConstants.ARTIFACT, artifact)
      .uniqueResult();

    if (pkg == null) {
      pkg = new Package();
      pkg.setGroup(group);
      pkg.setArtifact(artifact);
      OBDal.getInstance().save(pkg);
      OBDal.getInstance().flush();
    }
    return pkg;
  }

  /**
   * Fetches the package versions from the GitHub API.
   * @param packageName
   * @return
   * @throws Exception
   */
  private List<Map<String, Object>> fetchPackageVersions(String packageName) throws Exception {
    String url = GITHUB_VERSIONS_API_URL + packageName + GITHUB_API_URI_VERSIONS;
    String responseBody = sendHttpRequest(url);
    return objectMapper.readValue(responseBody, new TypeReference<>() {});
  }

  /**
   * Processes a package version from the GitHub API.
   * @param version
   * @param pkg
   */
  private void processPackageVersion(Map<String, Object> version, Package pkg) {
    String versionName = (String) version.get(NAME);
    PackageVersion pkgVersion = findOrCreatePackageVersion(pkg, versionName);

    if (pkgVersion.getFromCore() == null || pkgVersion.getLatestCore() == null) {
      assignCoreVersionFromPrevious(pkgVersion, pkg);
    }

    OBDal.getInstance().save(pkgVersion);
  }

  private void assignCoreVersionFromPrevious(PackageVersion currentPkgVersion, Package pkg) {
    List<PackageVersion> previousVersions = OBDal.getInstance()
      .createQuery(PackageVersion.class, "e where e.package.id = :packageId and e.version < :currentVersion order by e.version desc")
      .setNamedParameter("packageId", pkg.getId())
      .setNamedParameter("currentVersion", currentPkgVersion.getVersion())
      .list();

    if (!previousVersions.isEmpty()) {
      PackageVersion previousPkgVersion = previousVersions.get(0);
      currentPkgVersion.setFromCore(previousPkgVersion.getFromCore());
      currentPkgVersion.setLatestCore(previousPkgVersion.getLatestCore());
    }
  }

  /**
   * Processes a package version from the GitHub API and checks for dependencies.
   * If no dependencies are found for the package version, it fetches the POM XML and processes it.
   *
   * @param version The version map object from the GitHub API.
   * @param pkg The package object to which the version belongs.
   * @param group The group of the package.
   * @param artifact The artifact of the package.
   */
  private void processPackageDependencyVersion(Map<String, Object> version, Package pkg, String group, String artifact) {
    String versionName = (String) version.get(NAME);
    PackageVersion pkgVersion = findOrCreatePackageVersion(pkg, versionName);

    String coreVersionRange = PackageUtil.findCoreVersions(pkgVersion.getId());
    if (coreVersionRange != null) {
      String[] coreVersionSplit = PackageUtil.splitCoreVersionRange(coreVersionRange);
      pkgVersion.setFromCore(coreVersionSplit[0]);
      pkgVersion.setLatestCore(coreVersionSplit[1]);
      OBDal.getInstance().save(pkgVersion);
    }

    if (OBDal.getInstance()
      .createQuery(PackageDependency.class, "e where e.packageVersion.id = :packageVersionId")
      .setNamedParameter("packageVersionId", pkgVersion.getId())
      .count() == 0) {

      String pomUrl = buildPomUrl(group, artifact, versionName);
      log.debug("Fetching POM XML from {}", pomUrl);
      try {
        String pomXml = fetchPomXml(pomUrl);
        if (pomXml != null) {
          processPomXml(pomXml, pkgVersion);
        } else {
          log.error("No POM XML found or failed to fetch POM XML for URL: {}", pomUrl);
        }
      } catch (Exception e) {
        log.error("Error fetching or processing POM XML for URL: {}", pomUrl, e);
      }
    }
  }

  /**
   * Finds or creates a package version.
   * @param pkg
   * @param version
   * @return
   */
  private PackageVersion findOrCreatePackageVersion(Package pkg, String version) {
    PackageVersion pkgVersion = OBDal.getInstance()
      .createQuery(PackageVersion.class, "e where e.package.id = :packageId and e.version = :version")
      .setNamedParameter("packageId", pkg.getId())
      .setNamedParameter(DependencyManagerConstants.VERSION, version)
      .uniqueResult();

    if (pkgVersion == null) {
      pkgVersion = new PackageVersion();
      pkgVersion.setPackage(pkg);
      pkgVersion.setVersion(version);

      assignCoreVersionFromPrevious(pkgVersion, pkg);

      OBDal.getInstance().save(pkgVersion);
    }
    return pkgVersion;
  }

  /**
   * Builds the POM URL.
   *
   * @param group
   * @param artifact
   * @param versionName
   * @return
   */
  private String buildPomUrl(String group, String artifact, String versionName) {
    String groupPath = group.replace(".", "/");
    String artifactPath = artifact.replace(".", "/");
    String pomFileName = artifact + "-" + versionName + ".pom";
    
    StringBuilder urlBuilder = new StringBuilder();
    urlBuilder.append(GITHUB_POM_URL)
              .append(groupPath).append("/")
              .append(artifactPath).append("/")
              .append(versionName).append("/")
              .append(pomFileName);

    return urlBuilder.toString();
  }

  /**
   * Fetches the POM XML from the GitHub API.
   *
   * @param url
   * @return
   */
  private String fetchPomXml(String url) {
    try {
      HttpResponse<String> response = sendHttpRequestWithRedirect(url);
      if (response.statusCode() != 200) {
        log.error("Failed to fetch POM XML from {}", url);
        return null;
      }
      return response.body();
    } catch (Exception e) {
      log.error("Failed to fetch POM XML from {}", url, e);
      return null;
    }
  }

  /**
   * Processes the POM XML.
   *
   * @param pomXml
   * @param pkgVersion
   */
  private void processPomXml(String pomXml, PackageVersion pkgVersion) {
    try {
      Element xmlRootElement = XMLUtil.getInstance()
        .getRootElement(new ByteArrayInputStream(pomXml.getBytes()));
      Element dependencies = xmlRootElement.element("dependencies");

      if (dependencies != null) {
        for (Element dependency : dependencies.elements("dependency")) {
          String groupId = dependency.elementText("groupId");
          String artifactId = dependency.elementText("artifactId");
          String versionDep = dependency.elementText(DependencyManagerConstants.VERSION);
          findOrCreatePackageDependency(pkgVersion, groupId, artifactId, versionDep);
        }
      }
    } catch (Exception e) {
      log.error("Failed to parse XML input for extracting root element.", e);
    }
  }

  /**
   * Finds or creates a package dependency.
   * @param pkgVersion
   * @param group
   * @param artifact
   * @param version
   */
  private void findOrCreatePackageDependency(PackageVersion pkgVersion, String group, String artifact, String version) {
    PackageDependency dep = OBDal.getInstance()
      .createQuery(PackageDependency.class, "e where e.packageVersion.id = :packageVersionId and e.group = :group and e.artifact = :artifact and e.version = :version")
      .setNamedParameter("packageVersionId", pkgVersion.getId())
      .setNamedParameter(DependencyManagerConstants.GROUP, group)
      .setNamedParameter(DependencyManagerConstants.ARTIFACT, artifact)
      .setNamedParameter(DependencyManagerConstants.VERSION, version)
      .uniqueResult();

    if (dep == null) {
      dep = new PackageDependency();
      dep.setPackageVersion(pkgVersion);
      dep.setGroup(group);
      dep.setArtifact(artifact);
      dep.setVersion(version);
      dep.setExternalDependency(false);
      dep.setDependencyVersion(null);
      if (!StringUtils.equals(PackageUtil.ETENDO_CORE, dep.getArtifact())) {
        OBCriteria<Package> packageCriteria = OBDal.getInstance().createCriteria(Package.class);
        packageCriteria.add(Restrictions.eq(Package.PROPERTY_ARTIFACT, artifact));
        packageCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, group));
        Package dependencyPackage = (Package) packageCriteria.setMaxResults(1).uniqueResult();
        dep.setExternalDependency(true);
        if (dependencyPackage != null) {
          PackageVersion packageVersion;
          if (!PackageUtil.isMajorMinorPatchVersion(version)) {
            packageVersion = PackageUtil.getLastPackageVersion(dependencyPackage);
          } else {
            packageVersion = PackageUtil.getPackageVersion(dependencyPackage, version);
          }
          dep.setDependencyVersion(packageVersion);
          if (packageVersion != null) {
            dep.setExternalDependency(false);
          }
        }
      }
      OBDal.getInstance().save(dep);
    }
  }

  /**
   * Sends an HTTP request.
   *
   * @param url
   * @return
   * @throws Exception
   */
  private String sendHttpRequest(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
      .uri(new URI(url))
      .header(AUTHORIZATION_HEADER, this._auth)
      .version(HttpClient.Version.HTTP_2)
      .GET()
      .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response.body();
    } else {
      log.error("HTTP Request failed with status code: " + response.statusCode() + " and body: " + response.body());
      throw new OBException("HTTP Request failed with status code: " + response.statusCode());
    }
  }

  /**
   * Sends an HTTP request with redirect.
   *
   * @param url
   * @return
   * @throws Exception
   */
  private HttpResponse<String> sendHttpRequestWithRedirect(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
      .uri(new URI(url))
      .header(AUTHORIZATION_HEADER, this._auth)
      .version(HttpClient.Version.HTTP_2)
      .GET()
      .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 302) {
      String newUrl = response.headers().firstValue(LOCATION_HEADER).orElseThrow(() -> new OBException("Redirect URL not found in the response"));
      request = HttpRequest.newBuilder()
        .uri(new URI(newUrl))
        .header(AUTHORIZATION_HEADER, this._auth)
        .GET()
        .build();
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      return response;
    } else {
      String errorMessage = String.format(
          OBMessageUtils.messageBD("ETDEP_Redirect_HTTP_Request_Failed"),
          response.statusCode(),
          response.body()
      );
      log.error(errorMessage);
      throw new OBException(errorMessage);
    }
  }
}
