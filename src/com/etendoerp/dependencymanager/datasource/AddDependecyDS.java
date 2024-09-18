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

public class AddDependecyDS extends ReadOnlyDataSourceService {

  /**
   * Returns the count of data rows based on the provided parameters.
   * This method fetches all data rows and counts them.
   *
   * @param parameters
   *     A map containing the parameters used for fetching data.
   * @return The total number of data rows based on the parameters.
   */
  @Override
  protected int getCount(Map<String, String> parameters) {
    return getData(parameters, 0, Integer.MAX_VALUE).size();
  }

  /**
   * Retrieves data for the grid based on the provided parameters.
   *
   * @param parameters
   *     A map containing the parameters used for fetching data.
   * @param startRow
   *     The starting row index for the data to be retrieved, typically for pagination.
   * @param endRow
   *     The ending row index for the data to be retrieved, typically for pagination.
   * @return A list of maps where each map represents a row of data with corresponding fields and values.
   * @throws OBException
   *     if any error occurs during data retrieval.
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
   * Retrieves grid data based on the provided parameters and package version.
   *
   * @param parameters
   *     A map containing the parameters used for filtering and sorting the data.
   * @param packageVersion
   *     The `PackageVersion` object used to generate the dependency tree.
   * @return A list of maps where each map represents a row of data with fields for group, artifact, and version.
   * @throws JSONException
   *     if there is an error processing the JSON data for filtering and sorting.
   */
  private List<Map<String, Object>> getGridData(Map<String, String> parameters,
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
    DependencySelectedFilters selectedFilters = readCriteria(parameters);
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
    Collections.sort(result, new DependencyResultComparator(strSortBy));
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
  private DependencySelectedFilters readCriteria(Map<String, String> parameters) throws JSONException {
    DependencySelectedFilters selectedFilters = new DependencySelectedFilters();
    JSONArray criteriaArray = (JSONArray) JsonUtils.buildCriteria(parameters).get(DependencyManagerConstants.CRITERIA);

    for (int i = 0; i < criteriaArray.length(); i++) {
      JSONObject criteria = criteriaArray.getJSONObject(i);
      if (criteria.has(DependencyManagerConstants.CONSTRUCTOR) && StringUtils.equals(
          DependencyManagerConstants.ADVANCED_CRITERIA,
          criteria.getString(DependencyManagerConstants.CONSTRUCTOR)) && criteria.has(
          DependencyManagerConstants.CRITERIA)) {
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
  private void addCriteria(DependencySelectedFilters selectedFilters, JSONObject criteria) throws JSONException {
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
  }

  private static class DependencyResultComparator extends AbstractResultComparator {

    /**
     * Constructs an instance of {@code DependencyResultComparator} with the specified field to sort by.
     * This constructor initializes the comparator to use the sorting field provided.
     *
     * @param sortByField the field name to sort by
     */
    public DependencyResultComparator(String sortByField) {
      super(sortByField);
    }
  }

  private static class DependencySelectedFilters extends AbstractSelectedFilters {

    /**
     * Constructs an instance of {@code DependencySelectedFilters} with the default filter settings.
     * This constructor calls the superclass constructor to initialize the filters.
     */
    public DependencySelectedFilters() {
      super();
    }
  }

}