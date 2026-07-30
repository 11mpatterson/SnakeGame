package com.example.snakegame;
import javafx.scene.control.Slider;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.scene.media.AudioClip;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.prefs.Preferences;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class snakeApplication extends Application
{
    // Grid Configuration
    private static final int WIDTH = 30;
    private static final int HEIGHT = 30;
    private static final int CORNER_SIZE = 19; // Size of each grid square in pixels

    // Game Variables
    private static final int HUD_HEIGHT = 50;
    private final List<Corner> snake = new ArrayList<>();
    private Direction direction = Direction.RIGHT;
    private Direction nextDirection = Direction.RIGHT;
    private Corner food;
    private boolean gameOver = false;
    private final Random random = new Random();
    private int score = 0;
    private int highScore = 0;
    private PowerUp activePowerUp = null;
    private long tickInterval = 120_000_000L; // normal speed
    private String activeEffect = null;          // "SPEED" or "INVINCIBLE" or null
    private long effectExpiresAt = 0;
    private boolean paused = false;
    private String difficulty = "EASY";
    private VBox gameOverBox;
    private Label gameOverScoreLabel;
    private Label gameOverHighScoreLabel;
    private Label gameOverTitleLabel;
    private long startTimeNanos = 0;
    private long totalPausedNanos = 0;
    private long pauseStartNanos = 0;
    private long highScoreTime = 0;          // seconds of the record
    private String highScoreDifficulty = "EASY";
    private Label highScoreLabel;            // promote to field
    private final List<Corner> prevSnake = new ArrayList<>();
    private long lastTick = 0;          // was local inside AnimationTimer
    private Canvas canvas;
    private static final int DESIGN_W = WIDTH * CORNER_SIZE;
    private static final int DESIGN_H = HEIGHT * CORNER_SIZE + HUD_HEIGHT;
    // Golden Apple
    private Corner golden = null;
    private long goldenExpiresAt = 0;
    private int goldenMoveCounter = 0;

    private long getElapsedSeconds() {
        if (startTimeNanos == 0) return 0;
        long now = System.nanoTime();
        long pausedNanos = totalPausedNanos;
        if (paused) {                       // boolean field
            pausedNanos += now - pauseStartNanos;
        }
        return (now - startTimeNanos - pausedNanos) / 1_000_000_000L;
    }

    public static class Corner
    {
        int x, y;
        public Corner(int x, int y)
        {
            this.x = x;
            this.y = y;
        }
    }

    public static class PowerUp
    {
        int x,y;
        String type; // power-up type - speed/invincible
        long expiresAt;
        public PowerUp(int x, int y, String type, long durationNanos)
        {
            this.x = x;
            this.y = y;
            this.type = type;
            this.expiresAt = System.nanoTime() + durationNanos;
        }
        public boolean isExpired()
        {
            return System.nanoTime() > expiresAt;
        }
    }

    public enum Direction {
        LEFT, RIGHT, UP, DOWN
    }
    private AudioClip eatSound;
    private AudioClip powerSound;
    private AudioClip gameOverSound;
    private MediaPlayer backgroundMusic;

    @Override
    public void start(Stage primaryStage) {


        // Game Over Overlay VBOX
        gameOverBox = new VBox(15);
        gameOverBox.setAlignment(Pos.CENTER);
        gameOverBox.setStyle("-fx-background-color: rgba(0,0,0,0.7);");
        gameOverTitleLabel = new Label("GAME OVER");
        gameOverTitleLabel.setStyle("-fx-font-size: 28px; -fx-text-fill: red; -fx-font-weight: bold;");
        gameOverScoreLabel = new Label();
        gameOverScoreLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: white; -fx-font-weight: bold;");
        gameOverHighScoreLabel = new Label();
        gameOverHighScoreLabel.setStyle("-fx-font-size: 20px; -fx-text-fill: white; -fx-font-weight: bold;");
        gameOverBox.setVisible(false);

        Button restartBtn = new Button("Restart");
        Button menuBtn = new Button("Main Menu");
        gameOverBox.getChildren().addAll(gameOverTitleLabel, gameOverHighScoreLabel, gameOverScoreLabel, restartBtn, menuBtn);
        canvas = new Canvas(DESIGN_W, DESIGN_H);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        StackPane root = new StackPane(canvas);
        root.getChildren().add(gameOverBox);

        Scene scene = new Scene(root, DESIGN_W, DESIGN_H);

        // make it fill the window
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());

        primaryStage.setResizable(true);          // was false
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(400);

        var eatUrl = getClass().getResource("/eat.wav");
        if (eatUrl != null) eatSound = new AudioClip(eatUrl.toExternalForm());

        var powerUrl = getClass().getResource("/powerup.wav");
        if (powerUrl != null) powerSound = new AudioClip(powerUrl.toExternalForm());

        var gameOverUrl = getClass().getResource("/gameover.wav");
        if (gameOverUrl != null) gameOverSound = new AudioClip(gameOverUrl.toExternalForm());
        VBox menuRoot = new VBox(20);
        menuRoot.setAlignment(Pos.CENTER);
        menuRoot.setStyle("-fx-background-color: white;");
        Label title = new Label("SNAKE");

        Preferences prefs = Preferences.userNodeForPackage(snakeApplication.class);
        highScore = prefs.getInt("highScore", 0);
        Label highScoreLabel = new Label("High Score: " + highScore);
        highScoreLabel.setStyle("-fx-font-size: 18px;");

        highScoreTime = prefs.getLong("highScoreTime", 0);
        highScoreDifficulty = prefs.get("highScoreDifficulty", "EASY");
        highScoreLabel = new Label("High Score: " + highScore + " (" + highScoreTime + "s " + highScoreDifficulty + ")");
        highScoreLabel.setStyle("-fx-font-size: 18px;");

        title.setStyle("-fx-font-size: 36px; -fx-font-weight: bold;");
        Button startBtn = new Button("Start");
        Button difficultyBtn = new Button("Difficulty: " + difficulty);
        Button quitBtn = new Button("Quit");
        Slider volumeSlider = new Slider(0, 1, 0.3);
        volumeSlider.setShowTickLabels(true);
        volumeSlider.setMajorTickUnit(0.25);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (backgroundMusic != null) {
                backgroundMusic.setVolume(newVal.doubleValue());
            }
            if (eatSound != null) eatSound.setVolume(newVal.doubleValue());
            if (powerSound != null) powerSound.setVolume(newVal.doubleValue());
        });

        menuRoot.getChildren().addAll(highScoreLabel, title, startBtn, difficultyBtn, volumeSlider, quitBtn);
        Scene menuScene = new Scene(menuRoot, WIDTH * CORNER_SIZE, HEIGHT * CORNER_SIZE);

        var url = getClass().getResource("/bgmusic.mp3");
        if (url == null) {
            System.err.println("Music file not found!");
        } else {
            Media media = new Media(url.toExternalForm());
            backgroundMusic = new MediaPlayer(media);
            backgroundMusic.setCycleCount(MediaPlayer.INDEFINITE);
            backgroundMusic.setVolume(0.3);
            backgroundMusic.play();
        }
        newFood();
        snake.add(new Corner(WIDTH / 2, HEIGHT / 2)); // Spawn in center

        restartBtn.setOnAction(e -> {
            restartGame();                 // reset snake & food
            gameOverBox.setVisible(false);
        });
        menuBtn.setOnAction(e -> {
            gameOverBox.setVisible(false);
            primaryStage.setScene(menuScene); // switch to menu
        });
        startBtn.setOnAction(e -> {
            gameOverBox.setVisible(false);
            restartGame();                 // reset snake & food
            primaryStage.setScene(scene);  // switch to game
        });
        difficultyBtn.setOnAction(e -> {
            switch (difficulty) {
                case "EASY" -> difficulty = "MEDIUM";
                case "MEDIUM" -> difficulty = "HARD";
                case "HARD" -> difficulty = "EASY";
            }
            difficultyBtn.setText("Difficulty: " + difficulty);
        });

        quitBtn.setOnAction(e -> primaryStage.close());


        // Handle inputs
        scene.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            if (code == KeyCode.UP && direction != Direction.DOWN) nextDirection = Direction.UP;
            if (code == KeyCode.DOWN && direction != Direction.UP) nextDirection = Direction.DOWN;
            if (code == KeyCode.LEFT && direction != Direction.RIGHT) nextDirection = Direction.LEFT;
            if (code == KeyCode.RIGHT && direction != Direction.LEFT) nextDirection = Direction.RIGHT;
            if (code == KeyCode.R && gameOver) restartGame();
            if (code == KeyCode.ESCAPE) primaryStage.setScene(menuScene);
            if (code == KeyCode.SPACE) {
                if (!paused) pauseStartNanos = System.nanoTime();
                else totalPausedNanos += System.nanoTime() - pauseStartNanos;
                paused = !paused;
            }
        });

        // Throttled Game Loop
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastTick == 0) {
                    lastTick = now;
                    return;
                }
                if (now - lastTick > tickInterval) {
                    lastTick = now;
                    if (!paused) {
                        tick();
                    }
                }
                draw(gc);
            }
        }.start();

        primaryStage.setTitle("JavaFX Snake Game");
        primaryStage.setScene(menuScene);
        primaryStage.setResizable(true);     // must be true
        primaryStage.setMinWidth(400);
        primaryStage.setMinHeight(400);
        primaryStage.show();


    }

    public void tick() {
        if (gameOver) return;
        if (activePowerUp != null && "SPEED".equals(activePowerUp.type) && !activePowerUp.isExpired()) {
            if (difficulty.equals("EASY"))
            {
                tickInterval = 60_000_000L;  // easy speed
            }
            else if(difficulty.equals("MEDIUM")){
                tickInterval = 35_000_000L;  // medium speed
            }
            else if(difficulty.equals("HARD")){
                tickInterval = 25_000_000L;  // hard speed

            }
        } else {
            if (difficulty.equals("EASY"))
            {
                tickInterval = 120_000_000L;  // easy
            }
            else if(difficulty.equals("MEDIUM")){
                tickInterval = 70_000_000L;  // medium
            }
            else if(difficulty.equals("HARD")){
                tickInterval = 50_000_000L;  // hard

            }


        }
        if (activePowerUp != null && activePowerUp.isExpired()) {
            activePowerUp = null;
        }
        // Move body (from tail to neck)
        for (int i = snake.size() - 1; i > 0; i--) {
            snake.get(i).x = snake.get(i - 1).x;
            snake.get(i).y = snake.get(i - 1).y;
        }
        direction = nextDirection;

        // snapshot for smooth animation
        prevSnake.clear();
        for (Corner c : snake) {
            prevSnake.add(new Corner(c.x, c.y));
        }
        // Move head
        Corner head = snake.get(0);
        switch (direction) {
            case UP -> head.y--;
            case DOWN -> head.y++;
            case LEFT -> head.x--;
            case RIGHT -> head.x++;
        }

        // Collision Check (Self-collision & Wall boundaries)
        if ("INVINCIBLE".equals(activeEffect) && System.nanoTime() < effectExpiresAt) {
            // Pac-Man style wrap
            if (head.x < 0) head.x = WIDTH - 1;
            if (head.x >= WIDTH) head.x = 0;
            if (head.y < 0) head.y = HEIGHT - 1;
            if (head.y >= HEIGHT) head.y = 0;
        } else {
            // Normal death on walls
            if (head.x < 0 || head.y < 0 || head.x >= WIDTH || head.y >= HEIGHT) {
                gameOver = true;
            }
        }
        if (!("INVINCIBLE".equals(activeEffect) && System.nanoTime() < effectExpiresAt)) {
            for (int i = 1; i < snake.size(); i++) {
                if (head.x == snake.get(i).x && head.y == snake.get(i).y) {
                    gameOver = true;
                    break;
                }
            }
        }
        if (gameOver) {
            long finalTime = getElapsedSeconds();
            if (score > highScore) {
                highScore = score;
                highScoreTime = finalTime;
                highScoreDifficulty = difficulty;
                Preferences prefs = Preferences.userNodeForPackage(snakeApplication.class);
                prefs.putInt("highScore", highScore);
                prefs.putLong("highScoreTime", highScoreTime);
                prefs.put("highScoreDifficulty", highScoreDifficulty);
                try { prefs.flush(); } catch (Exception ignored) {}
                if (gameOverSound != null) gameOverSound.play();
                highScoreLabel.setText("High Score: " + highScore + " (" + highScoreTime + "s " + highScoreDifficulty + ")");
            }
            gameOverScoreLabel.setText("Score: " + score + "   Time: " + finalTime + "s");
            gameOverHighScoreLabel.setText("High Score: " + highScore + " (" + highScoreTime + "s " + highScoreDifficulty + ")");
            gameOverBox.setVisible(true);
        }
        // Food Consumption
        if (head.x == food.x && head.y == food.y) {
            if (eatSound != null) eatSound.play();
            Corner tail = snake.get(snake.size() - 1);
            snake.add(new Corner(tail.x, tail.y));
            score += 1;
            newFood();
        }
        // spawn golden apple
        if (golden == null && random.nextInt(400) == 0) {
            int x, y;
            boolean occupied;
            do {
                x = random.nextInt(WIDTH);
                y = random.nextInt(HEIGHT);
                occupied = false;
                for (Corner s : snake) {
                    if (s.x == x && s.y == y) {
                        occupied = true;
                        break;
                    }
                }
                if (food != null && food.x == x && food.y == y) occupied = true;
            } while (occupied);

            golden = new Corner(x, y);
            goldenExpiresAt = System.nanoTime() + 12_000_000_000L;
            goldenMoveCounter = 0;
        }

        // expire or move it
        if (golden != null) {
            if (System.nanoTime() > goldenExpiresAt) {
                golden = null;
            } else {
                goldenMoveCounter++;
                if (goldenMoveCounter >= 8) {          // move every 8 ticks
                    goldenMoveCounter = 0;
                    int[] dx = {0, 0, 1, -1};
                    int[] dy = {1, -1, 0, 0};
                    int dir = random.nextInt(4);
                    int nx = golden.x + dx[dir];
                    int ny = golden.y + dy[dir];
                    if (nx >= 0 && nx < WIDTH && ny >= 0 && ny < HEIGHT &&
                            snake.stream().noneMatch(s -> s.x == nx && s.y == ny) &&
                            (food == null || food.x != nx || food.y != ny)) {
                        golden.x = nx;
                        golden.y = ny;
                    }
                }
            }
        }

        // eat golden apple (put after normal food check)
        if (golden != null && head.x == golden.x && head.y == golden.y) {
            if (eatSound != null) eatSound.play();
            score += 3;

            // grow by 3
            Corner tail = snake.get(snake.size() - 1);
            for (int i = 0; i < 3; i++) {
                snake.add(new Corner(tail.x, tail.y));
            }

            activeEffect = "SPEED";
            effectExpiresAt = System.nanoTime() + 4_000_000_000L;
            golden = null;
        }
        if(activePowerUp == null && random.nextInt(15) == 0) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            String type = random.nextBoolean() ? "SPEED" : "INVINCIBLE";
            activePowerUp = new PowerUp(x, y, type, 5_000_000_000L); // 5 secs
        }
        if (activePowerUp != null && head.x == activePowerUp.x && head.y == activePowerUp.y) {
            if(powerSound != null) powerSound.play();
            activeEffect = activePowerUp.type;
            effectExpiresAt = System.nanoTime() + 10_000_000_000L; // 5 seconds from now
            activePowerUp = null; // remove from map
        }
        if ("SPEED".equals(activeEffect) && System.nanoTime() < effectExpiresAt) {
            if (difficulty.equals("EASY"))
            {
                tickInterval = 60_000_000L;  // easy speed
            }
            else if(difficulty.equals("MEDIUM")){
                tickInterval = 35_000_000L;  // medium speed
            }
            else if(difficulty.equals("HARD")){
                tickInterval = 25_000_000L;  // hard speed

            }
        } else {
            if (difficulty.equals("EASY"))
            {
                tickInterval = 120_000_000L;  // easy
            }
            else if(difficulty.equals("MEDIUM")){
                tickInterval = 70_000_000L;  // medium
            }
            else if(difficulty.equals("HARD")) {
                tickInterval = 50_000_000L;  // hard
            }
            if (activeEffect != null && System.nanoTime() >= effectExpiresAt) {
                activeEffect = null; // clear when finished
            }
        }
    }

    public void draw(GraphicsContext gc) {
        if (gameOver) {
            gameOverBox.setVisible(true);
            activeEffect = null;
            return;
        }
        double canvasW = canvas.getWidth();
        double canvasH = canvas.getHeight();
        if (canvasW <= 0 || canvasH <= 0) return;

        // letterbox / pillarbox scale
        double scale = Math.min(canvasW / DESIGN_W, canvasH / DESIGN_H);
        double offsetX = (canvasW - DESIGN_W * scale) / 2;
        double offsetY = (canvasH - DESIGN_H * scale) / 2;

        // full-window background (the letterbox bars)
        gc.setFill(Color.web("#0d1117"));
        gc.fillRect(0, 0, canvasW, canvasH);
        gc.save();
        gc.translate(offsetX, offsetY);
        gc.scale(scale, scale);

        // subtle grid
        gc.setStroke(Color.web("#161b22"));
        gc.setLineWidth(1);
        for (int x = 0; x <= WIDTH; x++) {
            gc.strokeLine(x * CORNER_SIZE, HUD_HEIGHT,
                    x * CORNER_SIZE, HEIGHT * CORNER_SIZE + HUD_HEIGHT);
        }
        for (int y = 0; y <= HEIGHT; y++) {
            gc.strokeLine(0, y * CORNER_SIZE + HUD_HEIGHT,
                    WIDTH * CORNER_SIZE, y * CORNER_SIZE + HUD_HEIGHT);
        }

        // HUD bar (stays at top)
        gc.setFill(Color.web("#000000aa"));
        gc.fillRect(0, 0, WIDTH * CORNER_SIZE, HUD_HEIGHT);
        // font for everything
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 15));
        gc.setFill(Color.web("#e6edf3"));

        // Draw Food
        gc.setFill(Color.web("#ff4d4d"));
        gc.fillOval(food.x * CORNER_SIZE + 2, food.y * CORNER_SIZE + HUD_HEIGHT + 2,
                CORNER_SIZE - 4, CORNER_SIZE - 4);
        gc.setFill(Color.web("#ffffff88"));
        gc.fillOval(food.x * CORNER_SIZE + 5, food.y * CORNER_SIZE + HUD_HEIGHT + 5, 5, 5);

        if (golden != null) {
            gc.setFill(Color.web("#ffd700"));          // gold
            gc.fillOval(golden.x * CORNER_SIZE + 2,
                    golden.y * CORNER_SIZE + HUD_HEIGHT + 2,
                    CORNER_SIZE - 4, CORNER_SIZE - 4);
            gc.setFill(Color.web("#ffffffaa"));
            gc.fillOval(golden.x * CORNER_SIZE + 5,
                    golden.y * CORNER_SIZE + HUD_HEIGHT + 5, 5, 5);
        }
        // Draw PowerUp
        if (activePowerUp != null && !activePowerUp.isExpired()) {
            gc.setFill(Color.web("#00e5ff"));
            gc.fillRoundRect(activePowerUp.x * CORNER_SIZE + 1,
                    activePowerUp.y * CORNER_SIZE + HUD_HEIGHT + 1,
                    CORNER_SIZE - 2, CORNER_SIZE - 2, 6, 6);
        }
        // Draw Snake
        // progress 0 → 1 between ticks
        double progress = 0.0;
        if (lastTick > 0 && tickInterval > 0) {
            progress = (System.nanoTime() - lastTick) / (double) tickInterval;
            if (progress > 1.0) progress = 1.0;
        }
        if (paused || gameOver) progress = 1.0;

        for (int i = snake.size() - 1; i >= 0; i--) {   // tail → head
            Corner curr = snake.get(i);
            double visX = curr.x;
            double visY = curr.y;

            if (i < prevSnake.size()) {
                Corner prev = prevSnake.get(i);
                double dx = curr.x - prev.x;
                double dy = curr.y - prev.y;

                // wrap detected → snap, no long animation
                if (Math.abs(dx) > 1) {
                    visX = curr.x;
                } else {
                    visX = prev.x + dx * progress;
                }

                if (Math.abs(dy) > 1) {
                    visY = curr.y;
                } else {
                    visY = prev.y + dy * progress;
                }
            }

            if (i == 0) { // head – drawn last
                if (activeEffect == null) gc.setFill(Color.web("#3fb950"));
                else if ("SPEED".equals(activeEffect)) gc.setFill(Color.WHITE);
                else gc.setFill(Color.web("#56d364"));
                gc.fillRoundRect(visX * CORNER_SIZE + 1, visY * CORNER_SIZE + HUD_HEIGHT + 1,
                        CORNER_SIZE - 2, CORNER_SIZE - 2, 8, 8);
                gc.setFill(Color.BLACK);
                gc.fillOval(visX * CORNER_SIZE + 5, visY * CORNER_SIZE + HUD_HEIGHT + 5, 3, 3);
                gc.fillOval(visX * CORNER_SIZE + 12, visY * CORNER_SIZE + HUD_HEIGHT + 5, 3, 3);
            } else {
                gc.setFill(Color.web("#238636"));
                gc.fillRoundRect(visX * CORNER_SIZE + 2, visY * CORNER_SIZE + HUD_HEIGHT + 2,
                        CORNER_SIZE - 4, CORNER_SIZE - 4, 6, 6);
            }
        }
        gc.setFill(Color.ORANGE);
        gc.fillText("Score: " + score, 12, 22);
        gc.fillText("Time: " + getElapsedSeconds() + "s", 12, 40);
        gc.fillText("High: " + highScore + " (" + highScoreTime + "s " + highScoreDifficulty + ")", 160, 22);
        gc.fillText("Effect: " + (activeEffect == null ? "-" : activeEffect), 160, 40);
        gc.fillText("Diff: " + difficulty, 420, 30);
        // Draw Controls
        gc.setFont(Font.font("Segoe UI", 11));
        gc.setFill(Color.web("#8b949e"));
        gc.fillText("ARROWS move   SPACE pause   ESC menu   R restart", 12, HEIGHT * CORNER_SIZE + HUD_HEIGHT - 12);
        if (paused) {
            gc.setFill(Color.web("#ffffffcc"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 36));
            gc.fillText("PAUSED", WIDTH * CORNER_SIZE / 2.0 - 60, HEIGHT * CORNER_SIZE / 2.0 + HUD_HEIGHT);
        }
        gc.restore();
    }

    private void newFood() {
        while (true) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            boolean onSnake = snake.stream().anyMatch(s -> s.x == x && s.y == y);
            if (!onSnake) {
                food = new Corner(x, y);
                break;
            }
        }
    }

    private void restartGame() {
        golden = null;
        goldenExpiresAt = 0;
        goldenMoveCounter = 0;
        direction = Direction.RIGHT;
        nextDirection = Direction.RIGHT;
        score = 0;
        snake.clear();
        snake.add(new Corner(WIDTH / 2, HEIGHT / 2));
        direction = Direction.RIGHT;
        prevSnake.clear();
        prevSnake.add(new Corner(WIDTH / 2, HEIGHT / 2));
        lastTick = 0;
        gameOver = false;
        newFood();
        startTimeNanos = System.nanoTime();
        totalPausedNanos = 0;
        pauseStartNanos = 0;
    }

    public static void main(String[] args) {
        launch(args);
    }
}