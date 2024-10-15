package com.etendoerp.dependencymanager.datasource;

import static com.etendoerp.dependencymanager.util.DependencyTreeBuilder.removeDependecyCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;

public class AddDependecyDS extends AbstractDependencyDS {

  /**
   * Retrieves and processes a list of dependencies for the given PackageVersion.
   * The result is presented in a format suitable for a grid, with each dependency's details
   * stored in a Map object. If the package is a bundle, core dependencies are removed from the list;
   * otherwise, a dependency tree is built.
   *
   * @param parameters
   *     A map of string parameters that may include sorting and filtering criteria.
   * @param packageVersion
   *     The version of the package for which dependencies are being fetched.
   * @return A list of maps, where each map contains details of a dependency such as group,
   *     artifact, version, and ID.
   * @throws JSONException
   *     If there is an error in processing the JSON data.
   */
  @Override
  protected List<Map<String, Object>> getGridData(Map<String, String> parameters,
      PackageVersion packageVersion) throws JSONException {
    List<Map<String, Object>> result = new ArrayList<>();
    List<PackageDependency> dependencyList;
    if (packageVersion.getPackage().isBundle()) {
      dependencyList = packageVersion.getETDEPPackageDependencyList();
      removeDependecyCore(dependencyList);
    } else {
      dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersion);
    }

    for (PackageDependency dependency : dependencyList) {
      Map<String, Object> map = new HashMap<>();
      map.put(DependencyManagerConstants.GROUP, dependency.getGroup());
      map.put(DependencyManagerConstants.ARTIFACT, dependency.getArtifact());
      map.put(DependencyManagerConstants.VERSION, dependency.getVersion());
      map.put(DependencyManagerConstants.ID, dependency.getId());

      result.add(map);
    }
    return applyFilterAndSort(parameters, result, readCriteria(parameters));
  }

  /**
   * Creates a result comparator used to sort the list of dependencies based on a specific field.
   *
   * @param sortByField
   *     The field by which the list of dependencies will be sorted.
   * @return A new instance of DependencyResultComparator.
   */
  @Override
  protected AbstractResultComparator createResultComparator(String sortByField) {
    return new DependencyResultComparator(sortByField);
  }

  /**
   * Creates an instance of DependencySelectedFilters to manage the selected filters applied to the dependency list.
   *
   * @return A new instance of DependencySelectedFilters.
   */
  @Override
  protected AbstractSelectedFilters createSelectedFilters() {
    return new DependencySelectedFilters();
  }

  private static class DependencyResultComparator extends AbstractResultComparator {
    /**
     * Constructor that initializes the comparator with the field by which to sort the results.
     *
     * @param sortByField
     *     The field by which the dependencies will be sorted.
     */
    public DependencyResultComparator(String sortByField) {
      super(sortByField);
    }
  }

  private static class DependencySelectedFilters extends AbstractSelectedFilters {
    /**
     * Default constructor for DependencySelectedFilters.
     */
    public DependencySelectedFilters() {
      super();
    }
  }
}