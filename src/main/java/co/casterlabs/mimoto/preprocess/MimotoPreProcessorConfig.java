package co.casterlabs.mimoto.preprocess;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.NonNull;

public interface MimotoPreProcessorConfig {

    /* ---------------- */
    /* Rate Limiting    */
    /* ---------------- */

    public boolean shouldRateLimit();

    /* ---------------- */
    /* Auth             */
    /* ---------------- */

    public boolean doAuth();

    public boolean requireAuth();

    public boolean requireVerifiedEmail();

    /* ---------------- */
    /* Validation       */
    /* ---------------- */

    default @NonNull List<String> getRequiredQueryParameters() {
        return Collections.emptyList();
    }

    default @NonNull List<String> getRequiredHeaders() {
        return Collections.emptyList();
    }

    default @NonNull List<String> getRequiredBodyProperties() {
        return Collections.emptyList();
    }

    default @NonNull Map<String, String> getQueryParameterRegex() {
        return Collections.emptyMap();
    }

    default @NonNull Map<String, String> getHeaderRegex() {
        return Collections.emptyMap();
    }

    default @NonNull Map<String, String> getBodyRegex() {
        return Collections.emptyMap();
    }

}
