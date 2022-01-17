package co.casterlabs.mimoto.preprocess;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.mimoto.accounts.Account;
import co.casterlabs.mimoto.session.SessionMeta;
import co.casterlabs.mimoto.session.SessionUtil;
import co.casterlabs.rakurai.io.http.HttpResponse;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonElement;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.rakurai.json.serialization.JsonParseException;
import co.casterlabs.sora.api.http.HttpPreProcessor;
import co.casterlabs.sora.api.http.SoraHttpSession;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class MimotoPreProcessor implements HttpPreProcessor<MimotoPreProcessorConfig> {
    public static final String ID = "co.casterlabs.mimoto.preprocessor";

    private static final String INVALID_AUTH_ERROR = "AUTHORIZATION_INVALID";
    private static final String AUTH_REQUIRED_ERROR = "AUTHORIZATION_REQUIRED";
    private static final String EMAIL_NOT_VERIFIED_ERROR = "ACCOUNT_EMAIL_NOT_VERIFIED";

    @Override
    public @Nullable HttpResponse preprocessHttpSession(MimotoPreProcessorConfig data, @NonNull SoraHttpSession session) {
        try {
            HttpResponse requestLintResponse = lintSession(data, session);
            if (requestLintResponse != null) {
                return requestLintResponse;
            }

            // Rate Limiting
            SessionMeta sessionMeta = SessionUtil.getSessionMeta(session, data.shouldRateLimit());

            if (data.shouldRateLimit() && sessionMeta.shouldBlock()) {
                return SessionUtil.createTooManyRequestsResponse(sessionMeta);
            }

            // Auth
            Account account = null;

            if (data.doAuth()) {
                String token = session.getHeader("Authorization");

                if ((token == null) || !token.startsWith("Bearer ")) {
                    if (data.requireAuth()) {
                        return SessionUtil.create(sessionMeta, StandardHttpStatus.UNAUTHORIZED, null, INVALID_AUTH_ERROR);
                    }
                } else {
                    token = token.substring("Bearer ".length());

                    // Lookup the account by the token.
                    String accountId = Account.getAccountIdFromToken(token);
                    account = Account.lookupAccountById(accountId);

                    if (account == null) {
                        // Check to see if auth is *required*
                        if (data.requireAuth()) {
                            return SessionUtil.create(sessionMeta, StandardHttpStatus.UNAUTHORIZED, null, INVALID_AUTH_ERROR, AUTH_REQUIRED_ERROR);
                        }
                    } else {
                        // See if the token is valid.
                        if (!account.isTokenValid(token)) {
                            account = null;
                            return SessionUtil.create(sessionMeta, StandardHttpStatus.UNAUTHORIZED, null, INVALID_AUTH_ERROR);
                        }

                        // Check if email verification is required.
                        else if (!account.isEmailVerified() && data.requireVerifiedEmail()) {
                            return SessionUtil.create(sessionMeta, StandardHttpStatus.UNAUTHORIZED, null, INVALID_AUTH_ERROR, EMAIL_NOT_VERIFIED_ERROR);
                        }
                    }
                }
            }

            // Attach the data and let Sora continue it's happy sequence :^)
            session.setAttachment(new MimotoRequestData(account, sessionMeta));
            return null;
        } catch (JsonParseException e) {
            return SessionUtil.create(null, StandardHttpStatus.BAD_REQUEST, e.getMessage(), "BAD_REQUEST");
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(null, StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    private static @Nullable HttpResponse lintSession(@NonNull MimotoPreProcessorConfig data, @NonNull SoraHttpSession session) throws IOException {

        // Check required query parameters
        for (String requiredQueryParameter : data.getRequiredQueryParameters()) {
            if (!session.getAllQueryParameters().containsKey(requiredQueryParameter)) {
                return generateLintResponse(ValidationErrorType.MISSING_QUERY_PARAMETER, requiredQueryParameter, null, null);
            }
        }

        // Check required headers
        for (String requiredHeader : data.getRequiredHeaders()) {
            if (!session.getHeaders().containsKey(requiredHeader)) {
                return generateLintResponse(ValidationErrorType.MISSING_HEADER, requiredHeader, null, null);
            }
        }

        // Check body
        if (!data.getRequiredBodyProperties().isEmpty()) {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            for (String requiredProperty : data.getRequiredBodyProperties()) {
                if (!body.containsKey(requiredProperty)) {
                    return generateLintResponse(ValidationErrorType.MISSING_BODY_PROPERTY, requiredProperty, null, null);
                }
            }
        }

        // Check the query parameter regex
        for (Map.Entry<String, String> entry : data.getQueryParameterRegex().entrySet()) {
            String queryParameter = entry.getKey();
            String regex = entry.getValue();

            List<String> values = session.getAllQueryParameters().get(queryParameter);

            if (values != null) {
                for (String value : values) {
                    if (!value.matches(regex)) {
                        return generateLintResponse(ValidationErrorType.INVALID_QUERY_VALUE, queryParameter, value, regex);
                    }
                }
            }
        }

        // Check the header regex
        for (Map.Entry<String, String> entry : data.getHeaderRegex().entrySet()) {
            String header = entry.getKey();
            String regex = entry.getValue();

            List<String> values = session.getHeaders().get(header);

            if (values != null) {
                for (String value : values) {
                    if (!value.matches(regex)) {
                        return generateLintResponse(ValidationErrorType.INVALID_HEADER_VALUE, header, value, regex);
                    }
                }
            }
        }

        // Check body regex
        if (!data.getBodyRegex().isEmpty()) {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            for (Map.Entry<String, String> entry : data.getBodyRegex().entrySet()) {
                String property = entry.getKey();
                String regex = entry.getValue();

                String value = transformJE(body.get(property));

                if (value != null) {
                    if (!value.matches(regex)) {
                        return generateLintResponse(ValidationErrorType.INVALID_BODY_VALUE, property, value, regex);
                    }
                }
            }
        }

        return null;
    }

    private static @Nullable String transformJE(JsonElement e) {
        if ((e == null) || e.isJsonNull()) {
            return null;
        } else if (e.isJsonString()) {
            return e.getAsString();
        } else if (e.isJsonBoolean()) {
            return String.valueOf(e.getAsBoolean());
        } else if (e.isJsonNumber()) {
            double value = e.getAsNumber().doubleValue();

            if (value == Math.floor(value)) {
                return String.valueOf(value);
            } else {
                return String.valueOf(e.getAsNumber().longValue());
            }
        } else {
            return e.toString();
        }
    }

    // DO NOT TOUCH.
    private static HttpResponse generateLintResponse(@NonNull ValidationErrorType error, @Nullable String reason, @Nullable String culprit, @Nullable String rule) {
        String message = null;

        switch (error) {
            case MISSING_QUERY_PARAMETER:
                message = String.format("Missing required query parameter: %s", reason);
                break;

            case MISSING_HEADER:
                message = String.format("Missing required header: %s", reason);
                break;

            case MISSING_BODY_PROPERTY:
                message = String.format("Missing required property in the body: %s", reason);
                break;

            case INVALID_QUERY_VALUE:
                message = String.format("Invalid query value %s=%s, (must match /%s/)", reason, culprit, rule);
                break;

            case INVALID_HEADER_VALUE:
                message = String.format("Invalid header value [%s: %s], (must match /%s/)", reason, culprit, rule);
                break;

            case INVALID_BODY_VALUE:
                message = String.format("Invalid body value {%s: %s}, (must match /%s/)", reason, culprit, rule);
                break;

        }

        return SessionUtil.create(null, StandardHttpStatus.BAD_REQUEST, message, error);
    }

    public static enum ValidationErrorType {
        MISSING_QUERY_PARAMETER,
        MISSING_HEADER,
        MISSING_BODY_PROPERTY,
        INVALID_QUERY_VALUE,
        INVALID_HEADER_VALUE,
        INVALID_BODY_VALUE;
    }

}
