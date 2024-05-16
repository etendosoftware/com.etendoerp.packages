package com.etendoerp.dependencymanager.util;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import org.apache.commons.lang.BooleanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.dom4j.Element;
import org.openbravo.base.session.OBPropertiesProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.xml.XMLUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

public class UpdateLocalPackagesUtil {

  private static final Logger log = LogManager.getLogger();
  public static final String ETDEP_PACKAGE = "ETDEP_Package";
  public static final String ETDEP_PACKAGE_VERSION = "ETDEP_Package_Version";
  public static final String ETDEP_PACKAGE_DEPENDENCY = "ETDEP_Package_Dependency";
  public static final String ETDEP_PACKAGE_TAG = "package";
  public static final String ETDEP_PACKAGE_VERSION_TAG = "packageVersion";
  public static final String ID = "id";
  public static final String ACTIVE = "active";
  public static final String GROUP = "group";
  public static final String ARTIFACT = "artifact";
  public static final String VERSION = "version";
  public static final String FROM_CORE = "fromCore";
  public static final String LATEST_CORE = "latestCore";
  public static final String EXTERNAL_DEPENDENCY = "externalDependency";
  public static final String DEPENDENCY_VERSION = "dependencyVersion";
  public static final String ISBUNDLE = "isBundle";
  public static final String DATASET_FILE_URL = "https://raw.githubusercontent.com/etendosoftware/com.etendoerp.dependencymanager/<branch>/referencedata/standard/Packages_dataset.xml";

  private static final String BRANCH_LOCAL_PACKAGES_PROPERTY = "branch.update.local.packages";

  private UpdateLocalPackagesUtil() {
  }

  /**
   * This method is overridden from the DalBaseProcess class.
   * It reads an XML file and processes its elements to update local packages, versions, and dependencies.
   *
   * @throws Exception If an error occurs during the execution of the method.
   */
  public static void update() throws IOException {
    try {
      OBContext.setAdminMode(true);

      Properties properties = OBPropertiesProvider.getInstance().getOpenbravoProperties();
      String updateLocalPackages = properties.getProperty(BRANCH_LOCAL_PACKAGES_PROPERTY, "main");
      String dataSetFileUrl = DATASET_FILE_URL.replace("<branch>", updateLocalPackages);
      File dataSetFile = downloadFile(dataSetFileUrl);
      try (FileInputStream fileInputStream = new FileInputStream(dataSetFile)) {
        var xmlRootElement = XMLUtil.getInstance().getRootElement(fileInputStream);
        processPackages(xmlRootElement);
        processPackageVersions(xmlRootElement);
        processPackageDependencies(xmlRootElement);
      } catch (Exception e) {
        throw new IOException("Error when updating packages", e);
      }
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private static File downloadFile(String fileUrl) throws IOException {
    URL url = new URL(fileUrl);
    File tempFile = File.createTempFile("download", null);

    try (InputStream in = url.openStream()) {
      Files.copy(in,tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
    return tempFile;
  }

  /**
   * This method processes the package elements from the XML file.
   * It creates or updates a Package object for each package element.
   *
   * @param xmlRootElement The root element of the XML file.
   */
  private static void processPackages(Element xmlRootElement) {
    for (Element packageElement : xmlRootElement.elements(ETDEP_PACKAGE)) {
      String id = packageElement.elementText(ID);
      Package pkg = OBDal.getInstance().get(Package.class, id);
      if (pkg == null) {
        pkg = new Package();
        pkg.setNewOBObject(true);
        pkg.setId(id);
      }
      pkg.setGroup(packageElement.elementText(GROUP));
      pkg.setArtifact(packageElement.elementText(ARTIFACT));
      pkg.setActive(
          BooleanUtils.toBooleanObject(packageElement.elementText(ACTIVE)));
      pkg.setBundle(
          BooleanUtils.toBooleanObject(packageElement.elementText(ISBUNDLE)));
      OBDal.getInstance().save(pkg);
    }
    OBDal.getInstance().flush();
  }

  /**
   * This method processes the package version elements from the XML file.
   * It creates or updates a PackageVersion object for each package version element.
   *
   * @param xmlRootElement The root element of the XML file.
   */
  private static void processPackageVersions(Element xmlRootElement) {
    for (Element packageElement : xmlRootElement.elements(ETDEP_PACKAGE_VERSION)) {
      String id = packageElement.elementText(ID);
      PackageVersion packageVersion = OBDal.getInstance().get(PackageVersion.class, id);
      if (packageVersion == null) {
        packageVersion = new PackageVersion();
        packageVersion.setNewOBObject(true);
        packageVersion.setId(id);
      }
      packageVersion.setPackage(OBDal.getInstance()
          .get(Package.class, packageElement.element(ETDEP_PACKAGE_TAG).attributeValue(ID)));
      packageVersion.setVersion(packageElement.elementText(VERSION));
      packageVersion.setActive(
          BooleanUtils.toBooleanObject(packageElement.elementText(ACTIVE)));
      packageVersion.setFromCore(packageElement.elementText(FROM_CORE));
      packageVersion.setLatestCore(packageElement.elementText(LATEST_CORE));

      OBDal.getInstance().save(packageVersion);
    }
    OBDal.getInstance().flush();
  }

  /**
   * This method processes the package dependency elements from the XML file.
   * It creates or updates a PackageDependency object for each package dependency element.
   *
   * @param xmlRootElement The root element of the XML file.
   */
  private static void processPackageDependencies(Element xmlRootElement) {
    for (Element packageElement : xmlRootElement.elements(ETDEP_PACKAGE_DEPENDENCY)) {
      String id = packageElement.elementText(ID);
      PackageDependency pkgDep = OBDal.getInstance().get(PackageDependency.class, id);
      if (pkgDep == null) {
        pkgDep = new PackageDependency();
        pkgDep.setNewOBObject(true);
        pkgDep.setId(id);
      }
      pkgDep.setPackageVersion(OBDal.getInstance()
          .get(PackageVersion.class,
              packageElement.element(ETDEP_PACKAGE_VERSION_TAG).attributeValue(ID)));
      pkgDep.setGroup(packageElement.elementText(GROUP));
      pkgDep.setArtifact(packageElement.elementText(ARTIFACT));
      pkgDep.setVersion(packageElement.elementText(VERSION));
      pkgDep.setActive(
          BooleanUtils.toBooleanObject(packageElement.elementText(ACTIVE)));
      pkgDep.setExternalDependency(
          BooleanUtils.toBooleanObject(packageElement.elementText(EXTERNAL_DEPENDENCY)));
      PackageVersion dependencyVersion = null;
      if (packageElement.element(DEPENDENCY_VERSION).attributeCount() > 1) {
        dependencyVersion = OBDal.getInstance().get(PackageVersion.class, packageElement.element(DEPENDENCY_VERSION).attributeValue(ID));
      }
      pkgDep.setDependencyVersion(dependencyVersion);
      OBDal.getInstance().save(pkgDep);
    }
    OBDal.getInstance().flush();
  }

}
