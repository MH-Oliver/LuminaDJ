const { app, BrowserWindow, ipcMain, shell } = require('electron');
const { spawn } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

let backendProcess;

function loadEnvFromFile() {
  const envPath = path.resolve(__dirname, '../../.env');
  if (!fs.existsSync(envPath)) {
    return {};
  }

  const parsed = {};
  const lines = fs.readFileSync(envPath, 'utf8').split(/\r?\n/);

  for (const rawLine of lines) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;

    const separatorIndex = line.indexOf('=');
    if (separatorIndex <= 0) continue;

    const key = line.slice(0, separatorIndex).trim();
    let value = line.slice(separatorIndex + 1).trim();

    if (
      (value.startsWith('"') && value.endsWith('"')) ||
      (value.startsWith("'") && value.endsWith("'"))
    ) {
      value = value.slice(1, -1);
    }

    parsed[key] = value;
  }

  return parsed;
}

function startBackend() {
  const jarPath = path.resolve(__dirname, '../../backend/target/lumina-backend-1.0-SNAPSHOT.jar');
  const fileEnv = loadEnvFromFile();

  backendProcess = spawn('java', ['-jar', jarPath], {
    cwd: path.resolve(__dirname, '../..'),
    stdio: 'inherit',
    windowsHide: true,
    env: {
      ...process.env,
      SPOTIFY_CLIENT_SECRET:
        process.env.SPOTIFY_CLIENT_SECRET ?? fileEnv.SPOTIFY_CLIENT_SECRET ?? '',
      GROQ_API_KEY: process.env.GROQ_API_KEY ?? fileEnv.GROQ_API_KEY ?? '',
    }
  });

  backendProcess.on('exit', () => {
    backendProcess = undefined;
  });
}

function stopBackend() {
  if (backendProcess && !backendProcess.killed) {
    backendProcess.kill('SIGTERM');
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.resolve(__dirname, 'preload.js'),
    },
  });

  // Entfernt die native System-Menüleiste (Datei, Bearbeiten, Ansicht...)
  win.removeMenu();

  const startUrl = process.env.ELECTRON_START_URL;
  if (startUrl) {
    win.loadURL(startUrl);

    win.webContents.openDevTools();
  } else {
    const indexPath = path.resolve(__dirname, '../dist/frontend/browser/index.html');
    win.loadFile(indexPath);
  }
}

app.whenReady().then(() => {
  ipcMain.handle('open-external-url', async (_event, url) => {
    if (typeof url !== 'string' || !/^https?:\/\//i.test(url)) {
      throw new Error('Ungültige URL');
    }
    await shell.openExternal(url);
  });

  if (!process.env.ELECTRON_START_URL) {
    startBackend();
  }

  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  stopBackend();
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

app.on('before-quit', stopBackend);
