import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ThemePreferences {

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".checkers-ui", "theme.properties");

    private ThemePreferences() {
    }

    public static boolean isLightTheme() {
        try {
            if (!Files.exists(FILE)) {
                return false;
            }
            for (String line : Files.readAllLines(FILE)) {
                line = line.trim();
                if (line.startsWith("theme=")) {
                    return "light".equalsIgnoreCase(line.substring(6).trim());
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void setLightTheme(boolean light) {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, "theme=" + (light ? "light" : "dark"));
        } catch (Exception ignored) {
        }
    }
}
