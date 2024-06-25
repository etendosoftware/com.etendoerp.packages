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
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.PackageUtil;

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
  private static final String STATUS = "status";
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
      String artifact = jsonContent.optString(PackageUtil.ARTIFACT);
      String updateToVersion = jsonContent.optString("updateToVersion");
      String currentVersion = jsonContent.optString("currentVersion");

      OBContext.setAdminMode();

      // Fetch the package based on group and artifact identifiers
      Package depPackage = fetchPackageByGroupAndArtifact(depGroup, artifact);

      // Fetch the package versions for the current and updated versions
      PackageVersion updateToPackageVersion = getPackageVersion(depPackage, updateToVersion);
      String currentCoreVersion = OBDal.getInstance().get(Module.class, "0").getVersion();

      PackageDependency updateToEtendoCoreDependency = getEtendoCoreDependency(updateToPackageVersion);
      // Check if the selected version is not compatible with the current core version
      String fromCore = updateToPackageVersion.getFromCore();
      String latestCore = updateToPackageVersion.getLatestCore();
      if (StringUtils.isEmpty(fromCore) || (!StringUtils.isEmpty(fromCore) && StringUtils.isEmpty(latestCore))) {
        throw new OBException(OBMessageUtils.messageBD("ETDEP_Core_Version_Not_Found"));
      }
      boolean isCompatible = isCoreVersionCompatible(currentCoreVersion, fromCore, latestCore);
      jsonResponse.put("warning", !isCompatible);

      // Include additional compatibility details in the response
      jsonResponse.put("currentCoreVersion", currentCoreVersion);
      jsonResponse.put("updateToCoreVersion", updateToEtendoCoreDependency != null ? updateToEtendoCoreDependency.getVersion() : "");

      // Compare the package versions and construct the comparison results
      JSONArray dependenciesComparison = comparePackageVersions(depPackage, currentVersion, updateToVersion);
      jsonResponse.put("comparison", dependenciesComparison);
    } catch (JSONException e) {
      throw new OBException(OBMessageUtils.messageBD("ETDEP_Error_Updating_Dependency_Version"), e);
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
  public Package fetchPackageByGroupAndArtifact(String depGroup, String artifact) {
    OBCriteria<Package> packageCriteria = OBDal.getInstance().createCriteria(Package.class);
    packageCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, depGroup))
        .add(Restrictions.eq(Package.PROPERTY_ARTIFACT, artifact))
        .setMaxResults(1);
    return (Package) packageCriteria.uniqueResult();
  }

  /**
   * Compares the package versions and lists dependencies that are new or updated.
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
  public Map<String, PackageDependency> getDependenciesMap(Package depPackage, String version) {
    Map<String, PackageDependency> dependencyMap = new HashMap<>();
    OBCriteria<PackageVersion> packageVersionCriteria = OBDal.getInstance()
        .createCriteria(PackageVersion.class)
        .add(Restrictions.eq(PackageVersion.PROPERTY_PACKAGE, depPackage))
        .add(Restrictions.eq(PackageVersion.PROPERTY_VERSION, version))
        .setMaxResults(1);
    PackageVersion packageVersion = (PackageVersion) packageVersionCriteria.uniqueResult();

    if (packageVersion != null) {
      String fromCore = packageVersion.getFromCore();
      String latestCore = packageVersion.getLatestCore();

      if (StringUtils.isEmpty(fromCore) || (!StringUtils.isEmpty(fromCore) && StringUtils.isEmpty(latestCore))) {
        throw new OBException(OBMessageUtils.messageBD("ETDEP_Invalid_Core_Versions") + version);
      }

      List<PackageDependency> dependencies = packageVersion.getETDEPPackageDependencyList();
      for (PackageDependency dep : dependencies) {
        String key = dep.getGroup() + ":" + dep.getArtifact();
        dependencyMap.put(key, dep);
      }
    } else {
      throw new OBException(OBMessageUtils.messageBD("ETDEP_Version_Not_Found") + version);
    }

    return dependencyMap;
  }

  /**
   * Compares two sets of dependencies to determine which are new or updated.
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
      if (StringUtils.contains(key, PackageUtil.ETENDO_CORE)) {
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
    dependencyInfo.put(PackageUtil.GROUP, key.split(":")[0]);
    dependencyInfo.put(PackageUtil.ARTIFACT, key.split(":")[1]);
    dependencyInfo.put(PackageUtil.VERSION_V1, depV1 != null ? depV1.getVersion() : "");
    dependencyInfo.put(PackageUtil.VERSION_V2, depV2 != null ? depV2.getVersion() : "");

    if (depV1 == null && depV2 != null) {
      dependencyInfo.put(STATUS, NEW_DEPENDENCY);
    } else if (depV1 != null && depV2 != null && !StringUtils.equals(depV1.getVersion(), depV2.getVersion())) {
      dependencyInfo.put(STATUS, UPDATED);
    } else {
      return null;
    }

    return dependencyInfo;
  }

  private PackageVersion getPackageVersion(Package depPackage, String version) {
    OBCriteria<PackageVersion> versionCriteria = OBDal.getInstance().createCriteria(PackageVersion.class);
    versionCriteria.add(Restrictions.eq(PackageVersion.PROPERTY_PACKAGE, depPackage));
    versionCriteria.add(Restrictions.eq(PackageVersion.PROPERTY_VERSION, version));
    versionCriteria.setMaxResults(1);
    return (PackageVersion) versionCriteria.uniqueResult();
  }

  /**
   * Retrieves the Etendo Core dependency from a given package version.
   *
   * This method iterates over the list of dependencies of the provided package version.
   * If a dependency with the artifact name "Etendo Core" is found, it is returned.
   * If no such dependency is found, the method returns null.
   *
   * @param packageVersion The package version from which to retrieve the Etendo Core dependency.
   * @return The Etendo Core dependency if found, null otherwise.
   */
  private PackageDependency getEtendoCoreDependency(PackageVersion packageVersion) {
    for (PackageDependency dep : packageVersion.getETDEPPackageDependencyList()) {
        if (StringUtils.equals(PackageUtil.ETENDO_CORE, dep.getArtifact())) {
            return dep;
        }
    }
    return null;
  }

  /**
   * Checks if the current core version is compatible with the required version range.
   *
   * @param currentCoreVersion The current version of the core.
   * @param requiredStart The start of the required version range.
   * @param requiredEnd The end of the required version range. If this is empty, it means that all versions higher than the start version are compatible.
   * @return true if the current core version is within the required version range, false otherwise.
   * @throws NumberFormatException if the version strings cannot be parsed into integers.
   */
  public boolean isCoreVersionCompatible(String currentCoreVersion, String requiredStart, String requiredEnd) {
    try {
      return compareVersions(currentCoreVersion, requiredStart) >= 0 && (StringUtils.isEmpty(requiredEnd) || compareVersions(currentCoreVersion, requiredEnd) <= 0);
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * Compares two version strings.
   *
   * The version strings are expected to be in the format "x.y.z", where x, y, and z are integers.
   * The method splits the version strings by the dot character and compares the resulting parts from left to right.
   * If a part in one version string is missing, it is treated as zero.
   *
   * @param version1 The first version string to compare.
   * @param version2 The second version string to compare.
   * @return A negative integer, zero, or a positive integer if the first version is less than, equal to, or greater than the second version respectively.
   */
  private int compareVersions(String version1, String version2) {
    String[] v1 = version1.split("\\.");
    String[] v2 = version2.split("\\.");

    for (int i = 0; i < Math.max(v1.length, v2.length); i++) {
      int num1 = i < v1.length ? Integer.parseInt(v1[i]) : 0;
      int num2 = i < v2.length ? Integer.parseInt(v2[i]) : 0;
      if (num1 != num2) {
        return Integer.compare(num1, num2);
      }
    }
    return 0;
  }
}
