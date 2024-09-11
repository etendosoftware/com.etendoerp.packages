/**
 * This class is responsible for managing the process of retrieving packages from repositories
 * and committing them as part of the module development process.
 * It extends the {@link DalBaseProcess} class, utilizing various utilities to
 * handle datasets and modules.
 */
package com.etendoerp.dependencymanager.process;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.dal.service.OBDal;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.ddlutils.util.ModulesUtil;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.utility.DataSet;
import org.openbravo.scheduling.ProcessBundle;
import org.openbravo.scheduling.ProcessContext;
import org.openbravo.scheduling.ProcessLogger;
import org.openbravo.service.db.DalBaseProcess;
import org.openbravo.service.db.DataExportService;

import java.io.*;
import java.net.URL;
import java.util.HashMap;

/**
 * This class is responsible for executing the process of retrieving packages
 * from repositories and committing them by exporting dataset data to XML,
 * saving it to a file, and updating the dataset's checksum.
 * It extends the {@link DalBaseProcess} class, utilizing various utilities to
 * handle datasets and modules.
 */
public class GetPackagesFromRepositoriesAndCommit extends DalBaseProcess {
    private static final Logger log4j = LogManager.getLogger();
    private ProcessLogger logger;

    /** The ID of the dataset to be processed. */
    public static final String AD_DATASET_ID = "9F0311EFA2C1406D81B03FE673FF0A17";

    /** The ID of the module associated with the dataset. */
    public static final String AD_MODULE_ID = "2EC4FFAFFE984592BA9859A8C9E25BF0";

    /**
     * Executes the process to retrieve packages from repositories and commit them.
     *
     * @param bundle The {@link ProcessBundle} containing the context and connection information.
     * @throws Exception if an error occurs during the execution of the process.
     */
    @Override
    protected void doExecute(ProcessBundle bundle) throws Exception {
        logger = bundle.getLogger();

        executeGetPackagesProcess(bundle);
        logger.logln("Get Packages Process: " + OBMessageUtils.messageBD("Success"));

        String scriptToRun1 = "setup.sh";
        String scriptOutput1 = executeScript(scriptToRun1);
        logger.logln("Script Output for " + scriptToRun1 + ":\n" + scriptOutput1);

        ProcessContext context = bundle.getContext();
        String language = context.getLanguage();
        processButton(language, bundle.getConnection());
        logger.logln("Export Reference Data: " + OBMessageUtils.messageBD("Success"));

        String scriptToRun2 = "git_operations.sh";
        String scriptOutput2 = executeScript(scriptToRun2);
        logger.logln("Script Output for " + scriptToRun2 + ":\n" + scriptOutput2);

    }

    /**
     * Executes the process of retrieving packages from repositories.
     *
     * @param bundle The {@link ProcessBundle} containing the context and connection information.
     * @throws Exception if an error occurs during the execution of the process.
     */
    protected void executeGetPackagesProcess(ProcessBundle bundle) throws Exception {
        GetPackagesFromRepositories getPackagesProcess = new GetPackagesFromRepositories();
        getPackagesProcess.doExecute(bundle);
    }

