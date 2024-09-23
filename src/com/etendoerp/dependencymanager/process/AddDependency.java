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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.actions.InstallDependency;
import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;
import com.etendoerp.dependencymanager.util.DependencyUtil;
import com.etendoerp.dependencymanager.util.PackageUtil;

public class AddDependency extends BaseActionHandler {

  private static final String SEVERITY = "severity";
  private static final String TITLE = "title";
  private static final String SUCCESS = "success";
  private static final String WARNING = "warning";

  private static final String MESSAGE = "message";
  private static final String MESSAGE_TYPE = "msgType";
  private static final String MESSAGE_TEXT = "msgText";
  private static final String MESSAGE_TITLE = "msgTitle";
  private static final String WAIT = "wait";
  private static final String DEPENDENCY_MANAGER_TABID = "0A5D3E90916C40C2B712785CC5B113BF";
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    JSONObject result = new JSONObject();
    try {
      JSONObject jsonContent = new JSONObject(data);
      final String packageVersionId = jsonContent.getString("Etdep_Package_Version_ID");
      PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, packageVersionId);
      String successMessage = StringUtils.EMPTY;
      String successType = WARNING;
      if (packageVersion != null) {
        successMessage = String.format(
            OBMessageUtils.messageBD("ETDEP_Not_Dependencies"), packageVersion.getPackage().getIdentifier(),
            packageVersion.getVersion());
        log.debug("Adding dependencies for package %s in version %s", packageVersion.getPackage().getIdentifier(),
            packageVersion.getIdentifier());
        List<PackageDependency> dependencyList = getPackageDependencies(packageVersion, jsonContent);
        boolean needFlush = false;
        for (PackageDependency packageDependency : dependencyList) {
          boolean isExternalDependency = packageDependency.isExternalDependency().booleanValue();
          if (DependencyUtil.existsDependency(packageDependency.getGroup(), packageDependency.getArtifact(),
              packageDependency.getVersion(),
              isExternalDependency)) {
            log.debug("Dependency already exists: %s:%s:%s", packageDependency.getGroup(),
                packageDependency.getArtifact(), packageDependency.getVersion());
            continue;
          }
          Dependency dependency = new Dependency();
          dependency.setVersion(packageDependency.getVersion());
          dependency.setGroup(packageDependency.getGroup());
          dependency.setArtifact(packageDependency.getArtifact());
          dependency.setInstallationStatus(DependencyUtil.STATUS_PENDING);
          String versionStatus;
          if (isExternalDependency) {
            dependency.setFormat(DependencyUtil.FORMAT_JAR);
            dependency.setExternalDependency(true);
            versionStatus = DependencyUtil.UNTRACKED_STATUS;
          } else {
            dependency.setFormat(DependencyUtil.FORMAT_SOURCE);
            PackageVersion latestPackageVersion = PackageUtil.getLastPackageVersion(packageDependency.getDependencyVersion().getPackage());
            versionStatus = InstallDependency.determineVersionStatus(packageDependency.getVersion(),
                latestPackageVersion.getVersion());
          }
          dependency.setVersionStatus(versionStatus);
          needFlush = true;
          OBDal.getInstance().save(dependency);
          log.debug("New dependency added: %s", dependency.getIdentifier());
        }

        needFlush |= addVersionToInstall(packageVersion, dependencyList);
        if (needFlush) {
          OBDal.getInstance().flush();
          successMessage = String.format(
              OBMessageUtils.messageBD("ETDEP_Added_Dependencies"), packageVersion.getPackage().getIdentifier(),
              packageVersion.getVersion());
          successType = SUCCESS;
        }
      }
      log.debug("Dependencies added successful");
      JSONArray actions = createSuccessActions(successMessage, successType);
      result.put("responseActions", actions);
    } catch (Exception e) {
      try {
        JSONObject message = new JSONObject();
        message.put(SEVERITY, "error");
        message.put(TITLE, "Error");
        result.put(MESSAGE, message);
      } catch (JSONException ignore) {
        log.error(ignore.getMessage());
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return result;
  }

  /**
   * Gets the dependencies of the specified package.
   *
   * <p>If the package is not a bundle, a dependency tree is created. If it is a bundle,
   * the dependencies are taken from the provided JSON, specifically from "_params" and "grid".</p>
   *
   * @param packageVersion The version of the package to get dependencies for.
   * @param jsonContent The JSON with the details to build dependencies, used when the package is a bundle.
   * @return A list of dependencies for the package.
   * @throws JSONException If the JSON does not have the necessary keys when the package is a bundle.
   */
  private static List<PackageDependency> getPackageDependencies(PackageVersion packageVersion,
      JSONObject jsonContent) throws JSONException {
    boolean isBundle = packageVersion.getPackage().isBundle();
    if (!isBundle) {
      return DependencyTreeBuilder.createDependencyTree(packageVersion);
    }

    JSONObject grid = jsonContent.optJSONObject("_params").optJSONObject("grid");
    if (grid == null) {
      throw new JSONException("Missing 'grid' key in '_params'");
    }

    JSONArray paramsSelect = grid.optJSONArray("_selection");
    if (paramsSelect == null) {
      throw new JSONException("Missing '_selection' key in 'grid'");
    }

    if (paramsSelect.length() == 0) {
      return new ArrayList<>();
    }

    return DependencyTreeBuilder.addDependenciesFromParams(paramsSelect);
  }

  private JSONArray createSuccessActions(String successMessage, String successType) throws JSONException {
    JSONArray actions = new JSONArray();

    // Message in tab from where the process is executed
    String titleSuccessMessage = OBMessageUtils.messageBD("Success");
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put(MESSAGE_TYPE, successType);
    showMsgInProcessView.put(MESSAGE_TITLE, titleSuccessMessage);
    showMsgInProcessView.put(MESSAGE_TEXT, successMessage);
    showMsgInProcessView.put(WAIT, false);

    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);

    actions.put(showMsgInProcessViewAction);

    if (StringUtils.equals(SUCCESS, successType)) {
      // New record info
      JSONObject openDirectTab = new JSONObject();
      openDirectTab.put("tabId", DEPENDENCY_MANAGER_TABID);
      openDirectTab.put("emptyFilterClause", true);
      openDirectTab.put(WAIT, true);

      JSONObject openDirectTabAction = new JSONObject();
      openDirectTabAction.put("openDirectTab", openDirectTab);

      actions.put(openDirectTabAction);

      // Message of the new opened tab
      JSONObject showMsgInView = new JSONObject();
      showMsgInView.put(MESSAGE_TYPE, successType);
      showMsgInView.put(MESSAGE_TITLE, titleSuccessMessage);
      showMsgInView.put(MESSAGE_TEXT, successMessage);

      JSONObject showMsgInViewAction = new JSONObject();
      showMsgInViewAction.put("showMsgInView", showMsgInView);

      actions.put(showMsgInViewAction);
    }
    return actions;
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
   * @param packageVersion
   *     The package version to add.
   * @param dependencyList
   *     The list of dependencies of the package version.
   * @return A boolean indicating whether a flush is needed.
   */
  private boolean addVersionToInstall(PackageVersion packageVersion, List<PackageDependency> dependencyList) {
    boolean needFlush = false;
    boolean isBundle = packageVersion.getPackage() != null && packageVersion.getPackage().isBundle().booleanValue();
    boolean existsDependency = DependencyUtil.existsDependency(packageVersion.getPackage().getGroup(),
        packageVersion.getPackage().getArtifact(),
        packageVersion.getVersion(),
        dependencyList.isEmpty());
    if (!isBundle && !existsDependency) {
      log.debug("The current package %s is a bundle a new dependency with them will be added.",
          packageVersion.getPackage().getIdentifier());
      Dependency dependency = new Dependency();
      dependency.setVersion(packageVersion.getVersion());
      dependency.setGroup(packageVersion.getPackage().getGroup());
      dependency.setArtifact(packageVersion.getPackage().getArtifact());
      dependency.setInstallationStatus(DependencyUtil.STATUS_PENDING);
      dependency.setFormat(DependencyUtil.FORMAT_SOURCE);
      PackageVersion latestPackageVersion = PackageUtil.getLastPackageVersion(packageVersion.getPackage());
      String versionStatus = InstallDependency.determineVersionStatus(packageVersion.getVersion(),
          latestPackageVersion.getVersion());
      dependency.setVersionStatus(versionStatus);
      OBDal.getInstance().save(dependency);
      needFlush = true;
      log.debug("Bundle dependency added: %s", dependency.getIdentifier());
    }
    return needFlush;
  }
}