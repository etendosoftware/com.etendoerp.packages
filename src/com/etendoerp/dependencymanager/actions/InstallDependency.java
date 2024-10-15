package com.etendoerp.dependencymanager.actions;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;
import com.etendoerp.dependencymanager.util.PackageUtil;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import com.etendoerp.dependencymanager.data.Package;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import java.util.List;
import java.util.Properties;

public class InstallDependency extends Action {
  private static final Logger log = LogManager.getLogger();
  private static final String PLATFORM_GROUP = "com.etendoerp.platform";
  private static final String CORE_ARTIFACT = "etendo-core";
  private static final String GITHUB_VERSIONS_API_URL = "https://deps.labs.etendo.cloud/api/v1/packages/maven/";
  private static final String GITHUB_API_URI_VERSIONS = "/versions";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  public static final String LOCATION_HEADER = "Location";
  private static final String BASIC_AUTH_TOKEN = "Basic ";
  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String GITHUB_USER = "githubUser";
  public static final String GITHUB_TOKEN = "githubToken";

  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    try {
      List<PackageVersion> packageVersions = getInputContents(getInputClass());
      for (PackageVersion version : packageVersions) {
        processPackageVersion(version);
      }
      return buildSuccessResult();
    } catch (Exception e) {
      return buildErrorResult(e);
    }
  }

  private void processPackageVersion(PackageVersion version) {
    updateOrCreateDependency(version.getPackage().getGroup(),
        version.getPackage().getArtifact(), version.getVersion());

    for (PackageDependency dependency : version.getETDEPPackageDependencyList()) {
      if (shouldSkipDependency(dependency)) {
        continue;
      }
      updateOrCreateDependency(dependency.getGroup(), dependency.getArtifact(),
          dependency.getVersion());
    }
  }

  private boolean shouldSkipDependency(PackageDependency dependency) {
    return StringUtils.equals(dependency.getGroup(), PLATFORM_GROUP) && StringUtils.equals(
        dependency.getArtifact(), CORE_ARTIFACT);
  }

  private static String sendHttpRequest(String url) throws Exception {
    Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
    String githubUser = properties.getProperty(GITHUB_USER, "");
    String githubToken = properties.getProperty(GITHUB_TOKEN, "");

    String authToken = BASIC_AUTH_TOKEN + Base64.getEncoder().encodeToString((githubUser + ":" + githubToken).getBytes());
    HttpRequest request = HttpRequest.newBuilder()
        .uri(new URI(url))
        .header(AUTHORIZATION_HEADER, authToken)
        .version(HttpClient.Version.HTTP_2)
        .GET()
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    return response.body();
  }

  private static List<Map<String, Object>> fetchPackageVersions(String packageName) throws Exception {
    String url = GITHUB_VERSIONS_API_URL + packageName + GITHUB_API_URI_VERSIONS;
    String responseBody = sendHttpRequest(url);
    return objectMapper.readValue(responseBody, new TypeReference<>() {});
  }

  public static String fetchLatestVersion(String group, String artifact) {
    try {
      String packageName = group + "." + artifact;
      List<Map<String, Object>> versions = fetchPackageVersions(packageName);
      if (!versions.isEmpty()) {
        Map<String, Object> lastVersion = versions.get(0);
        return (String) lastVersion.get("name");
      } else {
        return null;
      }
    } catch (Exception e) {
      log.error("Error fetching latest version for package: " + group + "." + artifact, e);
      return null;
    }
  }

  public static String determineVersionStatus(String installedVersion, String latestVersion) {
    return StringUtils.equals(installedVersion, latestVersion) ? "U" : "UA";
  }

  private synchronized void updateOrCreateDependency(String group, String artifact, String version) {
    Dependency existingDependency = OBDal.getInstance()
        .createQuery(Dependency.class, "as pv where pv.group = :group and pv.artifact = :artifact")
        .setNamedParameter(DependencyManagerConstants.GROUP, group)
        .setNamedParameter(DependencyManagerConstants.ARTIFACT, artifact)
        .uniqueResult();

    String latestVersion = fetchLatestVersion(group, artifact);
    String versionStatus = determineVersionStatus(version, latestVersion);

    if (existingDependency != null) {
      existingDependency.setVersion(version);
      existingDependency.setVersionStatus(versionStatus);
    } else {
      Dependency newDependency = new Dependency();
      newDependency.setGroup(group);
      newDependency.setArtifact(artifact);
      newDependency.setVersion(version);
      newDependency.setVersionStatus(versionStatus);
      existingDependency = newDependency;
    }
    OBDal.getInstance().save(existingDependency);

    updateInstalledVersion(group, artifact, version);
  }

  private void updateInstalledVersion(String group, String artifact, String version) {
    Package etdepPackage = OBDal.getInstance()
        .createQuery(Package.class, "where depgroup = :group and artifact = :artifact")
        .setNamedParameter(DependencyManagerConstants.GROUP, group)
        .setNamedParameter(DependencyManagerConstants.ARTIFACT, artifact)
        .uniqueResult();

    if (etdepPackage != null) {
      etdepPackage.setInstalledVersion(version);
      OBDal.getInstance().save(etdepPackage);
    }
  }

  private ActionResult buildSuccessResult() {
    ActionResult result = new ActionResult();
    result.setType(Result.Type.SUCCESS);
    result.setMessage(OBMessageUtils.getI18NMessage("Success"));
    return result;
  }

  private ActionResult buildErrorResult(Exception e) {
    // Log the exception e
    ActionResult result = new ActionResult();
    result.setType(Result.Type.ERROR);
    result.setMessage(OBMessageUtils.getI18NMessage("Error: " + e.getMessage()));
    return result;
  }

  @Override
  protected Class<PackageVersion> getInputClass() {
    return PackageVersion.class;
  }
}
