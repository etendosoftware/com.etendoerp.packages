package com.etendoerp.dependencymanager.process;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.service.db.DbUtility;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.util.DependencyUtil;

public class AddLocalDependencies extends BaseActionHandler {
  private static final List<String> MODULES_CORE_JAVAPACKAGES = List.of(
      "com.etendoerp.advpaymentmngt",
      "com.etendoerp.legacy.advancedpaymentmngt",
      "com.etendoerp.reportvaluationstock",
      "com.smf.jobs",
      "com.smf.jobs.defaults",
      "com.smf.mobile.utils",
      "com.smf.securewebservices",
      "com.smf.smartclient.boostedui",
      "com.smf.smartclient.debugtools",
      "com.smf.userinterface.skin.legacy",
      "org.openbravo.advpaymentmngt",
      "org.openbravo.apachejdbcconnectionpool",
      "org.openbravo.base.weld",
      "org.openbravo.client.application",
      "org.openbravo.client.htmlwidget",
      "org.openbravo.client.kernel",
      "org.openbravo.client.myob",
      "org.openbravo.client.querylist",
      "org.openbravo.client.widgets",
      "org.openbravo.financial.paymentreport",
      "org.openbravo.reports.ordersawaitingdelivery",
      "org.openbravo.service.datasource",
      "org.openbravo.service.integration.google",
      "org.openbravo.service.integration.openid",
      "org.openbravo.service.json",
      "org.openbravo.userinterface.selector",
      "org.openbravo.userinterface.skin.250to300Comp",
      "org.openbravo.userinterface.smartclient",
      "org.openbravo.utility.cleanup.log",
      "org.openbravo.v3",
      "org.openbravo.v3.datasets",
      "org.openbravo.v3.framework"
  );

  private static final Logger log = LogManager.getLogger();

  /**
   * Executes a process to add Local modules to the ETDEP_Dependency table.
   * This method searches for modules whose javapackage is not already loaded in the ETDEP_Dependency table
   * or located in the modules_core folder, and excluding the Core module. It then adds the modules to the
   * ETDEP_Dependency table with the 'Installed' status and the 'Local' format.
   *
   * @param parameters
   *     A map of parameters for the operation.
   * @param content
   *     The content to be processed.
   * @return A JSON object containing the result of the process execution.
   */
  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject jsonMessage = new JSONObject();
    try {
      OBContext.setAdminMode(true);

      // Get all dependencies currently loaded
      OBCriteria<Dependency> dependencyCriteria = OBDal.getInstance().createCriteria(Dependency.class);
      List<Dependency> depsList = dependencyCriteria.list();
      List<String> javaPkgsToNotAdd = depsList.stream().map(
              dep -> dep.getGroup() + "." + dep.getArtifact()) // Join group and artifact to get each dependency name
          .collect(Collectors.toList());

      // Combine dependency javapackages list with modules_core javapackages list
      javaPkgsToNotAdd.addAll(MODULES_CORE_JAVAPACKAGES);

      // Search for every module
      OBCriteria<Module> moduleCriteria = OBDal.getInstance().createCriteria(Module.class);
      // Whose javapackage is not already loaded in the ETDEP_Dependency table or located in the modules_core folder
      moduleCriteria.add(Restrictions.not(Restrictions.in(Module.PROPERTY_JAVAPACKAGE, javaPkgsToNotAdd)));
      // And is not the Core module
      moduleCriteria.add(Restrictions.ne(Module.PROPERTY_NAME, "Core"));

      List<Module> modulesToAdd = moduleCriteria.list();

      // Add each one of the obtained modules to de ETDEP_Dependency table, with the
      // 'Installed' status and the 'Local' format
      for (Module module : modulesToAdd) {
        // Get the module's group and artifact
        String[] packageParts = module.getJavaPackage().split("\\.");
        String group = packageParts[0] + "." + packageParts[1];
        String artifact = StringUtils.join(Arrays.copyOfRange(packageParts, 2, packageParts.length), ".");

        Dependency dependency = OBProvider.getInstance().get(Dependency.class);
        dependency.setGroup(group);
        dependency.setArtifact(artifact);
        dependency.setVersion(module.getVersion());
        dependency.setInstallationStatus(DependencyUtil.STATUS_INSTALLED);
        dependency.setFormat(DependencyUtil.FORMAT_LOCAL);
        dependency.setInstalledModule(module);
        OBDal.getInstance().save(dependency);
      }

      OBDal.getInstance().flush();
      JSONObject message = new JSONObject();
      message.put("severity", "success");
      message.put("title", "Success");
      message.put("text",
          String.format(OBMessageUtils.messageBD("ETDEP_Deps_Successfully_Added"), modulesToAdd.size()));
      jsonMessage.put("message", message);
      return jsonMessage;
    } catch (JSONException e) {
      JSONObject message = new JSONObject();
      try {
        message.put("severity", "error");
        message.put("title", "Error");
        message.put("text",
            OBMessageUtils.messageBD("ProcessRunError") + DbUtility.getUnderlyingSQLException(e).getMessage());
        jsonMessage.put("message", message);
        return jsonMessage;
      } catch (JSONException e2) {
        log.debug(e2);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return jsonMessage;
  }
}
