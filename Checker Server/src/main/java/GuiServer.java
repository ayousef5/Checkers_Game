import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.HashMap;

public class GuiServer extends Application {

	HashMap<String, Scene> sceneMap;
	Server serverConnection;
	ListView<String> logList;
	boolean lightTheme = ThemePreferences.isLightTheme();
	Stage primaryStage;

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		AppFonts.load();
		this.primaryStage = primaryStage;
		logList = new ListView<>();
		logList.getStyleClass().add("list-view");

		serverConnection = new Server(data -> {
			Platform.runLater(() -> logList.getItems().add(data.toString()));
		});

		sceneMap = new HashMap<>();
		sceneMap.put("server", createServerGui());

		primaryStage.setOnCloseRequest(e -> {
			Platform.exit();
			System.exit(0);
		});

		primaryStage.setScene(sceneMap.get("server"));
		primaryStage.setTitle("Checkers Server");
		primaryStage.show();
	}

	private void applyTheme(Scene scene) {
		if (scene == null) return;
		String path = lightTheme ? "/styles/light.css" : "/styles/dark.css";
		var url = getClass().getResource(path);
		if (url != null) {
			scene.getStylesheets().clear();
			scene.getStylesheets().add(url.toExternalForm());
		}
	}

	private void visitNodes(Node n, java.util.function.Consumer<Node> fn) {
		fn.accept(n);
		if (n instanceof Parent) {
			for (Node c : ((Parent) n).getChildrenUnmodifiable()) {
				visitNodes(c, fn);
			}
		}
	}

	private void syncThemeButton(Node root) {
		String g = lightTheme ? "\u263E" : "\u2600";
		visitNodes(root, n -> {
			if (n instanceof Button && n.getStyleClass().contains("btn-theme-toggle")) {
				((Button) n).setText(g);
			}
		});
	}

	public Scene createServerGui() {
		BorderPane pane = new BorderPane();
		pane.getStyleClass().add("root-app");
		pane.setPadding(new Insets(16));

		HBox topBar = new HBox();
		Region sp = new Region();
		HBox.setHgrow(sp, Priority.ALWAYS);
		Button themeBtn = new Button(lightTheme ? "\u263E" : "\u2600");
		themeBtn.getStyleClass().add("btn-theme-toggle");
		themeBtn.setOnAction(e -> {
			lightTheme = !lightTheme;
			ThemePreferences.setLightTheme(lightTheme);
			Scene sc = primaryStage.getScene();
			applyTheme(sc);
			syncThemeButton(sc.getRoot());
		});
		topBar.getChildren().addAll(sp, themeBtn);
		topBar.setPadding(new Insets(0, 0, 12, 0));

		Label title = new Label("SERVER LOG");
		title.getStyleClass().add("title-lg");

		VBox top = new VBox(8, topBar, title);
		pane.setTop(top);
		pane.setCenter(logList);

		Scene scene = new Scene(pane, 520, 440);
		applyTheme(scene);
		return scene;
	}
}
