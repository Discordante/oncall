package com.nextdigital.oncall.zadarma.client;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nextdigital.oncall.config.ZadarmaConfig;
import com.nextdigital.oncall.exception.ZadarmaApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Low-level HTTP client for the Zadarma VoIP API.
 * Handles HMAC-SHA1 authentication signing and communicates with
 * {@code https://api.zadarma.com/v1/}.
 *
 * <p>
 * Follows the same pattern as
 * {@link com.nextdigital.oncall.telegram.client.TelegramApiClient}.
 * </p>
 *
 * <p>
 * Only 2 API methods are needed (NOT the old 3-call flow):
 * </p>
 * <ul>
 * <li>{@code PUT /v1/sip/redirection/} — set call forwarding</li>
 * <li>{@code GET /v1/sip/redirection/} — check current forwarding
 * (idempotency)</li>
 * </ul>
 *
 * @see <a href="https://zadarma.com/es/support/api/#intro">Zadarma API docs</a>
 */
@Slf4j
@Component
public class ZadarmaApiClient {

    private static final String API_BASE = "https://api.zadarma.com";

    private final RestClient restClient;
    private final ZadarmaConfig config;
    private final String baseUrl;

    public ZadarmaApiClient(ZadarmaConfig config) {
        this.config = config;
        this.baseUrl = config.getApi().getBaseUrl() != null && !config.getApi().getBaseUrl().isBlank()
                ? config.getApi().getBaseUrl()
                : API_BASE;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    // ──────────────────────────────────────────────
    // Public API methods
    // ──────────────────────────────────────────────

    /**
     * Sets SIP call forwarding to a phone number.
     *
     * @param sipId       the Zadarma SIP ID (e.g. "921495")
     * @param phoneNumber the destination phone number in international format
     *                    without "+" prefix (e.g. "34653177761")
     * @return the API response
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaResponse setSipRedirection(String sipId, String phoneNumber) {
        var params = new TreeMap<String, String>();
        params.put("id", sipId);
        params.put("type", "phone");
        params.put("number", phoneNumber);
        params.put("format", "json");

        log.debug("Setting SIP redirection: sipId={}, phone={}", sipId, maskPhone(phoneNumber));

        return execute(params, "PUT", "/v1/sip/redirection/");
    }

    /**
     * Gets the current SIP redirection state.
     *
     * @param sipId the Zadarma SIP ID
     * @return the API response with current redirection details
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaResponse getSipRedirection(String sipId) {
        var params = new TreeMap<String, String>();
        params.put("id", sipId);
        params.put("format", "json");

        log.debug("Getting SIP redirection status: sipId={}", sipId);

        return execute(params, "GET", "/v1/sip/redirection/");
    }

    /**
     * Disables SIP call forwarding (turns redirection off).
     *
     * @param sipId the Zadarma SIP ID
     * @return the API response
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaResponse disableSipRedirection(String sipId) {
        var params = new TreeMap<String, String>();
        params.put("id", sipId);
        params.put("status", "off");
        params.put("format", "json");

        log.debug("Disabling SIP redirection: sipId={}", sipId);

        return execute(params, "PUT", "/v1/sip/redirection/");
    }

    // ──────────────────────────────────────────────
    // HTTP execution
    // ──────────────────────────────────────────────

    private ZadarmaResponse execute(Map<String, String> params, String method, String apiPath) {
        String paramsString = buildQueryString(params);
        String signature = buildSignature(method, apiPath + paramsString, paramsString);

        var headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", config.getApi().getKey() + ":" + signature);

        try {
            var requestEntity = org.springframework.http.RequestEntity
                    .method(org.springframework.http.HttpMethod.valueOf(method),
                            baseUrl + apiPath + "?" + paramsString)
                    .headers(httpHeaders -> httpHeaders.putAll(headers))
                    .build();

            // For PUT, we need to send form-urlencoded body.
            // The params are in the query string per Zadarma auth requirements,
            // and the body is also form-encoded.
            if ("PUT".equals(method)) {
                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                params.forEach(formData::add);
                return restClient.put()
                        .uri(uriBuilder -> uriBuilder.path(apiPath).build())
                        .headers(httpHeaders -> httpHeaders.putAll(headers))
                        .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                        .body(formData)
                        .retrieve()
                        .body(ZadarmaResponse.class);
            } else {
                // GET — only query string params
                return restClient.get()
                        .uri(uriBuilder -> uriBuilder.path(apiPath)
                                .queryParams(toMultiValueMap(params))
                                .build())
                        .headers(httpHeaders -> httpHeaders.putAll(headers))
                        .retrieve()
                        .body(ZadarmaResponse.class);
            }
        } catch (RestClientException e) {
            log.error("Zadarma API call failed: {} {} — {}", method, apiPath, e.getMessage());
            throw new ZadarmaApiException("Zadarma API call failed: " + apiPath + " — " + e.getMessage(), e);
        }
    }

    // ──────────────────────────────────────────────
    // Authentication: HMAC-SHA1 signing
    // ──────────────────────────────────────────────

    /**
     * Builds the HMAC-SHA1 signature for Zadarma API authentication.
     *
     * <p>
     * Algorithm per <a href="https://zadarma.com/es/support/api/#intro">Zadarma
     * docs</a>:
     * </p>
     * <ol>
     * <li>Sort params alphabetically by key</li>
     * <li>Build query string (RFC 1738)</li>
     * <li>Compute MD5 of the query string</li>
     * <li>Concatenate: method + paramsString + md5(paramsString)</li>
     * <li>Sign with HMAC-SHA1 using the API secret</li>
     * <li>Base64-encode the result</li>
     * </ol>
     *
     * @param httpMethod   the HTTP method name (e.g. "GET", "PUT")
     * @param fullPath     the full API path including version (e.g.
     *                     "/v1/sip/redirection/")
     * @param paramsString the alphabetically sorted query string
     * @return Base64-encoded HMAC-SHA1 signature
     */
    static String buildSignature(String httpMethod, String fullPath, String paramsString) {
        // Per the official Zadarma PHP SDK, the $method parameter in getAuthHeader()
        // is the API path (e.g. "/v1/sip/"), NOT the HTTP method ("GET"/"PUT").
        // The signature data is: apiPath + paramsString + md5(paramsString).
        // Since fullPath = apiPath + paramsString, we can simply use:
        String data = fullPath + md5(paramsString);
        return hmacSha1(data);
    }

    private static String buildQueryString(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
    }

    // ──────────────────────────────────────────────
    // Crypto helpers
    // ──────────────────────────────────────────────

    private static String md5(String input) {
        try {
            var md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }

    static String hmacSha1(String data) {
        try {
            String secret = getSecret();
            var mac = Mac.getInstance("HmacSHA1");
            var keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1");
            mac.init(keySpec);
            byte[] result = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            // Per Zadarma PHP SDK: hash_hmac('sha1', data, secret) returns HEX string,
            // then base64_encode() encodes that hex string.
            // So we must hex-encode the raw HMAC bytes first, then Base64 the hex.
            String hexResult = bytesToHex(result);
            return Base64.getEncoder().encodeToString(hexResult.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA1 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * URL-encodes a string per RFC 1738.
     * Only alphanumeric, ".", "-", "_", and "~" are left unencoded.
     */
    private static String encode(String value) {
        var sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_' || c == '~') {
                sb.append(c);
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append('%').append(String.format("%02X", b));
                }
            }
        }
        return sb.toString();
    }

    private static MultiValueMap<String, String> toMultiValueMap(Map<String, String> params) {
        var map = new LinkedMultiValueMap<String, String>();
        params.forEach(map::add);
        return map;
    }

    // ──────────────────────────────────────────────
    // Utilities
    // ──────────────────────────────────────────────

    /**
     * Masks a phone number for logging (shows last 4 digits only).
     */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() <= 4) {
            return "***";
        }
        return "***" + phone.substring(phone.length() - 4);
    }

    /**
     * Gets the API secret from config. Uses a holder for static method access.
     */
    private static String getSecret() {
        return SecretHolder.secret;
    }

    /**
     * Thread-safe holder for the API secret, initialized at startup.
     * This allows the static signing methods to access the secret without
     * requiring an instance.
     */
    static final class SecretHolder {
        private SecretHolder() {
        }

        static volatile String secret = "";
    }

    /**
     * Initializes the secret holder. Called automatically by Spring after
     * construction.
     */
    @jakarta.annotation.PostConstruct
    void initSecret() {
        SecretHolder.secret = config.getApi().getSecret();
        log.info("ZadarmaApiClient initialized with base URL: {}", config.getApi().getBaseUrl());
    }
}
