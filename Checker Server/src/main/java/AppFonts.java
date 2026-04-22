import javafx.scene.text.Font;

import java.io.InputStream;

public final class AppFonts {

    // hide ctor
    private AppFonts() {
    }

    // load project fonts
    public static void load() {
        loadFont("/fonts/DMSans-Regular.ttf");
        loadFont("/fonts/DMSans-Bold.ttf");
    }

    // one ttf
    private static void loadFont(String resourcePath) {
        try (InputStream in = AppFonts.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                Font.loadFont(in, 14);
            }
        } catch (Exception ignored) {
        }
    }
}
