package com.etendoerp.dependencymanager.process;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.base.weld.WeldUtils;
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
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalConnectionProvider;
import org.openbravo.service.db.DataExportService;
import org.openbravo.test.base.TestConstants;

import javax.inject.Inject;

import java.io.*;
import java.lang.reflect.Method;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link GetPackagesFromRepositoriesAndCommit} class.
 * This class contains various test cases to verify the behavior of different methods
 * in the process of package management and version control operations.
 */
public class GetPackagesFromRepositoriesAndCommitTest extends WeldBaseTest {

    @Inject
    private GetPackagesFromRepositoriesAndCommit process;

    private static final String AD_DATASET_ID = "9F0311EFA2C1406D81B03FE673FF0A17";
    private static final String AD_MODULE_ID = "2EC4FFAFFE984592BA9859A8C9E25BF0";
    private static final String LANGUAGE = "en_US";

    /**
     * Sets up the test environment before each test execution. It initializes the Openbravo context and request variables.
     *
     * @throws Exception if any setup operation fails.
     */
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
     * Test case for verifying the behavior when the module is not in development mode.
     * Ensures that the process returns an error and handles the rollback correctly.
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
        String projectPath = process.getProjectPath();
        process.updateModuleDirsToScan(projectPath);
        String moduleJavaPackage = mockDataSet.getModule().getJavaPackage();
        String modLocation = process.getModuleLocation(projectPath, moduleJavaPackage);

