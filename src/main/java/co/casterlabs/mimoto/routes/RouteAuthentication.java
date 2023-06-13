package co.casterlabs.mimoto.routes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import co.casterlabs.mimoto.Mimoto;
import co.casterlabs.mimoto.accounts.Account;
import co.casterlabs.mimoto.preprocess.MimotoPreProcessor;
import co.casterlabs.mimoto.preprocess.MimotoPreProcessorConfig;
import co.casterlabs.mimoto.preprocess.MimotoRequestData;
import co.casterlabs.mimoto.session.SessionUtil;
import co.casterlabs.rakurai.io.http.HttpMethod;
import co.casterlabs.rakurai.io.http.StandardHttpStatus;
import co.casterlabs.rakurai.io.http.server.HttpResponse;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.element.JsonObject;
import co.casterlabs.sora.api.http.HttpProvider;
import co.casterlabs.sora.api.http.SoraHttpSession;
import co.casterlabs.sora.api.http.annotations.HttpEndpoint;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class RouteAuthentication implements HttpProvider {

    @HttpEndpoint(uri = "/public/v3/auth/register", allowedMethods = {
            HttpMethod.POST
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = Register_PreProcessorConfig.class)
    public HttpResponse onRegister(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            String name = body.getString("name");
            String email = body.getString("email");
            String password = body.getString("password");

            if (Account.lookupAccountByEmail(email) != null) {
                // I know, I know, this defeats the purpose of the checks in the Login handler,
                // but that's just to deter script kiddies.
                return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.BAD_REQUEST, "An account already exists with that email.", "UNAUTHORIZED");
            }

            Account account = Account.createAccount(name, email, password);

            JsonObject response = new JsonObject()
                .put("account", Rson.DEFAULT.toJson(account).getAsObject())
                .put("token", account.issueToken());

            return SessionUtil.create(
                requestData.getSessionMeta(), StandardHttpStatus.CREATED, null,
                response
            );
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    @HttpEndpoint(uri = "/public/v3/auth/login", allowedMethods = {
            HttpMethod.POST
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = Login_PreProcessorConfig.class)
    public HttpResponse onLogin(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            String email = body.getString("email");
            String password = body.getString("password");

            Account account = Account.lookupAccountByEmail(email);

            // We check if the account is valid and if the user can login.
            // This helps mitigate brute force attacks (the attacker won't know which emails
            // are valid)
            if ((account != null) && account.tryLogin(password)) {
                String token = account.issueToken();

                return SessionUtil.create(
                    requestData.getSessionMeta(), StandardHttpStatus.OK, null,
                    JsonObject.singleton("token", token)
                );
            } else {
                return SessionUtil.create(
                    requestData.getSessionMeta(), StandardHttpStatus.BAD_REQUEST,
                    "That email and password combination do not match any on record.",
                    "UNAUTHORIZED"
                );
            }
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    /* -------------------------------- */
    /* -------------------------------- */
    /* -------------------------------- */

    public static class Register_PreProcessorConfig implements MimotoPreProcessorConfig {

        @Override
        public @NonNull List<String> getRequiredBodyProperties() {
            return Arrays.asList("name", "email", "password");
        }

        @Override
        public @NonNull Map<String, String> getBodyRegex() {
            return Collections.singletonMap("email", Mimoto.EMAIL_REGEX);
        }

        @Override
        public boolean shouldRateLimit() {
            return true;
        }

        @Override
        public boolean doAuth() {
            return false;
        }

        @Override
        public boolean requireAuth() {
            return false;
        }

        @Override
        public boolean requireVerifiedEmail() {
            return false;
        }

    }

    public static class Login_PreProcessorConfig implements MimotoPreProcessorConfig {

        @Override
        public @NonNull List<String> getRequiredBodyProperties() {
            return Arrays.asList("email", "password");
        }

        @Override
        public @NonNull Map<String, String> getBodyRegex() {
            return Collections.singletonMap("email", Mimoto.EMAIL_REGEX);
        }

        @Override
        public boolean shouldRateLimit() {
            return true;
        }

        @Override
        public boolean doAuth() {
            return false;
        }

        @Override
        public boolean requireAuth() {
            return false;
        }

        @Override
        public boolean requireVerifiedEmail() {
            return false;
        }

    }

}
