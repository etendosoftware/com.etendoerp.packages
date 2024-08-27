package com.etendoerp.dependencymanager.process;

import static com.etendoerp.dependencymanager.DependencyManagerTestConstants.FIRST_VERSION;
import static com.etendoerp.dependencymanager.DependencyManagerTestConstants.GROUP_COM_ETENDOERP;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.dependencymanager.DependencyManagerTestUtils;
import com.etendoerp.dependencymanager.data.Package;
import com.etendoerp.dependencymanager.data.PackageDependency;
import com.etendoerp.dependencymanager.data.PackageVersion;
import com.etendoerp.dependencymanager.util.DependencyTreeBuilder;

public class DependencyTreeBuilderTest extends WeldBaseTest {

  public static final String FROM_CORE = "21.4.0";
  public static final String LATEST_CORE = "24.2.0";
  public static final String PACKGE_1 = "packge1";
  public static final String PACKGE_2 = "packge2";
  public static final String PACKGE_3 = "packge3";

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    OBContext currentContext = OBContext.getOBContext();

    OBContext.setOBContext(TestConstants.Users.SYSTEM, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vsa = new VariablesSecureApp(currentContext.getUser().getId(),
        currentContext.getCurrentClient().getId(), currentContext.getCurrentOrganization().getId(),
        currentContext.getRole().getId());
    RequestContext.get().setVariableSecureApp(vsa);
  }

  private List<PackageVersion> setupPackageVersions() {
    Package package1 = DependencyManagerTestUtils.createPackage(PACKGE_1, GROUP_COM_ETENDOERP);
    Package package2 = DependencyManagerTestUtils.createPackage(PACKGE_2, GROUP_COM_ETENDOERP);
    Package package3 = DependencyManagerTestUtils.createPackage(PACKGE_3, GROUP_COM_ETENDOERP);

    PackageVersion packageVersion1 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package1, FROM_CORE,
        LATEST_CORE);
    PackageVersion packageVersion2 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package2, FROM_CORE,
        LATEST_CORE);
    PackageVersion packageVersion3 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package3, FROM_CORE,
        LATEST_CORE);

    List<PackageVersion> result = new java.util.ArrayList<>(List.of());
    result.add(packageVersion1);
    result.add(packageVersion2);
    result.add(packageVersion3);
    return result;
  }

  private void createDependenciesTwoLevels(PackageVersion packageVersion1, String module1,
      PackageVersion packageVersion2, String module2, PackageVersion packageVersion3) {
    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion2, module1, GROUP_COM_ETENDOERP,
        FIRST_VERSION, false);
    DependencyManagerTestUtils.createPackageDependency(packageVersion2, packageVersion3, module2, GROUP_COM_ETENDOERP,
        FIRST_VERSION, false);
  }

  private void createDependenciesOneLevel(PackageVersion packageVersion1, String module1,
      PackageVersion packageVersion2, String module2, PackageVersion packageVersion3) {
    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion2, module1, GROUP_COM_ETENDOERP,
        FIRST_VERSION, false);
    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion3, module2, GROUP_COM_ETENDOERP,
        FIRST_VERSION, false);
  }

  @Test
  public void testFindDependenciesFromPackageVersion2() {
    List<PackageVersion> packageVersions = setupPackageVersions();

    createDependenciesTwoLevels(packageVersions.get(0), "module.jar1", packageVersions.get(1), "module.jar2",
        packageVersions.get(2));

    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(packageVersions.get(0));
    OBDal.getInstance().refresh(packageVersions.get(1));

    List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersions.get(0));

    assertEquals(1, packageVersions.get(0).getETDEPPackageDependencyList().size());
    assertEquals(2, dependencyList.size());
  }

  @Test
  public void testRemoveEtendoCoreDependencyFromPackageVersion() {
    List<PackageVersion> packageVersions = setupPackageVersions();

    createDependenciesOneLevel(packageVersions.get(0), "module.jar1", packageVersions.get(1), "etendo-core",
        packageVersions.get(2));

    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(packageVersions.get(0));
    OBDal.getInstance().refresh(packageVersions.get(1));

    int initialDependencyCount = packageVersions.get(0).getETDEPPackageDependencyList().size();
    List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersions.get(0));
    assertEquals(2, initialDependencyCount);
    assertEquals(1, dependencyList.size());
  }

  @After
  public void cleanUp() {
    OBDal.getInstance().rollbackAndClose();
  }

}
