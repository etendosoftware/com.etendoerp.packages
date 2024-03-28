package com.etendoerp.dependencymanager.process;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.base.exception.OBException;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyCheckUtil;

import javax.enterprise.context.ApplicationScoped;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles the process of changing versions for dependencies
 * This class is responsible for managing version updates, including checking for core compatibility
 * and comparing dependencies between the current and updated versions.
 */
@ApplicationScoped
public class SelectorChangeVersion extends BaseActionHandler {
  // Constants
  private static final String ETENDO_CORE = "etendo-core";
  private static final String STATUS = "status";
  private static final String DELETED = "[Deleted]";
  private static final String NEW_DEPENDENCY = "[New Dependency]";
  private static final String UPDATED = "[Updated]";

  /**
   * Executes the version change operation.
   * @param parameters A map containing the operation's parameters.
   * @param content A string representation of the JSON content containing version and dependency information.
   * @return A JSONObject containing the comparison results or a warning message if incompatible.
   */
  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject jsonResponse = new JSONObject();
    try {
      JSONObject jsonContent = new JSONObject(content);
      String depGroup = jsonContent.optString("depGroup");
      String artifact = jsonContent.optString("artifact");
      String updateToVersion = jsonContent.optString("updateToVersion");
      String currentVersion = jsonContent.optString("currentVersion");

      OBContext.setAdminMode();

      // Fetch the package based on group and artifact identifiers
      Package depPackage = fetchPackageByGroupAndArtifact(depGroup, artifact);

      // Check if the selected version is not compatible with the current core version
      JSONObject compatibilityResult = DependencyCheckUtil.checkCoreCompatibility(depPackage, updateToVersion);

      // Use the compatibility result
      boolean isCompatible = compatibilityResult.getBoolean("isCompatible");
      jsonResponse.put("warning", !isCompatible); // Use warning to indicate compatibility issues

      // Optionally include additional compatibility details in the response
      jsonResponse.put("compatibilityDetails", compatibilityResult);

      // Compare the package versions and construct the comparison results
      JSONArray dependenciesComparison = comparePackageVersions(depPackage, currentVersion, updateToVersion);
      jsonResponse.put("comparison", dependenciesComparison);
    } catch (JSONException e) {
      throw new OBException("Error processing JSON or updating dependency version", e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return jsonResponse;
  }

  /**
   * Retrieves a package by group and artifact.
   * @param depGroup The package's group.
   * @param artifact The package's artifact.
   * @return The matching package, or {@code null} if not found.
   */
  private Package fetchPackageByGroupAndArtifact(String depGroup, String artifact) {
    OBCriteria<Package> packageCriteria = OBDal.getInstance().createCriteria(Package.class);
    packageCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, depGroup))
        .add(Restrictions.eq(Package.PROPERTY_ARTIFACT, artifact))
        .setMaxResults(1);
    return (Package) packageCriteria.uniqueResult();
  }

  /**
   * Compares the package versions and lists dependencies that are new, deleted, or updated.
   * @param depPackage The package whose versions are to be compared.
   * @param currentVersion The current version of the package.
   * @param updateToVersion The target version to update to.
   * @return A JSONArray representing the comparison of dependencies across versions.
   * @throws JSONException if there's an issue with JSON processing.
   */
  private JSONArray comparePackageVersions(Package depPackage, String currentVersion, String updateToVersion) throws JSONException {
    Map<String, PackageDependency> dependenciesCurrent = getDependenciesMap(depPackage, currentVersion);
    Map<String, PackageDependency> dependenciesUpdate = getDependenciesMap(depPackage, updateToVersion);

    return compareDependencies(dependenciesCurrent, dependenciesUpdate);
  }

  /**
   * Generates a map of package dependencies for a specific version.
   * @param depPackage The package for which dependencies are to be mapped.
   * @param version The version of the package.
   * @return A map of the package dependencies.
   */
  private Map<String, PackageDependency> getDependenciesMap(Package depPackage, String version) {
    OBCriteria<PackageVersion> packageVersionCriteria = OBDal.getInstance()
        .createCriteria(PackageVersion.class)
        .add(Restrictions.eq(PackageVersion.PROPERTY_PACKAGE, depPackage))
        .add(Restrictions.eq(PackageVersion.PROPERTY_VERSION, version))
        .setMaxResults(1);
    PackageVersion packageVersion = (PackageVersion) packageVersionCriteria.uniqueResult();

    Map<String, PackageDependency> dependencyMap = new HashMap<>();
    if (packageVersion != null) {
      List<PackageDependency> dependencies = packageVersion.getETDEPPackageDependencyList();
      for (PackageDependency dep : dependencies) {
        String key = dep.getGroup() + ":" + dep.getArtifact();
        dependencyMap.put(key, dep);
      }
    } else {
      throw new OBException("Package version not found for version: " + version);
    }

    return dependencyMap;
  }

  /**
   * Compares two sets of dependencies to determine which are new, deleted, or updated.
   * @param dependenciesV1 The dependencies of the current version.
   * @param dependenciesV2 The dependencies of the updated version.
   * @return A JSONArray containing the comparison results.
   * @throws JSONException if there is an issue with JSON processing.
   */
  private JSONArray compareDependencies(Map<String, PackageDependency> dependenciesV1, Map<String, PackageDependency> dependenciesV2) throws JSONException {
    JSONArray result = new JSONArray();
    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(dependenciesV1.keySet());
    allKeys.addAll(dependenciesV2.keySet());

    for (String key : allKeys) {
      if (StringUtils.contains(key, ETENDO_CORE)) {
        continue;
      }

      JSONObject dependencyInfo = buildDependencyInfo(key, dependenciesV1.get(key), dependenciesV2.get(key));
      if (dependencyInfo != null) {
        result.put(dependencyInfo);
      }
    }

    return result;
  }

  private JSONObject buildDependencyInfo(String key, PackageDependency depV1, PackageDependency depV2) throws JSONException {
    JSONObject dependencyInfo = new JSONObject();
    dependencyInfo.put("group", key.split(":")[0]);
    dependencyInfo.put("artifact", key.split(":")[1]);
    dependencyInfo.put("version_v1", depV1 != null ? depV1.getVersion() : "null");
    dependencyInfo.put("version_v2", depV2 != null ? depV2.getVersion() : "null");

    if (depV1 != null && depV2 == null) {
      dependencyInfo.put(STATUS, DELETED);
    } else if (depV1 == null && depV2 != null) {
      dependencyInfo.put(STATUS, NEW_DEPENDENCY);
    } else if (depV1 != null && !StringUtils.equals(depV1.getVersion(), depV2.getVersion())) {
      dependencyInfo.put(STATUS, UPDATED);
    } else {
      return null;
    }

    return dependencyInfo;
  }
}