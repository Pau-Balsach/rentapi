package com.rentapi.rentapi.scraper;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.conscrypt.Conscrypt;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cliente HTTP que suplanta un navegador Chrome a nivel de red.
 *
 * Conscrypt reemplaza el TLS provider de Java por BoringSSL, que es
 * exactamente el motor TLS que usa Chrome. El ClientHello resultante
 * es indistinguible del de un Chrome real.
 *
 * Sin Playwright. Sin navegador. Sin RAM extra.
 */
@Slf4j
@Component
public class BrowserHttpClient {

    private OkHttpClient client;

    // Cookie jar en memoria — persiste cookies entre peticiones de la misma sesión.
    // Crítico: algunos WAF validan que la cookie obtenida en la primera petición
    // esté presente en las siguientes.
    private final Map<String, List<Cookie>> cookieStore = new HashMap<>();

    @PostConstruct
    public void init() {
        // Registrar Conscrypt como TLS provider prioritario.
        // Esto reemplaza el ClientHello de Java por el de BoringSSL (Chrome).
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
        log.info("[BrowserHttpClient] Conscrypt TLS provider registrado.");

        client = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .followRedirects(true)
                .cookieJar(new CookieJar() {
                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url.host(), new ArrayList<>(cookies));
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url.host());
                        return cookies != null ? cookies : Collections.emptyList();
                    }
                })
                .build();

        log.info("[BrowserHttpClient] OkHttpClient inicializado.");
    }

    /**
     * Descarga una URL y la devuelve como Document de Jsoup.
     * Las cabeceras imitan exactamente a Chrome 124 en Windows.
     *
     * @throws ScraperBlockedException si WAF devuelve 403, redirect a CAPTCHA,
     *                                  o HTML vacío/inútil.
     */
    public Document fetchDocument(String url) throws ScraperBlockedException {
        return fetchDocument(url, null);
    }

    /**
     * Variante con Referer explícito (útil para paginación).
     */
    public Document fetchDocument(String url, String referer) throws ScraperBlockedException {
        log.debug("[BrowserHttpClient] GET {}", url);

        Request.Builder requestBuilder = new Request.Builder()
                .url(url)
                .header("User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                                "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                "Chrome/124.0.0.0 Safari/537.36")
                .header("Accept",
                        "text/html,application/xhtml+xml,application/xml;" +
                                "q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", referer != null ? "same-origin" : "none")
                .header("Sec-Fetch-User", "?1")
                .header("Sec-Ch-Ua",
                        "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", " +
                                "\"Not-A.Brand\";v=\"99\"")
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Cache-Control", "max-age=0");

        if (referer != null) {
            requestBuilder.header("Referer", referer);
        }

        try (Response response = client.newCall(requestBuilder.build()).execute()) {

            // ── Detección de bloqueo ───────────────────────────────────────────
            if (response.code() == 403 || response.code() == 429) {
                throw new ScraperBlockedException(
                        "HTTP " + response.code() + " en " + url);
            }

            if (response.body() == null) {
                throw new ScraperBlockedException("Respuesta sin body en " + url);
            }

            String html = response.body().string();

            // HTML demasiado corto → página de bot-detection sin contenido útil
            if (html.length() < 500) {
                throw new ScraperBlockedException(
                        "HTML sospechosamente corto (" + html.length() + " chars) en " + url);
            }

            // Presencia de tokens WAF sin contenido real
            if (isBlockedPage(html, response)) {
                throw new ScraperBlockedException(
                        "Página de bloqueo WAF detectada en " + url);
            }

            log.debug("[BrowserHttpClient] OK {} — {} chars", url, html.length());
            log.info("[BrowserHttpClient] HTML snippet: {}", html.substring(0, Math.min(2000, html.length())));
            return Jsoup.parse(html, url);

        } catch (ScraperBlockedException e) {
            throw e; // re-lanzar sin envolver
        } catch (Exception e) {
            throw new ScraperBlockedException(
                    "Error de red en " + url + ": " + e.getMessage());
        }
    }

    /**
     * Detecta si la página devuelta es una pantalla de bloqueo WAF / CAPTCHA.
     * Comprueba señales comunes sin depender de un texto concreto
     * (los WAF cambian el mensaje, pero la estructura es reconocible).
     */
    private boolean isBlockedPage(String html, Response response) {
        String htmlLower = html.toLowerCase();

        if (htmlLower.contains("aws-waf-token") && !htmlLower.contains("<article")) {
            log.warn("[BrowserHttpClient] Bloqueo: aws-waf-token sin articles");
            return true;
        }
        if (htmlLower.contains("cf-ray") && htmlLower.contains("checking your browser")) {
            log.warn("[BrowserHttpClient] Bloqueo: Cloudflare challenge");
            return true;
        }
        if (htmlLower.contains("captcha") && !htmlLower.contains("<article")) {
            log.warn("[BrowserHttpClient] Bloqueo: captcha sin articles");
            return true;
        }
        String finalUrl = response.request().url().toString().toLowerCase();
        if (finalUrl.contains("blocked") || finalUrl.contains("captcha") ||
                finalUrl.contains("deny") || finalUrl.contains("error403")) {
            log.warn("[BrowserHttpClient] Bloqueo: URL sospechosa {}", finalUrl);
            return true;
        }
        return false;
    }

    public void setCookies(String host, Map<String, String> cookies) {
        HttpUrl url = HttpUrl.parse("https://" + host);
        List<Cookie> cookieList = cookies.entrySet().stream()
                .map(e -> new Cookie.Builder()
                        .domain(host)
                        .name(e.getKey())
                        .value(e.getValue())
                        .build())
                .collect(java.util.stream.Collectors.toList());
        cookieStore.put(host, cookieList);
    }
}