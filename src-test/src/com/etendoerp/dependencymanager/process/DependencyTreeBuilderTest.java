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
    OBContext.setOBContext(TestConstants.Users.SYSTEM, TestConstants.Roles.SYS_ADMIN, TestConstants.Clients.SYSTEM,
        TestConstants.Orgs.MAIN);
    VariablesSecureApp vsa = new VariablesSecureApp(OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(), OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId());
    RequestContext.get().setVariableSecureApp(vsa);
  }

  @Test
  public void testFindDependenciesFromPackageVersion() {

    Package package1 = DependencyManagerTestUtils.createPackage(PACKGE_1, GROUP_COM_ETENDOERP);
    Package package2 = DependencyManagerTestUtils.createPackage(PACKGE_2, GROUP_COM_ETENDOERP);
    Package package3 = DependencyManagerTestUtils.createPackage(PACKGE_3, GROUP_COM_ETENDOERP);

    PackageVersion packageVersion1 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package1, FROM_CORE,
        LATEST_CORE);
    PackageVersion packageVersion2 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package2, FROM_CORE,
        LATEST_CORE);
    PackageVersion packageVersion3 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package3, FROM_CORE,
        LATEST_CORE);

    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion2, "module.jar1",
        GROUP_COM_ETENDOERP, FIRST_VERSION, false);
    DependencyManagerTestUtils.createPackageDependency(packageVersion2, packageVersion3, "module.jar2",
        GROUP_COM_ETENDOERP, FIRST_VERSION, false);

    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(packageVersion1);
    OBDal.getInstance().refresh(packageVersion2);

    List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersion1);
    assertEquals(1, packageVersion1.getETDEPPackageDependencyList().size());
    assertEquals(2, dependencyList.size());
  }

  @Test
  public void testRemoveEtendoCoreDependencyFromPackageVersion() {
    Package package1 = DependencyManagerTestUtils.createPackage(PACKGE_1, GROUP_COM_ETENDOERP);
    Package package2 = DependencyManagerTestUtils.createPackage(PACKGE_2, GROUP_COM_ETENDOERP);
    Package package3 = DependencyManagerTestUtils.createPackage(PACKGE_3, GROUP_COM_ETENDOERP);

    PackageVersion packageVersion1 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package1, FROM_CORE,
        LATEST_CORE);
    PackageVersion packageVersion2 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package2, FROM_CORE,
        LATEST_CORE);
    PackageVersion packageVersion3 = DependencyManagerTestUtils.createPackageVersion(FIRST_VERSION, package3, FROM_CORE,
        LATEST_CORE);

    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion2, "module.jar1",
        GROUP_COM_ETENDOERP, FIRST_VERSION, false);
    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion3, "etendo-core",
        GROUP_COM_ETENDOERP, FIRST_VERSION, false);

    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(packageVersion1);
    OBDal.getInstance().refresh(packageVersion2);

    int initialDependencyCount = packageVersion1.getETDEPPackageDependencyList().size();
    List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersion1);
    assertEquals(2, initialDependencyCount);
    assertEquals(1, dependencyList.size());
  }

  @After
  public void cleanUp() {
    OBDal.getInstance().rollbackAndClose();
  }

}
