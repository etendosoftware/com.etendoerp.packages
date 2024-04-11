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
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.util.ChangeFormatUtil;
import com.etendoerp.dependencymanager.util.DependencyUtil;
import com.etendoerp.dependencymanager.util.PackageUtil;
import com.smf.jobs.Action;
import com.smf.jobs.ActionResult;
import com.smf.jobs.Result;

public class ChangeFormat extends Action {
  int errors = 0;

  @Override
  protected ActionResult action(JSONObject parameters, MutableBoolean isStopped) {
    errors = 0;

    try {
      List<Dependency> dependencies = getInputContents(getInputClass());
      String newFormat = parameters.getString(ChangeFormatUtil.NEW_FORMAT_PARAM);

      int index = 0;
      StringBuilder message = new StringBuilder();
      if (dependencies.size() > 1) {
        throw new OBException("Process is not Multi Records");
      }
      for (Dependency dependency : dependencies) {
        String depName = dependency.getGroup() + "." + dependency.getArtifact();
        String messageHeader = "<strong>" + depName + "</strong>";
        String depResult = changeDependencyFormat(dependency, newFormat);
        message.append(messageHeader)
            .append(": ")
            .append(depResult)
            .append("<br>");
        index++;
        if (index % 100 == 0) { // Flush every 100 records
          OBDal.getInstance().flush();
        }
      }
      OBDal.getInstance().flush();
      if (errors == dependencies.size()) {
        throw new OBException(message.toString());
      }
      return buildSuccessResult(message.toString());
    } catch (Exception e) {
      return buildErrorResult(e);
    }
  }

  private String changeDependencyFormat(Dependency dependency, String newFormat) {
    String depName = dependency.getGroup() + "." + dependency.getArtifact();
    String previousFormat = dependency.getFormat();
    String strNewFormat = "";

    OBCriteria<Package> packageCriteria = OBDal.getInstance().createCriteria(Package.class);
    packageCriteria.add(Restrictions.eq(Package.PROPERTY_GROUP, dependency.getGroup()));
    packageCriteria.add(Restrictions.eq(Package.PROPERTY_ARTIFACT, dependency.getArtifact()));
    packageCriteria.setMaxResults(1);
    Package dependencyPackage = (Package) packageCriteria.uniqueResult();

    if (dependencyPackage == null) {
      errors++;
      return String.format(OBMessageUtils.messageBD("ETDEP_No_Dependency_Package"), depName);
    }
    if (depModuleIsInDevelopment(depName)) {
      errors++;
      return String.format(OBMessageUtils.messageBD("ETDEP_Dependency_Module_In_Development"));
    }

    if (StringUtils.equals(DependencyUtil.FORMAT_LOCAL, dependency.getFormat())) {
      String newVersion = PackageUtil.getCoreCompatibleOrLatestVersion(dependencyPackage);
      dependency.setVersion(newVersion);
    }
    if (StringUtils.equals(DependencyUtil.FORMAT_SOURCE, newFormat)) {
      dependency.setFormat(DependencyUtil.FORMAT_SOURCE);
      strNewFormat = "Source";
    } else if (StringUtils.equals(DependencyUtil.FORMAT_JAR, newFormat)) {
      dependency.setFormat(DependencyUtil.FORMAT_JAR);
      strNewFormat = "Jar";
      try {
        DependencyUtil.deleteSourceDependencyDir(depName);
      } catch (IOException e) {
        errors++;
        dependency.setFormat(previousFormat);
        return String.format(OBMessageUtils.messageBD("ETDEP_Error_File"), depName);
      }
    }
    OBDal.getInstance().save(dependency);
    return String.format(OBMessageUtils.messageBD("ETDEP_Format_Changed"), strNewFormat);
  }

  private boolean depModuleIsInDevelopment(String dependencyName) {
    OBCriteria<Module> moduleCrit = OBDal.getInstance().createCriteria(Module.class);
    moduleCrit.add(Restrictions.eq(Module.PROPERTY_JAVAPACKAGE, dependencyName));
    moduleCrit.setMaxResults(1);
    Module depModule = (Module) moduleCrit.uniqueResult();

    return depModule != null && depModule.isInDevelopment();
  }

  private ActionResult buildSuccessResult(String message) {
    ActionResult result = new ActionResult();
    result.setType(Result.Type.SUCCESS);
    result.setMessage(message);
    return result;
  }

  private ActionResult buildErrorResult(Exception e) {
    ActionResult result = new ActionResult();
    result.setType(Result.Type.ERROR);
    result.setMessage(e.getMessage());
    return result;
  }

  @Override
  protected Class<Dependency> getInputClass() {
    return Dependency.class;
  }
}
