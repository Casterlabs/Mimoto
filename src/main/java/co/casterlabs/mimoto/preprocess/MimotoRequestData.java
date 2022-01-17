package co.casterlabs.mimoto.preprocess;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.mimoto.accounts.Account;
import co.casterlabs.mimoto.session.SessionMeta;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MimotoRequestData {
    private @Nullable Account account;
    private @Nullable SessionMeta sessionMeta;

}
