package com.etendoerp.dependencymanager.util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;

public class DependencyTreeBuilder {

  public static final String RELEASE = "RELEASE";
  public static final String ETENDO_CORE = "etendo-core";

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
      return dependencyMap.values().stream().filter(dependency -> !isBundle(dependency)).collect(Collectors.toList());
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
  public static void removeDependecyCore(List<PackageDependency> dependencyList) {
    dependencyList.removeIf(dependency -> StringUtils.equals(ETENDO_CORE, dependency.getArtifact()));
  }

  /**
   * Adds a dependency to the map, replacing it if a newer version is found.
   *
   * @param dependencyMap
   *     the map of dependencies
   * @param dependency
   *     the dependency to add
   */
  public static void addDependency(Map<String, PackageDependency> dependencyMap, PackageDependency dependency) {
    String key = dependency.getArtifact();
    String newVersion = dependency.getVersion();

    if (dependencyMap.containsKey(key)) {
      PackageDependency existingDependency = dependencyMap.get(key);
      String existingVersion = existingDependency.getVersion();

      if (StringUtils.equals(RELEASE, newVersion) || (!StringUtils.equals(RELEASE,
          existingVersion) && PackageUtil.compareVersions(newVersion, existingVersion) > 0)) {
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
  public static List<PackageDependency> searchDependency(PackageDependency dependency,
      Map<String, PackageDependency> dependencyMap) {
    if (dependency.isExternalDependency()) {
      return new ArrayList<>();
    }

    List<PackageDependency> dependencies = dependency.getDependencyVersion().getETDEPPackageDependencyList();

    if (dependencies.size() == 1 && StringUtils.equals(ETENDO_CORE, dependencies.get(0).getArtifact())) {
      return new ArrayList<>();
    }
    removeDependecyCore(dependencies);
    Set<PackageDependency> allDependencies = new HashSet<>(dependencies);

    for (PackageDependency dep : dependencies) {
      List<PackageDependency> subDependencies = searchDependency(dep, dependencyMap);
      allDependencies.addAll(subDependencies);
    }

    return new ArrayList<>(allDependencies);
  }

  /**
   * Checks if the dependency is marked as 'bundle' based on its artifact.
   *
   * @param dependency
   *     the dependency to check
   * @return true if the dependency is a bundle, false otherwise
   */
  public static boolean isBundle(PackageDependency dependency) {
    // Check if the artifact contains '.extensions'
    return dependency.getArtifact().contains(".extensions");
  }

  /**
   * Recursively finds all sub-dependencies of the given dependency.
   * This method returns a list of all sub-dependencies, excluding external dependencies and those with the artifact "ETENDO_CORE".
   * It also updates the parent map with parent-child relationships.
   *
   * @param dependency
   *     The dependency for which to find sub-dependencies.
   * @param parentMap
   *     A map to be updated with parent-child relationships.
   * @return A list of sub-dependencies of the given dependency.
   */
  public static List<PackageDependency> searchSubDependency(PackageDependency dependency,
      Map<String, String> parentMap) {
    if (dependency.isExternalDependency()) {
      return new ArrayList<>();
    }

    List<PackageDependency> dependencies = dependency.getDependencyVersion().getETDEPPackageDependencyList();
    if (dependencies.size() == 1 && StringUtils.equals(ETENDO_CORE, dependencies.get(0).getArtifact())) {
      return new ArrayList<>();
    }
    removeDependecyCore(dependencies);
    Set<PackageDependency> allDependencies = new HashSet<>(dependencies);

    for (PackageDependency dep : dependencies) {
      parentMap.put(dep.getId(), dependency.getId());

      List<PackageDependency> subDependencies = searchSubDependency(dep, parentMap);
      allDependencies.addAll(subDependencies);
    }
    return  new ArrayList<>(allDependencies);
  }

  /**
   * Adds dependencies based on the provided JSON array and returns the list of dependencies.
   * This method processes each JSON object in the array to retrieve dependencies by their IDs,
   * adds them to a map, and also includes their sub-dependencies. The resulting list excludes any
   * dependencies that are bundles.
   *
   * @param paramsSelect
   *     A JSON array containing the IDs of the dependencies to add.
   * @return A list of dependencies, excluding bundles.
   * @throws JSONException
   *     If there is an error parsing the JSON array.
   */
  public static List<PackageDependency> addDependenciesFromParams(JSONArray paramsSelect) throws JSONException {
    Map<String, PackageDependency> dependencyMap = new HashMap<>();

    for (int i = 0; i < paramsSelect.length(); i++) {
      JSONObject jsonObject = paramsSelect.getJSONObject(i);
      String id = jsonObject.getString("id");
      PackageDependency dependency = OBDal.getInstance().get(PackageDependency.class, id);
      addDependency(dependencyMap, dependency);
      List<PackageDependency> subDependencies = searchDependency(dependency, dependencyMap);
      for (PackageDependency subDependency : subDependencies) {
        addDependency(dependencyMap, subDependency);
      }
    }
    return dependencyMap.values().stream().filter(dependency -> !isBundle(dependency)).collect(Collectors.toList());
  }

  /**
   * Adds dependencies and their sub-dependencies to the provided maps and updates parent-child relationships.
   * This method processes each dependency in the list to find and add its sub-dependencies to the
   * `dependencyMap`. It also updates the `parentMap` to record the parent-child relationships between
   * dependencies and sub-dependencies.
   *
   * @param dependenciesList
   *     A list of dependencies to process.
   * @param dependencyMap
   *     A map to which dependencies and their sub-dependencies are added.
   * @param parentMap
   *     A map that is updated with parent-child relationships.
   */
  public static void addDependenciesWithParents(List<PackageDependency> dependenciesList,
      Map<String, PackageDependency> dependencyMap, Map<String, String> parentMap) {
    for (PackageDependency dependency : dependenciesList) {
      List<PackageDependency> subDependencies = searchSubDependency(dependency, parentMap);
      for (PackageDependency subDependency : subDependencies) {
        addDependency(dependencyMap, subDependency);
        parentMap.put(subDependency.getId(), dependency.getId());
      }
    }
  }
}