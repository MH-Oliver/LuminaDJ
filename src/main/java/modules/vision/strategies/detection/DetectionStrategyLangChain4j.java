package modules.vision.strategies.detection;

import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;
import modules.vision.structures.Emotion;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class DetectionStrategyLangChain4j implements DetectionStrategy {

    interface VisionAnalyzer {
        // Hier geben wir der KI nun eine glasklare Anweisung mit auf den Weg!
        @UserMessage("Analysiere das angehängte Bild sehr genau. Zähle alle sichtbaren Personen im Raum, achte dabei besonders auf die dunklen Silhouetten und Personen im Hintergrund. Schätze zudem die grundlegende Stimmung (emotion) der Szene ein.")
        FrameDataDTO analyze(Image image);
    }

    private final VisionAnalyzer analyzer;
    // Mock als Sicherheitsnetz
    private final DetectionStrategy fallback = new DetectionStrategyMock();

    public DetectionStrategyLangChain4j(String apiKey) {
        var model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                // Nutze hier das aktuelle Modell, das auch im Free Tier unterstützt wird!
                .modelName("gemini-2.5-flash")
                .logRequestsAndResponses(true)
                .build();

        this.analyzer = AiServices.create(VisionAnalyzer.class, model);
    }
    @Override
    public FrameDataDTO analyse(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            Image langchainImage = Image.builder()
                    .base64Data(base64Image)
                    .mimeType("image/png")
                    .build();

            return analyzer.analyze(langchainImage);
        } catch (Exception e) {
            System.err.println("KI-Fehler (Quota/Limit): " + e.getMessage());
            System.out.println("Nutze Fallback-Daten (Mock)...");
            // Bei Fehler (z.B. 429) werden Mock-Daten geliefert
            return fallback.analyse(image);
        }
    }
}