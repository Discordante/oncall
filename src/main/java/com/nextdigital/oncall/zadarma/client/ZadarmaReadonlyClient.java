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
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.nextdigital.oncall.config.ZadarmaConfig;
import com.nextdigital.oncall.exception.ZadarmaApiException;
import com.nextdigital.oncall.zadarma.dto.ZadarmaDirectNumbersResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaSipListResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaSipRedirectionResponse;
import com.nextdigital.oncall.zadarma.dto.ZadarmaStatsResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Read-only {@link RestClient} for Zadarma analytics API endpoints.
 * <p>
 * Handles HMAC-SHA1 authentication signing (same scheme as
 * {@link ZadarmaApiClient}) but is focused exclusively on HTTP GET
 * operations for query/analytics endpoints that do not mutate state
 * on the Zadarma side.
 * </p>
 *
 * <p>
 * Supported operations:
 * </p>
 * <ul>
 * <li>{@code GET /v1/sip/} — list SIP numbers</li>
 * <li>{@code GET /v1/sip/redirection/} — view current SIP redirections</li>
 * <li>{@code GET /v1/statistics/incoming-calls/} — incoming call statistics
 * with optional date range</li>
 * </ul>
 *
 * @see <a href="https://zadarma.com/es/support/api/#intro">Zadarma API docs</a>
 */
@Slf4j
@Component
public class ZadarmaReadonlyClient {

    private static final String API_BASE = "https://api.zadarma.com";

    private final RestClient restClient;
    private final String baseUrl;

    public ZadarmaReadonlyClient(ZadarmaConfig config) {
        this.baseUrl = config.getApi().getBaseUrl() != null && !config.getApi().getBaseUrl().isBlank()
                ? config.getApi().getBaseUrl()
                : API_BASE;
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
        initSecret(config.getApi().getKey(), config.getApi().getSecret());
    }

    // ──────────────────────────────────────────────
    // Public read-only API methods
    // ──────────────────────────────────────────────

    /**
     * Retrieves the list of SIP IDs associated with the account.
     *
     * @return the deserialized SIP list response
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaSipListResponse getSipList() {
        var params = new TreeMap<String, String>();
        params.put("format", "json");

        log.debug("Fetching SIP list from Zadarma");

        return executeGet("/v1/sip/", params, ZadarmaSipListResponse.class);
    }

    /**
     * Retrieves incoming call statistics, optionally filtered by date range and
     * SIP.
     *
     * @param start start of the period ({@code YYYY-MM-DD HH:MM:SS}), or null
     * @param end   end of the period ({@code YYYY-MM-DD HH:MM:SS}), or null
     * @param sip   optional SIP number filter
     * @return the deserialized statistics response
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaStatsResponse getStatistics(String start, String end, String sip) {
        var params = new TreeMap<String, String>();
        params.put("format", "json");
        if (start != null && !start.isBlank()) {
            params.put("start", start);
        }
        if (end != null && !end.isBlank()) {
            params.put("end", end);
        }
        if (sip != null && !sip.isBlank()) {
            params.put("sip", sip);
        }

        log.debug("Fetching incoming call statistics: start={}, end={}, sip={}", start, end, sip);

        return executeGet("/v1/statistics/incoming-calls/", params, ZadarmaStatsResponse.class);
    }

    /**
     * Retrieves the current SIP call forwarding (redirection) configuration.
     * <p>
     * When called without a {@code sipId}, returns redirections for all SIP
     * numbers belonging to the account. When a specific {@code sipId} is
     * provided, returns only the redirection for that SIP.
     * </p>
     *
     * @param sipId optional specific SIP ID to filter by, or null for all
     * @return the deserialized redirection response with info list
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaSipRedirectionResponse getSipRedirections(String sipId) {
        var params = new TreeMap<String, String>();
        params.put("format", "json");
        if (sipId != null && !sipId.isBlank()) {
            params.put("id", sipId);
        }

        log.debug("Fetching SIP redirections from Zadarma: sipId={}", sipId != null ? sipId : "all");

        return executeGet("/v1/sip/redirection/", params, ZadarmaSipRedirectionResponse.class);
    }

    /**
     * Retrieves information about the virtual (direct) numbers purchased by the
     * user.
     *
     * @return the deserialized direct numbers response
     * @throws ZadarmaApiException on network or API error
     */
    public ZadarmaDirectNumbersResponse getDirectNumbers() {
        var params = new TreeMap<String, String>();
        params.put("format", "json");

        log.debug("Fetching direct numbers from Zadarma");

        return executeGet("/v1/direct_numbers/", params, ZadarmaDirectNumbersResponse.class);
    }

    // ──────────────────────────────────────────────
    // HTTP execution (RestClient)
    // ──────────────────────────────────────────────

    private <T> T executeGet(String apiPath, Map<String, String> params, Class<T> responseType) {
        String paramsString = buildQueryString(params);
        String signature = buildSignature("GET", apiPath + paramsString, paramsString);

        var headers = new org.springframework.http.HttpHeaders();
        headers.set("Authorization", SecretHolder.key + ":" + signature);

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(apiPath)
                            .queryParams(toMultiValueMap(params))
                            .build())
                    .headers(httpHeaders -> httpHeaders.putAll(headers))
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException e) {
            log.error("Zadarma read-only API call failed: GET {} — {}", apiPath, e.getMessage());
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
     * Algorithm per Zadarma docs:
     * </p>
     * <ol>
     * <li>Sort params alphabetically by key</li>
     * <li>Build query string (RFC 1738)</li>
     * <li>Compute MD5 of the query string</li>
     * <li>Concatenate: method + paramsString + md5(paramsString)</li>
     * <li>Sign with HMAC-SHA1 using the API secret</li>
     * <li>Base64-encode the result</li>
     * </ol>
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
     * URL-encodes a string per RFC 1738 (application/x-www-form-urlencoded).
     * Spaces are encoded as "+" (not "%20") to match PHP's
     * {@code http_build_query} with {@code PHP_QUERY_RFC1738}.
     */
    private static String encode(String value) {
        var sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_' || c == '~') {
                sb.append(c);
            } else if (c == ' ') {
                sb.append('+');
            } else {
                byte[] bytes = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                for (byte b : bytes) {
                    sb.append('%').append(String.format("%02X", b));
                }
            }
        }
        return sb.toString();
    }

    private static org.springframework.util.MultiValueMap<String, String> toMultiValueMap(Map<String, String> params) {
        var map = new org.springframework.util.LinkedMultiValueMap<String, String>();
        params.forEach(map::add);
        return map;
    }

    // ──────────────────────────────────────────────
    // Secret / Key holder
    // ──────────────────────────────────────────────

    private static String getSecret() {
        return SecretHolder.secret;
    }

    private void initSecret(String key, String secret) {
        SecretHolder.key = key != null ? key : "";
        SecretHolder.secret = secret != null ? secret : "";
    }

    @jakarta.annotation.PostConstruct
    void logInit() {
        log.info("ZadarmaReadonlyClient initialized with base URL: {}", baseUrl);
    }

    static final class SecretHolder {
        private SecretHolder() {
        }

        static volatile String secret = "";
        static volatile String key = "";
    }
}
