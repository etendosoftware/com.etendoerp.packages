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
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.actions.InstallDependency;
import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;
import com.etendoerp.dependencymanager.util.DependencyUtil;
import com.etendoerp.dependencymanager.util.PackageUtil;

public class AddDependency extends BaseActionHandler {

  public static final String NEED_FLUSH = "needFlush";
  private static final String SEVERITY = "severity";
  private static final String TITLE = "title";
  private static final String SUCCESS = "success";
  private static final String WARNING = "warning";
  private static final String ERROR = "error";
  private static final String MESSAGE = "message";
  private static final String MESSAGE_TYPE = "msgType";
  private static final String MESSAGE_TEXT = "msgText";
  private static final String MESSAGE_TITLE = "msgTitle";
  private static final String WAIT = "wait";
  private static final String DEPENDENCY_MANAGER_TABID = "0A5D3E90916C40C2B712785CC5B113BF";
  private static final Logger log = LogManager.getLogger();

  /**
   * Gets the dependencies of the specified package.
   *
   * <p>If the package is not a bundle, a dependency tree is created. If it is a bundle,
   * the dependencies are taken from the provided JSON, specifically from "_params" and "grid".</p>
   *
   * @param packageVersion
   *     The version of the package to get dependencies for.
   * @param jsonContent
   *     The JSON with the details to build dependencies, used when the package is a bundle.
   * @return A list of dependencies for the package.
   * @throws JSONException
   *     If the JSON does not have the necessary keys when the package is a bundle.
   */
  private static List<PackageDependency> getPackageDependencies(PackageVersion packageVersion,
      JSONObject jsonContent) throws JSONException {
    boolean isBundle = packageVersion.getPackage().isBundle();
    log.debug("Getting dependencies for packageVersion: %s", packageVersion.getPackage().getIdentifier());
    if (!isBundle) {
      log.debug("Non-bundle package, creating dependency tree");
      return DependencyTreeBuilder.createDependencyTree(packageVersion);
    }

    JSONObject grid = jsonContent.optJSONObject("_params").optJSONObject("grid");
    if (grid == null) {
      log.error("Missing 'grid' in parameters");
      throw new JSONException(OBMessageUtils.messageBD("ETDEP_Missing_Grid_Key_Params"));
    }

    JSONArray paramsSelect = grid.optJSONArray("_selection");
    if (paramsSelect == null) {
      log.error("Missing 'selection' in grid");
      throw new JSONException(OBMessageUtils.messageBD("ETDEP_Missing_Selection_Key_Grid"));
    }

    if (paramsSelect.length() == 0) {
      log.debug("No selection found, returning empty list");
      return new ArrayList<>();
    }

    log.debug("Adding dependencies from selection");
    return DependencyTreeBuilder.addDependenciesFromParams(paramsSelect);
  }

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    JSONObject result = new JSONObject();
    try {
      JSONObject jsonContent = new JSONObject(data);
      final String packageVersionId = jsonContent.getString("Etdep_Package_Version_ID");
      PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, packageVersionId);

      if (packageVersion == null) {
        return createErrorResponse(result, "Package version not found");
      }

      String message = String.format(OBMessageUtils.messageBD("ETDEP_Not_Dependencies"),
          packageVersion.getPackage().getIdentifier(), packageVersion.getVersion());
      log.debug("Adding dependencies for package %s in version %s", packageVersion.getPackage().getIdentifier(),
          packageVersion.getIdentifier());

      JSONObject processResult = processDependencies(packageVersion, jsonContent);
      if (processResult.getBoolean(ERROR)) {
        return createErrorResponse(result, processResult.getString(MESSAGE));
      }

      if (processResult.getBoolean(NEED_FLUSH)) {
        OBDal.getInstance().flush();
        message = String.format(OBMessageUtils.messageBD("ETDEP_Added_Updated_Dependencies"),
            packageVersion.getPackage().getIdentifier(), packageVersion.getVersion());
        log.debug("Dependencies added successfully");
      }

