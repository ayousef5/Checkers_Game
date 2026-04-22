import javafx.scene.text.Font;

import java.io.InputStream;

public final class AppFonts {

    private AppFonts() {
    }

    public static void load() {
        loadFont("/fonts/DMSans-Regular.ttf");
        loadFont("/fonts/DMSans-Bold.ttf");
    }

    private static void loadFont(String resourcePath) {
        try (InputStream in = AppFonts.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Font.loadFont(in, 14);
            }
        } catch (Exception ignored) {
        }
    }
}
