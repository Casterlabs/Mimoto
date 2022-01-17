package co.casterlabs.mimoto;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.jongo.Jongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import co.casterlabs.mimoto.preprocess.MimotoPreProcessor;
import co.casterlabs.mimoto.routes.RouteAccount;
import co.casterlabs.mimoto.routes.RouteAuthentication;
import co.casterlabs.mimoto.session.SessionUtil;
import co.casterlabs.mimoto.util.FileUtil;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.sora.Sora;
import co.casterlabs.sora.api.PluginImplementation;
import co.casterlabs.sora.api.SoraPlugin;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

@PluginImplementation
public class Mimoto extends SoraPlugin {
    public static final String EMAIL_REGEX = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])";

    private static @Getter Mimoto instance;

    private Map<String, Jongo> jongoCache = new HashMap<>();
    private MongoClient mongo;

    @SneakyThrows
    @Override
    public void onInit(Sora sora) {
        instance = this;

        String configContents = FileUtil.read(new File("plugins/Mimoto/config.json"));
        MimotoConfig config = Rson.DEFAULT.fromJson(configContents, MimotoConfig.class);

        // Connect to Mongo
        this.mongo = new MongoClient(new MongoClientURI(config.getMongoUri()));

        SessionUtil.setApiJongo(this.getJongoForDatabase("api"));

        sora.addHttpProvider(this, new RouteAuthentication());
        sora.addHttpProvider(this, new RouteAccount());
        sora.registerHttpPreProcessor(this, MimotoPreProcessor.ID, new MimotoPreProcessor());
    }

    @SuppressWarnings("deprecation")
    public Jongo getJongoForDatabase(String databaseName) {
        Jongo jongo = this.jongoCache.get(databaseName);

        if (jongo == null) {
            jongo = new Jongo(this.mongo.getDB(databaseName));

            this.jongoCache.put(databaseName, jongo);
        }

        return jongo;
    }

    @Override
    public void onClose() {
        this.mongo.close();
        this.jongoCache = null;
    }

    @Override
    public @Nullable String getVersion() {
        return "3.0.0";
    }

    @Override
    public @Nullable String getAuthor() {
        return "Casterlabs";
    }

    @Override
    public @NonNull String getName() {
        return "Mimoto";
    }

    @Override
    public @NonNull String getId() {
        return "co.casterlabs.mimoto";
    }

}
