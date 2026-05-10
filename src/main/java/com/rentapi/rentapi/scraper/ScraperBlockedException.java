package com.rentapi.rentapi.scraper;

/**
 * Se lanza cuando BrowserHttpClient detecta que la petición ha sido bloqueada
 * por un WAF (403, CAPTCHA, HTML vacío, redirect de bot-detection).
 *
 * ScraperRetryFilter la captura para marcar la fuente como BLOQUEADA
 * y activar el fallback correspondiente.
 */
public class ScraperBlockedException extends Exception {

    public ScraperBlockedException(String message) {
        super(message);
    }

    public ScraperBlockedException(String message, Throwable cause) {
        super(message, cause);
    }
}
