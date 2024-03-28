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
import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
public class DependencyCheckUtil {
  private static final Logger log = LogManager.getLogger();

  /**
   * Private constructor to prevent instantiation.
   */
  private DependencyCheckUtil() {
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
          .filter(dep -> "etendo-core".equals(dep.getArtifact()))
          .findFirst()
          .orElse(null);

      String currentCoreVersion = OBDal.getInstance().get(Module.class, "0").getVersion();
      result.put("currentCoreVersion", currentCoreVersion);

      if (coreDep == null) {
        result.put("isCompatible", true);
      } else {
        String coreVersionRange = coreDep.getVersion();
        result.put("coreVersionRange", coreVersionRange);

        boolean isCompatible = isCompatible(coreVersionRange, currentCoreVersion);
        result.put("isCompatible", isCompatible);
      }
    } catch (Exception e) {
      try {
        result.put("isCompatible", false);
        result.put("error", "An error occurred: " + e.getMessage());
      } catch (JSONException jsonEx) {
        log.debug(jsonEx);
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
}