package com.etendoerp.dependencymanager;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.module.Module;

import com.etendoerp.dependencymanager.data.Dependency;
import com.etendoerp.dependencymanager.process.UninstallDependency;

public class DependencyManagerTestUtils {

  private DependencyManagerTestUtils() {
  }

  public static Map<String, Object> generateProcessParameters() {
    Map<String, Object> parameters = new HashMap<>();

    parameters.put("processId", "69839B215B1D4876BBD6723819627F7C");
    parameters.put("_action", "com.etendoerp.dependencymanager.process.UninstallDependency");
    parameters.put("windowId", "6BEA3B5663AA48A489DAE256CB2ACD28");
    parameters.put("reportId", "null");

    return parameters;
  }

  public static String generateProcessContent(String modulePkg, String format) throws JSONException {
    String group = DependencyManagerTestConstants.GROUP_COM_ETENDOERP;
    String artifact = modulePkg.replace(group + ".", "");
    OBCriteria<Dependency> dependencyCriteria = OBDal.getInstance().createCriteria(Dependency.class);
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_GROUP, group));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_ARTIFACT, artifact));
    dependencyCriteria.add(Restrictions.eq(Dependency.PROPERTY_FORMAT, format));
    dependencyCriteria.setMaxResults(1);
    String dependencyId = ((Dependency) dependencyCriteria.uniqueResult()).getId();

    JSONObject content = new JSONObject();
    content.put("inpdepgroup", group);
    content.put("inpartifact", artifact);
    content.put("inpformat", format);
    content.put("inpetdepDependencyId", dependencyId);

    return content.toString();
  }

  public static void addModule(String moduleJavaPkg, String format) {
    Module module = OBProvider.getInstance().get(Module.class);
    module.setJavaPackage(moduleJavaPkg);
    module.setName(moduleJavaPkg + " Test Module");
    module.setDescription(moduleJavaPkg + " Test Module");
    module.setVersion(DependencyManagerTestConstants.FIRST_VERSION);
    module.setAuthor(DependencyManagerTestConstants.AUTHOR_ETENDO_SOFTWARE);
    module.setLicenseType(DependencyManagerTestConstants.LICENSE_APACHE);
    module.setType(DependencyManagerTestConstants.TYPE_MODULE);
    OBDal.getInstance().save(module);

    Dependency dependency = OBProvider.getInstance().get(Dependency.class);
    dependency.setGroup(DependencyManagerTestConstants.GROUP_COM_ETENDOERP);
    dependency.setArtifact(moduleJavaPkg.replace(DependencyManagerTestConstants.GROUP_COM_ETENDOERP + ".", ""));
    dependency.setVersion(DependencyManagerTestConstants.FIRST_VERSION);
    dependency.setInstalledModule(module);
    dependency.setFormat(format);
    OBDal.getInstance().save(dependency);

    OBDal.getInstance().flush();

    if (StringUtils.equals(format, DependencyManagerTestConstants.FORMAT_SOURCE)) {
      File sourceFile = new File(DependencyManagerTestConstants.MODULES_PATH + moduleJavaPkg);
      sourceFile.mkdir();
    }
  }

  public static class UninstallDepForTests extends UninstallDependency {

    @Override
    public JSONObject execute(Map<String, Object> parameters, String content) {
      return super.execute(parameters, content);
    }

  }
}
