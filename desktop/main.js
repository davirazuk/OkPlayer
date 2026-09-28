// okplayer desktop: the web player in a frameless window whose Aero title bar is the frame.
const { app, BrowserWindow, ipcMain } = require("electron");
const path = require("path");

function createWindow() {
  const win = new BrowserWindow({
    width: 520,
    height: 900,
    minWidth: 380,
    minHeight: 600,
    frame: false,
    backgroundColor: "#135a91",
    title: "okplayer",
    icon: path.join(__dirname, "web", "icon.svg"),
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      sandbox: true,
    },
  });
  win.loadFile(path.join(__dirname, "web", "index.html"));

  // Links in the page (lyrics credits, the repo) open in the browser, not in the player.
  win.webContents.setWindowOpenHandler(({ url }) => {
    require("electron").shell.openExternal(url);
    return { action: "deny" };
  });
}

ipcMain.on("window", (event, action) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (!win) return;
  if (action === "minimize") win.minimize();
  else if (action === "maximize") (win.isMaximized() ? win.unmaximize() : win.maximize());
  else if (action === "close") win.close();
});

app.whenReady().then(() => {
  createWindow();
  app.on("activate", () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
  });
});

app.on("window-all-closed", () => {
  if (process.platform !== "darwin") app.quit();
});
