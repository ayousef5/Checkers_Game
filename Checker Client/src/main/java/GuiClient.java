import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Consumer;

public class GuiClient extends Application {

	Client clientConnection;
	CheckersGame localGame;
	String myUsername;
	String opponentUsername;
	String myColor;
	HashMap<String, Scene> sceneMap;
	Stage primaryStage;
	boolean flipped = false;
	ListView<String> chatList;
	ListView<String> moveHistoryList;
	GridPane boardGrid;
	Label turnLabel;
	int selectedRow = -1;
	int selectedCol = -1;
	ArrayList<int[]> validDestinations = new ArrayList<>();

	private static final double BOARD_LEFT_LABEL_COL_PX = 22.0;
	private static final double BOARD_BOTTOM_LABEL_ROW_PX = 20.0;
	private static final double BOARD_WOOD_PAD_PX = 16.0;
	double squareSize = 48.0;

	StackPane boardWoodFrame;
	StackPane boardCenterStack;

	int myRating = 1200;
	int myWins;
	int myLosses;
	int myDraws;
	Label lobbyRatingLabel;
	VBox friendPendingRows;
	private boolean friendRequestsVisible;
	ListView<String> onlineFriendsListView;
	ListView<GameListEntry> gamesListView;
	Label opponentTimerLabel;
	Label myTimerLabel;
	int redTimeLeft = GameSessionMirror.START_SECONDS;
	int blackTimeLeft = GameSessionMirror.START_SECONDS;
	boolean spectating = false;
	String opponentLabelText = "";
	String youLabelText = "";
	boolean lightTheme = ThemePreferences.isLightTheme();

	static class GameSessionMirror {
		static final int START_SECONDS = 180;
	}

	// width of cell grid
	private double boardGridInnerWidth() {
		return BOARD_LEFT_LABEL_COL_PX + 8 * squareSize;
	}

	// height of cell grid
	private double boardGridInnerHeight() {
		return 8 * squareSize + BOARD_BOTTOM_LABEL_ROW_PX;
	}

	// width with wood pad
	private double boardFrameOuterWidth() {
		return boardGridInnerWidth() + 2 * BOARD_WOOD_PAD_PX;
	}

	// height with wood pad
	private double boardFrameOuterHeight() {
		return boardGridInnerHeight() + 2 * BOARD_WOOD_PAD_PX;
	}

	// keep frame from stretch
	private void applyRigidBoardFrameSize() {
		if (boardWoodFrame == null) return;
		double ow = boardFrameOuterWidth();
		double oh = boardFrameOuterHeight();
		boardWoodFrame.setMinSize(ow, oh);
		boardWoodFrame.setPrefSize(ow, oh);
		boardWoodFrame.setMaxSize(ow, oh);
		boardWoodFrame.setMaxWidth(Region.USE_PREF_SIZE);
		boardWoodFrame.setMaxHeight(Region.USE_PREF_SIZE);
	}

	// app entry
	public static void main(String[] args) {
		launch(args);
	}

	// first scene
	@Override
	public void start(Stage stage) throws Exception {
		AppFonts.load();
		this.primaryStage = stage;

		clientConnection = new Client(msg -> {
			Platform.runLater(() -> handleMessage(msg));
		});
		clientConnection.start();

		sceneMap = new HashMap<>();
		sceneMap.put("login", createLoginScene());
		sceneMap.put("lobby", createLobbyScene());
		sceneMap.put("waiting", createWaitingScene());

		stage.setOnCloseRequest(e -> {
			Platform.exit();
			System.exit(0);
		});

		stage.setScene(sceneMap.get("login"));
		stage.setTitle("Checkers");
		stage.show();
	}

	// light or dark sheet
	private void applyTheme(Scene scene) {
		if (scene == null) return;
		String path = lightTheme ? "/styles/light.css" : "/styles/dark.css";
		var url = getClass().getResource(path);
		if (url != null) {
			scene.getStylesheets().clear();
			scene.getStylesheets().add(url.toExternalForm());
		}
	}

	// walk scene tree
	private void visitNodes(Node n, Consumer<Node> fn) {
		fn.accept(n);
		if (n instanceof Parent) {
			for (Node c : ((Parent) n).getChildrenUnmodifiable()) {
				visitNodes(c, fn);
			}
		}
	}

	// theme toggle label
	private void syncThemeToggleLabel(Node root) {
		if (root == null) return;
		String label = lightTheme ? "Dark" : "Light";
		visitNodes(root, n -> {
			if (n instanceof Button && n.getStyleClass().contains("btn-theme-toggle")) {
				((Button) n).setText(label);
			}
		});
	}

	// flip light dark
	private void onThemeToggled() {
		lightTheme = !lightTheme;
		ThemePreferences.setLightTheme(lightTheme);
		for (String key : new String[]{"login", "lobby", "waiting"}) {
			Scene sc = sceneMap.get(key);
			if (sc != null) {
				applyTheme(sc);
				syncThemeToggleLabel(sc.getRoot());
			}
		}
		Scene gameSc = sceneMap.get("game");
		if (gameSc != null) {
			applyTheme(gameSc);
			syncThemeToggleLabel(gameSc.getRoot());
		}
		if (boardGrid != null) {
			renderBoard();
			recalculateBoardSquareSize();
		}
	}

	// top right theme control
	private Button createThemeToggleButton() {
		Button b = new Button(lightTheme ? "Dark" : "Light");
		b.getStyleClass().add("btn-theme-toggle");
		b.setOnAction(e -> onThemeToggled());
		return b;
	}

	// top bar for theme
	private HBox buildTopThemeBar() {
		HBox bar = new HBox();
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		bar.getChildren().addAll(spacer, createThemeToggleButton());
		bar.setPadding(new Insets(10, 18, 6, 18));
		return bar;
	}

	// checkerboard cell color
	private Color boardToneLightSquare(int boardRow, int boardCol) {
		return (boardRow + boardCol) % 2 == 0 ? Color.web("#FFFFFF") : Color.web("#1a1a1a");
	}

	// red man fill
	private Color pieceRed() {
		return lightTheme ? Color.web("#b85850") : Color.web("#a84840");
	}

	// black man fill
	private Color pieceBlue() {
		return lightTheme ? Color.web("#3d7a9e") : Color.web("#147493");
	}

