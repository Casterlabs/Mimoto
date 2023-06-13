package co.casterlabs.mimoto;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.jongo.Jongo;

import com.backblaze.b2.client.B2StorageClient;
import com.backblaze.b2.client.B2StorageClientFactory;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.mimoto.accounts.Account;
import co.casterlabs.mimoto.preprocess.MimotoPreProcessor;
import co.casterlabs.mimoto.routes.RouteAccount;
import co.casterlabs.mimoto.routes.RouteAuthentication;
import co.casterlabs.mimoto.session.SessionUtil;
import co.casterlabs.mimoto.util.FileUtil;
import co.casterlabs.mimoto.util.HtmlUtil;
import co.casterlabs.mimoto.util.Quotes;
import co.casterlabs.mimoto.util.Quotes.Quote;
import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.sora.Sora;
import co.casterlabs.sora.api.PluginImplementation;
import co.casterlabs.sora.api.SoraPlugin;
import co.casterlabs.zohoapijava.ZohoAuth;
import co.casterlabs.zohoapijava.requests.ZohoMailGetUserAccountDetailsRequest;
import co.casterlabs.zohoapijava.requests.ZohoMailSendEmailRequest;
import co.casterlabs.zohoapijava.types.ZohoUserAccount;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

@PluginImplementation
public class Mimoto extends SoraPlugin {
    public static final String EMAIL_REGEX = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9]))\\.){3}(?:(2(5[0-5]|[0-4][0-9])|1[0-9][0-9]|[1-9]?[0-9])|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])";

    private static @Getter Mimoto instance;

    private Map<String, Jongo> jongoCache = new HashMap<>();
    private MongoClient mongo;

    private String passwordResetEmailTemplate;
    private String emailVerificationEmailTemplate;

    private ZohoAuth zohoAuth;
    private ZohoUserAccount zohoAccount;

    private @Getter B2StorageClient b2;

    @SneakyThrows
    @Override
    public void onInit(Sora sora) {
        instance = this;

        this.passwordResetEmailTemplate = FileUtil.loadResource("passwordreset.html");
        this.emailVerificationEmailTemplate = FileUtil.loadResource("signup.html");

        String configContents = FileUtil.read(new File("plugins/Mimoto/config.json"));
        MimotoConfig config = Rson.DEFAULT.fromJson(configContents, MimotoConfig.class);

        // Connect to Backblaze
        this.b2 = B2StorageClientFactory
            .createDefaultFactory()
            .create(config.getB2Id(), config.getB2Key(), "Mimoto");

        // Connect to Mongo
        this.mongo = new MongoClient(new MongoClientURI(config.getMongoUri()));

        SessionUtil.setApiJongo(this.getJongoForDatabase("api"));

        // Setup Zoho
        this.zohoAuth = new ZohoAuth(config.getZohoRefreshToken(), config.getZohoClientId(), config.getZohoClientSecret(), config.getZohoRedirectUri(), config.getZohoScope());
        this.zohoAccount = new ZohoMailGetUserAccountDetailsRequest(this.zohoAuth).send().get(0);

        sora.addProvider(this, new RouteAuthentication());
        sora.addProvider(this, new RouteAccount());
        sora.registerPreProcessor(this, MimotoPreProcessor.ID, new MimotoPreProcessor());
    }

    @SuppressWarnings("deprecation")
    public Jongo getJongoForDatabase(@NonNull String databaseName) {
        Jongo jongo = this.jongoCache.get(databaseName);

        if (jongo == null) {
            jongo = new Jongo(this.mongo.getDB(databaseName));

            this.jongoCache.put(databaseName, jongo);
        }

        return jongo;
    }

    public void sendEmail(@NonNull String content, @NonNull String subject, @NonNull String email) {
        try {
            new ZohoMailSendEmailRequest(this.zohoAuth)
                .setAccountId(this.zohoAccount.getAccountId())
                .setContentsAsHtml(content)
                .setFromAddress(this.zohoAccount.getPrimaryEmailAddress())
                .setToAddress(email)
                .setSubject(subject)
                .send();
        } catch (ApiException e) {
            this.getLogger().exception(e);
        }
    }

    public String formatEmailVerificationEmail(@NonNull Account account, @NonNull String emailVerifyId) {
        Quote quote = Quotes.randomQuote();

        return this.emailVerificationEmailTemplate
            .replace("%account.name%", HtmlUtil.escapeHtml(account.getName()))
            .replace("%link%", String.format("https://casterlabs.co/account/verify?id=%s", HtmlUtil.encodeURIComponent(emailVerifyId)))
            .replace("%quote%", quote.getQuote())
            .replace("%quote.author%", quote.getAuthor());
    }

    public String formatPasswordResetEmail(@NonNull Account account, @NonNull String passwordResetId) {
        Quote quote = Quotes.randomQuote();

        return this.passwordResetEmailTemplate
            .replace("%link%", String.format("https://casterlabs.co/account/resetpassword?id=%s", HtmlUtil.encodeURIComponent(passwordResetId)))
            .replace("%quote%", quote.getQuote())
            .replace("%quote.author%", quote.getAuthor());
    }

    @Override
    public void onClose() {
        this.mongo.close();
        this.b2.close();
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
