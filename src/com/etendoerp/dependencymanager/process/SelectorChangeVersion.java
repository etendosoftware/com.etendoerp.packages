package com.etendoerp.dependencymanager.process;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.service.OBCriteria;

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
      OBCriteria<Package> packageCriteria = OBDal.getInstance().createCriteria(Package.class);
      packageCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, depGroup))
          .add(Restrictions.eq(Package.PROPERTY_ARTIFACT, artifact))
          .setMaxResults(1);
      Package depPackage = (Package) packageCriteria.uniqueResult();

      // Include the warning if the selected version is not compatible with the current core version
      if (!DependencyCheckUtil.checkCoreCompatibility(depPackage, updateToVersion)) {
        jsonResponse.put("warning", true);
      }

      // Compare the package versions and construct the comparison results
      JSONArray dependenciesComparison = comparePackageVersions(depPackage, artifact, currentVersion, updateToVersion);
      jsonResponse.put("comparison", dependenciesComparison);

    } catch (JSONException e) {
      throw new RuntimeException("Error processing JSON content", e);
    } finally {
      OBContext.restorePreviousMode();
    }
    return jsonResponse;
  }

  /**
   * Compares the package versions and lists dependencies that are new, deleted, or updated.
   * @param depPackage The package whose versions are to be compared.
   * @param artifact The artifact identifier.
   * @param currentVersion The current version of the package.
   * @param updateToVersion The target version to update to.
   * @return A JSONArray representing the comparison of dependencies across versions.
   * @throws JSONException if there's an issue with JSON processing.
   */
  private JSONArray comparePackageVersions(Package depPackage, String artifact, String currentVersion, String updateToVersion) throws JSONException {
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
    List<PackageDependency> dependencies = ((PackageVersion) packageVersionCriteria.uniqueResult()).getETDEPPackageDependencyList();

    Map<String, PackageDependency> dependencyMap = new HashMap<>();
    for (PackageDependency dep : dependencies) {
      String key = dep.getGroup() + ":" + dep.getArtifact();
      dependencyMap.put(key, dep);
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

    // Combine keys from both dependency maps to ensure all dependencies are considered
    Set<String> allKeys = new HashSet<>();
    allKeys.addAll(dependenciesV1.keySet());
    allKeys.addAll(dependenciesV2.keySet());

    // Iterate over each key to compare dependencies from both versions
    for (String key : allKeys) {
      // Exclude core dependencies by checking if the dependency is 'etendo-core'
      if (key.contains("etendo-core")) {
        continue;
      }

      // Create a JSON object to store dependency information
      JSONObject dependencyInfo = new JSONObject();
      PackageDependency depV1 = dependenciesV1.get(key);
      PackageDependency depV2 = dependenciesV2.get(key);

      // Split key to get group and artifact which are separated by ':'
      dependencyInfo.put("group", key.split(":")[0]);
      dependencyInfo.put("artifact", key.split(":")[1]);
      dependencyInfo.put("version_v1", depV1 != null ? depV1.getVersion() : "null");
      dependencyInfo.put("version_v2", depV2 != null ? depV2.getVersion() : "null");

      // Determine the status of each dependency (New, Deleted, Updated)
      if (depV1 != null && depV2 == null) {
        // Dependency was present in the current version but not in the new version
        dependencyInfo.put("status", "[Deleted]");
      } else if (depV1 == null && depV2 != null) {
        // Dependency is new in the updated version
        dependencyInfo.put("status", "[New Dependency]");
      } else if (depV1 != null && depV2 != null && !depV1.getVersion().equals(depV2.getVersion())) {
        // Dependency exists in both versions but with different versions
        dependencyInfo.put("status", "[Updated]");
      }

      // If a status was assigned, add the dependency info to the result
      if (dependencyInfo.has("status")) {
        result.put(dependencyInfo);
      }
    }

    return result;
  }
}