package com.etendoerp.dependencymanager.datasource;

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

  public static final String CRITERIA = "criteria";
  public static final String CONSTRUCTOR = "_constructor";
  public static final String VALUE = "value";
  public static final String LINE = "line";
  public static final String FIELD_NAME = "fieldName";
  public static final String ADVANCED_CRITERIA = "AdvancedCriteria";
  public static final String SORT_BY = "_sortBy";

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
    List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersion);
    for (PackageDependency dependency : dependencyList) {
      Map<String, Object> map = new HashMap<>();
      map.put(DependencyManagerConstants.GROUP, dependency.getGroup());
      map.put(DependencyManagerConstants.ARTIFACT, dependency.getArtifact());
      map.put(DependencyManagerConstants.VERSION, dependency.getVersion());

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
          row -> StringUtils.contains(row.get(DependencyManagerConstants.ARTIFACT).toString(), selectedFilters.getArtifact())).collect(
          Collectors.toList());
    }
    //group Filter
    if (selectedFilters.getGroup() != null) {
      result = result.stream().filter(
          row -> StringUtils.contains(row.get(DependencyManagerConstants.GROUP).toString(), selectedFilters.getGroup())).collect(
          Collectors.toList());
    }
    //version filter
    if (selectedFilters.getVersion() != null) {
      result = result.stream().filter(
          row -> StringUtils.contains(row.get(DependencyManagerConstants.VERSION).toString(), selectedFilters.getVersion())).collect(
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
    String strSortBy = parameters.getOrDefault(SORT_BY, LINE);
    Collections.sort(result, new ResultComparator(strSortBy));
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
    JSONArray criteriaArray = (JSONArray) JsonUtils.buildCriteria(parameters).get(CRITERIA);

    for (int i = 0; i < criteriaArray.length(); i++) {
      JSONObject criteria = criteriaArray.getJSONObject(i);
      if (criteria.has(CONSTRUCTOR) && StringUtils.equals(ADVANCED_CRITERIA,
          criteria.getString(CONSTRUCTOR)) && criteria.has(CRITERIA)) {
        JSONArray innerCriteriaArray = new JSONArray(criteria.getString(CRITERIA));
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
    String fieldName = criteria.getString(FIELD_NAME);

    if (!criteria.has(VALUE)) {
      return;
    }

    String value = criteria.getString(VALUE);

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

  private static class ResultComparator implements Comparator<Map<String, Object>> {
    private static final List<String> STRING_FIELD_LIST = List.of(DependencyManagerConstants.GROUP, DependencyManagerConstants.ARTIFACT);
    private static final List<String> STRING_VERSION_FIELD_LIST = List.of(DependencyManagerConstants.VERSION);
    private String sortByField;
    private int ascending;

    /**
     * Constructs a `ResultComparator` with the specified field for sorting.
     *
     * @param sortByField
     *     The field by which the data should be sorted. If the field name starts with a "-",
     *     sorting will be in descending order; otherwise, it will be in ascending order.
     */
    public ResultComparator(String sortByField) {
      this.sortByField = sortByField;
      ascending = 1;
      if (StringUtils.startsWith(sortByField, "-")) {
        ascending = -1;
        this.sortByField = StringUtils.substring(sortByField, 1);
      }
    }

    /**
     * Compares two maps based on the field specified in the `sortByField` property.
     * The comparison is done according to the field type: version or string.
     * The sorting order is determined by the `ascending` property.
     *
     * @param map1
     *     The first map to be compared.
     * @param map2
     *     The second map to be compared.
     * @return A negative integer, zero, or a positive integer as the first map is less than, equal to, or greater
     *     than the second map, respectively. The result is adjusted according to the `ascending` property.
     */
    @Override
    public int compare(Map<String, Object> map1, Map<String, Object> map2) {
      int returnValue = 0;
      if (STRING_VERSION_FIELD_LIST.contains(sortByField)) {
        returnValue = getVersionCompare(map1, map2);
      } else if (STRING_FIELD_LIST.contains(sortByField)) {
        returnValue = getStringCompare(map1, map2);
      }

      return returnValue * ascending;
    }

    /**
     * Compares version values from two maps based on the field specified in `sortByField`.
     *
     * @param map1
     *     The first map containing the version value.
     * @param map2
     *     The second map containing the version value.
     * @return A negative integer, zero, or a positive integer if the version in `map1` is less than, equal to,
     *     or greater than the version in `map2`, respectively.
     */
    private int getVersionCompare(Map<String, Object> map1, Map<String, Object> map2) {
      var val1 = map1.get(sortByField) != null ? map1.get(sortByField).toString() : StringUtils.EMPTY;
      var val2 = map2.get(sortByField) != null ? map2.get(sortByField).toString() : StringUtils.EMPTY;
      return compareVersions(val1, val2);
    }

    /**
     * Compares string values from two maps based on the field specified in `sortByField`.
     *
     * @param map1
     *     The first map containing the string value.
     * @param map2
     *     The second map containing the string value.
     * @return A negative integer, zero, or a positive integer if the string in `map1` is less than, equal to,
     *     or greater than the string in `map2`, respectively.
     */
    private int getStringCompare(Map<String, Object> map1, Map<String, Object> map2) {
      var val1 = map1.get(sortByField) != null ? map1.get(sortByField).toString() : StringUtils.EMPTY;
      var val2 = map2.get(sortByField) != null ? map2.get(sortByField).toString() : StringUtils.EMPTY;
      return val1.compareTo(val2);
    }

  }

  private static class DependencySelectedFilters {

    private String group;
    private String artifact;
    private String version;

    DependencySelectedFilters() {
      group = null;
      artifact = null;
      version = null;
    }

    /**
     * Returns the group filter value.
     *
     * @return The group filter value, or null if not set.
     */
    public String getGroup() {
      return group;
    }

    /**
     * Sets the group filter value.
     *
     * @param group
     *     The group filter value to set.
     */
    public void setGroup(String group) {
      this.group = group;
    }

    /**
     * Returns the artifact filter value.
     *
     * @return The artifact filter value, or null if not set.
     */
    public String getArtifact() {
      return artifact;
    }

    /**
     * Sets the artifact filter value.
     *
     * @param artifact
     *     The artifact filter value to set.
     */
    public void setArtifact(String artifact) {
      this.artifact = artifact;
    }

    /**
     * Returns the version filter value.
     *
     * @return The version filter value, or null if not set.
     */
    public String getVersion() {
      return version;
    }

    /**
     * Sets the version filter value.
     *
     * @param version
     *     The version filter value to set.
     */
    public void setVersion(String version) {
      this.version = version;
    }
  }

}