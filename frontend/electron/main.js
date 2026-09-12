const { app, BrowserWindow, ipcMain, shell } = require('electron');
const { spawn, exec} = require('node:child_process');
const path = require('node:path');

let backendProcess;

function startBackend() {
  const jarPath = app.isPackaged
    ? path.join(process.resourcesPath, 'lumina-backend-1.0-SNAPSHOT.jar')
    : path.resolve(__dirname, '../../backend/target/lumina-backend-1.0-SNAPSHOT.jar');

  const cwdPath = app.isPackaged
    ? process.resourcesPath
    : path.resolve(__dirname, '../..');

  backendProcess = spawn('java', ['-jar', jarPath], {
    cwd: cwdPath,
    windowsHide: true,
    env: { ...process.env }
  });

  backendProcess.on('error', (error) => {
    console.error('Java Backend Fehler:', error.message);
  });

  backendProcess.on('exit', () => {
    backendProcess = undefined;
  });
}

function stopBackend() {
  if (backendProcess && !backendProcess.killed) {
    if (process.platform === 'win32') {
      exec(`taskkill /F /T /PID ${backendProcess.pid}`, (err) => {
        if (err) console.error('Fehler beim Beenden des Java-Prozesses:', err);
      });
    } else {
      backendProcess.kill('SIGTERM');
    }
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1200,
    height: 800,
    icon: path.join(__dirname, '../icon.png'),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.resolve(__dirname, 'preload.js'),
    },
  });

  // Entfernt die native System-Menüleiste
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

  if (process.env.LUMINA_SKIP_BACKEND !== 'true') {
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
