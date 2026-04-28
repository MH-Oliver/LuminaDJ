package modules.vision.strategies.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class DetectionStrategyLangChain4j implements DetectionStrategy {

    private final OpenAiChatModel model;
    private final DetectionStrategy fallback = new DetectionStrategyMock();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DetectionStrategyLangChain4j(String apiKey) {
        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.groq.com/openai/v1")
                .modelName("meta-llama/llama-4-scout-17b-16e-instruct")
                .responseFormat("json_object")
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Override
    public FrameDataDTO analyse(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            UserMessage userMessage = UserMessage.from(
                    TextContent.from("Analysiere das angehängte Bild sehr genau. Zähle alle sichtbaren Personen im Raum, achte dabei besonders auf die dunklen Silhouetten und Personen im Hintergrund. Schätze zudem die grundlegende Stimmung (emotion) der Szene ein. Antworte AUSSCHLIESSLICH in validem JSON in exakt folgendem Format: {\"personCount\": <Zahl>, \"emotion\": \"Fear\" | \"Happy\" | \"Sad\" | \"Anger\"}"),
                    ImageContent.from(base64Image, "image/png")
            );

            String responseText = model.chat(userMessage).aiMessage().text();

            return objectMapper.readValue(responseText, FrameDataDTO.class);

        } catch (Exception e) {
            System.err.println("KI-Fehler: " + e.getMessage());
            System.out.println("Nutze Fallback-Daten (Mock)...");
            return fallback.analyse(image);
        }
    }
}