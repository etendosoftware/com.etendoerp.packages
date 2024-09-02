package com.etendoerp.dependencymanager.process;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.utility.DataSet;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessContext;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.test.base.TestConstants;

import javax.inject.Inject;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class GetPackagesFromRepositoriesAndCommitTest extends WeldBaseTest {

    @Inject
    private GetPackagesFromRepositoriesAndCommit process;

    private static final String AD_DATASET_ID = "9F0311EFA2C1406D81B03FE673FF0A17";
    private static final String AD_MODULE_ID = "2EC4FFAFFE984592BA9859A8C9E25BF0";
    private static final String LANGUAGE = "en_US";

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
    }

    /**
     * Test to verify the processButton method when the module is not in development.
     * Ensures that the method returns an error when the module is not in development.
     */
    @Test
    public void testModuleNotInDevelopment() {
        DalConnectionProvider conn = new DalConnectionProvider(false);
        DataSet mockDataSet = OBProvider.getInstance().get(DataSet.class);
        mockDataSet.setId(AD_DATASET_ID);
        Module mockModule = OBProvider.getInstance().get(Module.class);
        mockModule.setId(AD_MODULE_ID);
        mockModule.setInDevelopment(false);
        mockDataSet.setModule(mockModule);
        OBDal.getInstance().save(mockDataSet);
        OBError result = process.processButton(LANGUAGE, conn);
        assertNotNull(result);
        assertEquals("Error", result.getType());
        OBDal.getInstance().rollbackAndClose();
    }

    /**
     * Test to verify the isModuleInDevelopment method.
     * Ensures that the method correctly identifies whether a module is in development.
     */
    @Test
    public void testIsModuleInDevelopment() throws Exception {
        DataSet mockDataSet = OBProvider.getInstance().get(DataSet.class);
        mockDataSet.setId(AD_DATASET_ID);
        Module mockModule = OBProvider.getInstance().get(Module.class);
        mockModule.setId(AD_MODULE_ID);
        mockModule.setInDevelopment(true);
        mockDataSet.setModule(mockModule);
        Method method = GetPackagesFromRepositoriesAndCommit.class.getDeclaredMethod("isModuleInDevelopment", DataSet.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(process, mockDataSet);
        assertTrue(result);
        mockModule.setInDevelopment(false);
        result = (boolean) method.invoke(process, mockDataSet);
        assertFalse(result);
    }

    /**
     * Test to verify that the processButton method throws a RuntimeException when an exception occurs.
     * Ensures that the method correctly throws a RuntimeException with the expected cause.
     */
    @Test(expected = RuntimeException.class)
    public void testProcessButtonThrowsException() {
        DalConnectionProvider conn = new DalConnectionProvider(false);
        DataSet mockDataSet = OBProvider.getInstance().get(DataSet.class);
        mockDataSet.setId(AD_DATASET_ID);
        Module mockModule = OBProvider.getInstance().get(Module.class);
        mockModule.setId(AD_MODULE_ID);
        mockModule.setInDevelopment(true);
        mockDataSet.setModule(mockModule);
        OBDal.getInstance().save(mockDataSet);
        GetPackagesFromRepositoriesAndCommit processSpy = Mockito.spy(process);
        Mockito.doThrow(new RuntimeException("Simulated Exception")).when(processSpy).exportDataSetToXML(Mockito.any());
        try {
            processSpy.processButton(LANGUAGE, conn);
        } catch (RuntimeException e) {
            assertEquals("Simulated Exception", e.getCause().getMessage());
            throw e;
        } finally {
            OBDal.getInstance().rollbackAndClose();
        }
    }

    /**
     * Test to verify the doExecute method.
     * Ensures that the method invokes processButton with the correct parameters.
     */
    @Test
    public void testDoExecute() throws Exception {
        ProcessBundle bundle = Mockito.mock(ProcessBundle.class);
        ProcessContext context = Mockito.mock(ProcessContext.class);
        DalConnectionProvider conn = new DalConnectionProvider(false);
        Mockito.when(bundle.getContext()).thenReturn(context);
        Mockito.when(bundle.getConnection()).thenReturn(conn);
        Mockito.when(context.getLanguage()).thenReturn(LANGUAGE);
        GetPackagesFromRepositoriesAndCommit processSpy = Mockito.spy(process);
        Mockito.doReturn(new OBError()).when(processSpy).processButton(Mockito.eq(LANGUAGE), Mockito.eq(conn));
        processSpy.doExecute(bundle);
        Mockito.verify(processSpy).processButton(LANGUAGE, conn);
    }

    /**
     * Test to verify the saveXMLToFile method with a valid XML.
     * Ensures that the XML is correctly saved to the specified file.
     */
    @Test
    public void testSaveXMLToFile() throws Exception {
        String xmlContent = "<dataset>...</dataset>";
        String projectPath = "/tmp/testproject";
        String modLocation = "/modules";
        String moduleJavaPackage = "/com.etendoerp.test";
        File myFolder = new File(projectPath + modLocation + "/" + moduleJavaPackage + "/referencedata/standard");
        File myFile = new File(myFolder.getPath() + "/Packages_dataset.xml");
        if (myFile.exists()) {
            myFile.delete();
        }
        process.saveXMLToFile(xmlContent, projectPath, modLocation, moduleJavaPackage);
        assertTrue(myFile.exists());
        String fileContent = new String(Files.readAllBytes(myFile.toPath()), StandardCharsets.UTF_8);
        assertEquals(xmlContent, fileContent);
        if (myFile.exists()) {
            myFile.delete();
        }
    }

    /**
     * Test to verify the createErrorOBError method.
     * Ensures that an error OBError object is correctly created with the expected values.
     */
    @Test
    public void testCreateErrorOBError() throws Exception {
        DalConnectionProvider conn = new DalConnectionProvider(false);
        Method method = GetPackagesFromRepositoriesAndCommit.class.getDeclaredMethod("createErrorOBError", ConnectionProvider.class, String.class);
        method.setAccessible(true);
        OBError error = (OBError) method.invoke(process, conn, LANGUAGE);
        assertEquals("Error", error.getType());
        assertNotNull(error.getTitle());
        assertNotNull(error.getMessage());
    }

    /**
     * Test to verify the createSuccessOBError method.
     * Ensures that a success OBError object is correctly created with the expected values.
     */
    @Test
    public void testCreateSuccessOBError() throws Exception {
        DalConnectionProvider conn = new DalConnectionProvider(false);
        Method method = GetPackagesFromRepositoriesAndCommit.class.getDeclaredMethod("createSuccessOBError", ConnectionProvider.class, String.class);
        method.setAccessible(true);
        OBError success = (OBError) method.invoke(process, conn, LANGUAGE);
        assertEquals("Success", success.getType());
        assertNotNull(success.getTitle());
        assertNotNull(success.getMessage());
    }

    /**
     * Test that executes the full process of the GetPackagesFromRepositoriesAndCommit class.
     *
     * This test simulates the necessary environment and context to execute the main method of the process
     * and verifies that all operations are performed as expected. Make sure that the line
     * "executeGetPackagesProcess(bundle);" is commented out in the `doExecute` method of the
     * `GetPackagesFromRepositoriesAndCommit` class before running this test, as it is designed to test
     * the entire process except for that specific line.
     *
     * @throws Exception If an error occurs during the process execution.
     */
    @Test
    public void testFullProcessExecution() throws Exception {
        ProcessBundle bundle = Mockito.mock(ProcessBundle.class);
        ProcessContext context = Mockito.mock(ProcessContext.class);
        DalConnectionProvider conn = new DalConnectionProvider(false);

        Mockito.when(bundle.getContext()).thenReturn(context);
        Mockito.when(bundle.getConnection()).thenReturn(conn);
        Mockito.when(context.getLanguage()).thenReturn(LANGUAGE);

        OBContext.setOBContext(TestConstants.Users.SYSTEM, TestConstants.Roles.SYS_ADMIN,
                TestConstants.Clients.SYSTEM, TestConstants.Orgs.MAIN);

        DataSet mockDataSet = OBProvider.getInstance().get(DataSet.class);
        mockDataSet.setId(AD_DATASET_ID);

        Module mockModule = OBProvider.getInstance().get(Module.class);
        mockModule.setId(AD_MODULE_ID);
        mockModule.setInDevelopment(true);
        mockDataSet.setModule(mockModule);
        OBDal.getInstance().save(mockDataSet);

        process.doExecute(bundle);

        DataSet resultDataSet = OBDal.getInstance().get(DataSet.class, AD_DATASET_ID);
        assertNotNull(resultDataSet);
        assertEquals(mockDataSet.getModule().getId(), resultDataSet.getModule().getId());
        assertTrue(resultDataSet.getModule().isInDevelopment());

        OBError processResult = process.processButton(LANGUAGE, conn);
        assertNotNull(processResult);
        assertEquals("Success", processResult.getType());

        OBDal.getInstance().rollbackAndClose();
    }
}
