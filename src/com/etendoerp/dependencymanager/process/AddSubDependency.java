package com.etendoerp.dependencymanager.process;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.util.DependencyManagerConstants;

public class AddSubDependency extends BaseActionHandler {

  private static final String ADVANCED_CRITERIA = "AdvancedCriteria";
  private static final String OPERATOR = "operator";
  private static final String I_EQUALS = "iEquals";
  private static final String DEPENDENCY_ID = "dependencyId";
  private static final String AND = "and";
  private static final String OR = "or";

  /**
   * Executes the given parameters and content, creating an advanced criteria filter based on the provided
   * dependencies in the JSON content.
   *
   * @param parameters A map of parameters needed for execution.
   * @param content A string containing the JSON content which includes the dependency IDs.
   * @return A JSON object with the constructed filter based on the dependencies.
   * @throws OBException If an error occurs during the processing.
   */
  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    try {
      JSONObject result = new JSONObject();
      final JSONObject jsonData = new JSONObject(content);

      if (!jsonData.has(DEPENDENCY_ID)) {
        return result;
      }

      final JSONArray dependenciesList = jsonData.getJSONArray(DEPENDENCY_ID);

      if (dependenciesList.length() == 0) {
        return result;
      }

      final List<PackageDependency> dependencyList = new ArrayList<>();
      for (int i = 0; i < dependenciesList.length(); i++) {
        PackageDependency dependency = OBDal.getInstance().get(PackageDependency.class, dependenciesList.get(i));
        dependencyList.add(dependency);
      }

      JSONObject advancedCriteria = new JSONObject();
      advancedCriteria.put(OPERATOR, AND);
      advancedCriteria.put(DependencyManagerConstants.CONSTRUCTOR, ADVANCED_CRITERIA);

      JSONArray criteriaArray = createCriteria(dependencyList);

      advancedCriteria.put(DependencyManagerConstants.CRITERIA, criteriaArray);

      result.put("filter", advancedCriteria);
      return result;
    } catch (Exception e) {
      throw new OBException(e);
    }
  }

  /**
   * Creates a JSONArray of criteria based on the provided list of package dependencies.
   *
   * @param dependencyList The list of package dependencies to create criteria for.
   * @return A JSONArray containing the criteria for the dependencies.
   * @throws JSONException If an error occurs during the JSON processing.
   */
  private static JSONArray createCriteria(List<PackageDependency> dependencyList) throws JSONException {
    JSONArray criteriaArray = new JSONArray();

    if (dependencyList.size() == 1) {
      JSONObject criteriaItem = getOneCriteria(dependencyList);

      criteriaArray.put(criteriaItem);
    } else {
      JSONObject orCriteria = getOrCriteria(dependencyList);

      criteriaArray.put(orCriteria);
    }
    return criteriaArray;
  }

  /**
   * Creates criteria for a single dependency.
   *
   * @param dependencyList The list containing one package dependency.
   * @return A JSON object representing the criteria for the single dependency.
   * @throws JSONException If an error occurs during the JSON processing.
   */
  private static JSONObject getOneCriteria(List<PackageDependency> dependencyList) throws JSONException {
    String value = dependencyList.get(0).getId();

    JSONObject criteriaItem = new JSONObject();
    criteriaItem.put(DependencyManagerConstants.FIELD_NAME, DependencyManagerConstants.PARENT);
    criteriaItem.put(OPERATOR, I_EQUALS);
    criteriaItem.put(DependencyManagerConstants.VALUE, value);
    criteriaItem.put(DependencyManagerConstants.CONSTRUCTOR, ADVANCED_CRITERIA);
    return criteriaItem;
  }

  /**
   * Creates OR criteria for multiple dependencies.
   *
   * @param dependencyList The list of package dependencies.
   * @return A JSON object representing the OR criteria for the multiple dependencies.
   * @throws JSONException If an error occurs during the JSON processing.
   */
  private static JSONObject getOrCriteria(List<PackageDependency> dependencyList) throws JSONException {
    JSONArray orCriteriaArray = new JSONArray();

    for (PackageDependency dependency : dependencyList) {
      String value = dependency.getId();

      JSONObject criteriaItem = new JSONObject();
      criteriaItem.put(DependencyManagerConstants.FIELD_NAME, DependencyManagerConstants.PARENT);
      criteriaItem.put(OPERATOR, I_EQUALS);
      criteriaItem.put(DependencyManagerConstants.VALUE, value);

      orCriteriaArray.put(criteriaItem);
    }

    JSONObject orCriteria = new JSONObject();
    orCriteria.put(OPERATOR, OR);
    orCriteria.put(DependencyManagerConstants.CRITERIA, orCriteriaArray);
    orCriteria.put(DependencyManagerConstants.CONSTRUCTOR, ADVANCED_CRITERIA);
    return orCriteria;
  }
}