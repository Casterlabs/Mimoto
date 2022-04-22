package co.casterlabs.mimoto;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.Getter;

@Getter
@JsonClass(exposeAll = true)
public class MimotoConfig {
    private String mongoUri;

    private String zohoScope;
    private String zohoRefreshToken;
    private String zohoClientId;
    private String zohoClientSecret;
    private String zohoRedirectUri;

    private String b2Id;
    private String b2Key;

}
