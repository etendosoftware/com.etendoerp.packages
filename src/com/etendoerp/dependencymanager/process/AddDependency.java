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

import com.etendoerp.dependencymanager.actions.InstallDependency;
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
          PackageVersion latestPackageVersion = PackageUtil.getLastPackageVersion(packageVersion.getPackage());
          String versionStatus = InstallDependency.determineVersionStatus(packageDependency.getVersion(), latestPackageVersion.getVersion());
          dependency.setVersionStatus(versionStatus);
          needFlush = true;
          OBDal.getInstance().save(dependency);
        }

        needFlush |= addVersionToIntall(packageVersion, dependencyList);
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
    } catch (JSONException json) {
      try {
        JSONObject message = new JSONObject();
        message.put(SEVERITY, "error");
        message.put(TITLE, "Error");
        errorMessage.put(MESSAGE, message);
      } catch (JSONException ignore) {
        log.error(json);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return errorMessage;
  }

  /**
   * This method is used to add a version to install.
   * It creates a new Dependency object and sets its properties based on the provided PackageVersion and DependencyList.
   * If the DependencyList is empty, the format of the dependency is set to JAR_FORMAT and it is marked as an external dependency.
   * Otherwise, the format is set to SOURCE_FORMAT.
   * The method then retrieves the latest PackageVersion of the package, determines the version status, and sets it on the dependency.
   * The dependency is then saved using OBDal's save method.
   * If the package version's package is not null and is not a bundle, the method returns true indicating that a flush is needed.
   * Otherwise, it returns false.
   *
   * @param packageVersion The package version to add.
   * @param dependencyList The list of dependencies of the package version.
   * @return A boolean indicating whether a flush is needed.
   */
  private boolean addVersionToIntall(PackageVersion packageVersion, List<PackageDependency> dependencyList) {
    boolean needFlush = false;
    if (packageVersion.getPackage() != null && !packageVersion.getPackage().isBundle().booleanValue()) {
      Dependency dependency = new Dependency();
      dependency.setVersion(packageVersion.getVersion());
      dependency.setGroup(packageVersion.getPackage().getGroup());
      dependency.setArtifact(packageVersion.getPackage().getArtifact());
      dependency.setInstallationStatus(DEFAULT_INSTALLATION_STATUS);
      if (dependencyList.isEmpty()) {
        dependency.setFormat(JAR_FORTMAT);
        dependency.setExternalDependency(true);
      } else {
        dependency.setFormat(SOURCE_FORTMAT);
      }
      PackageVersion latestPackageVersion = PackageUtil.getLastPackageVersion(packageVersion.getPackage());
      String versionStatus = InstallDependency.determineVersionStatus(packageVersion.getVersion(), latestPackageVersion.getVersion());
      dependency.setVersionStatus(versionStatus);
      OBDal.getInstance().save(dependency);
      needFlush = true;
    }
    return needFlush;
  }
}