	// disk look
	private void styleFlatCheckerPiece(Circle circle, Color base) {
		circle.setFill(base);
		circle.setStroke(lightTheme ? Color.web("#0d0d0d", 0.55) : Color.web("#1a1a1a", 0.65));
		circle.setStrokeWidth(1.0);
	}

	// player row with clock
	private HBox buildPlayerCard(String displayLine, Label timerLabel, String avatarHex) {
		HBox row = new HBox(10);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().addAll("player-card", "player-bar");

		timerLabel.getStyleClass().clear();
		timerLabel.getStyleClass().addAll("label", "player-timer");
		timerLabel.setMinWidth(52);
		timerLabel.setMaxWidth(Region.USE_PREF_SIZE);
		timerLabel.setAlignment(Pos.CENTER_RIGHT);

		StackPane av = new StackPane();
		double r = 18;
		Circle avBg = new Circle(r);
		avBg.setFill(Color.web(avatarHex));
		String initial = (displayLine == null || displayLine.isEmpty()) ? "?"
				: displayLine.substring(0, 1).toUpperCase();
		Label ini = new Label(initial);
		ini.setTextFill(Color.WHITE);
		ini.setFont(Font.font("DM Sans", FontWeight.BOLD, 13));
		av.getChildren().addAll(avBg, ini);
		av.setMinSize(2 * r, 2 * r);
		av.setMaxSize(2 * r, 2 * r);

		Label name = new Label(displayLine);
		name.getStyleClass().add("player-name");
		name.setWrapText(false);

		HBox left = new HBox(8, av, name);
		left.setAlignment(Pos.CENTER_LEFT);
		HBox.setHgrow(left, Priority.NEVER);

		HBox timerBox = new HBox();
		timerBox.setAlignment(Pos.CENTER_RIGHT);
		timerBox.getChildren().add(timerLabel);
		HBox.setHgrow(timerBox, Priority.ALWAYS);

		row.getChildren().addAll(left, timerBox);
		return row;
	}

	// login and register
	private Scene createLoginScene() {
		BorderPane root = new BorderPane();
		root.getStyleClass().add("root-app");

		VBox card = new VBox(18);
		card.getStyleClass().add("card");
		card.setAlignment(Pos.CENTER);
		card.setMaxWidth(400);

		Label title = new Label("CHECKERS");
		title.getStyleClass().add("title-xl");

		TextField usernameField = new TextField();
		usernameField.setPromptText("Username");
		usernameField.setMaxWidth(320);
		usernameField.getStyleClass().add("text-field");

		PasswordField passwordField = new PasswordField();
		passwordField.setPromptText("Password");
		passwordField.setMaxWidth(320);
		passwordField.getStyleClass().add("text-field");

		Button loginBtn = new Button("LOG IN");
		loginBtn.getStyleClass().add("btn-primary");
		loginBtn.setMaxWidth(Double.MAX_VALUE);
		loginBtn.setOnAction(e -> {
			String u = usernameField.getText().trim();
			String p = passwordField.getText();
			if (!u.isEmpty() && !p.isEmpty()) {
				myUsername = u;
				clientConnection.send(new Message(Message.MessageType.login, new String[]{u, p}));
			}
		});

		Button registerBtn = new Button("REGISTER");
		registerBtn.getStyleClass().add("btn-secondary");
		registerBtn.setMaxWidth(Double.MAX_VALUE);
		registerBtn.setOnAction(e -> {
			String u = usernameField.getText().trim();
			String p = passwordField.getText();
			if (!u.isEmpty() && !p.isEmpty()) {
				myUsername = u;
				clientConnection.send(new Message(Message.MessageType.register, new String[]{u, p}));
			}
		});

		card.getChildren().addAll(title, usernameField, passwordField, loginBtn, registerBtn);

		StackPane center = new StackPane(card);
		root.setTop(buildTopThemeBar());
		root.setCenter(center);
		Scene scene = new Scene(root, 820, 660);
		applyTheme(scene);
		return scene;
	}

	// header stats line
	private void updateLobbyProfileText() {
		if (lobbyRatingLabel == null || myUsername == null) {
			return;
		}
		String d = (myDraws > 0) ? (" " + myDraws + "D") : "";
		lobbyRatingLabel.setText(myUsername + " (" + myRating + ") — " + myWins + "W " + myLosses + "L" + d);
	}

	// add or remove online friend
	private void syncFriendOnline(String friendName, boolean online) {
		if (onlineFriendsListView == null || friendName == null || friendName.isEmpty()) {
			return;
		}
		var items = onlineFriendsListView.getItems();
		if (online) {
			if (!items.contains(friendName)) {
				items.add(friendName);
			}
		} else {
			items.remove(friendName);
		}
	}

	// build request rows
	private void rebuildFriendPendingRows(Iterable<String> names) {
		if (friendPendingRows == null) {
			return;
		}
		friendPendingRows.getChildren().clear();
		for (String n : names) {
			if (n == null || n.isEmpty()) {
				continue;
			}
			Label l = new Label(n);
			l.getStyleClass().add("label");
			Button a = new Button("Accept");
			a.getStyleClass().add("btn-primary");
			String from = n;
			a.setOnAction(e -> clientConnection.send(
					new Message(Message.MessageType.friend_accept, from)));
			Button d = new Button("Decline");
			d.getStyleClass().add("btn-secondary");
			d.setOnAction(e -> clientConnection.send(
					new Message(Message.MessageType.friend_decline, from)));
			HBox row = new HBox(8, l, a, d);
			row.setAlignment(Pos.CENTER_LEFT);
			friendPendingRows.getChildren().add(row);
		}
	}

