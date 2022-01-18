package co.casterlabs.mimoto.util;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import co.casterlabs.rakurai.json.Rson;
import co.casterlabs.rakurai.json.TypeToken;
import co.casterlabs.rakurai.json.annotating.JsonClass;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

public class Quotes {
    private static final TypeToken<List<Quote>> QUOTES_TT = new TypeToken<List<Quote>>() {
    };

    private static List<Quote> quotes = Arrays.asList(
        new Quote("Life isn’t about getting and having, it’s about giving and being.", "Kevin Kruse")
    ); // One quote so if the load fails it's not empty.

    static {
        try {
            String quotesJson = FileUtil.loadResource("quotes.json");

            quotes = Rson.DEFAULT.fromJson(quotesJson, QUOTES_TT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Quote randomQuote() {
        int random = ThreadLocalRandom.current().nextInt(quotes.size());

        return quotes.get(random);
    }

    @Getter
    @ToString
    @AllArgsConstructor
    @JsonClass(exposeAll = true)
    public static class Quote {
        private String quote;
        private String author;

        public Quote() {}

    }

}
