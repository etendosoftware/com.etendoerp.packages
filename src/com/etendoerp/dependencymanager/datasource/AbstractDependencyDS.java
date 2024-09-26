package com.etendoerp.dependencymanager.datasource;

import java.util.Collections;
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

import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;

public abstract class AbstractDependencyDS extends ReadOnlyDataSourceService {

  /**
   * Retrieves the count of rows in the dataset, based on the given parameters.
   *
   * @param parameters
   *     Map of parameters used to filter or customize the query.
   * @return the total count of rows in the dataset.
   */
  @Override
  protected int getCount(Map<String, String> parameters) {
    return getData(parameters, 0, Integer.MAX_VALUE).size();
  }

  /**
   * Retrieves the data in a paginated format from the dataset, based on the given parameters.
   *
   * @param parameters
   *     Map of parameters used to filter or customize the query.
   * @param startRow
   *     the starting row index.
   * @param endRow
   *     the ending row index.
   * @return a list of maps containing the data from the dataset.
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
   * Abstract method to retrieve the grid data for a given package version.
   *
   * @param parameters
   *     Map of parameters used to filter or customize the query.
   * @param packageVersion
   *     the version of the package for which to retrieve dependencies.
   * @return a list of maps containing the dependency data for the specified package version.
   * @throws JSONException
   *     if there is an error parsing JSON data.
   */
  protected abstract List<Map<String, Object>> getGridData(Map<String, String> parameters,
      PackageVersion packageVersion) throws JSONException;

  /**
   * Applies filtering and sorting to the result set based on the provided parameters and filters.
   *
   * @param parameters
   *     Map of parameters used to filter or customize the query.
   * @param result
   *     List of maps containing the raw dataset.
   * @param selectedFilters
   *     Filters applied to the dataset.
   * @return a filtered and sorted list of maps representing the dataset.
   * @throws JSONException
   *     if there is an error during the filtering process.
   */
  protected List<Map<String, Object>> applyFilterAndSort(Map<String, String> parameters,
      List<Map<String, Object>> result, AbstractSelectedFilters selectedFilters) throws JSONException {
    if (selectedFilters.getArtifact() != null) {
      result = result.stream().filter(
          row -> StringUtils.contains(row.get(DependencyManagerConstants.ARTIFACT).toString(),
              selectedFilters.getArtifact())).collect(Collectors.toList());
    }
    if (selectedFilters.getGroup() != null) {
      result = result.stream().filter(row -> StringUtils.contains(row.get(DependencyManagerConstants.GROUP).toString(),
          selectedFilters.getGroup())).collect(Collectors.toList());
    }
    if (selectedFilters.getVersion() != null) {
      result = result.stream().filter(
          row -> StringUtils.contains(row.get(DependencyManagerConstants.VERSION).toString(),
              selectedFilters.getVersion())).collect(Collectors.toList());
    }
    sortResult(parameters, result);
    return result;
  }

  /**
   * Sorts the result set based on the given parameters.
   *
   * @param parameters
   *     Map of parameters used to define the sorting criteria.
   * @param result
   *     List of maps containing the dataset to be sorted.
   */
  protected void sortResult(Map<String, String> parameters, List<Map<String, Object>> result) {
    String strSortBy = parameters.getOrDefault(DependencyManagerConstants.SORT_BY, DependencyManagerConstants.LINE);
    Collections.sort(result, createResultComparator(strSortBy));
  }

  /**
   * Abstract method to create a comparator for sorting the dataset based on the specified field.
   *
   * @param sortByField
   *     the field to sort by.
   * @return an instance of {@link AbstractResultComparator} to sort the dataset.
   */
  protected abstract AbstractResultComparator createResultComparator(String sortByField);

  /**
   * Reads the criteria from the parameters and creates a filter object to apply filtering to the dataset.
   *
   * @param parameters
   *     Map of parameters used to filter or customize the query.
   * @return an instance of {@link AbstractSelectedFilters} with the applied criteria.
   * @throws JSONException
   *     if there is an error parsing JSON data.
   */
  protected AbstractSelectedFilters readCriteria(Map<String, String> parameters) throws JSONException {
    AbstractSelectedFilters selectedFilters = createSelectedFilters();
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
   * Abstract method to create a filter object for applying filtering to the dataset.
   *
   * @return an instance of {@link AbstractSelectedFilters}.
   */
  protected abstract AbstractSelectedFilters createSelectedFilters();

  /**
   * Adds criteria to the filter object for filtering the dataset.
   *
   * @param selectedFilters
   *     the filter object to which criteria will be added.
   * @param criteria
   *     a JSON object representing the criteria to be added.
   * @throws JSONException
   *     if there is an error parsing the criteria.
   */
  protected void addCriteria(AbstractSelectedFilters selectedFilters, JSONObject criteria) throws JSONException {
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
}