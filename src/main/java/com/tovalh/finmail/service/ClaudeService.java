package com.tovalh.finmail.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sugiere categorías con la API de Claude. La respuesta se pide con un JSON schema
 * cuyo enum son las categorías del usuario, así el modelo no puede inventar una que no exista.
 */
@Service
public class ClaudeService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeService.class);
    private static final String URL = "https://api.anthropic.com/v1/messages";
    private static final String VERSION_API = "2023-06-01";
    // Con lotes muy grandes la respuesta puede cortarse por max_tokens; 50 comercios por llamada sobra.
    private static final int TAMANO_LOTE = 50;

    private static final String INSTRUCCIONES = """
            Clasificas gastos personales de un usuario chileno.
            Los nombres de comercio vienen abreviados o en mayúsculas, tal como aparecen en los avisos del Banco de Chile.
            Elige la categoría más probable aunque el nombre esté abreviado. Por ejemplo, LIDER es Supermercado,
            UBER*EATS es Delivery, COPEC es Combustible y NETFLIX es Suscripciones.
            Si existe la categoría "Otro", úsala solo cuando de verdad no haya forma de saberlo.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String apiKey;
    private final String modelo;

    public ClaudeService(@Value("${app.claude.api-key}") String apiKey,
                         @Value("${app.claude.model:claude-haiku-5-5}") String modelo) {
        this.apiKey = apiKey;
        this.modelo = modelo;
        this.restClient = RestClient.create();
    }

    public String sugerirCategoria(String comercio, List<String> categorias) {
        Map<String, Object> schema = objeto(Map.of("categoria", unaDe(categorias)));
        try {
            return objectMapper.readTree(llamarClaude("Comercio: " + comercio, schema, 256))
                    .path("categoria").asText();
        } catch (Exception e) {
            log.error("Error al pedir a Claude la categoría de {}: {}", comercio, e.getMessage());
            return "Otro";
        }
    }

    /**
     * Clasifica varios comercios en lotes. Si un lote falla se pierde solo ese lote;
     * esos comercios quedan sin sugerencia y el usuario los categoriza a mano.
     */
    public Map<String, String> sugerirCategoriasBatch(List<String> comercios, List<String> categorias) {
        Map<String, String> resultado = new HashMap<>();
        for (int i = 0; i < comercios.size(); i += TAMANO_LOTE) {
            List<String> lote = comercios.subList(i, Math.min(i + TAMANO_LOTE, comercios.size()));
            try {
                resultado.putAll(clasificarLote(lote, categorias));
            } catch (Exception e) {
                log.error("Error al clasificar con Claude un lote de {} comercios: {}", lote.size(), e.getMessage());
            }
        }
        return resultado;
    }

    private Map<String, String> clasificarLote(List<String> lote, List<String> categorias) throws Exception {
        Map<String, Object> item = objeto(Map.of(
                "nombre", Map.of("type", "string"),
                "categoria", unaDe(categorias)));
        Map<String, Object> schema = objeto(Map.of("resultados", Map.of("type", "array", "items", item)));
        String pedido = "Clasifica cada comercio y devuelve su nombre tal cual viene:\n" + String.join("\n", lote);

        JsonNode respuesta = objectMapper.readTree(llamarClaude(pedido, schema, 256 + lote.size() * 48));
        Map<String, String> resultado = new HashMap<>();
        respuesta.path("resultados").forEach(r ->
                resultado.put(r.path("nombre").asText(), r.path("categoria").asText()));
        return resultado;
    }

    /** Devuelve el texto JSON de la respuesta. Protegido para poder reemplazarlo en los tests. */
    protected String llamarClaude(String pedido, Map<String, Object> schema, int maxTokens) throws Exception {
        Map<String, Object> body = Map.of(
                "model", modelo,
                "max_tokens", maxTokens,
                "system", INSTRUCCIONES,
                "messages", List.of(Map.of("role", "user", "content", pedido)),
                // Clasificar no necesita razonamiento extendido: sin thinking la respuesta es más rápida y barata.
                "thinking", Map.of("type", "disabled"),
                "output_config", Map.of(
                        "effort", "low",
                        "format", Map.of("type", "json_schema", "schema", schema)));

        String respuesta = restClient.post()
                .uri(URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", VERSION_API)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
        if (respuesta == null) {
            throw new IllegalStateException("Claude devolvió una respuesta vacía");
        }

        JsonNode root = objectMapper.readTree(respuesta);
        String stopReason = root.path("stop_reason").asText();
        if ("max_tokens".equals(stopReason) || "refusal".equals(stopReason)) {
            throw new IllegalStateException("Respuesta incompleta de Claude (stop_reason=" + stopReason + ")");
        }
        for (JsonNode bloque : root.path("content")) {
            if ("text".equals(bloque.path("type").asText())) {
                return bloque.path("text").asText();
            }
        }
        throw new IllegalStateException("La respuesta de Claude no trae texto");
    }

    private static Map<String, Object> objeto(Map<String, Object> propiedades) {
        return Map.of(
                "type", "object",
                "properties", propiedades,
                "required", List.copyOf(propiedades.keySet()),
                "additionalProperties", false);
    }

    private static Map<String, Object> unaDe(List<String> valores) {
        return Map.of("type", "string", "enum", valores);
    }
}
