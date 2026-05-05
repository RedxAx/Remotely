package redxax.oxy.remotely.packcontent;

import java.nio.file.Path;

public record PackContentDiagnostic(String providerId, Path sourceFile, String message) {
}
