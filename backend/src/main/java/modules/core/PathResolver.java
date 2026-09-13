package modules.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class PathResolver {
    private PathResolver() {}

    public static String resolve(String configuredPath) {
        Path path = Path.of(configuredPath);
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }

        List<Path> candidates = List.of(
                path.getFileName(),
                path,
                Path.of("backend").resolve(path)
        );

        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.normalize().toString();
            }
        }

        return path.normalize().toString();
    }
}