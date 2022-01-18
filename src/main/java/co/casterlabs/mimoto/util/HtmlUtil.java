package co.casterlabs.mimoto.util;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.stream.Collectors;

import lombok.NonNull;
import lombok.SneakyThrows;

public class HtmlUtil {

    public static String escapeHtml(@NonNull String str) {
        return str
            .codePoints()
            .mapToObj(c -> c > 127 || "\"'<>&".indexOf(c) != -1 ? "&#" + c + ";" : new String(Character.toChars(c)))
            .collect(Collectors.joining());
    }

    @SneakyThrows
    public static String decodeURIComponent(@NonNull String s) {
        return URLDecoder.decode(s, "UTF-8");
    }

    @SneakyThrows
    public static String encodeURIComponent(@NonNull String s) {
        return URLEncoder.encode(s, "UTF-8")
            .replaceAll("\\+", "%20")
            .replaceAll("\\%21", "!")
            .replaceAll("\\%27", "'")
            .replaceAll("\\%28", "(")
            .replaceAll("\\%29", ")")
            .replaceAll("\\%7E", "~");
    }

}
