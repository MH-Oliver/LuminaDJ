const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  openExternalUrl: (url) => ipcRenderer.invoke('open-external-url', url),
});
