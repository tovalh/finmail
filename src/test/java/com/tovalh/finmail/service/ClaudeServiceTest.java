package com.tovalh.finmail.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ClaudeServiceTest {

    private ClaudeService claudeService;

    @BeforeEach
    void setUp() {
        claudeService = spy(new ClaudeService("fake-key", "claude-haiku-5-5"));
    }

    @Test
    void sugerirCategoria_retornaCategoriaDelJson() throws Exception {
        doReturn("{\"categoria\": \"Supermercado\"}").when(claudeService).llamarClaude(anyString(), any(), anyInt());

        String resultado = claudeService.sugerirCategoria("LIDER", List.of("Supermercado", "Combustible", "Otro"));

        assertThat(resultado).isEqualTo("Supermercado");
    }

    @Test
    @SuppressWarnings("unchecked")
    void sugerirCategoria_limitaLaRespuestaALasCategoriasExistentes() throws Exception {
        doReturn("{\"categoria\": \"Otro\"}").when(claudeService).llamarClaude(anyString(), any(), anyInt());
        ArgumentCaptor<Map<String, Object>> schema = ArgumentCaptor.forClass(Map.class);

        claudeService.sugerirCategoria("LIDER", List.of("Supermercado", "Otro"));

        verify(claudeService).llamarClaude(anyString(), schema.capture(), anyInt());
        Map<String, Object> propiedades = (Map<String, Object>) schema.getValue().get("properties");
        Map<String, Object> categoria = (Map<String, Object>) propiedades.get("categoria");
        assertThat(categoria.get("enum")).isEqualTo(List.of("Supermercado", "Otro"));
        assertThat(schema.getValue().get("additionalProperties")).isEqualTo(false);
    }

    @Test
    void sugerirCategoria_retornaOtro_cuandoLaApiFalla() throws Exception {
        doThrow(new RuntimeException("API error")).when(claudeService).llamarClaude(anyString(), any(), anyInt());

        String resultado = claudeService.sugerirCategoria("DESCONOCIDO", List.of("Supermercado", "Otro"));

        assertThat(resultado).isEqualTo("Otro");
    }

    @Test
    void sugerirCategoria_retornaOtro_cuandoJsonEsMalformado() throws Exception {
        doReturn("esto no es json").when(claudeService).llamarClaude(anyString(), any(), anyInt());

        String resultado = claudeService.sugerirCategoria("LIDER", List.of("Supermercado", "Otro"));

        assertThat(resultado).isEqualTo("Otro");
    }

    @Test
    void sugerirCategoriasBatch_retornaMapaConTodasLasEntradas() throws Exception {
        String json = """
                {"resultados": [
                    {"nombre": "LIDER", "categoria": "Supermercado"},
                    {"nombre": "COPEC", "categoria": "Combustible"}
                ]}
                """;
        doReturn(json).when(claudeService).llamarClaude(anyString(), any(), anyInt());

        Map<String, String> resultado = claudeService.sugerirCategoriasBatch(
                List.of("LIDER", "COPEC"),
                List.of("Supermercado", "Combustible", "Otro")
        );

        assertThat(resultado)
                .containsEntry("LIDER", "Supermercado")
                .containsEntry("COPEC", "Combustible")
                .hasSize(2);
    }

    @Test
    void sugerirCategoriasBatch_retornaMapaVacio_cuandoLaApiFalla() throws Exception {
        doThrow(new RuntimeException("API error")).when(claudeService).llamarClaude(anyString(), any(), anyInt());

        Map<String, String> resultado = claudeService.sugerirCategoriasBatch(
                List.of("LIDER", "COPEC"),
                List.of("Supermercado", "Otro")
        );

        assertThat(resultado).isEmpty();
    }

    @Test
    void sugerirCategoriasBatch_divideEnLotesDe50() throws Exception {
        doReturn("{\"resultados\": []}").when(claudeService).llamarClaude(anyString(), any(), anyInt());
        List<String> comercios = IntStream.range(0, 120).mapToObj(i -> "COMERCIO " + i).toList();

        claudeService.sugerirCategoriasBatch(comercios, List.of("Supermercado", "Otro"));

        verify(claudeService, times(3)).llamarClaude(anyString(), any(), anyInt());
    }
}
