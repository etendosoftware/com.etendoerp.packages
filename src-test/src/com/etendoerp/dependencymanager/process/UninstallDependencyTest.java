package com.etendoerp.dependencymanager.process;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.ParameterCdiTest;
import org.openbravo.base.weld.test.ParameterCdiTestRule;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.test.base.TestConstants;

import com.etendoerp.dependencymanager.DependencyManagerTestConstants;
import com.etendoerp.dependencymanager.DependencyManagerTestUtils;

public class UninstallDependencyTest extends WeldBaseTest {
  public static final List<Object> PARAMS = Arrays.asList(
      // ModulePkg, format, expectedResult, expectedMessage
      new Object[]{ DependencyManagerTestConstants.MODULE_JAR_PGK_1, "J", "success", DependencyManagerTestConstants.SUCCESS_MSG_JAR_1 },
      new Object[]{ DependencyManagerTestConstants.MODULE_SOURCE_PKG_1, "S", "success", DependencyManagerTestConstants.SUCCESS_MSG_SOURCE_1 }
  );

  @Rule
  public ParameterCdiTestRule<Object> parameterCdiTestRule = new ParameterCdiTestRule<>(PARAMS);
  private @ParameterCdiTest Object[] parameters;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    OBContext.setOBContext(TestConstants.Users.SYSTEM, TestConstants.Roles.SYS_ADMIN,
        TestConstants.Clients.SYSTEM, TestConstants.Orgs.MAIN);
    VariablesSecureApp vsa = new VariablesSecureApp(
        OBContext.getOBContext().getUser().getId(),
        OBContext.getOBContext().getCurrentClient().getId(),
        OBContext.getOBContext().getCurrentOrganization().getId(),
        OBContext.getOBContext().getRole().getId()
    );
    RequestContext.get().setVariableSecureApp(vsa);
    DependencyManagerTestUtils.addModule((String) parameters[0], (String) parameters[1]);
  }

  @Test
  public void testUninstallDependency() throws JSONException {
    DependencyManagerTestUtils.UninstallDepForTests uninstallProcess = new DependencyManagerTestUtils.UninstallDepForTests();
    Map<String, Object> processParams = DependencyManagerTestUtils.generateProcessParameters();
    String content = DependencyManagerTestUtils.generateProcessContent((String) parameters[0], (String) parameters[1]);
    JSONObject result = uninstallProcess.execute(processParams, content);
    String severity = result.getJSONObject("message").getString("severity");
    String text = result.getJSONObject("message").getString("text");
    assertAll(
        () -> assertEquals(parameters[2], severity),
        () -> assertEquals(parameters[3], text)
    );
  }

  @After
  public void cleanUp() {
    OBDal.getInstance().rollbackAndClose();
  }
}
