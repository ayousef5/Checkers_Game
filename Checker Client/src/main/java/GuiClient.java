import javafx.application.Application; // base class for JavaFX apps
import javafx.application.Platform; // run code on UI thread
import javafx.geometry.Insets; // padding
import javafx.geometry.Pos; // alignment
import javafx.scene.Scene; // a screen/view
import javafx.scene.control.*; // buttons, labels, tabs, etc.
import javafx.scene.layout.*; // layout containers
import javafx.scene.paint.Color; // colors
import javafx.scene.shape.Circle; // piece shape
import javafx.scene.shape.Rectangle; // board square shape
import javafx.scene.text.Font; // font styling
import javafx.scene.text.FontWeight; // bold text
import javafx.scene.text.Text; // text node
import javafx.stage.Modality; // for dialogs
import javafx.stage.Stage; // window
import java.util.ArrayList; // needed for lists
import java.util.HashMap; // scene map

public class GuiClient extends Application { // main client GUI

	Client clientConnection; // connection to server
	CheckersGame localGame; // local copy of game state
	String myUsername; // this player's username
	String opponentUsername; // opponent's username
	String myColor; // "red" or "black"
	HashMap<String, Scene> sceneMap; // all scenes
	Stage primaryStage; // main window
	boolean flipped = false; // true for blue player, flips board orientation
	ListView<String> chatList; // chat messages
	ListView<String> moveHistoryList; // move history
	GridPane boardGrid; // the board UI
	Label turnLabel; // shows whose turn it is
	int selectedRow = -1; // currently selected piece row
	int selectedCol = -1; // currently selected piece col
	ArrayList<int[]> validDestinations = new ArrayList<>(); // valid squares for selected piece
	static final int SQUARE_SIZE = 65; // size of each board square in pixels

	public static void main(String[] args) { // entry point
		launch(args); // start JavaFX
	}

	@Override
	public void start(Stage stage) throws Exception { // called by JavaFX on startup
		this.primaryStage = stage; // save stage reference

		clientConnection = new Client(msg -> { // create client with callback
			Platform.runLater(() -> handleMessage(msg)); // handle on UI thread
		});
		clientConnection.start(); // connect to server

		sceneMap = new HashMap<>(); // initialize scene map
		sceneMap.put("login", createLoginScene()); // add login scene
		sceneMap.put("waiting", createWaitingScene()); // add waiting scene

		stage.setOnCloseRequest(e -> { // on window close
			Platform.exit(); // exit JavaFX
			System.exit(0); // exit app
		});

		stage.setScene(sceneMap.get("login")); // start with login
		stage.setTitle("Checkers"); // window title
		stage.show(); // show window
	}

	private Scene createLoginScene() { // login screen
		VBox root = new VBox(20); // vertical layout
		root.setAlignment(Pos.CENTER); // center everything
		root.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		Label title = new Label("CHECKERS"); // title label
		title.setFont(Font.font("Arial", FontWeight.BOLD, 36)); // large bold font
		title.setTextFill(Color.WHITE); // white text

		TextField usernameField = new TextField(); // username input
		usernameField.setPromptText("Enter username"); // placeholder text
		usernameField.setMaxWidth(250); // limit width
		usernameField.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white; -fx-prompt-text-fill: gray;"); // dark style

		Button joinBtn = new Button("JOIN GAME"); // join button
		joinBtn.setStyle("-fx-background-color: #147493; -fx-text-fill: white; -fx-font-weight: bold;"); // blue style
		joinBtn.setOnAction(e -> { // on click
			String name = usernameField.getText().trim(); // get username
			if (!name.isEmpty()) { // if not empty
				myUsername = name; // save username locally
				clientConnection.send(new Message(Message.MessageType.username, name)); // send to server
			}
		});

		root.getChildren().addAll(title, usernameField, joinBtn); // add to layout
		return new Scene(root, 800, 600); // return scene
	}