	// request popup
	private void showFriendIncomingDialog(String from) {
		Stage d = new Stage();
		d.initModality(Modality.WINDOW_MODAL);
		if (primaryStage != null) {
			d.initOwner(primaryStage);
		}
		d.setTitle("Friend request");

		VBox box = new VBox(16);
		box.getStyleClass().add("dialog-root");
		box.setAlignment(Pos.CENTER);
		Label t = new Label(from + " sent you a friend request");
		t.getStyleClass().add("dialog-message");
		Button acc = new Button("Accept");
		acc.getStyleClass().add("btn-primary");
		acc.setOnAction(e -> {
			clientConnection.send(new Message(Message.MessageType.friend_accept, from));
			d.close();
		});
		Button dec = new Button("Decline");
		dec.getStyleClass().add("btn-danger");
		dec.setOnAction(e -> {
			clientConnection.send(new Message(Message.MessageType.friend_decline, from));
			d.close();
		});
		HBox b = new HBox(10, dec, acc);
		b.setAlignment(Pos.CENTER);
		box.getChildren().addAll(t, b);
		Scene sc = new Scene(box, 420, 160);
		styleDialog(sc);
		d.setScene(sc);
		d.show();
	}

	// main lobby layout
	private Scene createLobbyScene() {
		BorderPane root = new BorderPane();
		root.getStyleClass().add("root-app");

		VBox friendsCol = new VBox(10);
		friendsCol.getStyleClass().add("friends-panel");
		friendsCol.setPadding(new Insets(12, 14, 12, 12));
		friendsCol.setPrefWidth(256);
		friendsCol.setMinWidth(200);
		friendsCol.setMaxWidth(300);

		Label friendsHeader = new Label("FRIENDS");
		friendsHeader.getStyleClass().add("section-header");

		Button requestsBtn = new Button("Requests");
		requestsBtn.getStyleClass().add("btn-secondary");
		friendPendingRows = new VBox(6);
		friendPendingRows.getStyleClass().add("friend-requests-block");
		friendRequestsVisible = false;
		friendPendingRows.setVisible(false);
		friendPendingRows.managedProperty().bind(friendPendingRows.visibleProperty());
		requestsBtn.setOnAction(e -> {
			friendRequestsVisible = !friendRequestsVisible;
			friendPendingRows.setVisible(friendRequestsVisible);
		});

		onlineFriendsListView = new ListView<>();
		onlineFriendsListView.getStyleClass().add("list-view");
		onlineFriendsListView.setPrefHeight(180);
		onlineFriendsListView.setPlaceholder(new Label("No friends online"));
		onlineFriendsListView.setCellFactory(lv -> new ListCell<String>() {
			// friend row with dot
			@Override
			protected void updateItem(String item, boolean empty) {
				super.updateItem(item, empty);
				if (empty || item == null) {
					setGraphic(null);
					return;
				}
				Circle dot = new Circle(5, Color.web("#22c55e"));
				Label n = new Label(item);
				n.getStyleClass().add("label");
				HBox row = new HBox(8, dot, n);
				row.setAlignment(Pos.CENTER_LEFT);
				setGraphic(row);
			}
		});

		HBox addRow = new HBox(6);
		TextField addFriendField = new TextField();
		addFriendField.setPromptText("username");
		addFriendField.getStyleClass().add("text-field");
		Button addFriendBtn = new Button("Add friend");
		addFriendBtn.getStyleClass().add("btn-secondary");
		addFriendBtn.setMinWidth(80.0);
		addFriendBtn.setMaxWidth(Region.USE_PREF_SIZE);
		addFriendBtn.setOnAction(e -> {
			String t = addFriendField.getText() != null ? addFriendField.getText().trim() : "";
			if (!t.isEmpty()) {
				clientConnection.send(new Message(Message.MessageType.friend_add, t));
				addFriendField.clear();
			}
		});
		HBox.setHgrow(addFriendField, Priority.ALWAYS);
		HBox.setHgrow(addFriendBtn, Priority.NEVER);
		addRow.getChildren().addAll(addFriendField, addFriendBtn);

		friendsCol.getChildren().addAll(friendsHeader, requestsBtn, friendPendingRows, onlineFriendsListView, addRow);
		VBox.setVgrow(onlineFriendsListView, Priority.ALWAYS);

		VBox card = new VBox(14);
		card.getStyleClass().add("card");
		card.setAlignment(Pos.TOP_CENTER);
		card.setMaxWidth(540);
		card.setFillWidth(true);

		Label title = new Label("LOBBY");
		title.getStyleClass().add("title-lg");

		lobbyRatingLabel = new Label("—");
		lobbyRatingLabel.getStyleClass().add("subtitle");
		updateLobbyProfileText();

		Label gamesTitle = new Label("Games in progress");
		gamesTitle.getStyleClass().add("section-header");

		gamesListView = new ListView<>();
		gamesListView.setPrefHeight(220);
		gamesListView.getStyleClass().add("list-view");

		Button refreshBtn = new Button("Refresh list");
		refreshBtn.getStyleClass().add("btn-secondary");
		refreshBtn.setMaxWidth(Double.MAX_VALUE);
		refreshBtn.setOnAction(e -> clientConnection.send(new Message(Message.MessageType.list_games, null)));

		Button spectateBtn = new Button("Spectate selected");
		spectateBtn.getStyleClass().add("btn-secondary");
		spectateBtn.setMaxWidth(Double.MAX_VALUE);
		spectateBtn.setOnAction(e -> {
			GameListEntry sel = gamesListView.getSelectionModel().getSelectedItem();
			if (sel != null) {
				clientConnection.send(new Message(Message.MessageType.spectator_join, sel.sessionId));
			}
		});

		Button playBtn = new Button("Search for Match");
		playBtn.getStyleClass().add("btn-primary");
		playBtn.setMaxWidth(Double.MAX_VALUE);
		playBtn.setOnAction(e -> clientConnection.send(new Message(Message.MessageType.join_queue, null)));

		Button vsBotBtn = new Button("Play vs Bot");
		vsBotBtn.getStyleClass().add("btn-primary");
		vsBotBtn.setMaxWidth(Double.MAX_VALUE);
		vsBotBtn.setOnAction(e -> clientConnection.send(new Message(Message.MessageType.play_vs_bot, null)));

		card.getChildren().addAll(title, lobbyRatingLabel, gamesTitle, gamesListView, refreshBtn, spectateBtn, playBtn,
				vsBotBtn);

		BorderPane.setMargin(card, new Insets(0, 12, 12, 0));
		StackPane center = new StackPane(card);
		root.setLeft(friendsCol);
		root.setTop(buildTopThemeBar());
		root.setCenter(center);
		Scene scene = new Scene(root, 1080, 700);
		applyTheme(scene);
		return scene;
	}

