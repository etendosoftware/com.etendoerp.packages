/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License.
 * The Original Code is Openbravo ERP.
 * The Initial Developer of the Original Code is Openbravo SLU
 * All portions are Copyright (C) 2015 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package com.etendoerp.dependencymanager.process;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.PackageUtil;

public class AddDependency extends BaseActionHandler {

  public static final String SEVERITY = "severity";
  public static final String TITLE = "title";
  public static final String MESSAGE = "message";
  public static final String DEFAULT_INSTALLATION_STATUS = "PENDING";
  public static final String JAR_FORTMAT = "J";
  public static final String SOURCE_FORTMAT = "S";
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    JSONObject jsonContent;
    JSONObject errorMessage = new JSONObject();
    try {
      jsonContent = new JSONObject(data);
      final String packageVersionId = jsonContent.getString("Etdep_Package_Version_ID");
      PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, packageVersionId);
      if (packageVersion != null) {
        OBCriteria<PackageDependency> packageDependencyCriteria = OBDal.getInstance().createCriteria(
            PackageDependency.class);
        packageDependencyCriteria.add(Restrictions.eq(PackageDependency.PROPERTY_PACKAGEVERSION, packageVersion));
        packageDependencyCriteria.add(Restrictions.ne(PackageDependency.PROPERTY_ARTIFACT, PackageUtil.ETENDO_CORE));
        List<PackageDependency> dependencyList = packageDependencyCriteria.list();

        boolean needFlush = false;
        for (PackageDependency packageDependency : dependencyList) {
          Dependency dependency = new Dependency();
          dependency.setVersion(packageDependency.getVersion());
          dependency.setGroup(packageDependency.getGroup());
          dependency.setArtifact(packageDependency.getArtifact());
          dependency.setInstallationStatus(DEFAULT_INSTALLATION_STATUS);
          if (packageDependency.isExternalDependency().booleanValue()) {
            dependency.setFormat(JAR_FORTMAT);
            dependency.setExternalDependency(true);
          } else {
            dependency.setFormat(SOURCE_FORTMAT);
          }
          needFlush = true;
          OBDal.getInstance().save(dependency);
        }
        if (needFlush) {
          OBDal.getInstance().flush();
        }
      }
      log.debug("Installation successful");
      JSONObject message = new JSONObject();
      message.put(SEVERITY, "success");
      message.put(TITLE, "Success");
      errorMessage.put(MESSAGE, message);
      OBDal.getInstance().flush();
      errorMessage.put("refreshParent", true);
    } catch (JSONException jsone) {
      try {
        JSONObject message = new JSONObject();
        message.put(SEVERITY, "error");
        message.put(TITLE, "Error");
        errorMessage.put(MESSAGE, message);
      } catch (JSONException ignore) {
        log.error(jsone);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return errorMessage;
  }
}
