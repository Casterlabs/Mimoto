package co.casterlabs.mimoto.session;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;
import org.jongo.Jongo;

import co.casterlabs.rakurai.io.http.HttpStatus;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.io.http.server.DropConnectionException;
import co.casterlabs.rakurai.io.http.server.HttpResponse;
import co.casterlabs.rakurai.io.http.server.HttpSession;
import co.casterlabs.rakurai.json.element.JsonArray;
import co.casterlabs.rakurai.json.element.JsonNull;
import co.casterlabs.rakurai.json.element.JsonObject;
import lombok.NonNull;
import lombok.Setter;

public class SessionUtil {
    private static final JsonArray EMPTY_ARRAY = new JsonArray();
    private static final long MAX_QPS = 15;

    private static @Setter Jongo apiJongo;

    public static SessionMeta getSessionMeta(@NonNull HttpSession session, boolean countTowardsRateLimit) throws DropConnectionException {
        String ip = session.getRemoteIpAddress();

        if (ip == null) {
            ip = "0.0.0.0";
            session
                .getLogger()
                .severe(
                    "Unable to get an ip address for a http session, raw response: %s, %s",
                    session.getRemoteIpAddress(),
                    session.getRequestHops()
                );
        }

        return getSessionMeta(ip, countTowardsRateLimit);
    }

    public static SessionMeta getSessionMeta(@NonNull String ip, boolean countTowardsRateLimit) throws DropConnectionException {
        long current = System.currentTimeMillis();

        // Insert the request.
        if (countTowardsRateLimit && !ip.equals("0.0.0.0")) {
            Date expiresAt = new Date(current + TimeUnit.SECONDS.toMillis(1));

            apiJongo
                .getCollection("ratelimits")
                .insert(
                    "{ip: #, timestamp: #, expiresAt: #}",
                    ip,
                    current,
                    expiresAt
                );
        }

        // Create the meta.
        {
            long period = current - TimeUnit.SECONDS.toMillis(1);

            long apiHits = apiJongo
                .getCollection("ratelimits")
                .count(
                    "{ip: #, timestamp: { $gt: # }}",
                    ip,
                    period
                );

            long remaining = MAX_QPS - apiHits;

            return new SessionMeta(ip, remaining, countTowardsRateLimit);
        }
    }

    public static HttpResponse create(@Nullable SessionMeta meta, @NonNull HttpStatus status, @Nullable String note, @NonNull JsonObject data) {
        JsonObject payload = new JsonObject();

        payload.put("data", data);
        payload.put("errors", EMPTY_ARRAY);

        return newResponse(meta, status, note, payload);
    }

    public static HttpResponse createTooManyRequestsResponse(@NonNull SessionMeta meta) {
        return create(meta, StandardHttpStatus.TOO_MANY_REQUESTS, null, "TOO_MANY_REQUESTS");
    }

    public static HttpResponse create(@Nullable SessionMeta meta, @NonNull HttpStatus status, @Nullable String note, @NonNull Enum<?>... errors) {
        JsonObject payload = new JsonObject();
        JsonArray array = new JsonArray();

        for (Enum<?> error : errors) {
            array.add(error.name());
        }

        payload.put("data", JsonNull.INSTANCE);
        payload.put("errors", array);

        return newResponse(meta, status, note, payload);
    }

    public static HttpResponse create(@Nullable SessionMeta meta, @NonNull HttpStatus status, @Nullable String note, @NonNull String... errors) {
        JsonObject payload = new JsonObject();
        JsonArray array = JsonArray.of((Object[]) errors);

        payload.put("data", JsonNull.INSTANCE);
        payload.put("errors", array);

        return newResponse(meta, status, note, payload);
    }

    private static HttpResponse newResponse(@Nullable SessionMeta meta, @NonNull HttpStatus status, @Nullable String note, @NonNull JsonObject payload) {
        if (note != null) {
            payload.put("__note", note);
        }

        HttpResponse response = HttpResponse.newFixedLengthResponse(status, payload);

        response.setMimeType("application/json");

        if (meta != null) {
            response.putHeader("x-requested-as", meta.getIp());
            response.putHeader("x-ratelimit-remaining", String.valueOf(meta.getRatelimitRemaining()));
            response.putHeader("x-ratelimit-success", String.valueOf(!meta.shouldBlock()));
            response.putHeader("x-request-counted-against-ratelimit", String.valueOf(meta.isDidCountAgainstRateLimit()));
        }

        return response;
    }

}