	// matchmaking view
	private Scene createWaitingScene() {
		BorderPane root = new BorderPane();
		root.getStyleClass().add("root-app");

		VBox card = new VBox(20);
		card.getStyleClass().add("card");
		card.setAlignment(Pos.CENTER);
		card.setMaxWidth(440);

		Label label = new Label("PLAY CHECKERS!");
		label.getStyleClass().add("title-xl");

		Label sub = new Label("Waiting for opponent...");
		sub.getStyleClass().add("subtitle");

		card.getChildren().addAll(label, sub);
		StackPane center = new StackPane(card);
		root.setTop(buildTopThemeBar());
		root.setCenter(center);
		Scene scene = new Scene(root, 820, 640);
		applyTheme(scene);
		return scene;
	}

	// play screen
	private Scene createGameScene() {
		turnLabel = new Label("");
		turnLabel.getStyleClass().add("turn-banner");

		BorderPane root = new BorderPane();
		root.getStyleClass().add("root-app");

		opponentTimerLabel = new Label(formatTime(GameSessionMirror.START_SECONDS));
		myTimerLabel = new Label(formatTime(GameSessionMirror.START_SECONDS));

		HBox oppCard = buildPlayerCard(opponentLabelText, opponentTimerLabel, "#6e6e6e");
		HBox youCard = buildPlayerCard(youLabelText, myTimerLabel, "#8a6a5a");

		boardGrid = new GridPane();
		boardWoodFrame = new StackPane(boardGrid);
		boardWoodFrame.getStyleClass().add("board-wood-frame");
		StackPane.setAlignment(boardGrid, Pos.CENTER);
		InnerShadow woodInset = new InnerShadow();
		woodInset.setRadius(10);
		woodInset.setChoke(0.2);
		woodInset.setColor(Color.color(0, 0, 0, 0.38));
		woodInset.setOffsetX(0);
		woodInset.setOffsetY(1);
		boardWoodFrame.setEffect(woodInset);
		boardWoodFrame.setPadding(new Insets(BOARD_WOOD_PAD_PX));

		applyBoardGridConstraints();
		renderBoard();
		applyRigidBoardFrameSize();

		boardCenterStack = new StackPane();
		boardCenterStack.getChildren().add(boardWoodFrame);
		StackPane.setAlignment(boardWoodFrame, Pos.CENTER);
		boardCenterStack.setMinSize(0, 0);
		boardCenterStack.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		boardCenterStack.setStyle("-fx-background-color: transparent;");

		VBox leftGroup = new VBox(6);
		leftGroup.setAlignment(Pos.TOP_CENTER);
		leftGroup.setPadding(Insets.EMPTY);
		VBox.setVgrow(boardCenterStack, Priority.ALWAYS);
		leftGroup.getChildren().addAll(oppCard, turnLabel, boardCenterStack, youCard);
		leftGroup.setMinWidth(0);

		turnLabel.setAlignment(Pos.CENTER);
		turnLabel.setMaxWidth(Region.USE_PREF_SIZE);
		oppCard.maxWidthProperty().bind(boardWoodFrame.widthProperty());
		oppCard.minWidthProperty().bind(boardWoodFrame.widthProperty());
		youCard.maxWidthProperty().bind(boardWoodFrame.widthProperty());
		youCard.minWidthProperty().bind(boardWoodFrame.widthProperty());
		turnLabel.maxWidthProperty().bind(boardWoodFrame.widthProperty());

		final int sidebarFixedPx = 300;
		VBox rightPanel = createRightPanel();
		rightPanel.setMinWidth(sidebarFixedPx);
		rightPanel.setMaxWidth(sidebarFixedPx);
		rightPanel.setPrefWidth(sidebarFixedPx);
		rightPanel.getStyleClass().add("sidebar-panel");
		HBox.setHgrow(rightPanel, Priority.NEVER);

		HBox gameRow = new HBox(12);
		gameRow.setPadding(new Insets(0, 12, 12, 12));
		gameRow.setAlignment(Pos.TOP_LEFT);
		gameRow.getChildren().addAll(leftGroup, rightPanel);
		gameRow.setFillHeight(true);
		HBox.setHgrow(leftGroup, Priority.ALWAYS);
		rightPanel.maxHeightProperty().bind(gameRow.heightProperty());

		boardCenterStack.widthProperty().addListener((o, a, b) -> recalculateBoardSquareSize());
		boardCenterStack.heightProperty().addListener((o, a, b) -> recalculateBoardSquareSize());

		root.setTop(buildTopThemeBar());
		root.setCenter(gameRow);

		updateTimerLabels();
		Scene scene = new Scene(root, 1120, 760);
		applyTheme(scene);
		Platform.runLater(() -> {
			recalculateBoardSquareSize();
			scene.widthProperty().addListener((o, a, b) -> recalculateBoardSquareSize());
			scene.heightProperty().addListener((o, a, b) -> recalculateBoardSquareSize());
		});
		return scene;
	}

	// moves chat rules
	private VBox createRightPanel() {
		VBox panel = new VBox(12);
		panel.setPadding(new Insets(14, 14, 14, 10));

		TabPane tabs = new TabPane();
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
		tabs.getStyleClass().add("tab-pane");

		moveHistoryList = new ListView<>();
		moveHistoryList.getStyleClass().add("list-view");
		moveHistoryList.getItems().add("Starting Position");
		Tab movesTab = new Tab("Moves", moveHistoryList);

		chatList = new ListView<>();
		chatList.getStyleClass().add("list-view");
		TextField chatInput = new TextField();
		chatInput.setPromptText("Message...");
		chatInput.getStyleClass().add("text-field");
		Button sendBtn = new Button("Send");
		sendBtn.getStyleClass().add("btn-primary");
		sendBtn.setOnAction(e -> sendChat(chatInput.getText(), chatInput));
		HBox chatInputBox = new HBox(8, chatInput, sendBtn);
		HBox.setHgrow(chatInput, Priority.ALWAYS);
		VBox chatBox = new VBox(8, chatList, chatInputBox);
		VBox.setVgrow(chatList, Priority.ALWAYS);
		Tab chatTab = new Tab("Chat", chatBox);

		TextArea rulesText = new TextArea(
				"CHECKERS RULES\n\n" +
						"- Pieces move diagonally forward.\n" +
						"- Captures are mandatory.\n" +
						"- Multi-jump in one turn is required.\n" +
						"- Reach the last row to become a King.\n" +
						"- Kings move in all diagonal directions.\n" +
						"- Win by leaving opponent with no moves."
		);
		rulesText.setEditable(false);
		rulesText.setWrapText(true);
		rulesText.getStyleClass().add("text-area");
		Tab rulesTab = new Tab("Rules", rulesText);

		tabs.getTabs().addAll(movesTab, chatTab, rulesTab);
		VBox.setVgrow(tabs, Priority.ALWAYS);

		Button drawBtn = new Button("½ Draw");
		drawBtn.getStyleClass().add("btn-secondary");
		drawBtn.setOnAction(e -> showDrawDialog());
		drawBtn.setDisable(spectating);

		Button abortBtn = new Button("Abort");
		abortBtn.getStyleClass().add("btn-danger");
		abortBtn.setOnAction(e -> showResignDialog());
		abortBtn.setDisable(spectating);

		HBox btnRow = new HBox(10, drawBtn, abortBtn);
		btnRow.setAlignment(Pos.CENTER);

		panel.getChildren().addAll(tabs, btnRow);
		return panel;
	}

