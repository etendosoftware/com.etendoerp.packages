package com.etendoerp.dependencymanager.util;

import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import org.apache.commons.lang.BooleanUtils;
import org.dom4j.Element;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.dal.xml.XMLUtil;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class UpdateLocalPackagesUtil {

  public static final String ETDEP_PACKAGE = "ETDEP_Package";
  public static final String ETDEP_PACKAGE_VERSION = "ETDEP_Package_Version";
  public static final String ETDEP_PACKAGE_DEPENDENCY = "ETDEP_Package_Dependency";
  public static final String ETDEP_PACKAGE_TAG = "etdepPackage";
  public static final String ETDEP_PACKAGE_VERSION_TAG = "etdepPackageVersion";
  public static final String ID = "id";
  public static final String ACTIVE = "active";
  public static final String GROUP = "group";
  public static final String ARTIFACT = "artifact";
  public static final String VERSION = "version";
  public static final String INSTALL = "install";
  public static final String DEPGROUP = "depgroup";

  public static final String DATASET_FILE_URL = "https://raw.githubusercontent.com/etendosoftware/com.etendoerp.dependencymanager/main/referencedata/standard/Packages_dataset.xml";

  private UpdateLocalPackagesUtil() {
  }

  /**
   * This method is overridden from the DalBaseProcess class.
   * It reads an XML file and processes its elements to update local packages, versions, and dependencies.
   *
   * @param bundle The ProcessBundle object passed to this method.
   * @throws Exception If an error occurs during the execution of the method.
   */
  public static void update() throws IOException {
    OBContext.setAdminMode(true);
    // download DATASET_FILE_URL
    File dataSetFile = downloadFile(DATASET_FILE_URL);
    var xmlRootElement = XMLUtil.getInstance().getRootElement(new FileInputStream(dataSetFile));
    processPackages(xmlRootElement);
    processPackageVersions(xmlRootElement);
    processPackageDependencies(xmlRootElement);
    OBContext.restorePreviousMode();
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
      packageVersion.setInstall(
          BooleanUtils.toBooleanObject(packageElement.elementText(INSTALL)));
      packageVersion.setActive(
          BooleanUtils.toBooleanObject(packageElement.elementText(ACTIVE)));
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
      pkgDep.setGroup(packageElement.elementText(DEPGROUP));
      pkgDep.setArtifact(packageElement.elementText(ARTIFACT));
      pkgDep.setVersion(packageElement.elementText(VERSION));
      pkgDep.setActive(
          BooleanUtils.toBooleanObject(packageElement.elementText(ACTIVE)));
      OBDal.getInstance().save(pkgDep);
    }
    OBDal.getInstance().flush();
  }

}
