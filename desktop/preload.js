// Gives the page its window controls, the taskbar buttons and files opened with okplayer.
const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("okDesktop", {
  minimize: () => ipcRenderer.send("window", "minimize"),
  maximize: () => ipcRenderer.send("window", "maximize"),
  close: () => ipcRenderer.send("window", "close"),
  platform: process.platform,
  // Tells the taskbar thumbnail whether to show play or pause.
  setState: (state) => ipcRenderer.send("state", { playing: !!(state && state.playing) }),
  // "prev", "toggle" or "next" from the taskbar thumbnail buttons.
  onCommand: (fn) => ipcRenderer.on("command", (_event, cmd) => fn(cmd)),
  // Files opened with okplayer: [{ name, relPath, data }].
  onOpenFiles: (fn) => ipcRenderer.on("open-files", (_event, files) => fn(files)),
});
