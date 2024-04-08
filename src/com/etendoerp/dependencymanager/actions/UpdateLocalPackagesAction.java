package com.etendoerp.dependencymanager.actions;

import com.etendoerp.dependencymanager.util.UpdateLocalPackagesUtil;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.erpCommon.utility.OBMessageUtils;

public class UpdateLocalPackagesAction extends Action {
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    var result = new ActionResult();
    try {
      UpdateLocalPackagesUtil.update();
      result.setMessage(OBMessageUtils.messageBD("ETDEP_Package_Update_Success"));
      result.setType(Result.Type.SUCCESS);
    } catch (Exception e) {
      result.setMessage(e.getMessage());
      result.setType(Result.Type.ERROR);
      return result;
    }
    return result;

  }

  @Override
  protected Class<?> getInputClass() {
    return com.etendoerp.dependencymanager.data.Package.class;
  }
}
