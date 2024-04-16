package com.etendoerp.dependencymanager.process;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.PackageUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Element;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.xml.XMLUtil;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.service.db.DalBaseProcess;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

/**
 * This process updates the packages and package versions from the GitHub API.
 */
public class UpdatePackages extends DalBaseProcess {
  private static final Logger log = LogManager.getLogger();
  private static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String LOCATION_HEADER = "Location";
  private static final String BASIC_AUTH_TOKEN = "Basic ";
  private static final String GITHUB_API_URL = "https://deps.labs.etendo.cloud/api/v1/packages?page=";
  private static final String GITHUB_VERSIONS_API_URL = "https://deps.labs.etendo.cloud/api/v1/packages/maven/";
  private static final String GITHUB_POM_URL = "https://deps.labs.etendo.cloud/api/v1/pom/";
  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String GITHUB_USER = "githubUser";
  public static final String GITHUB_TOKEN = "githubToken";
  public static final String NAME = "name";
  public static final String GITHUB_API_URI_VERSIONS = "/versions";
  public static final String VERSION = "version";
  private String _auth;
  private static final List<String> EXCLUDED_PACKAGE_PREFIXES = Arrays.asList("com.etendorx");
  private static final List<String> EXCLUDED_PACKAGES = Arrays.asList(
      "com.etendoerp.platform.etendo-core", "com.etendoerp.gradleplugin",
      "com.etendoerp.gradleplugin.com.etendoerp.gradleplugin.gradle.plugin");

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

    for (int page = 1; page < 10; page++) {
      List<Map<String, Object>> packages = fetchPackages(page);
      if (packages.isEmpty()) {
        break;
      }
      for (Map<String, Object> pkg : packages) {
        try {
          processPackage(pkg);
        } catch (Exception e) {
          log.error("Failed to process package", e);
        }
      }
    }
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
   *
   * @param pkg
   * @throws Exception
   */
  private void processPackage(Map<String, Object> pkg) throws OBException {
    log.debug("Processing package: {}", pkg.get(NAME));
    String name = (String) pkg.get(NAME);

    if (!isPackageExcluded(name)) {
      String[] parts = name.split("\\.");
      String group = parts[0] + "." + parts[1];
      String artifact = String.join(".", Arrays.copyOfRange(parts, 2, parts.length));

      Package res = findOrCreatePackage(group, artifact);

      List<Map<String, Object>> versions = fetchPackageVersions(name);
      for (Map<String, Object> version : versions) {
        processPackageVersion(version, res, group, artifact);
      }
    } else {
      log.debug("Skipping excluded package: {}", name);
    }
  }

  /**
   * Checks if a given package name or group should be excluded based on its name or prefix.
   * Excluded packages are not processed or shown in the module management window (core, plugins, or rx packages).
   *
   * @param packageName The full name of the package to check for exclusion.
   * @return true if the package is to be excluded, false otherwise.
   */
  private boolean isPackageExcluded(String packageName) {
    for (String prefix : EXCLUDED_PACKAGE_PREFIXES) {
      if (StringUtils.startsWith(packageName, prefix)) {
        return true;
      }
    }

    return EXCLUDED_PACKAGES.contains(packageName);
  }

  /**
   * Finds or creates a package.
   *
   * @param group
   * @param artifact
   * @return
   */
  private Package findOrCreatePackage(String group, String artifact) {
    Package pkg = OBDal.getInstance()
        .createQuery(Package.class, "e where e.group = :group and e.artifact = :artifact")
        .setNamedParameter(PackageUtil.GROUP, group)
        .setNamedParameter(PackageUtil.ARTIFACT, artifact)
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
   *
   * @param packageName
   * @return
   * @throws Exception
   */
  private List<Map<String, Object>> fetchPackageVersions(String packageName) throws OBException {
    try {
      String url = GITHUB_VERSIONS_API_URL + packageName + GITHUB_API_URI_VERSIONS;
      String responseBody = sendHttpRequest(url);
      return objectMapper.readValue(responseBody, new TypeReference<>() {
      });
    } catch (Exception e) {
      throw new OBException("Failed to fetch package versions", e);
    }
  }

  /**
   * Processes a package version from the GitHub API.
   *
   * @param version
   * @param pkg
   * @param group
   * @param artifact
   */
  private void processPackageVersion(Map<String, Object> version, Package pkg, String group,
      String artifact) {
    String versionName = (String) version.get(NAME);
    PackageVersion pkgVersion = findOrCreatePackageVersion(pkg, versionName);

    if (OBDal.getInstance()
        .createQuery(PackageDependency.class, "e where e.packageVersion.id = :packageVersionId")
        .setNamedParameter("packageVersionId", pkgVersion.getId())
        .count() == 0) {

      String pomUrl = buildPomUrl(group, artifact, versionName);
      String pomXml = fetchPomXml(pomUrl);
      if (pomXml != null) {
        processPomXml(pomXml, pkgVersion);
      }
    }
  }

  /**
   * Finds or creates a package version.
   *
   * @param pkg
   * @param version
   * @return
   */
  private PackageVersion findOrCreatePackageVersion(Package pkg, String version) {
    PackageVersion pkgVersion = OBDal.getInstance()
        .createQuery(PackageVersion.class,
            "e where e.package.id = :packageId and e.version = :version")
        .setNamedParameter("packageId", pkg.getId())
        .setNamedParameter(VERSION, version)
        .uniqueResult();

    if (pkgVersion == null) {
      pkgVersion = new PackageVersion();
      pkgVersion.setPackage(pkg);
      pkgVersion.setVersion(version);
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
    String pomName = artifact + "-" + versionName + ".pom";
    return GITHUB_POM_URL + groupPath + "/" + artifactPath + "/" + versionName + "/" + pomName;
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
          String versionDep = dependency.elementText(VERSION);
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
            .setNamedParameter(PackageUtil.GROUP, group)
            .setNamedParameter(PackageUtil.ARTIFACT, artifact)
            .setNamedParameter(PackageUtil.VERSION, version)
            .uniqueResult();

    if (dep == null) {
      dep = new PackageDependency();
      dep.setPackageVersion(pkgVersion);
      dep.setGroup(group);
      dep.setArtifact(artifact);
      dep.setVersion(version);
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
  private String sendHttpRequest(String url) throws OBException {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new URI(url))
          .header(AUTHORIZATION_HEADER, this._auth)
          .version(HttpClient.Version.HTTP_2)
          .GET()
          .build();
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      return response.body();
    } catch (Exception e) {
      Thread.currentThread().interrupt();
      throw new OBException("Failed to send HTTP request", e);
    }
  }

  /**
   * Sends an HTTP request with redirect.
   *
   * @param url
   * @return
   * @throws Exception
   */
  private HttpResponse<String> sendHttpRequestWithRedirect(String url) throws OBException {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(new URI(url))
          .header(AUTHORIZATION_HEADER, this._auth)
          .version(HttpClient.Version.HTTP_2)
          .GET()
          .build();
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 302) {
        request = HttpRequest.newBuilder()
            .uri(new URI(response.headers().firstValue(LOCATION_HEADER).orElseThrow()))
            .headers(AUTHORIZATION_HEADER, this._auth)
            .version(HttpClient.Version.HTTP_2)
            .GET()
            .build();
        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      }
      return response;
    } catch (Exception e) {
      Thread.currentThread().interrupt();
      throw new OBException("Failed to send HTTP request with redirect", e);
    }
  }
}
