OB.ETDEP = {};
OB.ETDEP.UninstallDependency = {};

OB.ETDEP.UninstallDependency.onLoad = function(view) {
  var message = "<li>" + OB.I18N.getLabel("ETDEP_Must_Compile") + "</li>";
  var sndMessage = "";
  var selectedRecord = view.parentWindow.view.viewGrid.getSelectedRecord();
  var format = selectedRecord.format;
  var module = selectedRecord.group + '.' + selectedRecord.artifact;

  if (format === 'S' || format === 'L') {
    format = format == 'S'? "SOURCE" : "LOCAL";
    // Each replace only replaces the first appeareance of the placeholder
    sndMessage = "<li>" + OB.I18N.getLabel("ETDEP_Will_Delete_Source_Dirs").replace('%s', module) + "</li>";
    sndMessage = sndMessage.replace('%s', format);
    sndMessage = sndMessage.replace('%s', module);
  }
  message = "<ul>" + message + sndMessage + "</ul>";

  view.messageBar.setMessage(
    'warning',
    'Warning',
    message
  );
};