    /**
     * Processes the button click event, exporting dataset data to XML, saving it to a file,
     * and updating the dataset's checksum.
     *
     * @param language The language to be used for messages.
     * @param conn The {@link ConnectionProvider} for database connections.
     * @return An {@link OBError} object representing the success or failure of the process.
     */
    protected OBError processButton(String language, ConnectionProvider conn) {
        OBError myError;
        try {
            DataSet myDataset = OBDal.getInstance().get(DataSet.class, AD_DATASET_ID);

            if (!isModuleInDevelopment(myDataset)) {
                return createErrorOBError(conn, language);
            }

            String moduleJavaPackage = myDataset.getModule().getJavaPackage();
            String xml = exportDataSetToXML(myDataset);
            String projectPath = getProjectPath();
            updateModuleDirsToScan(projectPath);
            String modLocation = getModuleLocation(projectPath, moduleJavaPackage);
            saveXMLToFile(xml, projectPath, modLocation, moduleJavaPackage);
            myError = createSuccessOBError(conn, language);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return myError;
    }

    /**
     * Checks if the module associated with the dataset is in development.
     *
     * @param myDataset The {@link DataSet} to check.
     * @return {@code true} if the module is in development, {@code false} otherwise.
     */
    protected boolean isModuleInDevelopment(DataSet myDataset) {
        return myDataset.getModule().isInDevelopment();
    }

    /**
     * Creates an {@link OBError} object indicating an error.
     *
     * @param conn The {@link ConnectionProvider} for database connections.
     * @param language The language to be used for messages.
     * @return An {@link OBError} object representing an error state.
     */
    private OBError createErrorOBError(ConnectionProvider conn, String language) {
        OBError myError = new OBError();
        myError.setType("Error");
        myError.setTitle(Utility.messageBD(conn, "Error", language));
        myError.setMessage(Utility.messageBD(conn, "20532", language));
        return myError;
    }

    /**
     * Creates an {@link OBError} object indicating success.
     *
     * @param conn The {@link ConnectionProvider} for database connections.
     * @param language The language to be used for messages.
     * @return An {@link OBError} object representing a success state.
     */
    private OBError createSuccessOBError(ConnectionProvider conn, String language) {
        OBError myError = new OBError();
        myError.setType("Success");
        myError.setTitle(Utility.messageBD(conn, "Success", language));
        myError.setMessage(Utility.messageBD(conn, "Success", language));
        return myError;
    }

    /**
     * Exports the given dataset to an XML string.
     *
     * @param myDataset The {@link DataSet} to export.
     * @return The XML representation of the dataset.
     */
    protected String exportDataSetToXML(DataSet myDataset) {
        return DataExportService.getInstance()
                .exportDataSetToXML(myDataset, AD_MODULE_ID, new HashMap<>());
    }

    /**
     * Gets the project path where the application is running.
     *
     * @return The absolute path of the project.
     */
    protected String getProjectPath() {
        String absolute = getClass().getProtectionDomain().getCodeSource().getLocation().toExternalForm();
        if (absolute.startsWith("file:")) {
            absolute = absolute.substring(5);
        }
        File classesDir = new File(absolute);
        File projectDir = classesDir.getParentFile().getParentFile();
        return projectDir.getAbsolutePath();
    }

    /**
     * Updates the directories to be scanned for modules based on the project path.
     *
     * @param projectPath The path of the project.
     */
    protected void updateModuleDirsToScan(String projectPath) {
        ModulesUtil.checkCoreInSources(ModulesUtil.coreInSources(projectPath), projectPath);
    }

    /**
     * Gets the location of the module based on the project path and module Java package.
     *
     * @param projectPath The path of the project.
     * @param moduleJavaPackage The Java package of the module.
     * @return The location of the module within the project.
     */
    protected String getModuleLocation(String projectPath, String moduleJavaPackage) {
        String modLocation = File.separator + ModulesUtil.MODULES_BASE + File.separator;

        for (String modDir : ModulesUtil.moduleDirs) {
            File modDirLocation = new File(projectPath, modDir + File.separator + moduleJavaPackage);
            if (modDirLocation.exists()) {
                modLocation = File.separator + modDir + File.separator;
                break;
            }
        }

        return modLocation;
    }

    /**
     * Saves the XML content to a file in the specified module location.
     *
     * @param xml The XML content to save.
     * @param projectPath The path of the project.
     * @param modLocation The location of the module.
     * @param moduleJavaPackage The Java package of the module.
     * @throws Exception if an error occurs while saving the file.
     */
    protected void saveXMLToFile(String xml, String projectPath, String modLocation, String moduleJavaPackage) throws Exception {
        File myFolder = new File(projectPath + (AD_MODULE_ID.equals("0") ? "" : modLocation + moduleJavaPackage) + "/referencedata/standard");
        File myFile = new File(myFolder.getPath() + "/Packages_dataset" + ".xml");

        if (!myFolder.exists()) {
            myFolder.mkdirs();
        }

        try (FileOutputStream myOutputStream = new FileOutputStream(myFile)) {
            myOutputStream.write(xml.getBytes("UTF-8"));
        }

        System.out.println(myFile);
    }

    protected String executeScript(String scriptName) {
        StringBuilder output = new StringBuilder();
        try {
            String scriptPath = "com/etendoerp/dependencymanager/util/" + scriptName;
            URL scriptUrl = getClass().getClassLoader().getResource(scriptPath);
            if (scriptUrl == null) {
                throw new FileNotFoundException("Script file not found: " + scriptPath);
            }
            File scriptFile = new File(scriptUrl.toURI());
            String absoluteScriptPath = scriptFile.getAbsolutePath();

            File scriptDir = scriptFile.getParentFile();
            String[] cmd = {"/bin/bash", absoluteScriptPath};

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(scriptDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                output.append("Script executed successfully.\n");
            } else {
                output.append("An error occurred during script execution. Exit code: ").append(exitCode).append("\n");
            }

        } catch (InterruptedIOException e) {
            Thread.currentThread().interrupt();
            output.append("Script execution was interrupted: ").append(e.getMessage()).append("\n");
        } catch (Exception e) {
            output.append("An error occurred: ").append(e.getMessage()).append("\n");
        }

        return output.toString();
    }
}
