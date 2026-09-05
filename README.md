# LuminaDJ

LuminaDJ ist eine intelligente Java-Anwendung, die visuelle Eindrücke (wie das Kamerabild eines Raumes) analysiert und basierend auf der erkannten Stimmung und dem Kontext (z. B. Bar, Party, Büro) automatisch passende Musik auswählt und abspielt.

## Inhaltsverzeichnis
1. [Voraussetzungen](#voraussetzungen)
2. [Groq API-Key einrichten](#groq-api-key-einrichten)
3. [Live-Kamera-Feedback über Smartphone](#live-kamera-feedback-über-smartphone)
4. [Mathematische Methodik: HistoryStrategy](#mathematische-methodik-historystrategy)
5. [Projekt starten](#projekt-starten)
6. [Weitere Konfigurationen](#weitere-konfigurationen)

---

## Voraussetzungen
* Java 21
* Maven
* Node.js 20+ und npm

---

## Groq API-Key einrichten
LuminaDJ nutzt die GroqCloud und multimodale Llama-Modelle, um die Bilder performant zu analysieren. Damit das funktioniert, benötigst du einen kostenlosen API-Key von Groq.

Folge diesen Schritten, um deinen Schlüssel zu erhalten:

1. **Account erstellen:** Besuche die [GroqCloud Console](https://console.groq.com/) und logge dich ein (oder erstelle dir einen neuen, kostenlosen Account).
2. **API Keys öffnen:** Klicke in der linken Seitenleiste auf den Menüpunkt **"API Keys"**.
3. **Key generieren:** Klicke auf den Button **"Create API Key"**.
4. **Benennen & Speichern:** Gib dem Key einen Namen (z. B. `LuminaDJ-Key`). Sobald der Schlüssel generiert wurde, kopiere ihn sofort. **Wichtig:** Aus Sicherheitsgründen wird der Schlüssel danach nie wieder vollständig angezeigt!
5. **In LuminaDJ einfügen:** Setze die Umgebungsvariable `GROQ_API_KEY` in der Run-Config, bevor du die App startest

---

## Live-Kamera-Feedback über Smartphone
Als `LiveFeedbackStrategy` steht die Klasse `SmartphoneKameraStrategy` zur Verfügung.
Hier wird eine Live-Verbindung zu der Kamera von einem Smartphone über eine HTTP Schnittstelle realisiert.

**Einrichtung:**
1. Für Android-Geräte kann über den Google-Play-Store die App `IP Webcam` installiert werden.
2. Sicherstellen, dass PC und Smartphone im gleichen Netzwerk.
3. In der App ganz nach unten scrollen, dort den Button `Server starten` klicken.
4. Ist der Server gestartet, kann nun auch der Code ausgeführt werden.
   - Die Verbindung zum Smartphone sollte innerhalb weniger Sekunden automatisch hergestellt werden.
   - Falls automatische Verbindung fehlgeschlagen, 
   muss die IP-Adresse (in der Smartphone App ganz unten zu finden) manuell im Dialog eingegeben werden.

---

## Mathematische Methodik: HistoryStrategy

Die `HistoryStrategy` nutzt ein Verfahren aus dem Bereich des maschinellen Lernens namens **Locally Weighted Learning (LWL)** in Kombination mit einer **Radial Basis Function (Gauß-Kernel)**.

Anstatt einen simplen Durchschnitt aller vergangenen Songs zu bilden, berechnet der Algorithmus für den aktuellen Song ($x$) die musikalische Ähnlichkeit zu jedem historischen Song ($x_i$). Diese Ähnlichkeit wird mit dem damaligen Live-Feedback der Crowd gewichtet, um den optimalen Zielwert für das nächste Lied vorherzusagen.

Die Berechnung für eine Menge an musikalischen Attributen $\mathcal{A}$ (Energy, BPM, Danceability, etc.) erfolgt in 6 Schritten:

### 1. Distanzmessung (Normalisierte Euklidische Distanz)
Zuerst wird die musikalische Distanz $d$ zwischen dem aktuellen Track $x$ und einem historischen Track $x_i$ im 6-dimensionalen Raum berechnet. Um zu verhindern, dass große Werte (wie BPM) kleine Werte (wie Energy) dominieren, wird die BPM-Differenz durch 200 normalisiert.

$$d(x, x_i) = \sqrt{ \sum_{a \in \mathcal{A}} \Delta_a(x, x_i)^2 }$$

*(Wobei $\Delta_a = \frac{x.\text{bpm} - x_i.\text{bpm}}{200}$ für BPM gilt, und $\Delta_a = x.a - x_i.a$ für alle anderen Attribute).*

### 2. Gauß-Kernel (Ähnlichkeitsfunktion)
Die berechnete Distanz wird durch eine Gaußsche Glockenkurve in einen Ähnlichkeitswert $K$ transformiert. $\sigma$ (im Code `0.5`) bestimmt die Bandbreite. Songs, die sehr ähnlich klingen, erhalten einen Wert nahe `1.0`, völlig andere Songs fallen exponentiell gegen `0.0`.

$$K(x, x_i) = \exp\left( -\frac{d(x, x_i)^2}{2\sigma^2} \right)$$

### 3. Feedback Reward (Erfolgsgewichtung)
Ein historischer Song ist nur wertvoll, wenn er auch gut bei der Crowd ankam. Sei $f_i$ die gemessene Intensität der Kamera und $T_i$ der Zustand des Trends (positiv/negativ). Bei einem negativen Trend wird das Gewicht stark bestraft (Penalty), um Fehler nicht zu wiederholen.

$$R(f_i) = \begin{cases} f_i & \text{falls } T_i = \text{positiv} \\ (1.0 - f_i) \cdot 0.2 & \text{falls } T_i = \text{negativ} \end{cases}$$

### 4. Lokales Gesamtgewicht
Das Stimmrecht (Gesamtgewicht $w_i$) eines historischen Songs für die Vorhersage ergibt sich aus seiner musikalischen Nähe zum aktuellen Song und seinem damaligen Erfolg.

$$w_i = K(x, x_i) \cdot R(f_i)$$

### 5. Zielwert-Vorhersage (Lokal gewichteter Mittelwert)
Der geschätzte optimale Zielwert $\hat{y}_a$ für ein spezifisches Attribut $a$ (z.B. Energy) berechnet sich nun aus der Summe aller historischen Werte dieses Attributs, gewichtet mit ihrem jeweiligen Stimmrecht $w_i$, geteilt durch die Summe aller abgegebenen Stimmen.

$$\hat{y}_a = \frac{\sum_{i=1}^{n} w_i \cdot x_i.a}{\sum_{i=1}^{n} w_i}$$

### 6. Anpassungsfaktor (Für den Aggregator)
Da der `PredictionAggregator` Multiplikatoren erwartet, wird der errechnete Zielwert durch den Ist-Wert des aktuellen Tracks geteilt.

$$\text{Faktor}_a = \frac{\hat{y}_a}{x.a}$$

---

## Ganzes Projekt starten
### 1) Frontend-Abhängigkeiten installieren
```bash
npm --prefix frontend ci
```
Erwartet:
- Installation läuft ohne Fehler durch.
- `frontend/node_modules` ist vorhanden.

### 2) API-Keys hinterlegen
`/.env.template` nach `/.env` kopieren und Werte eintragen:
- `SPOTIFY_CLIENT_SECRET=...`
- `GROQ_API_KEY=...` (irrelevant, wenn Smartphone nicht verbunden)

Beim Start lädt Electron diese Werte automatisch und gibt sie an das Backend weiter.

### 2.2) Song auf Spotify kurz starten
Es muss Spotify geöffnet werden, und ein beliebiger Song kurz gestartet werden, kann auch direkt wieder gestoppt werden.


### 3) Skript starten
Unter den Run-Configs findet sich "Start LuminaDJ (Full Build)".
Diese normal über Intellij starten.

Erwartet:
- Electron-Fenster öffnet sich.
- Im Terminal erscheint `LuminaDJ backend is running on http://localhost:8081`.
- Beim Klick auf den Button in der UI erscheint ein Erfolgsstatus (`Dummy-Context erfolgreich an das Backend gesendet.`).
- Es wird Musik abgespielt

## Nur das Frontend starten
### 1) Ganzes Projekt einmal starten
### 2) Frontend-Start (mit Live-Update bei File-Changes)
```bash
npm run start:live
```