	// minutes seconds text
	private static String formatTime(int sec) {
		sec = Math.max(0, sec);
		return (sec / 60) + ":" + String.format("%02d", sec % 60);
	}

	// fit cell size to available stack
	private void recalculateBoardSquareSize() {
		if (boardCenterStack == null || boardGrid == null) {
			return;
		}
		double W = boardCenterStack.getWidth();
		double H = boardCenterStack.getHeight();
		if (W < 80 || H < 80) {
			return;
		}
		double outerPad = 2 * BOARD_WOOD_PAD_PX;
		double sW = (W - BOARD_LEFT_LABEL_COL_PX - outerPad) / 8.0;
		double sH = (H - BOARD_BOTTOM_LABEL_ROW_PX - outerPad) / 8.0;
		double s = Math.min(sW, sH);
		s = Math.max(16, Math.min(s, 160));
		if (Math.abs(s - squareSize) < 0.4) {
			applyRigidBoardFrameSize();
			return;
		}
		squareSize = s;
		applyBoardGridConstraints();
		renderBoard();
		applyRigidBoardFrameSize();
	}

	// row col sizes for board
	private void applyBoardGridConstraints() {
		boardGrid.getColumnConstraints().clear();
		boardGrid.getRowConstraints().clear();
		ColumnConstraints labelCol = new ColumnConstraints(BOARD_LEFT_LABEL_COL_PX, BOARD_LEFT_LABEL_COL_PX, BOARD_LEFT_LABEL_COL_PX);
		labelCol.setHgrow(Priority.NEVER);
		boardGrid.getColumnConstraints().add(labelCol);
		for (int i = 0; i < 8; i++) {
			ColumnConstraints cc = new ColumnConstraints(squareSize, squareSize, squareSize);
			cc.setHgrow(Priority.NEVER);
			boardGrid.getColumnConstraints().add(cc);
		}
		RowConstraints row0 = new RowConstraints(0, 0, 0);
		row0.setVgrow(Priority.NEVER);
		boardGrid.getRowConstraints().add(row0);
		for (int i = 0; i < 8; i++) {
			RowConstraints rc = new RowConstraints(squareSize, squareSize, squareSize);
			rc.setVgrow(Priority.NEVER);
			boardGrid.getRowConstraints().add(rc);
		}
		RowConstraints bottomLabels = new RowConstraints(
				BOARD_BOTTOM_LABEL_ROW_PX, BOARD_BOTTOM_LABEL_ROW_PX, BOARD_BOTTOM_LABEL_ROW_PX);
		bottomLabels.setVgrow(Priority.NEVER);
		boardGrid.getRowConstraints().add(bottomLabels);
		boardGrid.setMinSize(boardGridInnerWidth(), boardGridInnerHeight());
		boardGrid.setPrefSize(boardGridInnerWidth(), boardGridInnerHeight());
		boardGrid.setMaxSize(boardGridInnerWidth(), boardGridInnerHeight());
	}

	// add move line
	private void appendMoveHistoryText(String line) {
		if (moveHistoryList == null || line == null || line.isEmpty()) return;
		moveHistoryList.getItems().add(line);
		int last = moveHistoryList.getItems().size() - 1;
		Platform.runLater(() -> moveHistoryList.scrollTo(last));
	}

	// sync clock text
	private void updateTimerLabels() {
		if (opponentTimerLabel == null || myTimerLabel == null) return;
		if (spectating) {
			opponentTimerLabel.setText(formatTime(blackTimeLeft));
			myTimerLabel.setText(formatTime(redTimeLeft));
		} else {
			int mine = myColor.equals("red") ? redTimeLeft : blackTimeLeft;
			int opp = myColor.equals("red") ? blackTimeLeft : redTimeLeft;
			opponentTimerLabel.setText(formatTime(opp));
			myTimerLabel.setText(formatTime(mine));
		}
	}

	// turn banner color
	private void setTurnStyle(boolean yourTurn, boolean captureRequired) {
		if (yourTurn) {
			if (captureRequired) {
				turnLabel.setTextFill(lightTheme ? Color.web("#c2410c") : Color.web("#fb923c"));
			} else {
				turnLabel.setTextFill(lightTheme ? Color.web("#1a1a1a") : Color.web("#e8e8e8"));
			}
		} else {
			turnLabel.setTextFill(Color.web("#dc2626"));
		}
	}

