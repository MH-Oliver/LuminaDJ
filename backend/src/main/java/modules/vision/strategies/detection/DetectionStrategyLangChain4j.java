package modules.vision.strategies.detection;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import modules.userContext.strategies.core.UserContextStrategy;
import modules.userContext.structures.UserContextDTO;
import modules.vision.strategies.core.DetectionStrategy;
import modules.vision.structures.FrameDataDTO;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

/**
 * Implementierung der {@link DetectionStrategy}, die LangChain4j nutzt, um Bilder
 * über die GroqCloud (mit dem multimodalen Llama 4 Modell) zu analysieren.
 * Diese Strategie extrahiert die Anzahl der Personen sowie die Bewegungsintensität
 * aus einem Bild und berücksichtigt dabei den dynamischen User-Kontext (z. B. die Location).
 */
public class DetectionStrategyLangChain4j implements DetectionStrategy {

    private final OpenAiChatModel model;
    private final DetectionStrategy fallback = new DetectionStrategyMock();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final UserContextStrategy contextStrategy;

    public DetectionStrategyLangChain4j(UserContextStrategy contextStrategy) {
        this.contextStrategy = contextStrategy;

        Config conf = ConfigFactory.load();
        String apiKey = conf.getString("groq.apiKey");

        this.model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl("https://api.groq.com/openai/v1")
                .modelName("meta-llama/llama-4-scout-17b-16e-instruct")
                .responseFormat("json_object")
                .logRequests(false)
                .logResponses(false)
                .build();
    }

    /**
     * Analysiert das übergebene Bild mithilfe des LLMs und gibt strukturierte Daten zurück.
     * Der Prompt wird dabei dynamisch anhand des aktuellen Kontexts aus der
     * {@link UserContextStrategy} angepasst.
     * Sollte die KI-Anfrage fehlschlagen (z. B. wegen Rate-Limits), wird auf eine
     * Fallback-Strategie (Mock) zurückgegriffen.
     *
     * @param image   Das zu analysierende Bild (z.B. ein Frame aus einem Videostream).
     * @return Ein {@link FrameDataDTO}, das die Anzahl der Personen und die Intensität enthält.
     */
    @Override
    public FrameDataDTO analyse(BufferedImage image) {
        try {
        } catch (IllegalStateException e) {
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

            String promptText = getPromptText(contextStrategy.getUserContext());

            UserMessage userMessage = UserMessage.from(
                    TextContent.from(promptText),
                    ImageContent.from(base64Image, "image/png")
            );

            String responseText = model.chat(userMessage).aiMessage().text();

            if (responseText.contains("```")) {
                responseText = responseText.replaceAll("```json", "").replaceAll("```", "").trim();
            }

            return objectMapper.readValue(responseText, FrameDataDTO.class);

        } catch (Exception e) {
            System.err.println("VLM analysis failed, falling back to mock: " + e.getMessage());
            return fallback.analyse(image);
        }
    }

    private String getPromptText(UserContextDTO context) {
        String locationName = context != null ? context.location().name() : "Unbekannt";

        return String.format(
                "Analysiere das angehängte Bild sehr genau. Der Nutzer hat angegeben, dass sich diese Szene in folgendem Kontext abspielt: '%s'. " +
                        "Bitte passe deine visuelle Analyse an diese Umgebung an (z.B. erwarte schlechte Lichtverhältnisse in einer Bar, oder schnelle Bewegungen auf einer Party). " +
                        "Zähle alle sichtbaren Personen im Raum (personCount). " +
                        "Schätze zudem die Bewegungsintensität bzw. Energie der Szene (intensity) auf einer Skala von 0 bis 100 ein (0 = alle sitzen/stehen völlig ruhig, 100 = alle tanzen und springen wild). " +
                        "Antworte AUSSCHLIESSLICH in validem JSON in exakt folgendem Format: {\"intensity\": <Zahl>, \"personCount\": <Zahl>}",
                locationName
        );
    }
}
