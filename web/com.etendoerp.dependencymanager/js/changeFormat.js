OB.ETDEP = OB.ETDEP || {};
OB.ETDEP.ChangeFormat = OB.ETDEP.ChangeFormat || {};

OB.ETDEP.ChangeFormat.onLoad = function(view) {
  var form = view.theForm;
  var newFormatField = form.getItem('newFormat');
  var selectedRecords = view.parentWindow.view.viewGrid.getSelectedRecords();
  var currentFormat = selectedRecords[0].format;

  OB.RemoteCallManager.call(
      'com.etendoerp.dependencymanager.defaults.ChangeFormatDefaults',
      {
        currentFormat: currentFormat
      },
      {
      },
      function(response, data, request) {
        var newFormats = data.newFormats;
        var currentValues = newFormatField.getValueMap();
        var newValues = {};

        for (i = 0; i < newFormats.length; i++) {
          var newFormat = newFormats[i];
          if (currentValues[newFormat]) {
              newValues[newFormat] = currentValues[newFormat];
          }
        }

        newFormatField.setValueMap(newValues);
      }
  );
  if(currentFormat == "L") {
      OB.RemoteCallManager.call(
          'com.etendoerp.dependencymanager.process.SelectLatestCompVersions',
          {
            records: selectedRecords
          },
          {},
          function(response, data, request) {
            var msg = data.message;
            var messageType = 'info';
            if (data.warning) {
              messageType = 'warning'
            }
            if (msg != null) {
              view.messageBar.setMessage(messageType, messageType === 'warning' ? 'Warning' : 'Information', msg);
            }
          }
      );
  }
}
