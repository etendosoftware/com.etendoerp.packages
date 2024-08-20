package com.etendoerp.dependencymanager.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openbravo.base.exception.OBException;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;

public class DependencyTreeBuilder {

  /**
   * Private constructor to prevent instantiation of this utility class.
   */
  private DependencyTreeBuilder() {

  }

  /**
   * Creates a dependency tree for the given package version.
   *
   * @param packageVersion
   *     the package version to process
   * @return a list of dependencies, excluding 'etendo-core'
   * @throws OBException
   *     if an error occurs while resolving dependencies
   */
  public static List<PackageDependency> createDependencyTree(PackageVersion packageVersion) {
    List<PackageDependency> dependencyList = packageVersion.getETDEPPackageDependencyList();
    try {
      removeDependecyCore(dependencyList);

      Map<String, PackageDependency> dependencyMap = new HashMap<>();

      for (PackageDependency dependency : dependencyList) {
        addDependency(dependencyMap, dependency);
        List<PackageDependency> subDependencies = searchDependency(dependency, dependencyMap);
        for (PackageDependency subDependency : subDependencies) {
          addDependency(dependencyMap, subDependency);
        }
      }
      return new ArrayList<>(dependencyMap.values());
    } catch (Exception e) {
      throw new OBException(OBMessageUtils.messageBD("ETDEP_Dep_Resolve_Error"));
    }
  }

  /**
   * Removes 'etendo-core' dependencies from the list.
   *
   * @param dependencyList
   *     the list of dependencies
   */
  private static void removeDependecyCore(List<PackageDependency> dependencyList) {
    dependencyList.removeIf(dependency -> dependency.getArtifact().equals("etendo-core"));
  }

  /**
   * Adds a dependency to the map, replacing it if a newer version is found.
   *
   * @param dependencyMap
   *     the map of dependencies
   * @param dependency
   *     the dependency to add
   */
  private static void addDependency(Map<String, PackageDependency> dependencyMap, PackageDependency dependency) {
    String key = dependency.getArtifact();
    if (dependencyMap.containsKey(key)) {
      PackageDependency existingDependency = dependencyMap.get(key);
      if (PackageUtil.compareVersions(dependency.getVersion(), existingDependency.getVersion()) > 0) {
        dependencyMap.put(key, dependency);
      }
    } else {
      dependencyMap.put(key, dependency);
    }
  }

  /**
   * Recursively searches for sub-dependencies, excluding 'etendo-core'.
   *
   * @param dependency
   *     the dependency to process
   * @param dependencyMap
   *     the map of dependencies
   * @return a list of sub-dependencies
   */
  private static List<PackageDependency> searchDependency(PackageDependency dependency,
      Map<String, PackageDependency> dependencyMap) {
    if (dependency.isExternalDependency()) {
      return new ArrayList<>();
    }

    List<PackageDependency> dependencies = dependency.getDependencyVersion().getETDEPPackageDependencyList();

    if (dependencies.size() == 1 && dependencies.get(0).getArtifact().equals("etendo-core")) {
      return new ArrayList<>();
    }
    removeDependecyCore(dependencies);
    List<PackageDependency> allDependencies = new ArrayList<>(dependencies);

    for (PackageDependency dep : dependencies) {
      List<PackageDependency> subDependencies = searchDependency(dep, dependencyMap);
      allDependencies.addAll(subDependencies);
    }

    return allDependencies;
  }
}
