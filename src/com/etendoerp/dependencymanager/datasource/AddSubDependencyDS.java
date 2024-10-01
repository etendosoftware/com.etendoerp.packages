package com.etendoerp.dependencymanager.datasource;

import static com.etendoerp.dependencymanager.util.DependencyTreeBuilder.removeDependecyCore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;

public class AddSubDependencyDS extends AbstractDependencyDS {

  /**
   * Retrieves and processes a list of sub-dependencies for the given PackageVersion.
   * This includes mapping dependencies to their parent artifacts and applying filters and sorting
   * criteria provided in the parameters.
   *
   * @param parameters
   *     A map of string parameters for filtering and sorting the results.
   * @param packageVersion
   *     The version of the package whose sub-dependencies are being processed.
   * @return A list of maps where each map contains the details of a sub-dependency
   *     (group, artifact, version, ID, and parent artifact).
   * @throws JSONException
   *     If there is an error processing the JSON data.
   */
  @Override
  protected List<Map<String, Object>> getGridData(Map<String, String> parameters,
      PackageVersion packageVersion) throws JSONException {
    List<Map<String, Object>> result = new ArrayList<>();

    List<PackageDependency> dependenciesList = packageVersion.getETDEPPackageDependencyList();
    removeDependecyCore(dependenciesList);

    Map<String, PackageDependency> dependencyMap = new HashMap<>();
    Map<String, String> parentMap = new HashMap<>();

    DependencyTreeBuilder.addDependenciesWithParents(dependenciesList, dependencyMap, parentMap);

    List<PackageDependency> dependencyList = new ArrayList<>(dependencyMap.values());
    for (PackageDependency dependency : dependencyList) {
      Map<String, Object> map = new HashMap<>();
      map.put(DependencyManagerConstants.GROUP, dependency.getGroup());
      map.put(DependencyManagerConstants.ARTIFACT, dependency.getArtifact());
      map.put(DependencyManagerConstants.VERSION, dependency.getVersion());
      map.put(DependencyManagerConstants.ID, dependency.getId());

      String parentArtifact = parentMap.get(dependency.getId());
      if (parentArtifact != null) {
        map.put(DependencyManagerConstants.PARENT, parentArtifact);
      }
      result.add(map);
    }
    return applyFilterAndSort(parameters, result, readCriteria(parameters));
  }

  /**
   * Applies additional filtering based on parent artifact values after the basic filter and sorting.
   *
   * @param parameters
   *     A map of string parameters for sorting and filtering.
   * @param result
   *     The list of dependencies to be filtered and sorted.
   * @param abstractSelectedFilters
   *     The selected filters applied to the result set.
   * @return The filtered and sorted list of dependencies.
   * @throws JSONException
   *     If there is an error during filtering or sorting.
   */
  @Override
  protected List<Map<String, Object>> applyFilterAndSort(Map<String, String> parameters,
      List<Map<String, Object>> result, AbstractSelectedFilters abstractSelectedFilters) throws JSONException {
    SubDependencySelectedFilters selectedFilters = (SubDependencySelectedFilters) abstractSelectedFilters;
    result = super.applyFilterAndSort(parameters, result, selectedFilters);

    if (!selectedFilters.getParent().isEmpty()) {
      result = result.stream().filter(
          row -> selectedFilters.getParent().contains(row.get(DependencyManagerConstants.PARENT))).collect(
          Collectors.toList());
    }

    return result;
  }

  /**
   * Creates a comparator to sort the list of sub-dependencies based on a specified field.
   *
   * @param sortByField
   *     The field used to sort the list of sub-dependencies.
   * @return A new instance of SubDependencyResultComparator.
   */
  @Override
  protected AbstractResultComparator createResultComparator(String sortByField) {
    return new SubDependencyResultComparator(sortByField);
  }

  /**
   * Creates an instance of SubDependencySelectedFilters to manage filters applied to the sub-dependencies list.
   *
   * @return A new instance of SubDependencySelectedFilters.
   */
  @Override
  protected AbstractSelectedFilters createSelectedFilters() {
    return new SubDependencySelectedFilters();
  }

  /**
   * Adds custom filtering criteria to the selected filters based on the specified JSON criteria.
   *
   * @param abstractSelectedFilters
   *     The selected filters to which criteria are added.
   * @param criteria
   *     The JSON object containing the filtering criteria.
   * @throws JSONException
   *     If there is an error processing the JSON data.
   */
  @Override
  protected void addCriteria(AbstractSelectedFilters abstractSelectedFilters,
      JSONObject criteria) throws JSONException {
    super.addCriteria(abstractSelectedFilters, criteria);
    SubDependencySelectedFilters selectedFilters = (SubDependencySelectedFilters) abstractSelectedFilters;

    String fieldName = criteria.getString(DependencyManagerConstants.FIELD_NAME);
    if (StringUtils.equals(fieldName, DependencyManagerConstants.PARENT)) {
      selectedFilters.addParent(criteria.getString(DependencyManagerConstants.VALUE));
    }
  }

  private static class SubDependencyResultComparator extends AbstractResultComparator {
    /**
     * Constructor that initializes the comparator with the field by which to sort.
     *
     * @param sortByField
     *     The field used for sorting the sub-dependencies.
     */
    public SubDependencyResultComparator(String sortByField) {
      super(sortByField);
      this.stringFieldList.add(DependencyManagerConstants.PARENT);
    }
  }

  private static class SubDependencySelectedFilters extends AbstractSelectedFilters {
    private List<String> parent;

    /**
     * Default constructor that initializes the parent filter list.
     */
    public SubDependencySelectedFilters() {
      super();
      this.parent = new ArrayList<>();
    }

    /**
     * Retrieves the list of parent filters.
     *
     * @return The list of parent filters.
     */
    public List<String> getParent() {
      return parent;
    }

    /**
     * Sets the list of parent filters.
     *
     * @param parentL
     *     The list of parent filters to set.
     */
    public void setParent(List<String> parentL) {
      this.parent = parentL;
    }

    /**
     * Adds a parent artifact to the list of filters.
     *
     * @param parent
     *     The parent artifact to add.
     */
    public void addParent(String parent) {
      this.parent.add(parent);
    }
  }
}