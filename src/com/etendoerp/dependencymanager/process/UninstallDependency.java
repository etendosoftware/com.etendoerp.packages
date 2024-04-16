package com.etendoerp.dependencymanager.process;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.data.Dependency;

public class UninstallDependency extends BaseActionHandler {
  public static final String SEVERITY = "severity";
  public static final String TITLE = "title";
  public static final String MESSAGE = "message";
  private static final Logger log = LogManager.getLogger();

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String content) {
    JSONObject jsonContent;
    JSONObject errorMessage = new JSONObject();
    try {
      jsonContent = new JSONObject(content);
      String dependencyName = jsonContent.getString("inpdepgroup") + "."
          + jsonContent.getString("inpartifact");

      log.debug("Uninstalling dependency: {}...", dependencyName);
      OBContext.setAdminMode(true);

      log.debug("Deleting dependency from database...");
      Dependency dependency = OBDal.getInstance().get(Dependency.class, jsonContent.get("inpetdepDependencyId"));
      OBDal.getInstance().remove(dependency);

      log.debug("Uninstallation successful");
      JSONObject message = new JSONObject();
      message.put(SEVERITY, "success");
      message.put(TITLE, "Success");
      message.put("text", String.format(OBMessageUtils.messageBD("ETDEP_Module_Uninstalled"), dependencyName));
      errorMessage.put(MESSAGE, message);
      errorMessage.put("refreshParent", true);
      OBDal.getInstance().flush();
    } catch (JSONException jsone) {
      try {
        JSONObject message = new JSONObject();
        message.put(SEVERITY, "error");
        message.put(TITLE, "Error");
        message.put("text", String.format(OBMessageUtils.messageBD("ETDEP_Error_Uninstalling"), jsone.getMessage()));
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
