import javafx.application.Application; // base class for JavaFX apps
import javafx.application.Platform; // run code on UI thread
import javafx.geometry.Insets; // padding
import javafx.scene.Scene; // a screen/view
import javafx.scene.control.ListView; // log list
import javafx.scene.control.Label; // text label
import javafx.scene.layout.BorderPane; // layout
import javafx.scene.layout.VBox; // vertical layout
import javafx.scene.paint.Color; // colors
import javafx.scene.text.Font; // font
import javafx.scene.text.FontWeight; // bold
import javafx.stage.Stage; // window
import java.util.HashMap; // scene map

public class GuiServer extends Application { // server GUI

	HashMap<String, Scene> sceneMap; // all scenes
	Server serverConnection; // the server
	ListView<String> logList; // server event log

	public static void main(String[] args) { // entry point
		launch(args); // start JavaFX
	}

	@Override
	public void start(Stage primaryStage) throws Exception { // called on startup
		logList = new ListView<>(); // create log list first
		logList.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;"); // dark style

		serverConnection = new Server(data -> { // start server with callback
			Platform.runLater(() -> logList.getItems().add(data.toString())); // log on UI thread
		});

		sceneMap = new HashMap<>(); // initialize scene map
		sceneMap.put("server", createServerGui()); // add server scene

		primaryStage.setOnCloseRequest(e -> { // on window close
			Platform.exit(); // exit JavaFX
			System.exit(0); // exit app
		});

		primaryStage.setScene(sceneMap.get("server")); // set scene
		primaryStage.setTitle("Checkers Server"); // window title
		primaryStage.show(); // show window
	}

	public Scene createServerGui() { // build server UI
		BorderPane pane = new BorderPane(); // main layout
		pane.setStyle("-fx-background-color: #2b2b2b;"); // dark background
		pane.setPadding(new Insets(20)); // padding

		Label title = new Label("SERVER LOG"); // title label
		title.setFont(Font.font("Arial", FontWeight.BOLD, 18)); // bold font
		title.setTextFill(Color.WHITE); // white text

		VBox top = new VBox(10, title); // top section
		top.setPadding(new Insets(0, 0, 10, 0)); // bottom padding

		pane.setTop(top); // add title at top
		pane.setCenter(logList); // add log list in center

		return new Scene(pane, 500, 400); // return scene
	}
}