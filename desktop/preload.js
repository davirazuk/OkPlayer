// Gives the page just the window controls for its own title bar.
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("okDesktop", {
  minimize: () => ipcRenderer.send("window", "minimize"),
  maximize: () => ipcRenderer.send("window", "maximize"),
  close: () => ipcRenderer.send("window", "close"),
  platform: process.platform,
});
