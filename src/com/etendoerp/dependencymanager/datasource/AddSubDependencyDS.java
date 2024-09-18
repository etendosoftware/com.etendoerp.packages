package com.etendoerp.dependencymanager.datasource;

import static com.etendoerp.dependencymanager.util.DependencyTreeBuilder.removeDependecyCore;
import static com.etendoerp.dependencymanager.util.PackageUtil.compareVersions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBDal;
import org.openbravo.service.datasource.ReadOnlyDataSourceService;
import org.openbravo.service.json.JsonUtils;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;

public class AddSubDependencyDS extends ReadOnlyDataSourceService {

  /**
   * Returns the count of the data based on the provided parameters.
   *
   * <p>This method retrieves all the data (without limits) and returns the size of the resulting list.</p>
   *
   * @param parameters
   *     The map of parameters used to filter or specify the data.
   * @return The number of data items that match the given parameters.
   */
  @Override
  protected int getCount(Map<String, String> parameters) {
    return getData(parameters, 0, Integer.MAX_VALUE).size();
  }

  /**
   * Retrieves a list of data maps based on the provided parameters and pagination settings.
   *
   * <p>This method fetches a subset of the data starting from {@code startRow} to {@code endRow}.</p>
   *
   * @param parameters
   *     The map of parameters used to filter or specify the data.
   * @param startRow
   *     The starting row index for pagination.
   * @param endRow
   *     The ending row index for pagination.
   * @return A list of data maps containing the retrieved data.
   * @throws OBException
   *     If an error occurs during data retrieval.
   */
  @Override
  protected List<Map<String, Object>> getData(Map<String, String> parameters, int startRow, int endRow) {
    List<Map<String, Object>> result;
    try {
      final String strETDEPPackageVersionId = parameters.get("@ETDEP_Package_Version.id@");
      final PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, strETDEPPackageVersionId);

      result = getGridData(parameters, packageVersion);

    } catch (Exception e) {
      throw new OBException(e.getMessage());
    }

