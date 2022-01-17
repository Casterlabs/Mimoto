package co.casterlabs.mimoto.accounts;

import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;
import org.jongo.Jongo;
import org.mindrot.BCrypt;

import co.casterlabs.mimoto.Mimoto;
import co.casterlabs.mimoto.util.CryptoUtil;
import co.casterlabs.rakurai.json.annotating.JsonField;
import lombok.Data;
import lombok.NonNull;
import lombok.ToString;

@Data
@ToString
public class Account {

    /* Login */

    @NonNull
    @JsonField
    private String accountId; // Cannot be changed.

    @NonNull
    @JsonField
    private String email; // Cannot be changed.

    @NonNull
    private String passwordHash;

    /* Info */

    @NonNull
    @JsonField
    private String name; // Always defaults to the email (everything before the @).

    @JsonField
    private boolean emailVerified = false;

    @JsonField
    private boolean isBanned = false;

    @JsonField
    private long creationTimestamp;  // Cannot be changed.

    /* Verification */

    @Nullable
    private String emailVerificationId = "";

    @Nullable
    private String resetRequestId = "";

    private long resetRequestTimestamp = 0;

    /* ---------------- */

    public Account() {} // For Jackson.

    /* ---------------- */

    public void sendEmailVerification() {
        if (!this.emailVerified && !this.isBanned) {
            this.emailVerificationId = new String(CryptoUtil.generateSecureRandomKey());
            this.save();

            // TODO Fire off the email.
            System.out.println("VERIFY EMAIL!");

        }
    }

    public void initiatePasswordReset() {
        this.resetRequestId = new String(CryptoUtil.generateSecureRandomKey());
        this.resetRequestTimestamp = System.currentTimeMillis();

        this.save();

        // TODO Fire off the email.
        System.out.println("RESET PASSWORD!");
    }

    /**
     * @return an error message, or null if success.
     */
    public @Nullable String tryVerifyEmail(@NonNull String id) {
        if (!this.emailVerified && id.equals(this.emailVerificationId)) {
            this.emailVerificationId = "";
            this.emailVerified = true;
            this.save();
            return null;
        } else {
            return "VERIFICATION_ID_INVALID";
        }
    }

    /**
     * @return an error message, or null if success.
     */
    public @Nullable String tryResetPassword(@NonNull String id, @NonNull String newPassword) {
        if ((System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15)) > this.resetRequestTimestamp) {
            return "RESET_ID_EXPIRED";
        }

        if (!id.equals(this.resetRequestId)) {
            return "RESET_ID_INVALID";
        }

        this.passwordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt());
        this.resetRequestId = "";
        this.resetRequestTimestamp = 0;

        this.save();

        return null;
    }

    public boolean tryLogin(String password) {
        if (this.isBanned) {
            return false;
        } else {
            return BCrypt.checkpw(password, this.passwordHash);
        }
    }

    public String issueToken() {
        // We encrypt the password has with the account secret.
        // If the user changes their password, the decryption will fail, and because the
        // underlying algorithm is based on HMAC it'll always be unique. I know, it
        // would be better to use a JWT, But I don't care, this works so well and is
        // secure.
        String challenge = CryptoUtil.encrypt(this.passwordHash);

        return this.accountId + ':' + challenge;
    }

    public boolean isTokenValid(String token) {
        if (this.isBanned) {
            return false;
        } else {
            String[] split = token.split(":", 2);

            String id = split[0];
            String challenge = split[1];

            if (!id.equals(this.accountId)) {
                return false;
            }

            return CryptoUtil.decryptCompare(challenge, this.passwordHash);
        }
    }

    public void save() {
        Jongo jongo = Mimoto.getInstance().getJongoForDatabase("auth");

        jongo
            .getCollection("accounts")
            .update("{ accountId: #}", this.accountId)
            .upsert()
            .with(this);
    }

    /**
     * Care needs to be taken to ensure the account doesn't already exist.
     */
    public static Account createAccount(@NonNull String name, @NonNull String email, @NonNull String password) {
        Account acc = new Account();

        acc.accountId = new String(CryptoUtil.generateRandomId());
        acc.email = email.toLowerCase();
        acc.name = name;
        acc.passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        acc.creationTimestamp = System.currentTimeMillis();

        acc.sendEmailVerification();
        acc.save();

        return acc;
    }

    public static @Nullable Account lookupAccountById(@NonNull String id) {
        Jongo jongo = Mimoto.getInstance().getJongoForDatabase("auth");

        return jongo
            .getCollection("accounts")
            .findOne("{ accountId: #}", id)
            .as(Account.class);
    }

    public static @Nullable Account lookupAccountByEmail(@NonNull String email) {
        Jongo jongo = Mimoto.getInstance().getJongoForDatabase("auth");

        return jongo
            .getCollection("accounts")
            .findOne("{ email: #}", email.toLowerCase())
            .as(Account.class);
    }

    public static String getAccountIdFromToken(String token) {
        String[] split = token.split(":", 2);

        String id = split[0];

        return id;
    }

}
