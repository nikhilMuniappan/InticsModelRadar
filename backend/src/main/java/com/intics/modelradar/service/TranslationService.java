package com.intics.modelradar.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Best-effort on-demand translation to English, used for tooltips on non-English
 * model details (mainly foreign-language descriptions). Only text that actually
 * contains non-Latin letters is translated; everything else is treated as English
 * and skipped. Results are cached in-memory; any failure falls back to the original
 * (the UI simply shows the untranslated text).
 *
 * <p>Uses the keyless Google translate endpoint. It's unofficial but adequate for an
 * internal tool; if it's ever blocked, translation quietly degrades to "no translation".
 */
@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);
    private static final int MAX_QUERY = 800;   // keep the GET URL sane
    private static final int MAX_CACHE = 10_000;

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public TranslationService(WebClient.Builder builder) {
        HttpClient httpClient = HttpClient.create()
                .followRedirect(true)
                .responseTimeout(Duration.ofSeconds(6));
        this.webClient = builder
                .baseUrl("https://translate.googleapis.com")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    /** True if the text contains any non-Latin letter (CJK, Cyrillic, Arabic, …). */
    public boolean isNonEnglish(String text) {
        return text != null && text.codePoints().anyMatch(TranslationService::isNonLatinLetter);
    }

    /**
     * English translation of non-English text, or {@code null} if the text is blank,
     * already English/Latin, or translation failed. Cached.
     */
    public String toEnglish(String text) {
        if (text == null || text.isBlank() || !isNonEnglish(text)) {
            return null;
        }
        String key = text.length() > MAX_QUERY ? text.substring(0, MAX_QUERY) : text;
        String cached = cache.get(key);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }
        String result = call(key);
        if (cache.size() < MAX_CACHE) {
            cache.put(key, result == null ? "" : result);
        }
        return result;
    }

    private String call(String text) {
        try {
            String body = webClient.get()
                    .uri(uri -> uri.path("/translate_a/single")
                            .queryParam("client", "gtx")
                            .queryParam("sl", "auto")
                            .queryParam("tl", "en")
                            .queryParam("dt", "t")
                            .queryParam("q", text)
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode segments = mapper.readTree(body).get(0);
            if (segments == null || !segments.isArray()) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            for (JsonNode seg : segments) {
                JsonNode chunk = seg.get(0);
                if (chunk != null && !chunk.isNull()) {
                    sb.append(chunk.asText());
                }
            }
            String translated = sb.toString().trim();
            return translated.isEmpty() ? null : translated;
        } catch (Exception e) {
            log.debug("Translation failed: {}", e.toString());
            return null;
        }
    }

    private static boolean isNonLatinLetter(int cp) {
        if (!Character.isLetter(cp)) {
            return false;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(cp);
        return script != Character.UnicodeScript.LATIN && script != Character.UnicodeScript.COMMON;
    }
}