	// draw squares and pieces
	public void renderBoard() {
		if (boardGrid == null) return;
		boardGrid.getChildren().clear();

		for (int visualCol = 0; visualCol < 8; visualCol++) {
			int boardCol = flipped ? 7 - visualCol : visualCol;
			Label colLabel = new Label(String.valueOf((char) ('A' + boardCol)));
			colLabel.setMinSize(squareSize, BOARD_BOTTOM_LABEL_ROW_PX);
			colLabel.setAlignment(Pos.CENTER);
			colLabel.getStyleClass().add("board-axis-label");
			boardGrid.add(colLabel, visualCol + 1, 9);
		}

		for (int visualRow = 0; visualRow < 8; visualRow++) {
			int boardRow = flipped ? 7 - visualRow : visualRow;
			Label rowLabel = new Label(String.valueOf(8 - boardRow));
			rowLabel.setMinSize(BOARD_LEFT_LABEL_COL_PX, squareSize);
			rowLabel.setAlignment(Pos.CENTER);
			rowLabel.getStyleClass().add("board-axis-label");
			boardGrid.add(rowLabel, 0, visualRow + 1);
		}

		ArrayList<int[]> captureSources = new ArrayList<>();
		if (!spectating && localGame != null && localGame.currentPlayer.equals(myColor)) {
			ArrayList<Move> allValid = localGame.getValidMoves(myColor);
			if (!allValid.isEmpty() && !allValid.get(0).capturedPositions.isEmpty()) {
				for (Move m : allValid) {
					boolean dup = false;
					for (int[] s : captureSources) {
						if (s[0] == m.fromRow && s[1] == m.fromCol) {
							dup = true;
							break;
						}
					}
					if (!dup) captureSources.add(new int[]{m.fromRow, m.fromCol});
				}
			}
		}

		for (int visualRow = 0; visualRow < 8; visualRow++) {
			for (int visualCol = 0; visualCol < 8; visualCol++) {
				int boardRow = flipped ? 7 - visualRow : visualRow;
				int boardCol = flipped ? 7 - visualCol : visualCol;

				StackPane square = new StackPane();
				square.setMinSize(squareSize, squareSize);

				Rectangle rect = new Rectangle(squareSize, squareSize);
				rect.setFill(boardToneLightSquare(boardRow, boardCol));
				square.getChildren().add(rect);

				boolean mustCapture = false;
				for (int[] src : captureSources) {
					if (src[0] == boardRow && src[1] == boardCol) {
						mustCapture = true;
						break;
					}
				}
				if (mustCapture) {
					Rectangle tint = new Rectangle(squareSize, squareSize);
					tint.setFill(Color.color(1, 0.35, 0.2, lightTheme ? 0.28 : 0.38));
					square.getChildren().add(tint);
				}

				boolean isSelected = boardRow == selectedRow && boardCol == selectedCol;
				if (isSelected) {
					Circle ring = new Circle(squareSize / 2.0 - 4);
					ring.setFill(Color.TRANSPARENT);
					ring.setStroke(lightTheme ? Color.web("#2a2a2a") : Color.web("#8a8a8a"));
					ring.setStrokeWidth(2);
					square.getChildren().add(ring);
				}

				boolean isValidDest = false;
				for (int[] dest : validDestinations) {
					if (dest[0] == boardRow && dest[1] == boardCol) {
						isValidDest = true;
						break;
					}
				}
				if (isValidDest) {
					Circle dot = new Circle(Math.max(5, squareSize * 0.14));
					dot.setMouseTransparent(true);
					dot.setFill(lightTheme ? Color.web("#3a3a3a", 0.8) : Color.web("#5a5a5a", 0.9));
					square.getChildren().add(dot);
				}

				if (localGame != null) {
					Piece piece = localGame.board.getPiece(boardRow, boardCol);
					if (piece != null) {
						Circle circle = new Circle(squareSize / 2.0 - 6);
						Color base = piece.color.equals("red") ? pieceRed() : pieceBlue();
						styleFlatCheckerPiece(circle, base);
						square.getChildren().add(circle);

						if (piece.isKing) {
							Text k = new Text("K");
							k.setFill(Color.WHITE);
							k.setFont(Font.font("DM Sans", FontWeight.BOLD, Math.max(10, squareSize * 0.28)));
							square.getChildren().add(k);
						}
					}
				}

				final int r = boardRow;
				final int c = boardCol;
				square.setOnMouseClicked(e -> onSquareClicked(r, c));
				boardGrid.add(square, visualCol + 1, visualRow + 1);
			}
		}
		if (localGame != null && turnLabel != null) {
			if (spectating) {
				String t = localGame.currentPlayer.equals("red") ? "Red" : "Black";
				turnLabel.setText(t + "'s turn");
				turnLabel.setTextFill(lightTheme ? Color.web("#5a4d42") : Color.web("#a8a8a8"));
			} else if (localGame.currentPlayer.equals(myColor)) {
				if (!captureSources.isEmpty()) {
					turnLabel.setText("Your Turn — Capture Required!");
					setTurnStyle(true, true);
				} else {
					turnLabel.setText("Your Turn");
					setTurnStyle(true, false);
				}
			} else {
				turnLabel.setText("Opponent's Turn");
				setTurnStyle(false, false);
			}
		}
	}

	// select or move
	private void onSquareClicked(int row, int col) {
		if (spectating) return;
		if (localGame == null) return;
		if (!localGame.currentPlayer.equals(myColor)) return;

		Piece piece = localGame.board.getPiece(row, col);

		if (selectedRow == -1) {
			if (piece != null && piece.color.equals(myColor)) {
				selectedRow = row;
				selectedCol = col;
				validDestinations.clear();
				for (Move m : localGame.getValidMoves(myColor)) {
					if (m.fromRow == row && m.fromCol == col) {
						validDestinations.add(new int[]{m.toRow, m.toCol});
					}
				}
				renderBoard();
			}
		} else {
			if (piece != null && piece.color.equals(myColor)) {
				selectedRow = row;
				selectedCol = col;
				validDestinations.clear();
				for (Move m : localGame.getValidMoves(myColor)) {
					if (m.fromRow == row && m.fromCol == col) {
						validDestinations.add(new int[]{m.toRow, m.toCol});
					}
				}
				renderBoard();
			} else {
				Move move = new Move(selectedRow, selectedCol, row, col);
				clientConnection.send(new Message(Message.MessageType.move, move));
				selectedRow = -1;
				selectedCol = -1;
				validDestinations.clear();
			}
		}
	}

	// post chat line
	private void sendChat(String text, TextField input) {
		if (!text.trim().isEmpty()) {
			clientConnection.send(new Message(Message.MessageType.chat, text));
			input.clear();
		}
	}

	// theme on popup
	private void styleDialog(Scene sc) {
		applyTheme(sc);
	}

