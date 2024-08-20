package com.etendoerp.dependencymanager.process;

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

    Package package1 = DependencyManagerTestUtils.createPackage("packge1");
    Package package2 = DependencyManagerTestUtils.createPackage("packge2");
    Package package3 = DependencyManagerTestUtils.createPackage("packge3");

    PackageVersion packageVersion1 = DependencyManagerTestUtils.createPackageVersion("1.0.0", package1);
    PackageVersion packageVersion2 = DependencyManagerTestUtils.createPackageVersion("2.0.0", package2);
    PackageVersion packageVersion3 = DependencyManagerTestUtils.createPackageVersion("3.0.0", package3);

    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion2, "module.jar1");
    DependencyManagerTestUtils.createPackageDependency(packageVersion2, packageVersion3, "module.jar2");

    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(packageVersion1);
    OBDal.getInstance().refresh(packageVersion2);

    List<PackageDependency> dependencyList = DependencyTreeBuilder.createDependencyTree(packageVersion1);
    assertEquals(1, packageVersion1.getETDEPPackageDependencyList().size());
    assertEquals(2, dependencyList.size());
  }

  @Test
  public void testRemoveEtendoCoreDependencyFromPackageVersion() {
    Package package1 = DependencyManagerTestUtils.createPackage("packge1");
    Package package2 = DependencyManagerTestUtils.createPackage("packge2");
    Package package3 = DependencyManagerTestUtils.createPackage("packge3");

    PackageVersion packageVersion1 = DependencyManagerTestUtils.createPackageVersion("1.0.0", package1);
    PackageVersion packageVersion2 = DependencyManagerTestUtils.createPackageVersion("2.0.0", package2);
    PackageVersion packageVersion3 = DependencyManagerTestUtils.createPackageVersion("3.0.0", package3);

    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion2, "module.jar1");
    DependencyManagerTestUtils.createPackageDependency(packageVersion1, packageVersion3, "etendo-core");

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
