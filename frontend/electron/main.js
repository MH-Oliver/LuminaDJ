const { app, BrowserWindow, ipcMain, shell } = require('electron');
const { spawn } = require('node:child_process');
const path = require('node:path');

let backendProcess;

function startBackend() {
  const jarPath = path.resolve(__dirname, '../../backend/target/lumina-backend-1.0-SNAPSHOT.jar');

  backendProcess = spawn('java', ['-jar', jarPath], {
    cwd: path.resolve(__dirname, '../..'),
    stdio: 'inherit',
    windowsHide: true,
    env: {
      ...process.env
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