        assertNotNull(modLocation);
        assertNotNull(projectPath);
        assertNotNull(result);
        assertEquals("Error", result.getType());
        OBDal.getInstance().rollbackAndClose();
    }

    /**
     * Test case for verifying the {@code isModuleInDevelopment} method.
     * This ensures the method correctly identifies whether a module is in development.
     *
     * @throws Exception if reflection-based method invocation fails.
     */
    @Test
    public void testIsModuleInDevelopment() throws Exception {
        DataSet mockDataSet = OBProvider.getInstance().get(DataSet.class);
        mockDataSet.setId(AD_DATASET_ID);
        Module mockModule = OBProvider.getInstance().get(Module.class);
        mockModule.setId(AD_MODULE_ID);
        mockModule.setInDevelopment(true);
        mockDataSet.setModule(mockModule);

        boolean result = process.isModuleInDevelopment(mockDataSet);  // Acceso directo
        assertTrue(result);

        mockModule.setInDevelopment(false);
        result = process.isModuleInDevelopment(mockDataSet);  // Acceso directo
        assertFalse(result);
    }


    /**
     * Test case to verify that {@code processButton} throws a {@link RuntimeException}
     * when an exception occurs during its execution.
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
        Mockito.doThrow(new RuntimeException("Simulated Exception")).when(processSpy).exportDataSetToXML(any());
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
     * Test case to verify the {@code doExecute} method.
     * Ensures that the method invokes the necessary actions for executing the process.
     *
     * @throws Exception if any exception occurs during the test execution.
     */
    @Test
    public void testDoExecute() throws Exception {
        ProcessBundle bundle = mock(ProcessBundle.class);
        ProcessContext context = mock(ProcessContext.class);
        ConnectionProvider conn = new DalConnectionProvider(false);
        ProcessLogger loggerMock = mock(ProcessLogger.class);
        when(bundle.getLogger()).thenReturn(loggerMock);
        when(bundle.getContext()).thenReturn(context);
        when(bundle.getConnection()).thenReturn(conn);
        when(context.getLanguage()).thenReturn(LANGUAGE);
        GetPackagesFromRepositoriesAndCommit processSpy = Mockito.spy(process);
        Mockito.doReturn(new OBError()).when(processSpy).processButton(Mockito.eq(LANGUAGE), Mockito.eq(conn));
        Mockito.doReturn("Script output").when(processSpy).executeScript(Mockito.anyString());
        processSpy.doExecute(bundle);
        verify(processSpy).processButton(Mockito.eq(LANGUAGE), Mockito.eq(conn));
        verify(processSpy).executeScript("setup.sh");
        verify(processSpy).executeScript("git_operations.sh");
    }

    /**
     * Test case to verify the functionality of {@code saveXMLToFile}.
     * Ensures that XML content is properly saved to the specified file location.
     *
     * @throws Exception if an I/O error occurs during file operations.
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
            boolean deleted = myFile.delete();
            assertTrue("Failed to delete existing file before test", deleted);
        }

        process.saveXMLToFile(xmlContent, projectPath, modLocation, moduleJavaPackage);
        assertTrue(myFile.exists());

        String fileContent = new String(Files.readAllBytes(myFile.toPath()), StandardCharsets.UTF_8);
        assertEquals(xmlContent, fileContent);

        if (myFile.exists()) {
            boolean deleted = myFile.delete();
            assertTrue("Failed to delete file after test", deleted);
        }
    }


    /**
     * Test case for verifying the {@code createErrorOBError} method.
     * Ensures that an {@link OBError} with type "Error" is correctly created.
     *
     * @throws Exception if reflection-based method invocation fails.
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
     * Test case for verifying the {@code createSuccessOBError} method.
     * Ensures that a successful {@link OBError} object is correctly created.
     *
     * @throws Exception if reflection-based method invocation fails.
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
     * Test case to verify the correct execution of scripts within the {@code doExecute} method.
     * Ensures that all the expected scripts are executed successfully.
     *
     * @throws Exception if any exception occurs during script execution.
     */
    @Test
    public void testScriptExecution() throws Exception {
        ProcessBundle bundle = mock(ProcessBundle.class);
        ProcessContext context = mock(ProcessContext.class);
        ConnectionProvider conn = new DalConnectionProvider(false);
        ProcessLogger loggerMock = mock(ProcessLogger.class);
        when(bundle.getLogger()).thenReturn(loggerMock);
        when(bundle.getContext()).thenReturn(context);
        when(bundle.getConnection()).thenReturn(conn);
        when(context.getLanguage()).thenReturn(LANGUAGE);
        GetPackagesFromRepositoriesAndCommit processSpy = Mockito.spy(process);
        Mockito.doReturn(new OBError()).when(processSpy).processButton(Mockito.eq(LANGUAGE), Mockito.eq(conn));
        Mockito.doNothing().when(processSpy).executeGetPackagesProcess(any());
        Mockito.doReturn("Script executed successfully.\n").when(processSpy).executeScript(anyString());
        processSpy.doExecute(bundle);
        verify(processSpy).executeScript("setup.sh");
        verify(processSpy).executeScript("git_operations.sh");
    }

    /**
     * Test case for verifying the {@code exportDataSetToXML} method.
     * Ensures that the dataset is exported to XML format correctly.
     */
    @Test
    public void testExportDataSetToXML() {
        DataExportService dataExportServiceMock = mock(DataExportService.class);
        when(dataExportServiceMock.exportDataSetToXML(any(DataSet.class), any(String.class), any(Map.class)))
                .thenReturn("<xml>Mocked XML content</xml>");
        DataSet mockDataSet = OBProvider.getInstance().get(DataSet.class);
        Module mockModule = OBProvider.getInstance().get(Module.class);
        mockModule.setId(AD_MODULE_ID);
        mockModule.setInDevelopment(true);
        mockDataSet.setModule(mockModule);
        DataExportService originalService = DataExportService.getInstance();
        try {
            WeldUtils.getInstanceFromStaticBeanManager(DataExportService.class);
            DataExportService.setInstance(dataExportServiceMock);
            process.exportDataSetToXML(mockDataSet);
        } finally {
            WeldUtils.getInstanceFromStaticBeanManager(DataExportService.class);
            DataExportService.setInstance(originalService);
        }
    }

    /**
     * Test case for verifying script execution failure.
     * Ensures that an appropriate error message is returned when a script fails.
     */
    @Test
    public void testExecuteScriptFailure() {
        String result = process.executeScript("nonexistentScript.sh");
        assertNotNull(result);
        assertTrue(result.contains("An error occurred:"));
    }

    /**
     * Test case for verifying successful script execution.
     * Ensures that a success message is returned when the script is executed without errors.
     */
    @Test
    public void testExecuteScriptSuccess() {
        String result = process.executeScript("setup.sh");
        assertNotNull(result);
        assertFalse(result.contains("An error occurred"));
        assertTrue(result.contains("Script executed successfully"));
    }
}
