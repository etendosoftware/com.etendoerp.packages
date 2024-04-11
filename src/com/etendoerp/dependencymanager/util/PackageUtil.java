package com.etendoerp.dependencymanager.util;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.actions.InstallDependency;
import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
public class PackageUtil {
  // Constants
  private static final Logger log = LogManager.getLogger();
  private static final String IS_COMPATIBLE = "isCompatible";
  public static final String ETENDO_CORE = "etendo-core";
  public static final String GROUP = "group";
  public static final String ARTIFACT = "artifact";
  public static final String VERSION = "version";
  public static final String STATUS = "status";
  public static final String NEW_DEPENDENCY = "New Dependency";
  public static final String UPDATED = "Updated";
  public static final String VERSION_V1 = "version_v1";
  public static final String VERSION_V2 = "version_v2";

  /**
   * Private constructor to prevent instantiation.
   */
  private PackageUtil() {
    throw new IllegalStateException("Utility class");
  }
  /**
   * Checks the compatibility of a package with the core version and returns a JSONObject with the result.
   *
   * @param pkg The package to check compatibility for.
   * @param version The version of the package being checked.
   * @return JSONObject with compatibility result and version details.
   */
  public static JSONObject checkCoreCompatibility(Package pkg, String version) {
    JSONObject result = new JSONObject();
    try {
      PackageVersion pkgVersion = getPackageVersion(pkg, version);
      PackageDependency coreDep = pkgVersion.getETDEPPackageDependencyList().stream()
          .filter(dep -> StringUtils.equals(ETENDO_CORE, dep.getArtifact()))
          .findFirst()
          .orElse(null);

      String currentCoreVersion = OBDal.getInstance().get(Module.class, "0").getVersion();
      result.put("currentCoreVersion", currentCoreVersion);

      if (coreDep == null) {
        result.put(IS_COMPATIBLE, true);
      } else {
        String coreVersionRange = coreDep.getVersion();
        result.put("coreVersionRange", coreVersionRange);

        boolean isCompatible = isCompatible(coreVersionRange, currentCoreVersion);
        result.put(IS_COMPATIBLE, isCompatible);
      }
    } catch (Exception e) {
      try {
        result.put(IS_COMPATIBLE, false);
        result.put("error", "An error occurred: " + e.getMessage());
      } catch (JSONException jsonEx) {
        log.error(jsonEx);
      }
    }
    return result;
  }
  /**
   * Retrieves the package version based on the specified package and version.
   *
   * @param depPackage
   *     The package to retrieve the version for.
   * @param version
   *     The version of the package to retrieve.
   * @return The PackageVersion object corresponding to the specified package and version, or null if not found.
   */
  private static PackageVersion getPackageVersion(Package depPackage, String version) {
    OBCriteria<PackageVersion> packageVersionCriteria = OBDal.getInstance().createCriteria(PackageVersion.class);
    packageVersionCriteria.add(Restrictions.eq(PackageVersion.PROPERTY_PACKAGE, depPackage));
    packageVersionCriteria.add(Restrictions.eq(PackageVersion.PROPERTY_VERSION, version));
    packageVersionCriteria.setMaxResults(1);
    return (PackageVersion) packageVersionCriteria.uniqueResult();
  }
  /**
   * Checks if a given version falls within a specified version range.
   *
   * @param versionRange
   *     The version range to check against.
   * @param versionToCheck
   *     The version to check compatibility for.
   * @return true if the version falls within the range, false otherwise.
   */
  private static boolean isCompatible(String versionRange, String versionToCheck) {
    if (StringUtils.isEmpty(versionRange) || StringUtils.isEmpty(versionToCheck)) {
      return false;
    }

    boolean isLowerInclusive = StringUtils.startsWith(versionRange, "[");
    boolean isUpperInclusive = StringUtils.endsWith(versionRange, "]");

    String cleanRange = "";
    if (StringUtils.isNotEmpty(versionRange) && versionRange.length() > 2) {
      cleanRange = StringUtils.substring(versionRange, 1, versionRange.length() - 1);
    }

    String[] limits = StringUtils.split(cleanRange, ",");
    if (limits == null || limits.length < 2) {
      return false;
    }

    String lowerLimit = StringUtils.trim(limits[0]);
    String upperLimit = StringUtils.trim(limits[1]);

    int lowerComparison = compareVersions(versionToCheck, lowerLimit);
    int upperComparison = compareVersions(versionToCheck, upperLimit);

    boolean isAboveLowerLimit = isLowerInclusive ? lowerComparison >= 0 : lowerComparison > 0;
    boolean isBelowUpperLimit = isUpperInclusive ? upperComparison <= 0 : upperComparison < 0;

    return isAboveLowerLimit && isBelowUpperLimit;
  }
  /**
   * Compares two version strings numerically.
   *
   * @param version1
   *     The first version string to compare.
   * @param version2
   *     The second version string to compare.
   * @return An integer value representing the comparison result:
   *     0 if the versions are equal, a positive value if version1 is greater, and a negative value if version2 is greater.
   */
  private static int compareVersions(String version1, String version2) {
    String[] parts1 = version1.split("\\.");
    String[] parts2 = version2.split("\\.");
    int length = Math.max(parts1.length, parts2.length);
    for (int i = 0; i < length; i++) {
      int part1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
      int part2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
      if (part1 != part2) {
        return part1 - part2;
      }
    }
    return 0;
  }
  /**
   * Updates an existing dependency or creates a new one if it does not exist.
   * 
   * @param group
   *     The group of the dependency.
   * @param artifact
   *     The artifact of the dependency.
   * @param version
   *     The version of the dependency.
   * @return The updated or created Dependency object.
   */
  public static synchronized void updateOrCreateDependency(String group, String artifact, String version) {
    Dependency existingDependency = OBDal.getInstance()
        .createQuery(Dependency.class, "as pv where pv.group = :group and pv.artifact = :artifact")
        .setNamedParameter(GROUP, group)
        .setNamedParameter(ARTIFACT, artifact)
        .uniqueResult();

    String latestVersion = InstallDependency.fetchLatestVersion(group, artifact);
    String versionStatus = InstallDependency.determineVersionStatus(version, latestVersion);

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
  }
}