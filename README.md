# 🎧 LuminaDJ

<p align="center">
  <img src="https://img.shields.io/badge/Angular-DD0031?style=for-the-badge&logo=angular&logoColor=white" />
  <img src="https://img.shields.io/badge/Electron-47848F?style=for-the-badge&logo=electron&logoColor=white" />
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" />
  <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white" />
  <img src="https://img.shields.io/badge/OpenCV-5C3EE8?style=for-the-badge&logo=opencv&logoColor=white" />
</p>

**LuminaDJ** ist dein intelligenter, interaktiver Musik-Player, der sich nahtlos an deine Stimmung anpasst. Anstatt statische Playlists abzuspielen, generiert die Software eine dynamische Reise durch verschiedene Genres und Audio-Features (Macro-Curves).

Das absolute Highlight: Du kannst die Musik komplett freihändig über deine Webcam steuern. Eine fortschrittliche Computer-Vision-Engine im Hintergrund wertet deine Handgesten in Echtzeit aus und ermöglicht es dir, Songs zu pausieren, Genres zu überspringen oder deine Lieblings-Vibes für die KI zu priorisieren.

---

## 📖 Anwendungshandbuch & Demo

### ✨ Freihändige Gestensteuerung
LuminaDJ wartet im Hintergrund intelligent auf deine Eingaben, ohne versehentliche Bewegungen auszuwerten.

<p align="center">
  <img src="docs/assets/luminadj-demo.gif" alt="Gestensteuerung in Aktion" width="650" style="border-radius: 8px; box-shadow: 0 4px 8px rgba(0,0,0,0.2);"/>
</p>

**So funktioniert's:**
1. **Aktivieren:** Halte die **offene Hand** ✋ für 2 Sekunden still vor die Kamera. Das System meldet sich mit "READY".
2. **Aktion wählen:** Du hast nun 7 Sekunden Zeit für eine Aktion. Forme eine der folgenden Gesten und halte sie kurz (2 Sekunden), um sie einzuloggen:
   * 👆 **Zeigefinger:** Play / Pause
   * ✌️ **Peace:** Aktuelles Genre überspringen (Skip Genre)
   * 👍 **Daumen hoch:** Song liken & Vibe für zukünftige Empfehlungen priorisieren

### 🖥️ Die Benutzeroberfläche

Die App ist übersichtlich aufgebaut und führt dich in zwei simplen Schritten zu deiner perfekten Session:

<table style="width:100%; border: none;">
  <tr>
    <td width="50%" align="center" style="border: none;">
      <img src="docs/assets/screenshot-session-setup.png" alt="Session Setup" width="100%" style="border-radius: 8px;"/>
      <br><br>
      <b>1. Session Setup</b><br>
      Hier definierst du die Parameter deiner Reise. Lege die Gesamtdauer fest und wähle die gewünschten Genres (z.B. ein fließender Übergang von Acoustic zu Deep-House).
    </td>
    <td width="50%" align="center" style="border: none;">
      <img src="docs/assets/screenshot-active-session.png" alt="Active Session" width="100%" style="border-radius: 8px;"/>
      <br><br>
      <b>2. Active Session</b><br>
      Das Herzstück der App. Oben siehst du das Live-Bild der Kamera-Auswertung. Darunter visualisiert die interaktive Timeline, wo du dich gerade in deinem musikalischen Übergang befindest.
    </td>
  </tr>
</table>

---

## 🏗️ Architektur

LuminaDJ besteht aus einem modernen **Electron/Angular Frontend** und einem leistungsstarken **Spring Boot Java Backend**. Die Kommunikation läuft komplett über REST-APIs.

Ein intelligenter `HybridSourceAdapter` kombiniert eine lokale Vektor-Datenbank (für saubere musikalische Übergänge) mit der Spotify-API (als Fallback).

### 🧩 Klassendiagramm
Das Klassendiagramm zeigt die Entkopplung der Module und den Fokus auf saubere Interfaces (Strategies & Adapters).

<p align="center">
  <img src="docs/assets/klassendiagramm.png" alt="Klassendiagramm" width="800" />
</p>

* **Frontend:** Das Angular-UI greift nur auf REST-Controller zu.
* **Prediction:** Verschiedene Strategien (Timeline, Priorisierung) streiten im `PredictionAggregator` um den perfekten Vibe des nächsten Songs.
* **Music:** Ein hybrider Ansatz garantiert, dass auch bei ausgefallener lokaler Datenbank immer ein Song via Spotify gefunden wird.

### ⚙️ Ablauf der Musik-Session
Das Aktivitätsdiagramm veranschaulicht den Lebenszyklus einer DJ-Session, von der Nutzer-Konfiguration bis zur Berechnung des nächsten Songs.<br/>
Der Prozess läuft in einer Endlosschleife, bis die Session beendet wird. Parallel zur reinen Musikwiedergabe läuft der `GestureRecognitionService` asynchron mit und überwacht die Umgebung auf Nutzereingaben.

<p align="center">
  <img src="docs/assets/aktivitaetsdiagramm.png" alt="Aktivitätsdiagramm" width="600" />
</p>

## 🚀 Projekt starten & bauen

### Voraussetzungen
Bevor du das Projekt zum ersten Mal startest oder baust, musst du sicherstellen, dass dein System vorbereitet ist:

* **Java 21:** Für beide Ausführungsvarianten ist zwingend Java 21 (oder neuer) erforderlich. Prüfe deine aktive Version im Terminal mit dem Befehl:
  `java -version`
* **Abhängigkeiten installieren (einmalig):**
  Bevor du lokal entwickelst, müssen die Pakete für Backend und Frontend heruntergeladen werden.
   * Navigiere in den Ordner `backend` und führe `mvn clean install` aus.
   * Navigiere in den Ordner `frontend` und führe `npm install` aus.

---

### 1. Lokale Entwicklung: "Start LuminaDJ (Full Build)"
Nutze dieses Skript in IntelliJ für die alltägliche Entwicklung und zum Testen der App.
* Führt zuerst im `backend`-Ordner einen Maven-Build (`clean package`) aus.
* Führt danach im `frontend`-Ordner den Befehl `npm run start:desktop` aus.
* **Ergebnis:** Die App startet im lokalen Entwicklungsmodus als Electron-Fenster.

### 2. Release erstellen: "App-Release"
Nutze dieses Skript in IntelliJ, wenn du eine fertige, installierbare App (z. B. als `.exe`) generieren möchtest.
* Führt ebenfalls zuerst im `backend`-Ordner einen Maven-Build (`clean package`) aus.
* Führt danach im `frontend`-Ordner den Befehl `npm run build:app` aus.
* **Ergebnis:** Der Electron-Builder verpackt das Frontend sowie die gebaute Backend-`.jar` und legt das fertige Setup im Ordner `frontend/release/` ab.