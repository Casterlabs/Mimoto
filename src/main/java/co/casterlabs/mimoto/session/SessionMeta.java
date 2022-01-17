package co.casterlabs.mimoto.session;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessionMeta {
    private String ip;
    private long ratelimitRemaining;
    private boolean didCountAgainstRateLimit;

    public boolean shouldBlock() {
        return this.ratelimitRemaining < 0;
    }

}