      return createSuccessResponse(result, message);

    } catch (Exception e) {
      try {
        JSONObject message = new JSONObject();
        message.put(SEVERITY, ERROR);
        message.put(TITLE, "Error");
        result.put(MESSAGE, message);
      } catch (JSONException ignore) {
        log.error(ignore.getMessage());
      }
    }
    return result;
  }

  /**
   * Creates a JSON object containing a success response with the specified message.
   * <p>
   * This method adds an array of response actions to the provided JSON result object,
   * including the success message. It returns the updated JSON object.
   *
   * @param result
   *     The JSON object to which the response actions will be added.
   * @param message
   *     The success message to be included in the response actions.
   * @return The updated JSON object containing the success response actions.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object.
   */
  private JSONObject createSuccessResponse(JSONObject result, String message) throws JSONException {
    JSONArray actions = createResponseActions(message, SUCCESS);
    result.put("responseActions", actions);
    return result;
  }

  /**
   * Processes the dependencies of the given package version and returns a JSON object with the result.
   * <p>
   * This method retrieves the list of dependencies for the specified package version, processes each one,
   * and checks for errors or the need to flush data. It also handles the package itself as a dependency
   * if it is not part of a bundle. The method returns a success object if no errors are found, or an error
   * object if any dependency processing fails.
   *
   * @param packageVersion
   *     The package version whose dependencies will be processed.
   * @param jsonContent
   *     The JSON content containing information related to the dependencies.
   * @return A JSON object representing the result of processing the dependencies. It includes whether
   *     data needs to be flushed or any error encountered during processing.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object.
   */
  private JSONObject processDependencies(PackageVersion packageVersion, JSONObject jsonContent) throws JSONException {
    List<PackageDependency> dependencyList = getPackageDependencies(packageVersion, jsonContent);
    boolean needFlush = false;
    for (PackageDependency packageDependency : dependencyList) {
      JSONObject dependencyResult = processDependency(packageVersion, packageDependency);
      if (dependencyResult.getBoolean(ERROR)) {
        return createErrorObject(dependencyResult.getString(MESSAGE));
      }
      needFlush |= dependencyResult.getBoolean(NEED_FLUSH);
    }

    boolean isBundle = packageVersion.getPackage().isBundle();
    if (!isBundle) {
      JSONObject selfDependencyResult = processSelfAsDependency(packageVersion);
      if (selfDependencyResult.getBoolean(ERROR)) {
        return createErrorObject(selfDependencyResult.getString(MESSAGE));
      }
      needFlush |= selfDependencyResult.getBoolean(NEED_FLUSH);
    }

    return createSuccessObject(needFlush);
  }

  /**
   * Creates a JSON object representing a successful operation.
   * <p>
   * This method generates a JSON object containing a success flag (`ERROR` set to false)
   * and an indicator of whether a flush is needed (`NEED_FLUSH`).
   *
   * @param needFlush
   *     A boolean indicating whether data needs to be flushed.
   * @return A JSON object containing the success status and flush requirement.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object.
   */
  private JSONObject createSuccessObject(boolean needFlush) throws JSONException {
    JSONObject result = new JSONObject();
    result.put(ERROR, false);
    result.put(NEED_FLUSH, needFlush);
    return result;
  }

  /**
   * Creates a JSON object representing an error.
   * <p>
   * This method generates a JSON object containing an error flag (`ERROR` set to true)
   * and an error message provided by the caller.
   *
   * @param message
   *     The error message to be included in the JSON object.
   * @return A JSON object containing the error status and the provided error message.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object.
   */
  private JSONObject createErrorObject(String message) throws JSONException {
    JSONObject result = new JSONObject();
    result.put(ERROR, true);
    result.put(MESSAGE, message);
    return result;
  }

  /**
   * Creates a JSON object containing an error response with the specified message.
   * <p>
   * This method adds an array of response actions to the provided JSON result object,
   * including the error message. It returns the updated JSON object.
   *
   * @param result
   *     The JSON object to which the error response actions will be added.
   * @param message
   *     The error message to be included in the response actions.
   * @return The updated JSON object containing the error response actions.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object.
   */
  private JSONObject createErrorResponse(JSONObject result, String message) throws JSONException {
    JSONArray actions = createResponseActions(message, ERROR);
    result.put("responseActions", actions);
    return result;
  }

  /**
   * Processes the current package version as a dependency of itself.
   * <p>
   * This method creates a `PackageDependency` object representing the package version itself,
   * sets the necessary properties (group, artifact, version, and external dependency flag),
   * and then processes it as a dependency.
   *
   * @param packageVersion
   *     The current package version to be processed as its own dependency.
   * @return A JSON object representing the result of processing the self-dependency.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object during the processing.
   */
  private JSONObject processSelfAsDependency(PackageVersion packageVersion) throws JSONException {
    PackageDependency selfDependency = new PackageDependency();
    selfDependency.setGroup(packageVersion.getPackage().getGroup());
    selfDependency.setArtifact(packageVersion.getPackage().getArtifact());
    selfDependency.setVersion(packageVersion.getVersion());
    selfDependency.setExternalDependency(false);

    return processDependency(packageVersion, selfDependency);
  }

  /**
   * Processes a given dependency for the specified package version.
   * <p>
   * This method checks whether the specified dependency is already installed or if an existing dependency needs
   * to be updated. If the dependency is not installed, a new dependency is created and saved. If an existing
   * dependency is found, it is updated if necessary. The method also handles error cases such as missing declared
   * dependencies or version conflicts.
   *
   * @param packageVersion
   *     The current version of the package for which the dependency is being processed.
   * @param packageDependency
   *     The dependency to be processed, which includes details like group, artifact, version,
   *     and whether it is an external dependency.
   * @return A JSON object containing the result of processing the dependency. This includes whether a flush is needed
   *     or if an error occurred, along with a relevant error message.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON object.
   */
  private JSONObject processDependency(PackageVersion packageVersion,
      PackageDependency packageDependency) throws JSONException {
    JSONObject result = new JSONObject();
    result.put(NEED_FLUSH, false);
    result.put(ERROR, false);

    String group = packageDependency.getGroup();
    String artifact = packageDependency.getArtifact();
    Module installedModule = DependencyUtil.getInstalledModule(group, artifact);
    Dependency existingDependency = DependencyUtil.getInstalledDependency(group, artifact,
        packageDependency.isExternalDependency());

    if (installedModule == null) {
      if (existingDependency == null) {
        Dependency newDependency = createNewDependency(packageDependency, packageVersion);
        OBDal.getInstance().save(newDependency);
        result.put(NEED_FLUSH, true);
      } else {
        updateExistingDependency(existingDependency, packageDependency, packageVersion);
        result.put(NEED_FLUSH, true);
      }
    } else {
      if (existingDependency == null) {
        result.put(ERROR, true);
        String message = String.format(OBMessageUtils.messageBD("ETDEP_Missing_Declared_Dependency"),
            group + "." + artifact);
        result.put(MESSAGE, message);
      } else {
        if (PackageUtil.compareVersions(packageDependency.getVersion(), installedModule.getVersion()) >= 0) {
          updateExistingDependency(existingDependency, packageDependency, packageVersion);
          result.put(NEED_FLUSH, true);
        } else {
          result.put(ERROR, true);
          String message = String.format(OBMessageUtils.messageBD("ETDEP_Version_Conflict"), group + "." + artifact,
              packageDependency.getVersion(), packageDependency.getVersion());
          result.put(MESSAGE, message);
        }
      }
    }
    return result;
  }

  /**
   * Creates a new dependency object based on the provided package dependency and package version.
   * <p>
   * This method initializes a new `Dependency` object, setting its version, group, artifact,
   * installation status, format (either JAR or source), and external dependency flag based on the
   * details from the provided `PackageDependency`. It also updates the version status of the new
   * dependency using the provided package version.
   *
   * @param packageDependency
   *     The `PackageDependency` object containing the details of the dependency
   *     to be created, such as version, group, artifact, and whether it is an external dependency.
   * @param packageVersion
   *     The `PackageVersion` object representing the version of the package for which the dependency is being created.
   * @return A new `Dependency` object initialized with the details from the package dependency and package version.
   */
  private Dependency createNewDependency(PackageDependency packageDependency, PackageVersion packageVersion) {
    Dependency dependency = new Dependency();
    dependency.setVersion(packageDependency.getVersion());
    dependency.setGroup(packageDependency.getGroup());
    dependency.setArtifact(packageDependency.getArtifact());
    dependency.setInstallationStatus(DependencyUtil.STATUS_PENDING);
    dependency.setFormat(
        packageDependency.isExternalDependency().booleanValue() ? DependencyUtil.FORMAT_JAR : DependencyUtil.FORMAT_SOURCE);
    dependency.setExternalDependency(packageDependency.isExternalDependency());
    updateVersionStatus(dependency, packageDependency, packageVersion);
    return dependency;
  }

  /**
   * Updates the version status of the given dependency based on its package dependency and package version.
   * <p>
   * This method sets the version status of the `Dependency` object depending on whether the dependency
   * is external or internal. For external dependencies, it sets the status to "untracked." For internal
   * dependencies, it checks if the package is a bundle, retrieves the latest package version, and determines
   * the appropriate version status by comparing the dependency's version with the latest version.
   *
   * @param dependency
   *     The `Dependency` object whose version status is being updated.
   * @param packageDependency
   *     The `PackageDependency` object providing the details of the dependency.
   * @param packageVersion
   *     The `PackageVersion` object representing the current version of the package being processed.
   */
  private void updateVersionStatus(Dependency dependency, PackageDependency packageDependency,
      PackageVersion packageVersion) {
    PackageVersion latestPackageVersion;
    if (packageDependency.isExternalDependency().booleanValue()) {
      dependency.setVersionStatus(DependencyUtil.UNTRACKED_STATUS);
    } else {
      if (DependencyTreeBuilder.isBundle(packageDependency)) {
        latestPackageVersion = PackageUtil.getLastPackageVersion(packageDependency.getPackageVersion().getPackage());
      } else {
        latestPackageVersion = PackageUtil.getLastPackageVersion(packageVersion.getPackage());
      }

      String versionStatus = InstallDependency.determineVersionStatus(dependency.getVersion(),
          latestPackageVersion.getVersion());
      dependency.setVersionStatus(versionStatus);
    }
  }

  /**
   * Updates the details of an existing dependency based on the provided package dependency and package version.
   * <p>
   * This method modifies the version and installation status of the existing `Dependency` object,
   * setting its version to that of the provided `PackageDependency` and updating its installation status
   * to pending. It also updates the version status by comparing it with the latest version information.
   * Finally, it saves the updated dependency object to the database.
   *
   * @param existingDependency
   *     The `Dependency` object to be updated.
   * @param packageDependency
   *     The `PackageDependency` object containing the new details for the dependency.
   * @param packageVersion
   *     The `PackageVersion` object representing the current version of the package for reference.
   */
  private void updateExistingDependency(Dependency existingDependency, PackageDependency packageDependency,
      PackageVersion packageVersion) {
    existingDependency.setVersion(packageDependency.getVersion());
    existingDependency.setInstallationStatus(DependencyUtil.STATUS_PENDING);
    updateVersionStatus(existingDependency, packageDependency, packageVersion);
    OBDal.getInstance().save(existingDependency);
  }

  /**
   * Creates a JSON array of response actions based on the provided message and message type.
   * <p>
   * This method constructs a series of actions to be performed in response to a process,
   * including displaying messages in the process view and, if the message type indicates
   * success, opening a new tab and displaying additional success messages.
   *
   * @param message
   *     The message text to be displayed in the response actions.
   * @param messageType
   *     The type of message (e.g., success, error) that determines the actions to create.
   * @return A JSON array containing the defined response actions, including messages and tab management.
   * @throws JSONException
   *     If there is an error while creating or modifying the JSON objects or array.
   */
  private JSONArray createResponseActions(String message, String messageType) throws JSONException {
    JSONArray actions = new JSONArray();

    // Message in tab from where the process is executed
    String titleMessage = getTitleForMessageType(messageType);
    JSONObject showMsgInProcessView = new JSONObject();
    showMsgInProcessView.put(MESSAGE_TYPE, messageType);
    showMsgInProcessView.put(MESSAGE_TITLE, titleMessage);
    showMsgInProcessView.put(MESSAGE_TEXT, message);
    showMsgInProcessView.put(WAIT, false);

    JSONObject showMsgInProcessViewAction = new JSONObject();
    showMsgInProcessViewAction.put("showMsgInProcessView", showMsgInProcessView);

    actions.put(showMsgInProcessViewAction);

    if (StringUtils.equals(SUCCESS, messageType)) {
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
      showMsgInView.put(MESSAGE_TYPE, messageType);
      showMsgInView.put(MESSAGE_TITLE, titleMessage);
      showMsgInView.put(MESSAGE_TEXT, message);

      JSONObject showMsgInViewAction = new JSONObject();
      showMsgInViewAction.put("showMsgInView", showMsgInView);

      actions.put(showMsgInViewAction);
    }
    return actions;
  }

  /**
   * Returns a title based on the provided message type.
   *
   * @param messageType
   *     the type of the message (e.g., SUCCESS, WARNING, ERROR)
   * @return a String representing the title for the specified message type.
   *     Returns "Success" for SUCCESS, "Warning" for WARNING,
   *     "Error" for ERROR, and "Message" for any other type.
   */
  private String getTitleForMessageType(String messageType) {
    switch (messageType) {
      case SUCCESS:
        return "Success";
      case WARNING:
        return "Warning";
      case ERROR:
        return "Error";
      default:
        return "Message";
    }
  }
}
