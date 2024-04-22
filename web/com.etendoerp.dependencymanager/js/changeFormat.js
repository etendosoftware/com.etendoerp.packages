OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.ChangeFormat = OB.ETDEP.ChangeFormat || {};

OB.ETDEP.ChangeFormat.onLoad = function (view) {
  var form = view.theForm;
  var newFormatField = form.getItem('newFormat');
  var selectedRecords = view.parentWindow.view.viewGrid.getSelectedRecords();
  var currentFormat = selectedRecords[0].format;

  // Function to handle the response for setting new format values
  function handleNewFormatResponse(response, data, request) {
    var newFormats = data.newFormats;
    var currentValues = newFormatField.getValueMap();
    var newValues = {};

    newFormats.forEach(function (newFormat) {
      if (currentValues[newFormat]) {
        newValues[newFormat] = currentValues[newFormat];
      }
    });

    newFormatField.setValueMap(newValues);
  }

  // Call to update the format options
  OB.RemoteCallManager.call(
    'com.etendoerp.dependencymanager.defaults.ChangeFormatDefaults',
    { currentFormat: currentFormat },
    {},
    handleNewFormatResponse
  );

  // Additional call if the current format is 'L'
  if (currentFormat == 'L') {
    function handleSelectLatestCompVersionsResponse(response, data, request) {
      if (data.dependencies != null) {
        var messageType = 'warning';
        view.messageBar.setMessage(messageType, messageType.toUpperCase(), data.message);
      }
    }

    OB.RemoteCallManager.call(
      'com.etendoerp.dependencymanager.process.SelectLatestCompVersions',
      { records: selectedRecords },
      {},
      handleSelectLatestCompVersionsResponse
    );
  } else if (currentFormat == 'S') {
    var messageType = 'warning';
    var depJavaPackage = selectedRecords[0].group + "." + selectedRecords[0].artifact;
    view.messageBar.setMessage(messageType, messageType.toUpperCase(),
      appendWarnAboutDeletingSourceFiles("", depJavaPackage, currentFormat));
  }
}

OB.ETDEP.ChangeFormat.onChange = function (item, view, form) {
  view.messageBar.hide();
  var newFormatField = form.getItem('newFormat');
  var selectedRecords = view.parentWindow.view.viewGrid.getSelectedRecords();
  var currentFormat = selectedRecords[0].format;
  var message = "";
  var messageType = "success";
  if ((currentFormat == 'L' || currentFormat == 'S') && newFormatField.getValue() == 'J') {
    var depJavaPackage = selectedRecords[0].group + "." + selectedRecords[0].artifact;
    messageType = "warning";
    message = appendWarnAboutDeletingSourceFiles("", depJavaPackage, currentFormat);
  }
  if (currentFormat == 'L') {
    OB.RemoteCallManager.call(
      'com.etendoerp.dependencymanager.process.SelectLatestCompVersions',
      { records: selectedRecords },
      {},
      function (response, data, request) {
        message = data.message + "</br>" + message;
        if (data.dependencies != null) {
            messageType = 'warning';
        }
        view.messageBar.setMessage(messageType, messageType.toUpperCase(), message)
      }
    );
  }
}

function appendWarnAboutDeletingSourceFiles(message, module, format) {
  var formatName = format == 'S' ? "SOURCE" : "LOCAL";
  // Each replace only replaces the first appearance of the placeholder
  var sndMessage = OB.I18N.getLabel("ETDEP_Will_Delete_Source_Dirs").replace('%s', module);
  sndMessage = sndMessage.replace('%s', formatName);
  sndMessage = sndMessage.replace('%s', module);

  return message + sndMessage;
}
