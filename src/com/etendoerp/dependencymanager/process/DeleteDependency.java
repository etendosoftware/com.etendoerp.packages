package com.etendoerp.dependencymanager.process;

import com.etendoerp.dependencymanager.data.Dependency;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.client.application.process.BaseProcessActionHandler;
import org.openbravo.client.application.process.ResponseActionsBuilder;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBMessageUtils;

import java.util.HashMap;
import java.util.Map;

public class DeleteDependency extends BaseProcessActionHandler {

    /**
     * Executes the process of deleting a dependency. It handles the process flow, including
     * deserializing the input, uninstalling the dependency, and building the appropriate response.
     *
     * @param parameters Additional parameters that may be used by the process.
     * @param content    JSON string containing the dependency details and format information.
     * @return A JSON object representing the success or failure of the operation.
     */
    @Override
    protected JSONObject doExecute(Map<String, Object> parameters, String content) {
        OBContext.setAdminMode(true);

        try {
            JSONObject jsonResponse = new JSONObject(content);
            uninstallDependency(jsonResponse);
            return buildSuccessResponse(jsonResponse);

        } catch (JSONException e) {
            return buildErrorResponse(e.getMessage());
        } finally {
            OBContext.restorePreviousMode();
        }
    }

    /**
     * Uninstalls the specified dependency if it exists.
     *
     * @param jsonResponse JSON object containing the dependency details.
     * @throws JSONException If there is an issue parsing the JSON data.
     */
    private void uninstallDependency(JSONObject jsonResponse) throws JSONException {
        Dependency dependency = OBDal.getInstance().get(Dependency.class, jsonResponse.get("inpetdepDependencyId"));
        if (dependency != null) {
            UninstallDependency uninstallDependency = new UninstallDependency();
            uninstallDependency.execute(new HashMap<>(), jsonResponse.toString());
        }
    }

    /**
     * Builds a success response after the dependency has been successfully uninstalled.
     *
     * @param jsonResponse JSON object containing details of the uninstalled dependency.
     * @return A JSON object representing a success response.
     * @throws JSONException If there is an issue building the JSON response.
     */
    private JSONObject buildSuccessResponse(JSONObject jsonResponse) throws JSONException {
        String group = jsonResponse.getString("inpdepgroup");
        String artifact = jsonResponse.getString("inpartifact");

        String messageTemplate = OBMessageUtils.messageBD("ETDEP_Dependency_Uninstalled");
        String formattedMessage = String.format(messageTemplate, group, artifact);

        return getResponseBuilder()
                .showMsgInProcessView(ResponseActionsBuilder.MessageType.SUCCESS, "Success", formattedMessage)
                .build();
    }

    /**
     * Builds an error response with the provided error message.
     *
     * @param errorMessage The error message to be included in the response.
     * @return A JSON object representing an error response.
     */
    private JSONObject buildErrorResponse(String errorMessage) {
        return getResponseBuilder()
                .showMsgInProcessView(ResponseActionsBuilder.MessageType.ERROR, "Error", errorMessage)
                .build();
    }
}
