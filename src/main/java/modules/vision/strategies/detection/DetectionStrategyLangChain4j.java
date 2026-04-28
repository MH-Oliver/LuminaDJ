package modules.vision.strategies.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import modules.userContext.structures.UserContextDTO;
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
                .logResponses(false)
                .build();
    }

    @Override
    public FrameDataDTO analyse(BufferedImage image, UserContextDTO context) { // <- Parameter angepasst
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            // Prompt dynamisch anhand der Location aufbauen
            String locationName = context != null ? context.location().name() : "Unbekannt";

            String promptText = String.format(
                    "Analysiere das angehängte Bild sehr genau. Der Nutzer hat angegeben, dass sich diese Szene in folgendem Kontext abspielt: '%s'. " +
                            "Bitte passe deine visuelle Analyse an diese Umgebung an (z.B. erwarte schlechte Lichtverhältnisse in einer Bar, oder schnelle Bewegungen auf einer Party). " +
                            "Zähle alle sichtbaren Personen im Raum. Schätze zudem die grundlegende Stimmung (emotion) der Szene ein. " +
                            "Antworte AUSSCHLIESSLICH in validem JSON in exakt folgendem Format: {\"personCount\": <Zahl>, \"emotion\": \"Fear\" | \"Happy\" | \"Sad\" | \"Anger\"}",
                    locationName
            );

            UserMessage userMessage = UserMessage.from(
                    TextContent.from(promptText),
                    ImageContent.from(base64Image, "image/png")
            );

            String responseText = model.chat(userMessage).aiMessage().text();
            return objectMapper.readValue(responseText, FrameDataDTO.class);

        } catch (Exception e) {
            return fallback.analyse(image, context);
        }
    }
}