	// offer draw
	private void showDrawDialog() {
		if (spectating) return;
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Offer Draw");

		VBox layout = new VBox(18);
		layout.getStyleClass().add("dialog-root");
		layout.setAlignment(Pos.CENTER);

		Label msg = new Label("Offer a draw to your opponent?");
		msg.getStyleClass().add("dialog-message");

		Button confirm = new Button("Draw");
		confirm.getStyleClass().add("btn-primary");
		confirm.setOnAction(e -> {
			clientConnection.send(new Message(Message.MessageType.draw_offer, null));
			dialog.close();
		});

		Button cancel = new Button("Cancel");
		cancel.getStyleClass().add("btn-secondary");
		cancel.setOnAction(e -> dialog.close());

		HBox buttons = new HBox(10, cancel, confirm);
		buttons.setAlignment(Pos.CENTER);
		layout.getChildren().addAll(msg, buttons);

		Scene sc = new Scene(layout, 320, 160);
		styleDialog(sc);
		dialog.setScene(sc);
		dialog.show();
	}

	// confirm resign
	private void showResignDialog() {
		if (spectating) return;
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Resign");

		VBox layout = new VBox(18);
		layout.getStyleClass().add("dialog-root");
		layout.setAlignment(Pos.CENTER);

		Label msg = new Label("Are you sure you want to resign?");
		msg.getStyleClass().add("dialog-message");

		Button confirm = new Button("Resign");
		confirm.getStyleClass().add("btn-danger");
		confirm.setOnAction(e -> {
			clientConnection.send(new Message(Message.MessageType.resign, null));
			dialog.close();
		});

		Button cancel = new Button("Cancel");
		cancel.getStyleClass().add("btn-secondary");
		cancel.setOnAction(e -> dialog.close());

		HBox buttons = new HBox(10, cancel, confirm);
		buttons.setAlignment(Pos.CENTER);
		layout.getChildren().addAll(msg, buttons);

		Scene sc = new Scene(layout, 340, 170);
		styleDialog(sc);
		dialog.setScene(sc);
		dialog.show();
	}

	// process server message
	private void handleMessage(Message msg) {
		switch (msg.type) {

			case auth_ok:
				if (msg.data instanceof int[]) {
					int[] s = (int[]) msg.data;
					myRating = s[0];
					myWins = s[1];
					myLosses = s[2];
					myDraws = s[3];
				} else {
					myRating = (Integer) msg.data;
					myWins = myLosses = myDraws = 0;
				}
				updateLobbyProfileText();
				clientConnection.send(new Message(Message.MessageType.list_games, null));
				primaryStage.setScene(sceneMap.get("lobby"));
				syncThemeToggleLabel(sceneMap.get("lobby").getRoot());
				break;

			case auth_fail:
				Alert a = new Alert(Alert.AlertType.ERROR);
				a.setTitle("Account");
				a.setContentText((String) msg.data);
				a.show();
				break;

			case games_list:
				@SuppressWarnings("unchecked")
				ArrayList<GameListEntry> entries = (ArrayList<GameListEntry>) msg.data;
				gamesListView.getItems().clear();
				if (entries != null) {
					gamesListView.getItems().addAll(entries);
				}
				break;

			case spectator_fail:
				Alert sf = new Alert(Alert.AlertType.ERROR);
				sf.setTitle("Spectate");
				sf.setContentText((String) msg.data);
				sf.show();
				break;

			case spectator_ok:
				spectating = true;
				Object[] sp = (Object[]) msg.data;
				Board sb = (Board) sp[0];
				int[] srat = (int[]) sp[1];
				int[] st = (int[]) sp[2];
				String sRed = (String) sp[3];
				String sBlack = (String) sp[4];
				String sCur = (String) sp[5];
				localGame = new CheckersGame(sRed, sBlack);
				localGame.board = sb;
				localGame.currentPlayer = sCur;
				redTimeLeft = st[0];
				blackTimeLeft = st[1];
				flipped = false;
				opponentLabelText = sBlack + " (" + srat[1] + ")";
				youLabelText = sRed + " (" + srat[0] + ")";
				sceneMap.put("game", createGameScene());
				primaryStage.setScene(sceneMap.get("game"));
				syncThemeToggleLabel(sceneMap.get("game").getRoot());
				break;

			case username_ok:
				primaryStage.setScene(sceneMap.get("waiting"));
				syncThemeToggleLabel(sceneMap.get("waiting").getRoot());
				break;

			case username_taken:
				Alert alert = new Alert(Alert.AlertType.ERROR);
				alert.setTitle("Username Taken");
				alert.setContentText("That username is already taken. Try another.");
				alert.show();
				break;

			case game_start:
				spectating = false;
				Object[] data = (Object[]) msg.data;
				String[] info = (String[]) data[0];
				String oppPlain = info[0];
				myColor = info[1];
				opponentUsername = oppPlain;
				flipped = myColor.equals("black");
				int[] ratings = (int[]) data[2];
				int[] times = (int[]) data[3];
				int oppR = myColor.equals("red") ? ratings[1] : ratings[0];
				int meR = myColor.equals("red") ? ratings[0] : ratings[1];
				opponentLabelText = oppPlain + " (" + oppR + ")";
				youLabelText = myUsername + " (" + meR + ")";
				localGame = new CheckersGame(
						myColor.equals("red") ? myUsername : oppPlain,
						myColor.equals("black") ? myUsername : oppPlain
				);
				localGame.board = (Board) data[1];
				redTimeLeft = times[0];
				blackTimeLeft = times[1];
				sceneMap.put("game", createGameScene());
				primaryStage.setScene(sceneMap.get("game"));
				syncThemeToggleLabel(sceneMap.get("game").getRoot());
				break;

			case move:
				if (localGame == null) {
					break;
				}
				Object[] mv = (Object[]) msg.data;
				localGame.board = (Board) mv[0];
				redTimeLeft = ((Number) mv[1]).intValue();
				blackTimeLeft = ((Number) mv[2]).intValue();
				localGame.currentPlayer = localGame.currentPlayer.equals("black") ? "red" : "black";
				if (mv.length >= 4 && mv[3] instanceof String) {
					appendMoveHistoryText((String) mv[3]);
				}
				updateTimerLabels();
				renderBoard();
				break;

			case timer_sync:
				int[] ts = (int[]) msg.data;
				redTimeLeft = ts[0];
				blackTimeLeft = ts[1];
				updateTimerLabels();
				break;

			case rating_update:
				if (msg.data instanceof int[]) {
					int[] s = (int[]) msg.data;
					myRating = s[0];
					myWins = s[1];
					myLosses = s[2];
					myDraws = s[3];
				} else {
					myRating = (Integer) msg.data;
				}
				updateLobbyProfileText();
				break;

			case friend_list_online:
				@SuppressWarnings("unchecked")
				ArrayList<String> onlineF = (ArrayList<String>) msg.data;
				if (onlineFriendsListView != null) {
					onlineFriendsListView.getItems().clear();
					if (onlineF != null) {
						onlineFriendsListView.getItems().addAll(onlineF);
					}
				}
				break;

			case online_status:
				if (msg.data instanceof Object[]) {
					Object[] os = (Object[]) msg.data;
					if (os.length >= 2 && os[0] instanceof String && os[1] instanceof Boolean) {
						syncFriendOnline((String) os[0], (Boolean) os[1]);
					}
				}
				break;

			case friend_pending_list:
				@SuppressWarnings("unchecked")
				ArrayList<String> pending = (ArrayList<String>) msg.data;
				rebuildFriendPendingRows(pending != null ? pending : new ArrayList<String>());
				break;

			case friend_incoming:
				showFriendIncomingDialog((String) msg.data);
				break;

			case friend_error: {
				Alert fe = new Alert(Alert.AlertType.ERROR);
				fe.setTitle("Friends");
				fe.setContentText((String) msg.data);
				fe.show();
				break;
			}

			case friend_notice: {
				Alert fn = new Alert(Alert.AlertType.INFORMATION);
				fn.setTitle("Friends");
				fn.setContentText((String) msg.data);
				fn.show();
				break;
			}

			case invalid_move:
				Alert invalidAlert = new Alert(Alert.AlertType.WARNING);
				invalidAlert.setTitle("Invalid Move");
				String invalidMsg = "That move is not allowed. Try again.";
				if (!spectating && localGame != null && myColor != null) {
					ArrayList<Move> vm = localGame.getValidMoves(myColor);
					if (!vm.isEmpty() && !vm.get(0).capturedPositions.isEmpty()) {
						invalidMsg = "A capture is available! You must move a highlighted piece.";
					}
				}
				invalidAlert.setContentText(invalidMsg);
				invalidAlert.show();
				break;

			case game_over:
				String result = (String) msg.data;
				showGameOverDialog(result);
				break;

			case chat:
				if (chatList != null) {
					chatList.getItems().add((String) msg.data);
				}
				break;

			case draw_offer:
				if (!spectating && localGame != null) {
					showDrawResponseDialog();
				}
				break;

			case draw_decline:
				if (chatList != null) {
					chatList.getItems().add("Your draw offer was declined.");
				}
				break;

			case waiting:
				primaryStage.setScene(sceneMap.get("waiting"));
				syncThemeToggleLabel(sceneMap.get("waiting").getRoot());
				clientConnection.send(new Message(Message.MessageType.list_games, null));
				break;

			default:
				break;
		}
	}

