/**
 * This class is responsible for managing the process of retrieving packages from repositories
 * and committing them as part of the module development process.
 * It extends the {@link DalBaseProcess} class, utilizing various utilities to
 * handle datasets and modules.
 */
package com.etendoerp.dependencymanager.process;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.session.OBPropertiesProvider;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileOutputStream;

import java.net.URISyntaxException;
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
    private static final String SUCCESS = "Success";
    public static final String AD_DATASET_ID = "9F0311EFA2C1406D81B03FE673FF0A17";
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
        logger.logln("Get Packages Process: " + OBMessageUtils.messageBD(SUCCESS));

        ProcessContext context = bundle.getContext();
        String language = context.getLanguage();
        processButton(language, bundle.getConnection());
        logger.logln("Export Reference Data: " + OBMessageUtils.messageBD(SUCCESS));

        String scriptToRun = "git_operations.sh";
        String scriptOutput = executeScript(scriptToRun);
        logger.logln("Script Output for " + scriptToRun + ":\n" + scriptOutput);
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
    protected OBError createErrorOBError(ConnectionProvider conn, String language) {
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
    protected OBError createSuccessOBError(ConnectionProvider conn, String language) {
        OBError myError = new OBError();
        myError.setType(SUCCESS);
        myError.setTitle(Utility.messageBD(conn, SUCCESS, language));
        myError.setMessage(Utility.messageBD(conn, SUCCESS, language));
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
        return OBPropertiesProvider.getInstance()
                .getOpenbravoProperties()
                .getProperty("source.path");
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
        File myFolder = new File(projectPath + (StringUtils.equals(AD_MODULE_ID, "0") ? "" : modLocation + moduleJavaPackage) + "/referencedata/standard");
        File myFile = new File(myFolder.getPath() + "/Packages_dataset" + ".xml");

        if (!myFolder.exists()) {
            myFolder.mkdirs();
        }

        try (FileOutputStream myOutputStream = new FileOutputStream(myFile)) {
            myOutputStream.write(xml.getBytes("UTF-8"));
        }

        log4j.info("Saved XML file to: " + myFile.getAbsolutePath());
    }

    /**
     * Executes a Bash script located in the project's resources directory.
     *
     * This method locates the specified script in the project's resources directory,
     * executes it using a system process, and captures the script's output. The output
     * of the script is appended to a {@link StringBuilder} and returned as a string.
     *
     * @param scriptName The name of the script file to execute. It should be located
     *        in the resources directory under the path
     *        <code>com/etendoerp/dependencymanager/util/</code>.
     * @return A string containing the script's output and a message indicating whether
     *         the execution was successful or if any errors occurred.
     * @throws RuntimeException If an error occurs while converting the script URL to a URI.
     */
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

        } catch (IOException e) {
            throw new IllegalStateException("Script execution failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Script execution interrupted: " + e.getMessage(), e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Script URI syntax error: " + e.getMessage(), e);
        }

        return output.toString();
    }
}