	private Scene createWaitingScene() { // waiting for opponent screen
		VBox root = new VBox(20); // vertical layout
		root.setAlignment(Pos.CENTER); // center everything
		root.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		Label label = new Label("PLAY CHECKERS!"); // waiting text
		label.setFont(Font.font("Arial", FontWeight.BOLD, 36)); // large bold font
		label.setTextFill(Color.WHITE); // white text

		Label sub = new Label("Waiting for opponent..."); // sub label
		sub.setFont(Font.font("Arial", 18)); // medium font
		sub.setTextFill(Color.GRAY); // gray text

		root.getChildren().addAll(label, sub); // add to layout
		return new Scene(root, 800, 600); // return scene
	}

	private Scene createGameScene() { // main game screen
		turnLabel = new Label(""); // turn indicator
		turnLabel.setFont(Font.font("Arial", FontWeight.BOLD, 14)); // bold font
		HBox root = new HBox(); // horizontal split layout
		root.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		VBox leftPanel = new VBox(10); // left panel for board
		leftPanel.setAlignment(Pos.CENTER); // center board
		leftPanel.setPadding(new Insets(20)); // padding around board

		Label opponentLabel = new Label(opponentUsername); // opponent name at top
		opponentLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16)); // bold font
		opponentLabel.setTextFill(Color.WHITE); // white text

		Label youLabel = new Label("YOU"); // your name at bottom
		youLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16)); // bold font
		youLabel.setTextFill(Color.WHITE); // white text

		boardGrid = new GridPane(); // board grid
		renderBoard(); // draw initial board

		leftPanel.getChildren().addAll(opponentLabel, turnLabel, boardGrid, youLabel);
		HBox.setHgrow(leftPanel, Priority.ALWAYS); // take up 70%

		VBox rightPanel = createRightPanel(); // right panel with tabs
		rightPanel.setPrefWidth(240); // 30% width
		rightPanel.setStyle("-fx-background-color: #1e1e1e;"); // darker background

		root.getChildren().addAll(leftPanel, rightPanel); // combine panels
		return new Scene(root, 900, 650); // return scene
	}

	private VBox createRightPanel() { // right panel with tabs and buttons
		VBox panel = new VBox(10); // vertical layout
		panel.setPadding(new Insets(10)); // padding

		TabPane tabs = new TabPane(); // tab container
		tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); // no close button
		tabs.setStyle("-fx-background-color: #1e1e1e;"); // dark style

		// moves tab
		moveHistoryList = new ListView<>(); // move history list
		moveHistoryList.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;"); // dark style
		Tab movesTab = new Tab("Moves", moveHistoryList); // moves tab

		// chat tab
		chatList = new ListView<>(); // chat messages list
		chatList.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;"); // dark style
		TextField chatInput = new TextField(); // chat input
		chatInput.setPromptText("Message..."); // placeholder
		chatInput.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white; -fx-prompt-text-fill: gray;"); // dark style
		Button sendBtn = new Button("Send"); // send button
		sendBtn.setStyle("-fx-background-color: #147493; -fx-text-fill: white;"); // blue style
		sendBtn.setOnAction(e -> sendChat(chatInput.getText(), chatInput)); // send on click
		HBox chatInputBox = new HBox(5, chatInput, sendBtn); // input row
		HBox.setHgrow(chatInput, Priority.ALWAYS); // input takes full width
		VBox chatBox = new VBox(5, chatList, chatInputBox); // chat layout
		VBox.setVgrow(chatList, Priority.ALWAYS); // list takes remaining space
		Tab chatTab = new Tab("Chat", chatBox); // chat tab

		// rules tab
		TextArea rulesText = new TextArea( // rules content
				"CHECKERS RULES\n\n" +
						"- Pieces move diagonally forward.\n" +
						"- Captures are mandatory.\n" +
						"- Multi-jump in one turn is required.\n" +
						"- Reach the last row to become a King.\n" +
						"- Kings move in all diagonal directions.\n" +
						"- Win by leaving opponent with no moves."
		);
		rulesText.setEditable(false); // read only
		rulesText.setWrapText(true); // wrap long lines
		rulesText.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white; -fx-control-inner-background:#2b2b2b;"); // dark style
		Tab rulesTab = new Tab("Rules", rulesText); // rules tab

		tabs.getTabs().addAll(movesTab, chatTab, rulesTab); // add all tabs
		VBox.setVgrow(tabs, Priority.ALWAYS); // tabs take remaining space

		// bottom buttons
		Button drawBtn = new Button("½ Draw"); // draw offer button
		drawBtn.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white;"); // dark style
		drawBtn.setOnAction(e -> showDrawDialog()); // show draw dialog on click

		Button abortBtn = new Button("Abort"); // abort/resign button
		abortBtn.setStyle("-fx-background-color: #8B0000; -fx-text-fill: white;"); // red style
		abortBtn.setOnAction(e -> showResignDialog()); // show resign dialog on click

		HBox btnRow = new HBox(10, drawBtn, abortBtn); // button row
		btnRow.setAlignment(Pos.CENTER); // center buttons

		panel.getChildren().addAll(tabs, btnRow); // add to panel
		return panel; // return panel
	}

	public void renderBoard() { // draw the board and pieces
		boardGrid.getChildren().clear(); // clear old board

		for (int visualCol = 0; visualCol < 8; visualCol++) { // column labels
			int boardCol = flipped ? 7 - visualCol : visualCol; // flip if needed
			Label colLabel = new Label(String.valueOf((char)('A' + boardCol))); // A-H
			colLabel.setMinSize(SQUARE_SIZE, 20); // fixed size
			colLabel.setAlignment(Pos.CENTER); // center text
			colLabel.setTextFill(Color.WHITE); // white text
			boardGrid.add(colLabel, visualCol + 1, 9); // below board
		}

		for (int visualRow = 0; visualRow < 8; visualRow++) { // row labels
			int boardRow = flipped ? 7 - visualRow : visualRow; // flip if needed
			Label rowLabel = new Label(String.valueOf(8 - boardRow)); // 1-8
			rowLabel.setMinSize(20, SQUARE_SIZE); // fixed size
			rowLabel.setAlignment(Pos.CENTER); // center text
			rowLabel.setTextFill(Color.WHITE); // white text
			boardGrid.add(rowLabel, 0, visualRow + 1); // left of board
		}

		ArrayList<int[]> captureSources = new ArrayList<>(); // pieces that must capture this turn
		if (localGame != null && localGame.currentPlayer.equals(myColor)) { // only on my turn
			ArrayList<Move> allValid = localGame.getValidMoves(myColor); // get all valid moves
			if (!allValid.isEmpty() && !allValid.get(0).capturedPositions.isEmpty()) { // jumps exist
				for (Move m : allValid) { // collect unique source positions
					boolean dup = false;
					for (int[] s : captureSources) { // check for duplicates
						if (s[0] == m.fromRow && s[1] == m.fromCol) { dup = true; break; }
					}
					if (!dup) captureSources.add(new int[]{m.fromRow, m.fromCol}); // add source
				}
			}
		}

		for (int visualRow = 0; visualRow < 8; visualRow++) { // each row
			for (int visualCol = 0; visualCol < 8; visualCol++) { // each column
				int boardRow = flipped ? 7 - visualRow : visualRow; // actual board row
				int boardCol = flipped ? 7 - visualCol : visualCol; // actual board col

				StackPane square = new StackPane(); // container for square + piece
				square.setMinSize(SQUARE_SIZE, SQUARE_SIZE); // fixed size

				Rectangle rect = new Rectangle(SQUARE_SIZE, SQUARE_SIZE); // background
				if ((boardRow + boardCol) % 2 == 0) { // light square
					rect.setFill(Color.web("#F0D9B5")); // light brown
				} else { // dark square
					rect.setFill(Color.web("#8B4513")); // dark brown
				}
				square.getChildren().add(rect); // add background

				if (boardRow == selectedRow && boardCol == selectedCol) { // selected
					rect.setFill(Color.web("#aef060")); // green highlight
				}
				boolean isValidDest = false; // check if this square is a valid destination
				for (int[] dest : validDestinations) { // check each valid destination
					if (dest[0] == boardRow && dest[1] == boardCol) { // match found
						isValidDest = true; // mark as valid
						break;
					}
				}
				if (isValidDest) { // highlight valid destination
					rect.setFill(Color.web("#f6f669")); // yellow highlight
				}
				if (!isValidDest && !(boardRow == selectedRow && boardCol == selectedCol)) { // not already highlighted
					for (int[] src : captureSources) { // check mandatory capture sources
						if (src[0] == boardRow && src[1] == boardCol) { // must-capture piece
							rect.setFill(Color.web("#FF6B35")); // orange = must capture
							break;
						}
					}
				}

				if (localGame != null) { // if game exists
					Piece piece = localGame.board.getPiece(boardRow, boardCol); // get piece
					if (piece != null) { // if piece exists
						Circle circle = new Circle(SQUARE_SIZE / 2.0 - 6); // piece circle
						if (piece.color.equals("red")) { // orange piece
							circle.setFill(Color.web("#F9A186")); // orange color
						} else { // blue piece
							circle.setFill(Color.web("#147493")); // blue color
						}
						square.getChildren().add(circle); // add piece

						if (piece.isKing) { // if king
							Text k = new Text("K"); // king label
							k.setFill(Color.WHITE); // white text
							k.setFont(Font.font("Arial", FontWeight.BOLD, 16)); // bold
							square.getChildren().add(k); // add on top
						}
					}
				}

				final int r = boardRow; // board row for click handler
				final int c = boardCol; // board col for click handler
				square.setOnMouseClicked(e -> onSquareClicked(r, c)); // click handler
				boardGrid.add(square, visualCol + 1, visualRow + 1); // add to grid
			}
		}
		if (localGame != null && turnLabel != null) { // update turn label
			if (localGame.currentPlayer.equals(myColor)) { // my turn
				if (!captureSources.isEmpty()) { // mandatory captures exist
					turnLabel.setText("Your Turn — Capture Required!"); // warn player
					turnLabel.setTextFill(Color.ORANGE); // orange to match piece highlights
				} else {
					turnLabel.setText("Your Turn"); // normal turn
					turnLabel.setTextFill(Color.GREEN); // green
				}
			} else { // opponent's turn
				turnLabel.setText("Opponent's Turn"); // show opponent's turn
				turnLabel.setTextFill(Color.GRAY); // gray
			}
		}
	}

	private void onSquareClicked(int row, int col) { // handle board click
		if (localGame == null) return; // no game yet
		if (!localGame.currentPlayer.equals(myColor)) return; // not my turn

		Piece piece = localGame.board.getPiece(row, col); // get clicked piece

		if (selectedRow == -1) { // no piece selected yet
			if (piece != null && piece.color.equals(myColor)) { // clicked my piece
				selectedRow = row; // select it
				selectedCol = col; // select it
				validDestinations.clear(); // clear old destinations
				for (Move m : localGame.getValidMoves(myColor)) { // get all valid moves
					if (m.fromRow == row && m.fromCol == col) { // filter for selected piece
						validDestinations.add(new int[]{m.toRow, m.toCol}); // save destination
					}
				}
				renderBoard(); // re-render to show highlights
			}
		} else { // piece already selected
			if (piece != null && piece.color.equals(myColor)) { // clicked another own piece
				selectedRow = row; // switch selection
				selectedCol = col; // switch selection
				validDestinations.clear(); // clear old destinations
				for (Move m : localGame.getValidMoves(myColor)) { // get all valid moves
					if (m.fromRow == row && m.fromCol == col) { // filter for selected piece
						validDestinations.add(new int[]{m.toRow, m.toCol}); // save destination
					}
				}
				renderBoard(); // re-render
			} else { // clicked destination square
				Move move = new Move(selectedRow, selectedCol, row, col); // create move
				clientConnection.send(new Message(Message.MessageType.move, move)); // send to server
				selectedRow = -1; // clear selection
				selectedCol = -1; // clear selection
				validDestinations.clear(); // clear destinations
			}
		}
	}

	private void sendChat(String text, TextField input) { // send a chat message
		if (!text.trim().isEmpty()) { // if not empty
			clientConnection.send(new Message(Message.MessageType.chat, text)); // send to server
			input.clear(); // clear input field
		}
	}

	private void showDrawDialog() { // show draw offer confirmation
		Stage dialog = new Stage(); // new window
		dialog.initModality(Modality.APPLICATION_MODAL); // block main window
		dialog.setTitle("Offer Draw"); // title

		Label msg = new Label("Offer a draw to your opponent?"); // message
		msg.setTextFill(Color.WHITE); // white text

		Button confirm = new Button("Draw"); // confirm button
		confirm.setStyle("-fx-background-color: #147493; -fx-text-fill: white;"); // blue
		confirm.setOnAction(e -> { // on confirm
			clientConnection.send(new Message(Message.MessageType.draw_offer, null)); // send offer
			dialog.close(); // close dialog
		});

		Button cancel = new Button("Cancel"); // cancel button
		cancel.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white;"); // dark
		cancel.setOnAction(e -> dialog.close()); // close on cancel

		HBox buttons = new HBox(10, cancel, confirm); // button row
		buttons.setAlignment(Pos.CENTER); // center
		VBox layout = new VBox(20, msg, buttons); // dialog layout
		layout.setAlignment(Pos.CENTER); // center
		layout.setPadding(new Insets(20)); // padding
		layout.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		dialog.setScene(new Scene(layout, 300, 150)); // set scene
		dialog.show(); // show dialog
	}

	private void showResignDialog() { // show resign confirmation
		Stage dialog = new Stage(); // new window
		dialog.initModality(Modality.APPLICATION_MODAL); // block main window
		dialog.setTitle("Resign"); // title

		Label msg = new Label("Are you sure you want to resign?"); // message
		msg.setTextFill(Color.WHITE); // white text

		Button confirm = new Button("Resign"); // confirm button
		confirm.setStyle("-fx-background-color: #8B0000; -fx-text-fill: white;"); // red
		confirm.setOnAction(e -> { // on confirm
			clientConnection.send(new Message(Message.MessageType.resign, null)); // send resign
			dialog.close(); // close dialog
		});

		Button cancel = new Button("Cancel"); // cancel button
		cancel.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white;"); // dark
		cancel.setOnAction(e -> dialog.close()); // close on cancel

		HBox buttons = new HBox(10, cancel, confirm); // button row
		buttons.setAlignment(Pos.CENTER); // center
		VBox layout = new VBox(20, msg, buttons); // dialog layout
		layout.setAlignment(Pos.CENTER); // center
		layout.setPadding(new Insets(20)); // padding
		layout.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		dialog.setScene(new Scene(layout, 320, 150)); // set scene
		dialog.show(); // show dialog
	}

	private void handleMessage(Message msg) { // process incoming server messages
		switch (msg.type) { // check message type

			case username_ok: // username accepted
				primaryStage.setScene(sceneMap.get("waiting")); // go to waiting screen
				break;

			case username_taken: // username already in use
				Alert alert = new Alert(Alert.AlertType.ERROR); // error dialog
				alert.setTitle("Username Taken"); // title
				alert.setContentText("That username is already taken. Try another."); // message
				alert.show(); // show dialog
				break;

			case game_start: // game is starting
				Object[] data = (Object[]) msg.data; // unpack data
				String[] info = (String[]) data[0]; // get player info
				opponentUsername = info[0]; // set opponent name
				myColor = info[1]; // set my color
				flipped = myColor.equals("black"); // blue player sees flipped board
				localGame = new CheckersGame(myUsername, opponentUsername); // create local game
				localGame.board = (Board) data[1]; // set board from server
				sceneMap.put("game", createGameScene()); // create game scene
				primaryStage.setScene(sceneMap.get("game")); // switch to game scene
				break;

			case move: // server sent updated board
				localGame.board = (Board) msg.data; // update local board
				localGame.currentPlayer = localGame.currentPlayer.equals("black") ? "red" : "black"; // switch turn
				renderBoard(); // re-render board
				break;

			case invalid_move: // server rejected move
				Alert invalidAlert = new Alert(Alert.AlertType.WARNING); // popup warning
				invalidAlert.setTitle("Invalid Move"); // title
				String invalidMsg = "That move is not allowed. Try again."; // default message
				if (localGame != null) { // check if capture is forcing the issue
					ArrayList<Move> vm = localGame.getValidMoves(myColor); // get valid moves
					if (!vm.isEmpty() && !vm.get(0).capturedPositions.isEmpty()) { // jumps exist
						invalidMsg = "A capture is available! You must move an orange-highlighted piece."; // explain
					}
				}
				invalidAlert.setContentText(invalidMsg); // message
				invalidAlert.show(); // show popup
				break;

			case game_over: // game ended
				String result = (String) msg.data; // get result
				showGameOverDialog(result); // show result dialog
				break;

			case chat: // chat message received
				chatList.getItems().add((String) msg.data); // add to chat list
				break;

			case draw_offer: // opponent offered draw
				showDrawResponseDialog(); // show accept/decline dialog
				break;

			case draw_decline: // opponent declined draw
				chatList.getItems().add("Your draw offer was declined."); // notify in chat
				break;

			case waiting: // waiting for opponent
				primaryStage.setScene(sceneMap.get("waiting")); // show waiting screen
				break;

			default: // unknown message
				break;
		}
	}

	private void showDrawResponseDialog() { // respond to a draw offer
		Stage dialog = new Stage(); // new window
		dialog.initModality(Modality.APPLICATION_MODAL); // block main window
		dialog.setTitle("Draw Offer"); // title

		Label msg = new Label("Your opponent offered a draw."); // message
		msg.setTextFill(Color.WHITE); // white text

		Button accept = new Button("Accept"); // accept button
		accept.setStyle("-fx-background-color: #147493; -fx-text-fill: white;"); // blue
		accept.setOnAction(e -> { // on accept
			clientConnection.send(new Message(Message.MessageType.draw_accept, null)); // send accept
			dialog.close(); // close dialog
		});

		Button decline = new Button("Decline"); // decline button
		decline.setStyle("-fx-background-color: #8B0000; -fx-text-fill: white;"); // red
		decline.setOnAction(e -> { // on decline
			clientConnection.send(new Message(Message.MessageType.draw_decline, null)); // send decline
			dialog.close(); // close dialog
		});

		HBox buttons = new HBox(10, decline, accept); // button row
		buttons.setAlignment(Pos.CENTER); // center
		VBox layout = new VBox(20, msg, buttons); // dialog layout
		layout.setAlignment(Pos.CENTER); // center
		layout.setPadding(new Insets(20)); // padding
		layout.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		dialog.setScene(new Scene(layout, 300, 150)); // set scene
		dialog.show(); // show dialog
	}

	private void showGameOverDialog(String result) { // show game over screen
		Stage dialog = new Stage(); // new window
		dialog.initModality(Modality.APPLICATION_MODAL); // block main window
		dialog.setTitle("Game Over"); // title

		String text = result.equals("draw") ? "Game drawn!" : // draw message
				result.equals(myUsername) ? "You win!" : "You lose!"; // win/lose message

		Label msg = new Label(text); // result label
		msg.setFont(Font.font("Arial", FontWeight.BOLD, 24)); // large bold font
		msg.setTextFill(Color.WHITE); // white text

		Button playAgain = new Button("Play Again"); // play again button
		playAgain.setStyle("-fx-background-color: #147493; -fx-text-fill: white;"); // blue
		playAgain.setOnAction(e -> { // on click
			clientConnection.send(new Message(Message.MessageType.play_again, null)); // request replay
			dialog.close(); // close dialog
		});

		Button quit = new Button("Quit"); // quit button
		quit.setStyle("-fx-background-color: #3c3c3c; -fx-text-fill: white;"); // dark
		quit.setOnAction(e -> { // on click
			Platform.exit(); // exit app
			System.exit(0); // exit process
		});

		HBox buttons = new HBox(10, quit, playAgain); // button row
		buttons.setAlignment(Pos.CENTER); // center
		VBox layout = new VBox(20, msg, buttons); // dialog layout
		layout.setAlignment(Pos.CENTER); // center
		layout.setPadding(new Insets(20)); // padding
		layout.setStyle("-fx-background-color: #2b2b2b;"); // dark background

		dialog.setScene(new Scene(layout, 300, 200)); // set scene
		dialog.show(); // show dialog
	}
}