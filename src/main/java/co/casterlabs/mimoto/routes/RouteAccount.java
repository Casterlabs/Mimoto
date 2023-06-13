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

public class RouteAccount implements HttpProvider {

    @HttpEndpoint(uri = "/public/v3/account", allowedMethods = {
            HttpMethod.GET
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = Authorized_PreProcessorConfig.class)
    public HttpResponse onValidate(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            return SessionUtil.create(
                requestData.getSessionMeta(),
                StandardHttpStatus.OK,
                null,
                Rson.DEFAULT.toJson(requestData.getAccount()).getAsObject()
            );
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    @HttpEndpoint(uri = "/public/v3/account/sendemailverification", allowedMethods = {
            HttpMethod.POST
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = Authorized_PreProcessorConfig.class)
    public HttpResponse onSendEmailVerification(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            Account account = requestData.getAccount();
            boolean canVerify = !account.isEmailVerified(); // if NOT already verified.

            if (canVerify) {
                account.sendEmailVerification();
            }

            return SessionUtil.create(
                requestData.getSessionMeta(), StandardHttpStatus.OK, null,
                new JsonObject()
                    .put("success", canVerify)
            );
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    @HttpEndpoint(uri = "/public/v3/account/verifyemail", allowedMethods = {
            HttpMethod.POST
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = VerifyEmail_PreProcessorConfig.class)
    public HttpResponse onVerifyEmail(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            String[] verificationToken = body.getString("id").split(":", 2);

            String accountId = verificationToken[0];
            String verifyId = verificationToken[1];

            Account account = Account.lookupAccountById(accountId);

            String error = "VERIFICATION_ID_INVALID";

            if (account != null) {
                error = account.tryVerifyEmail(verifyId);
            }

            if (error == null) {
                return SessionUtil.create(
                    requestData.getSessionMeta(), StandardHttpStatus.OK, null,
                    new JsonObject()
                        .put("success", true)
                );
            } else {
                return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.BAD_REQUEST, null, error);
            }
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    @HttpEndpoint(uri = "/public/v3/account/requestpasswordreset", allowedMethods = {
            HttpMethod.POST
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = RequestPasswordReset_PreProcessorConfig.class)
    public HttpResponse onSendPasswordReset(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            String email = body.getString("email");

            Account account = Account.lookupAccountByEmail(email);

            if (account != null) {
                account.initiatePasswordReset();
            }

            return SessionUtil.create(
                requestData.getSessionMeta(), StandardHttpStatus.OK,
                "If an account exists with that email it should receive an email with password reset instructions.",
                new JsonObject()
                    .put("success", "|Ψ〉 = α|0〉 + β|1〉") // IM SO QUIRKY!
            );
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    @HttpEndpoint(uri = "/public/v3/account/resetpassword", allowedMethods = {
            HttpMethod.POST
    }, preprocessor = MimotoPreProcessor.ID, preprocessorData = ResetPassword_PreProcessorConfig.class)
    public HttpResponse onPasswordReset(SoraHttpSession session) {
        MimotoRequestData requestData = session.getAttachment();

        try {
            JsonObject body = session.getRequestBodyJson(Rson.DEFAULT).getAsObject();

            String[] resetToken = body.getString("id").split(":", 2);

            String newPassword = body.getString("newPassword");
            String accountId = resetToken[0];
            String resetId = resetToken[1];

            Account account = Account.lookupAccountById(accountId);

            String error = "RESET_ID_INVALID";

            if (account != null) {
                error = account.tryResetPassword(resetId, newPassword);
            }

            if (error == null) {
                return SessionUtil.create(
                    requestData.getSessionMeta(), StandardHttpStatus.OK, null,
                    new JsonObject()
                        .put("success", true)
                );
            } else {
                return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.BAD_REQUEST, null, error);
            }
        } catch (Exception e) {
            FastLogger.logException(e);
            return SessionUtil.create(requestData.getSessionMeta(), StandardHttpStatus.INTERNAL_ERROR, null, "INTERNAL_ERROR");
        }
    }

    /* -------------------------------- */
    /* -------------------------------- */
    /* -------------------------------- */

    public static class RequestPasswordReset_PreProcessorConfig implements MimotoPreProcessorConfig {

        @Override
        public @NonNull List<String> getRequiredBodyProperties() {
            return Arrays.asList("email");
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

    public static class ResetPassword_PreProcessorConfig implements MimotoPreProcessorConfig {

        @Override
        public @NonNull List<String> getRequiredBodyProperties() {
            return Arrays.asList("id", "newPassword");
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

    public static class VerifyEmail_PreProcessorConfig implements MimotoPreProcessorConfig {

        @Override
        public @NonNull List<String> getRequiredBodyProperties() {
            return Arrays.asList("id");
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

    public static class Authorized_PreProcessorConfig implements MimotoPreProcessorConfig {

        @Override
        public @NonNull List<String> getRequiredHeaders() {
            return Arrays.asList("Authorization");
        }

        @Override
        public boolean shouldRateLimit() {
            return true;
        }

        @Override
        public boolean doAuth() {
            return true;
        }

        @Override
        public boolean requireAuth() {
            return true;
        }

        @Override
        public boolean requireVerifiedEmail() {
            return false;
        }

    }

}
