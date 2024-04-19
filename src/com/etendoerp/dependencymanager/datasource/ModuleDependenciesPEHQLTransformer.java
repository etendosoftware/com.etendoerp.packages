/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2018-2020 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package com.etendoerp.dependencymanager.datasource;

import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.openbravo.client.kernel.ComponentProvider;
import org.openbravo.service.datasource.hql.HqlQueryTransformer;

@ComponentProvider.Qualifier("FE1C3952DDBB4B3D8F048A11A603A9D7")
public class ModuleDependenciesPEHQLTransformer extends HqlQueryTransformer {

  @Override
  public String transformHqlQuery(String _hqlQuery, Map<String, String> requestParameters,
      Map<String, Object> queryNamedParameters) {

    final String strETDEPPackageVersionId = requestParameters.get("@ETDEP_Package_Version.id@");
    queryNamedParameters.put("packageVersionId", strETDEPPackageVersionId);

    String transformedHql = _hqlQuery.replace("@selectClause@", getSelectClauseHQL());
    transformedHql = transformedHql.replace("@fromClause@", getFromClauseHQL());
    transformedHql = transformedHql.replace("@whereClause@", getWhereClauseHQL());
    transformedHql = transformedHql.replace("@groupByClause@", getGroupByHQL());
    transformedHql = transformedHql.replace("@orderByClause@", getOrderByHQL());
    return transformedHql;
  }

  protected String getSelectClauseHQL() {
    return StringUtils.EMPTY;
  }

  protected String getFromClauseHQL() {
    StringBuilder fromClause = new StringBuilder();
    fromClause.append(" ETDEP_Package_Dependency e");
    return fromClause.toString();
  }

  protected String getWhereClauseHQL() {
    StringBuilder whereClause = new StringBuilder();
    whereClause.append(" AND e.packageVersion.id = :packageVersionId");
    whereClause.append(" AND e.group <> 'com.etendoerp.platform'");
    whereClause.append(" AND e.artifact <> 'etendo-core'");
    return whereClause.toString();
  }

  protected String getGroupByHQL() {
    StringBuilder groupByClause = new StringBuilder();
    groupByClause.append(" e.id");
    return groupByClause.toString();
  }

  protected String getOrderByHQL() {
    return " e.group DESC, e.artifact DESC";
  }

}