	// answer draw offer
	private void showDrawResponseDialog() {
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Draw Offer");

		VBox layout = new VBox(18);
		layout.getStyleClass().add("dialog-root");
		layout.setAlignment(Pos.CENTER);

		Label msg = new Label("Your opponent offered a draw.");
		msg.getStyleClass().add("dialog-message");

		Button accept = new Button("Accept");
		accept.getStyleClass().add("btn-primary");
		accept.setOnAction(e -> {
			clientConnection.send(new Message(Message.MessageType.draw_accept, null));
			dialog.close();
		});

		Button decline = new Button("Decline");
		decline.getStyleClass().add("btn-danger");
		decline.setOnAction(e -> {
			clientConnection.send(new Message(Message.MessageType.draw_decline, null));
			dialog.close();
		});

		HBox buttons = new HBox(10, decline, accept);
		buttons.setAlignment(Pos.CENTER);
		layout.getChildren().addAll(msg, buttons);

		Scene sc = new Scene(layout, 320, 170);
		styleDialog(sc);
		dialog.setScene(sc);
		dialog.show();
	}

	// reset to lobby
	private void returnToLobbyAfterGame() {
		localGame = null;
		selectedRow = -1;
		selectedCol = -1;
		validDestinations.clear();
		opponentUsername = null;
		myColor = null;
		flipped = false;
		spectating = false;
		opponentLabelText = "";
		youLabelText = "";
		redTimeLeft = GameSessionMirror.START_SECONDS;
		blackTimeLeft = GameSessionMirror.START_SECONDS;
		boardGrid = null;
		boardWoodFrame = null;
		boardCenterStack = null;
		turnLabel = null;
		chatList = null;
		moveHistoryList = null;
		opponentTimerLabel = null;
		myTimerLabel = null;
		updateLobbyProfileText();
		primaryStage.setScene(sceneMap.get("lobby"));
		syncThemeToggleLabel(sceneMap.get("lobby").getRoot());
		clientConnection.send(new Message(Message.MessageType.list_games, null));
	}

	// end game modal
	private void showGameOverDialog(String result) {
		Stage dialog = new Stage();
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("Game Over");

		String text;
		if (spectating) {
			text = result.equals("draw") ? "Game drawn." : result + " wins.";
		} else {
			text = result.equals("draw") ? "Game drawn!" :
					result.equals(myUsername) ? "You win!" : "You lose!";
		}

		VBox layout = new VBox(20);
		layout.getStyleClass().add("dialog-root");
		layout.setAlignment(Pos.CENTER);

		Label msg = new Label(text);
		msg.getStyleClass().add("title-lg");
		msg.setWrapText(true);

		Button lobbyBtn = new Button("Return to Lobby");
		lobbyBtn.getStyleClass().add("btn-primary");
		lobbyBtn.setOnAction(e -> {
			dialog.close();
			returnToLobbyAfterGame();
		});

		HBox buttons = new HBox(10, lobbyBtn);
		buttons.setAlignment(Pos.CENTER);
		layout.getChildren().addAll(msg, buttons);

		Scene sc = new Scene(layout, 340, 220);
		styleDialog(sc);
		dialog.setScene(sc);
		dialog.show();
	}
}
