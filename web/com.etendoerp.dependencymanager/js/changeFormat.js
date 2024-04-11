OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.ChangeFormat = OB.ETDEP.ChangeFormat || {};

OB.ETDEP.ChangeFormat.onLoad = function(view) {
  var form = view.theForm;
  var newFormatField = form.getItem('newFormat');
  var selectedRecords = view.parentWindow.view.viewGrid.getSelectedRecords();
  var currentFormat = selectedRecords[0].format;

  // Function to handle the response for setting new format values
  function handleNewFormatResponse(response, data, request) {
    var newFormats = data.newFormats;
    var currentValues = newFormatField.getValueMap();
    var newValues = {};

    newFormats.forEach(function(newFormat) {
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
  if (currentFormat === 'L') {
    function handleSelectLatestCompVersionsResponse(response, data, request) {
      var msg = data.message;
      var messageType = data.warning ? 'warning' : 'info';
      if (msg != null) {
        view.messageBar.setMessage(messageType, messageType.toUpperCase(), msg);
      }
    }

    OB.RemoteCallManager.call(
      'com.etendoerp.dependencymanager.process.SelectLatestCompVersions',
      { records: selectedRecords },
      {},
      handleSelectLatestCompVersionsResponse
    );
  }
}
