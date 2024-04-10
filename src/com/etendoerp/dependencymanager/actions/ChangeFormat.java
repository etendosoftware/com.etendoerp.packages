package com.etendoerp.dependencymanager.actions;

import java.io.IOException;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.mutable.MutableBoolean;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.exception.OBException;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.util.ChangeFormatUtil;
import com.etendoerp.dependencymanager.util.DependencyUtil;
import com.etendoerp.dependencymanager.util.PackageUtil;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

public class ChangeFormat extends Action {
  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    try {
      List<Dependency> dependencies = getInputContents(getInputClass());
       String newFormat = parameters.getString(ChangeFormatUtil.NEW_FORMAT_PARAM);

      int index = 0;
      for (Dependency dependency : dependencies) {
        changeDependencyFormat(dependency, newFormat);
        index++;
        if (index % 100 == 0) { // Flush every 100 records
          OBDal.getInstance().flush();
        }
      }
      OBDal.getInstance().flush();
      return buildSuccessResult();
    } catch (Exception e) {
      return buildErrorResult(e);
    }
  }

  private void changeDependencyFormat(Dependency dependency, String newFormat) {
    String depName = dependency.getGroup() + "." + dependency.getArtifact();

    OBCriteria<Package> packageCriteria = OBDal.getInstance().createCriteria(Package.class);
    packageCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, dependency.getGroup()));
    packageCriteria.add(Restrictions.eq(Package.PROPERTY_ARTIFACT, dependency.getArtifact()));
    packageCriteria.setMaxResults(1);
    Package dependencyPackage = (Package) packageCriteria.uniqueResult();

    if (dependencyPackage == null) {
      throw new OBException(String.format(OBMessageUtils.messageBD("ETDEP_No_Dependency_Package"), depName));
    }

    if (StringUtils.equals(DependencyUtil.FORMAT_LOCAL, dependency.getFormat())) {
      String newVersion = PackageUtil.getCoreCompatibleOrLatestVersion(dependencyPackage);
      dependency.setVersion(newVersion);
    }
    if (StringUtils.equals(DependencyUtil.FORMAT_SOURCE, newFormat)) {
      dependency.setFormat(DependencyUtil.FORMAT_SOURCE);
    } else if (StringUtils.equals(DependencyUtil.FORMAT_JAR, newFormat)) {
      dependency.setFormat(DependencyUtil.FORMAT_JAR);
      try {
        DependencyUtil.deleteSourceDependencyDir(depName);
      } catch (IOException e) {
        throw new OBException(String.format(OBMessageUtils.messageBD("ETDEP_Error_File"), depName));
      }
    }
    OBDal.getInstance().save(dependency);
  }

  private ActionResult buildSuccessResult() {
    ActionResult result = new ActionResult();
    result.setType(Result.Type.SUCCESS);
    result.setMessage(OBMessageUtils.getI18NMessage("Success"));
    return result;
  }

  private ActionResult buildErrorResult(Exception e) {
    ActionResult result = new ActionResult();
    result.setType(Result.Type.ERROR);
    result.setMessage(OBMessageUtils.getI18NMessage(e.getMessage()));
    return result;
  }

  @Override
  protected Class<Dependency> getInputClass() {
    return Dependency.class;
  }
}
