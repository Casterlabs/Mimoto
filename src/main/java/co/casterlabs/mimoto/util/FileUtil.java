package co.casterlabs.mimoto.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import co.casterlabs.rakurai.io.IOUtil;

public class FileUtil {

    public static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    public static String loadResource(String path) throws IOException {
        try (InputStream in = FileUtil.class.getClassLoader().getResourceAsStream(path)) {
            return IOUtil.readInputStreamString(in, StandardCharsets.UTF_8);
        }
    }

}
