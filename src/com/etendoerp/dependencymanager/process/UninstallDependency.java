package com.etendoerp.dependencymanager.process;

import java.io.IOException;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.util.DependencyUtil;

public class UninstallDependency extends BaseActionHandler {
  public static final String SEVERITY = "severity";
  public static final String TITLE = "title";
  public static final String MESSAGE = "message";
  private static final Logger log = LogManager.getLogger();
  private static final String SOURCE = "S";
  private static final String LOCAL = "L";

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
      String format = jsonContent.getString("inpformat");

      log.debug("Deleting dependency from database...");
      Dependency dependency = OBDal.getInstance().get(Dependency.class, jsonContent.get("inpetdepDependencyId"));
      OBDal.getInstance().remove(dependency);
      if (StringUtils.equals(SOURCE, format) || StringUtils.equals(LOCAL, format)) {

        log.debug("Dependency format is '{}', deleting source directory...", format);
        DependencyUtil.deleteSourceDependencyDir(dependencyName);
      }

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
    } catch (IOException ioe) {
      try {
        jsonContent = new JSONObject(content);
        String dependencyName = jsonContent.getString("inpdepgroup") + "."
            + jsonContent.getString("inpartifact");
        JSONObject message = new JSONObject();
        message.put(SEVERITY, "error");
        message.put(TITLE, "Error");
        message.put("text", String.format(OBMessageUtils.messageBD("ETDEP_Error_File"), dependencyName));
        errorMessage.put(MESSAGE, message);
      } catch (Exception ignore) {
        log.error(ioe);
      } finally {
        OBDal.getInstance().rollbackAndClose();
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return errorMessage;
  }
}
