package co.casterlabs.mimoto;

import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.Getter;

@Getter
@JsonClass(exposeAll = true)
public class MimotoConfig {
    private String mongoUri;

}
