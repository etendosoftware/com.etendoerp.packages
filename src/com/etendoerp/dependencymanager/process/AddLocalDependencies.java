package com.etendoerp.dependencymanager.process;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.client.kernel.BaseActionHandler;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.model.ad.module.Module;
import org.openbravo.service.db.DbUtility;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.etendoerp.dependencymanager.data.Dependency;

public class AddLocalDependencies extends BaseActionHandler {
  private static final Logger log = LogManager.getLogger();

  /**
   * Searches for Java packages within the specified base directory.
   *
   * @param baseDir
   *     The base directory to search for Java packages.
   * @return A list of Java packages found within the subdirectories of the base directory.
   */
  private static List<String> searchJavaPackages(File baseDir) {
    List<String> javaPackages = new ArrayList<>();

    if (baseDir.isDirectory()) {
      File[] subDirs = baseDir.listFiles(File::isDirectory);
      if (subDirs != null) {
        for (File dir : subDirs) {
          File xmlFile = new File(dir, "src-db/database/sourcedata/AD_MODULE.xml");
          if (xmlFile.exists()) {
            String javaPackage = parseJavaPackage(xmlFile);
            if (javaPackage != null) {
              javaPackages.add(javaPackage);
            }
          }
        }
      }
    }
    return javaPackages;
  }

  /**
   * Parses a Java package from the given XML file.
   *
   * @param xmlFile
   *     The XML file to parse.
   * @return The Java package extracted from the XML file, or null if not found or an error occurs.
   */
  private static String parseJavaPackage(File xmlFile) {
    try {
      // Prepare the XML file to parse
      DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();

      // Disable DOCTYPE declaration to avoid any possible XXE attacks
      dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(xmlFile);

      // Normalize the XML document
      doc.getDocumentElement().normalize();

      // Search for the JAVAPACKAGE tag
      NodeList nodes = doc.getElementsByTagName("JAVAPACKAGE");
      if (nodes.getLength() > 0) {
        Element element = (Element) nodes.item(0);
        return element.getTextContent();
      }
    } catch (Exception e) {
      log.error("Could not get all javapackages: {}", e.getMessage());
    }
    return null; // Return null if the tag is not found or if an error occurs
  }

  /**
   * Executes a process to add Local modules to the ETDEP_Dependency table.
   * <p>
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
              dep -> dep.getGroup() + "." + dep.getArtifact()) // Concatena los resultados de getGroup y getFormat para cada Dependency
          .collect(Collectors.toList());

      // Get all modules' javapackages in modules_core folder
      String modulesCorePath = OBPropertiesProvider.getInstance().getOpenbravoProperties().getProperty(
          "source.path") + "/modules_core";
      File modulesCore = new File(modulesCorePath);
      List<String> modulesCoreJavaPkgs = searchJavaPackages(modulesCore);

      // Combine dependency javapackages list with modules_core javapackages list
      javaPkgsToNotAdd.addAll(modulesCoreJavaPkgs);

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
        dependency.setInstallationStatus("INSTALLED");
        dependency.setFormat("L");
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
