import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
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
	static final int SQUARE_SIZE = 65;

	int myRating = 1200;
	Label lobbyRatingLabel;
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

	public static void main(String[] args) {
		launch(args);
	}

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

	private void applyTheme(Scene scene) {
		if (scene == null) return;
		String path = lightTheme ? "/styles/light.css" : "/styles/dark.css";
		var url = getClass().getResource(path);
		if (url != null) {
			scene.getStylesheets().clear();
			scene.getStylesheets().add(url.toExternalForm());
		}
	}

	private void visitNodes(Node n, Consumer<Node> fn) {
		fn.accept(n);
		if (n instanceof Parent) {
			for (Node c : ((Parent) n).getChildrenUnmodifiable()) {
				visitNodes(c, fn);
			}
		}
	}

	private void syncThemeToggleGlyphs(Node root) {
		if (root == null) return;
		String glyph = lightTheme ? "\u263E" : "\u2600";
		visitNodes(root, n -> {
			if (n instanceof Button && n.getStyleClass().contains("btn-theme-toggle")) {
				((Button) n).setText(glyph);
			}
		});
	}

	private void onThemeToggled() {
		lightTheme = !lightTheme;
		ThemePreferences.setLightTheme(lightTheme);
		for (String key : new String[]{"login", "lobby", "waiting"}) {
			Scene sc = sceneMap.get(key);
			if (sc != null) {
				applyTheme(sc);
				syncThemeToggleGlyphs(sc.getRoot());
			}
		}
		Scene gameSc = sceneMap.get("game");
		if (gameSc != null) {
			applyTheme(gameSc);
			syncThemeToggleGlyphs(gameSc.getRoot());
		}
		if (boardGrid != null) {
			renderBoard();
		}
	}

	private Button createThemeToggleButton() {
		Button b = new Button(lightTheme ? "\u263E" : "\u2600");
		b.getStyleClass().add("btn-theme-toggle");
		b.setOnAction(e -> onThemeToggled());
		return b;
	}

	private HBox buildTopThemeBar() {
		HBox bar = new HBox();
		Region spacer = new Region();
		HBox.setHgrow(spacer, Priority.ALWAYS);
		bar.getChildren().addAll(spacer, createThemeToggleButton());
		bar.setPadding(new Insets(10, 18, 6, 18));
		return bar;
	}

	private Color boardToneLightSquare(int boardRow, int boardCol) {
		if (lightTheme) {
			return (boardRow + boardCol) % 2 == 0 ? Color.web("#f2e8dc") : Color.web("#c9a87a");
		}
		return (boardRow + boardCol) % 2 == 0 ? Color.web("#e8d4b8") : Color.web("#7a4f28");
	}

	private RadialGradient pieceGradient(Color base) {
		Color hi = base.interpolate(Color.WHITE, 0.42);
		Color lo = base.darker();
		return new RadialGradient(0, 0, 0.38, 0.38, 0.62, true, CycleMethod.NO_CYCLE,
				new Stop(0, hi),
				new Stop(0.55, base),
				new Stop(1, lo));
	}

	private Color pieceRed() {
		return lightTheme ? Color.web("#e07a6a") : Color.web("#f9a186");
	}

	private Color pieceBlack() {
		return lightTheme ? Color.web("#3d7a9e") : Color.web("#147493");
	}

	private HBox buildPlayerCard(String displayLine, Label timerLabel, String avatarHex) {
		HBox row = new HBox(14);
		row.setAlignment(Pos.CENTER_LEFT);
		row.getStyleClass().add("player-card");
		row.setMaxWidth(Double.MAX_VALUE);

		StackPane av = new StackPane();
		Circle avBg = new Circle(22);
		avBg.setFill(Color.web(avatarHex));
		String initial = (displayLine == null || displayLine.isEmpty()) ? "?"
				: displayLine.substring(0, 1).toUpperCase();
		Label ini = new Label(initial);
		ini.setTextFill(Color.WHITE);
		ini.setFont(Font.font("Nunito", FontWeight.BOLD, 15));
		av.getChildren().addAll(avBg, ini);

		VBox textCol = new VBox(4);
		Label name = new Label(displayLine);
		name.getStyleClass().add("player-name");
		timerLabel.getStyleClass().clear();
		timerLabel.getStyleClass().addAll("label", "player-timer");
		textCol.getChildren().addAll(name, timerLabel);
		HBox.setHgrow(textCol, Priority.ALWAYS);

		row.getChildren().addAll(av, textCol);
		return row;
	}

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

	private Scene createLobbyScene() {
		BorderPane root = new BorderPane();
		root.getStyleClass().add("root-app");

		VBox card = new VBox(14);
		card.getStyleClass().add("card");
		card.setAlignment(Pos.TOP_CENTER);
		card.setMaxWidth(520);
		card.setFillWidth(true);

		Label title = new Label("LOBBY");
		title.getStyleClass().add("title-lg");

		lobbyRatingLabel = new Label("Rating: —");
		lobbyRatingLabel.getStyleClass().add("subtitle");

		Label gamesTitle = new Label("Games in progress");
		gamesTitle.getStyleClass().add("label-muted");

		gamesListView = new ListView<>();
		gamesListView.setPrefHeight(240);
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

		Button playBtn = new Button("FIND MATCH");
		playBtn.getStyleClass().add("btn-primary");
		playBtn.setMaxWidth(Double.MAX_VALUE);
		playBtn.setOnAction(e -> clientConnection.send(new Message(Message.MessageType.join_queue, null)));

		card.getChildren().addAll(title, lobbyRatingLabel, gamesTitle, gamesListView, refreshBtn, spectateBtn, playBtn);

		StackPane center = new StackPane(card);
		root.setTop(buildTopThemeBar());
		root.setCenter(center);
		Scene scene = new Scene(root, 820, 680);
		applyTheme(scene);
		return scene;
	}

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

	private Scene createGameScene() {
		turnLabel = new Label("");
		turnLabel.getStyleClass().add("turn-banner");

		BorderPane root = new BorderPane();
		root.getStyleClass().add("root-app");

		opponentTimerLabel = new Label(formatTime(GameSessionMirror.START_SECONDS));
		myTimerLabel = new Label(formatTime(GameSessionMirror.START_SECONDS));

		HBox oppCard = buildPlayerCard(opponentLabelText, opponentTimerLabel, "#5b8def");
		HBox youCard = buildPlayerCard(youLabelText, myTimerLabel, "#e8956a");

		boardGrid = new GridPane();
		applyBoardGridConstraints();
		renderBoard();

		ScrollPane boardScroll = new ScrollPane(boardGrid);
		boardScroll.setFitToWidth(false);
		boardScroll.setFitToHeight(false);
		boardScroll.setPannable(true);
		boardScroll.getStyleClass().add("scroll-pane");
		boardScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
		boardScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

		StackPane boardOuter = new StackPane(boardScroll);
		boardOuter.getStyleClass().add("board-outer");

		VBox leftCol = new VBox(12);
		leftCol.setPadding(new Insets(8, 20, 18, 20));
		leftCol.setAlignment(Pos.TOP_CENTER);
		leftCol.setMinWidth(32 + 8 * SQUARE_SIZE + 40);
		VBox.setVgrow(boardOuter, Priority.ALWAYS);
		leftCol.getChildren().addAll(oppCard, turnLabel, boardOuter, youCard);

		VBox rightPanel = createRightPanel();
		rightPanel.setPrefWidth(260);
		rightPanel.setMinWidth(240);
		rightPanel.getStyleClass().add("sidebar-panel");

		root.setTop(buildTopThemeBar());
		root.setCenter(leftCol);
		root.setRight(rightPanel);

		updateTimerLabels();
		Scene scene = new Scene(root, 1120, 760);
		applyTheme(scene);
		return scene;
	}

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

	private static String formatTime(int sec) {
		sec = Math.max(0, sec);
		return (sec / 60) + ":" + String.format("%02d", sec % 60);
	}

	private void applyBoardGridConstraints() {
		boardGrid.getColumnConstraints().clear();
		boardGrid.getRowConstraints().clear();
		ColumnConstraints labelCol = new ColumnConstraints(22, 22, 22);
		labelCol.setHgrow(Priority.NEVER);
		boardGrid.getColumnConstraints().add(labelCol);
		for (int i = 0; i < 8; i++) {
			ColumnConstraints cc = new ColumnConstraints(SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
			cc.setHgrow(Priority.NEVER);
			boardGrid.getColumnConstraints().add(cc);
		}
		RowConstraints row0 = new RowConstraints(0, 0, 0);
		row0.setVgrow(Priority.NEVER);
		boardGrid.getRowConstraints().add(row0);
		for (int i = 0; i < 8; i++) {
			RowConstraints rc = new RowConstraints(SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);
			rc.setVgrow(Priority.NEVER);
			boardGrid.getRowConstraints().add(rc);
		}
		RowConstraints bottomLabels = new RowConstraints(20, 20, 20);
		bottomLabels.setVgrow(Priority.NEVER);
		boardGrid.getRowConstraints().add(bottomLabels);
		boardGrid.setMaxWidth(Region.USE_PREF_SIZE);
		boardGrid.setMaxHeight(Region.USE_PREF_SIZE);
	}

	private void appendMoveHistoryText(String line) {
		if (moveHistoryList == null || line == null || line.isEmpty()) return;
		moveHistoryList.getItems().add(line);
		int last = moveHistoryList.getItems().size() - 1;
		Platform.runLater(() -> moveHistoryList.scrollTo(last));
	}

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

	private void setTurnStyle(boolean yourTurn, boolean captureRequired) {
		if (lightTheme) {
			if (yourTurn) {
				turnLabel.setTextFill(captureRequired ? Color.web("#c2410c") : Color.web("#15803d"));
			} else {
				turnLabel.setTextFill(Color.web("#6b5d4f"));
			}
		} else {
			if (yourTurn) {
				turnLabel.setTextFill(captureRequired ? Color.web("#fb923c") : Color.web("#4ade80"));
			} else {
				turnLabel.setTextFill(Color.web("#a1a1aa"));
			}
		}
	}

	public void renderBoard() {
		if (boardGrid == null) return;
		boardGrid.getChildren().clear();

		for (int visualCol = 0; visualCol < 8; visualCol++) {
			int boardCol = flipped ? 7 - visualCol : visualCol;
			Label colLabel = new Label(String.valueOf((char) ('A' + boardCol)));
			colLabel.setMinSize(SQUARE_SIZE, 20);
			colLabel.setAlignment(Pos.CENTER);
			colLabel.getStyleClass().add("board-axis-label");
			boardGrid.add(colLabel, visualCol + 1, 9);
		}

		for (int visualRow = 0; visualRow < 8; visualRow++) {
			int boardRow = flipped ? 7 - visualRow : visualRow;
			Label rowLabel = new Label(String.valueOf(8 - boardRow));
			rowLabel.setMinSize(20, SQUARE_SIZE);
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
				square.setMinSize(SQUARE_SIZE, SQUARE_SIZE);

				Rectangle rect = new Rectangle(SQUARE_SIZE, SQUARE_SIZE);
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
					Rectangle tint = new Rectangle(SQUARE_SIZE, SQUARE_SIZE);
					tint.setFill(Color.color(1, 0.35, 0.2, lightTheme ? 0.28 : 0.38));
					square.getChildren().add(tint);
				}

				boolean isSelected = boardRow == selectedRow && boardCol == selectedCol;
				if (isSelected) {
					Circle ring = new Circle(SQUARE_SIZE / 2.0 - 4);
					ring.setFill(Color.TRANSPARENT);
					ring.setStroke(lightTheme ? Color.web("#2563eb") : Color.web("#7dd3fc"));
					ring.setStrokeWidth(3);
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
					Circle dot = new Circle(8);
					dot.setMouseTransparent(true);
					dot.setFill(lightTheme ? Color.web("#4a7de8").deriveColor(0, 1, 1, 0.88)
							: Color.web("#5b8def").deriveColor(0, 1, 1, 0.9));
					square.getChildren().add(dot);
				}

				if (localGame != null) {
					Piece piece = localGame.board.getPiece(boardRow, boardCol);
					if (piece != null) {
						Circle circle = new Circle(SQUARE_SIZE / 2.0 - 6);
						Color base = piece.color.equals("red") ? pieceRed() : pieceBlack();
						circle.setFill(pieceGradient(base));
						square.getChildren().add(circle);

						if (piece.isKing) {
							Text k = new Text("K");
							k.setFill(Color.WHITE);
							k.setFont(Font.font("Nunito", FontWeight.BOLD, 15));
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
				turnLabel.setTextFill(lightTheme ? Color.web("#5a4d42") : Color.web("#c4c4d8"));
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

	private void sendChat(String text, TextField input) {
		if (!text.trim().isEmpty()) {
			clientConnection.send(new Message(Message.MessageType.chat, text));
			input.clear();
		}
	}

	private void styleDialog(Scene sc) {
		applyTheme(sc);
	}

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

	private void handleMessage(Message msg) {
		switch (msg.type) {

			case auth_ok:
				myRating = (Integer) msg.data;
				lobbyRatingLabel.setText("Rating: " + myRating + "  —  " + myUsername);
				clientConnection.send(new Message(Message.MessageType.list_games, null));
				primaryStage.setScene(sceneMap.get("lobby"));
				syncThemeToggleGlyphs(sceneMap.get("lobby").getRoot());
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
				syncThemeToggleGlyphs(sceneMap.get("game").getRoot());
				break;

			case username_ok:
				primaryStage.setScene(sceneMap.get("waiting"));
				syncThemeToggleGlyphs(sceneMap.get("waiting").getRoot());
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
				syncThemeToggleGlyphs(sceneMap.get("game").getRoot());
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
				myRating = (Integer) msg.data;
				if (lobbyRatingLabel != null) {
					lobbyRatingLabel.setText("Rating: " + myRating + "  —  " + myUsername);
				}
				break;

			case invalid_move:
				Alert invalidAlert = new Alert(Alert.AlertType.WARNING);
				invalidAlert.setTitle("Invalid Move");
				String invalidMsg = "That move is not allowed. Try again.";
				if (!spectating && localGame != null && myColor != null) {
					ArrayList<Move> vm = localGame.getValidMoves(myColor);
					if (!vm.isEmpty() && !vm.get(0).capturedPositions.isEmpty()) {
						invalidMsg = "A capture is available! You must move an orange-highlighted piece.";
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
				syncThemeToggleGlyphs(sceneMap.get("waiting").getRoot());
				clientConnection.send(new Message(Message.MessageType.list_games, null));
				break;

			default:
				break;
		}
	}

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
		turnLabel = null;
		chatList = null;
		moveHistoryList = null;
		opponentTimerLabel = null;
		myTimerLabel = null;
		if (lobbyRatingLabel != null && myUsername != null) {
			lobbyRatingLabel.setText("Rating: " + myRating + "  —  " + myUsername);
		}
		primaryStage.setScene(sceneMap.get("lobby"));
		syncThemeToggleGlyphs(sceneMap.get("lobby").getRoot());
		clientConnection.send(new Message(Message.MessageType.list_games, null));
	}

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
