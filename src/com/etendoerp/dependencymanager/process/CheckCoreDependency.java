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

import java.util.Map;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.base.exception.OBException;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.service.OBDal;

import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.PackageUtil;

public class CheckCoreDependency extends BaseActionHandler {

  @Override
  protected JSONObject execute(Map<String, Object> parameters, String data) {
    try {
      final JSONObject jsonData = new JSONObject(data);
      final String packageVersionId = jsonData.getString("packageVersionId");
      JSONObject result = new JSONObject();
      PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, packageVersionId);
      if (packageVersion != null) {
        result = PackageUtil.checkCoreCompatibility(packageVersion.getPackage(), packageVersion.getVersion());
      }
      return result;
    } catch (Exception e) {
      throw new OBException(e);
    }
  }
}
