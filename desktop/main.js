// okplayer desktop: the web player in a frameless window whose Aero title bar is the frame.
const { app, BrowserWindow, ipcMain, nativeImage, screen, shell } = require("electron");
const fs = require("fs");
const path = require("path");

const AUDIO = /\.(flac|mp3|m4a|aac|mp4|ogg|opus|wav)$/i;
let win = null;
let pageReady = false; // the page has loaded and is listening for files
// The files on a command line: everything after the executable except switches and,
// when run with "electron .", the app's own folder.
const fileArgs = (argv) =>
  argv.slice(1).filter((a) => a && !a.startsWith("-") && path.resolve(a) !== path.resolve(app.getAppPath()));
let pending = fileArgs(process.argv); // files okplayer was opened with

// One okplayer at a time: opening a file while it runs hands the file to the open window.
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", (_event, argv) => {
    if (!win) return;
    if (win.isMinimized()) win.restore();
    win.focus();
    openPaths(fileArgs(argv));
  });
  app.on("open-file", (event, file) => {
    event.preventDefault();
    openPaths([file]);
  });
}

/* ---------- window size and position, kept between runs ---------- */

const statePath = () => path.join(app.getPath("userData"), "window.json");

function loadBounds() {
  try {
    const s = JSON.parse(fs.readFileSync(statePath(), "utf8"));
    // Only reuse a position that's still on a connected screen.
    const visible = screen.getAllDisplays().some(({ workArea: a }) =>
      s.x < a.x + a.width - 40 && s.x + s.width > a.x + 40 && s.y >= a.y - 10 && s.y < a.y + a.height - 40);
    return visible ? s : { width: s.width, height: s.height, maximized: s.maximized };
  } catch {
    return {};
  }
}

function saveBounds() {
  if (!win) return;
  try {
    fs.writeFileSync(statePath(), JSON.stringify({ ...win.getNormalBounds(), maximized: win.isMaximized() }));
  } catch {}
}

/* ---------- the window ---------- */

function createWindow() {
  const saved = loadBounds();
  win = new BrowserWindow({
    x: saved.x,
    y: saved.y,
    width: saved.width || 520,
    height: saved.height || 900,
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
  if (saved.maximized) win.maximize();
  win.loadFile(path.join(__dirname, "web", "index.html"));
  win.on("close", saveBounds);
  win.on("closed", () => (win = null));

  // Links in the page (lyrics credits, the repo) open in the browser, not in the player.
  win.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url);
    return { action: "deny" };
  });

  win.webContents.on("did-start-navigation", (details) => {
    if (details.isMainFrame && !details.isSameDocument) pageReady = false;
  });
  win.webContents.on("did-finish-load", () => {
    pageReady = true;
    updateThumbar(false);
    const files = pending;
    pending = [];
    openPaths(files);
  });
}

/* ---------- Windows taskbar: previous / play / next under the thumbnail, like WMP ---------- */

const icon = (name) => nativeImage.createFromPath(path.join(__dirname, "icons", `${name}.png`));

function updateThumbar(playing) {
  if (!win || process.platform !== "win32") return;
  const send = (cmd) => () => win && win.webContents.send("command", cmd);
  win.setThumbarButtons([
    { tooltip: "Previous", icon: icon("prev"), click: send("prev") },
    { tooltip: playing ? "Pause" : "Play", icon: icon(playing ? "pause" : "play"), click: send("toggle") },
    { tooltip: "Next", icon: icon("next"), click: send("next") },
  ]);
}

ipcMain.on("state", (_event, state) => updateThumbar(!!(state && state.playing)));

/* ---------- files opened with okplayer ---------- */

// Collects audio files from the paths given, walking into folders.
function collect(paths) {
  const out = [];
  const walk = (p, base) => {
    let st;
    try { st = fs.statSync(p); } catch { return; }
    if (st.isDirectory()) {
      let names = [];
      try { names = fs.readdirSync(p).sort(); } catch { return; }
      for (const name of names) walk(path.join(p, name), base);
    } else if (AUDIO.test(p)) {
      out.push({ path: p, relPath: path.relative(base, p).split(path.sep).join("/") });
    }
  };
  for (const p of paths) {
    if (!p || p.startsWith("-")) continue;
    const abs = path.resolve(p);
    walk(abs, path.dirname(abs));
  }
  return out;
}

function openPaths(paths) {
  // isLoading() stays true while fonts and the like finish, so readiness is tracked instead.
  if (!win || !pageReady) {
    pending.push(...paths);
    return;
  }
  // Read them here and hand the bytes over; the page never gets to touch the disk itself.
  const payload = [];
  for (const f of collect(paths).slice(0, 500)) {
    try {
      payload.push({ name: path.basename(f.path), relPath: f.relPath, data: fs.readFileSync(f.path) });
    } catch {}
  }
  if (payload.length) win.webContents.send("open-files", payload);
}

ipcMain.on("window", (event, action) => {
  const w = BrowserWindow.fromWebContents(event.sender);
  if (!w) return;
  if (action === "minimize") w.minimize();
  else if (action === "maximize") (w.isMaximized() ? w.unmaximize() : w.maximize());
  else if (action === "close") w.close();
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
