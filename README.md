# LuminaDJ

LuminaDJ ist eine intelligente Java-Anwendung, die visuelle Eindrücke (wie das Kamerabild eines Raumes) analysiert und basierend auf der erkannten Stimmung und dem Kontext (z. B. Bar, Party, Büro) automatisch passende Musik auswählt und abspielt.

## Inhaltsverzeichnis
1. [Voraussetzungen](#voraussetzungen)
2. [Groq API-Key einrichten](#groq-api-key-einrichten)
3. [Projekt starten](#projekt-starten)
4. [Weitere Konfigurationen](#weitere-konfigurationen) *(Platzhalter für später)*

---

## Voraussetzungen
* Java 17 (oder neuer)
* Maven
* Ein gültiger Groq API-Key für die visuelle KI-Analyse.

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

## Projekt starten
*(Bisher noch kein richtiges Deployment umgesetzt)*