    return result;
  }

  /**
   * Retrieves the grid data based on the package version and parameters.
   *
   * <p>This method processes the package dependencies, builds a map of the dependencies,
   * and returns a filtered and sorted list of data maps.</p>
   *
   * @param parameters
   *     The map of parameters used to filter the data.
   * @param packageVersion
   *     The version of the package to retrieve the dependencies for.
   * @return A list of data maps containing the dependency information.
   * @throws JSONException
   *     If there is an error processing the JSON data.
   */
  private List<Map<String, Object>> getGridData(Map<String, String> parameters,
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
    return applyFilterAndSort(parameters, result);
  }

  /**
   * Applies filtering and sorting to the given data based on the provided parameters.
   *
   * @param parameters
   *     A map containing the parameters used for filtering and sorting the data.
   * @param result
   *     A list of maps representing the data to be filtered and sorted.
   * @return A list of maps representing the filtered and sorted data.
   * @throws JSONException
   *     if there is an error processing the JSON data for filtering and sorting.
   */
  private List<Map<String, Object>> applyFilterAndSort(Map<String, String> parameters,
      List<Map<String, Object>> result) throws JSONException {
    SubDependencySelectedFilters selectedFilters = readCriteria(parameters);
    //artifact Filter
    if (selectedFilters.getArtifact() != null) {
      result = result.stream().filter(
          row -> StringUtils.contains(row.get(DependencyManagerConstants.ARTIFACT).toString(),
              selectedFilters.getArtifact())).collect(Collectors.toList());
    }
    //group Filter
    if (selectedFilters.getGroup() != null) {
      result = result.stream().filter(row -> StringUtils.contains(row.get(DependencyManagerConstants.GROUP).toString(),
          selectedFilters.getGroup())).collect(Collectors.toList());
    }
    //version filter
    if (selectedFilters.getVersion() != null) {
      result = result.stream().filter(
          row -> StringUtils.contains(row.get(DependencyManagerConstants.VERSION).toString(),
              selectedFilters.getVersion())).collect(Collectors.toList());
    }

    //parent filter
    if (!selectedFilters.getParent().isEmpty()) {
      result = result.stream().filter(
          row -> selectedFilters.getParent().contains(row.get(DependencyManagerConstants.PARENT))).collect(
          Collectors.toList());
    }

    sortResult(parameters, result);
    return result;
  }


  /**
   * Sorts the given data based on the sorting criteria provided in the parameters.
   *
   * @param parameters
   *     A map containing the sorting criteria.
   * @param result
   *     A list of maps representing the data to be sorted.
   */
  private void sortResult(Map<String, String> parameters, List<Map<String, Object>> result) {
    String strSortBy = parameters.getOrDefault(DependencyManagerConstants.SORT_BY, DependencyManagerConstants.LINE);
    Collections.sort(result, new SubDependencyResultComparator(strSortBy));
  }

  /**
   * Reads and processes filtering criteria from the provided parameters to create a `DependencySelectedFilters` object.
   *
   * @param parameters
   *     A map containing the filtering criteria in JSON format.
   * @return A `DependencySelectedFilters` object populated with the extracted criteria.
   * @throws JSONException
   *     if there is an error parsing the JSON data.
   */
  private SubDependencySelectedFilters readCriteria(Map<String, String> parameters) throws JSONException {
    SubDependencySelectedFilters selectedFilters = new SubDependencySelectedFilters();
    JSONArray criteriaArray = (JSONArray) JsonUtils.buildCriteria(parameters).get(DependencyManagerConstants.CRITERIA);

    for (int i = 0; i < criteriaArray.length(); i++) {
      JSONObject criteria = criteriaArray.getJSONObject(i);
      if (criteria.has(DependencyManagerConstants.CONSTRUCTOR) && StringUtils.equals(DependencyManagerConstants.ADVANCED_CRITERIA,
          criteria.getString(DependencyManagerConstants.CONSTRUCTOR)) && criteria.has(DependencyManagerConstants.CRITERIA)) {
        JSONArray innerCriteriaArray = new JSONArray(criteria.getString(DependencyManagerConstants.CRITERIA));
        for (int j = 0; j < innerCriteriaArray.length(); j++) {
          criteria = innerCriteriaArray.getJSONObject(j);
          addCriteria(selectedFilters, criteria);
        }
      } else {
        addCriteria(selectedFilters, criteria);
      }
    }
    return selectedFilters;
  }

  /**
   * Adds filtering criteria from a JSON object to the specified `DependencySelectedFilters` object.
   *
   * @param selectedFilters
   *     The `DependencySelectedFilters` object to which the criteria should be added.
   * @param criteria
   *     A JSON object containing the filtering criteria.
   * @throws JSONException
   *     if there is an error parsing the JSON object.
   */
  private void addCriteria(SubDependencySelectedFilters selectedFilters, JSONObject criteria) throws JSONException {
    String fieldName = criteria.getString(DependencyManagerConstants.FIELD_NAME);

    if (!criteria.has(DependencyManagerConstants.VALUE)) {
      return;
    }

    String value = criteria.getString(DependencyManagerConstants.VALUE);

    if (StringUtils.equals(fieldName, DependencyManagerConstants.GROUP)) {
      selectedFilters.setGroup(value);
      return;
    }
    if (StringUtils.equals(fieldName, DependencyManagerConstants.ARTIFACT)) {
      selectedFilters.setArtifact(value);
      return;
    }
    if (StringUtils.equals(fieldName, DependencyManagerConstants.VERSION)) {
      selectedFilters.setVersion(value);
    }
    if (StringUtils.equals(fieldName, DependencyManagerConstants.PARENT)) {
      selectedFilters.addParent(value);
    }
  }

  private static class SubDependencyResultComparator extends AbstractResultComparator {

    /**
     * Constructs an instance of {@code SubDependencyResultComparator} with the specified field to sort by.
     * Adds the "parent" field to the list of string fields used for comparison.
     *
     * @param sortByField the field name to sort by
     */
    public SubDependencyResultComparator(String sortByField) {
      super(sortByField);
      this.stringFieldList.add(DependencyManagerConstants.PARENT);
    }
  }

  private static class SubDependencySelectedFilters extends AbstractSelectedFilters {
    /**
     * List of parent dependencies for filtering sub-dependencies.
     */
    private List<String> parent;

    /**
     * Constructs an instance of {@code SubDependencySelectedFilters} with an empty list of parent dependencies.
     */
    public SubDependencySelectedFilters() {
      super();
      this.parent = new ArrayList<>();
    }

    /**
     * Returns the list of parent dependencies.
     *
     * @return the list of parent dependencies
     */
    public List<String> getParent() {
      return parent;
    }

    /**
     * Sets the list of parent dependencies.
     *
     * @param parentL the new list of parent dependencies
     */
    public void setParent(List<String> parentL) {
      this.parent = parentL;
    }

    /**
     * Adds a parent dependency to the list.
     *
     * @param parent the parent dependency to add
     */
    public void addParent(String parent) {
      this.parent.add(parent);
    }
  }

}
