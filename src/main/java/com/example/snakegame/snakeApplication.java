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
public class snakeApplication extends Application
{
    // Grid Configuration
    private static final int WIDTH = 30;
    private static final int HEIGHT = 30;
    private static final int CORNER_SIZE = 20; // Size of each grid square in pixels

    // Game Variables
    private final List<Corner> snake = new ArrayList<>();
    private Direction direction = Direction.RIGHT;
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

        Canvas canvas = new Canvas(WIDTH * CORNER_SIZE, HEIGHT * CORNER_SIZE);
        GraphicsContext gc = canvas.getGraphicsContext2D();
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

        StackPane root = new StackPane(canvas);
        root.getChildren().add(gameOverBox);
        gameOverBox.getChildren().addAll(gameOverTitleLabel, gameOverHighScoreLabel, gameOverScoreLabel, restartBtn, menuBtn);
        Scene scene = new Scene(root);
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
            if (code == KeyCode.UP && direction != Direction.DOWN) direction = Direction.UP;
            if (code == KeyCode.DOWN && direction != Direction.UP) direction = Direction.DOWN;
            if (code == KeyCode.LEFT && direction != Direction.RIGHT) direction = Direction.LEFT;
            if (code == KeyCode.RIGHT && direction != Direction.LEFT) direction = Direction.RIGHT;
            if (code == KeyCode.R && gameOver) restartGame();
            if (code == KeyCode.ESCAPE) primaryStage.setScene(menuScene);
            if (code == KeyCode.SPACE) {
                paused = !paused;
            }
        });

        // Throttled Game Loop
        new AnimationTimer() {
            long lastTick = 0;

            @Override
            public void handle(long now) {
                if (lastTick == 0) {
                    lastTick = now;
                    return;
                }

                // Controls the game speed (update every 120ms)
                if (now - lastTick > tickInterval) {
                    lastTick = now;
                    if(!paused) {
                        tick();
                    }
                    draw(gc);
                }
            }
        }.start();

        primaryStage.setTitle("JavaFX Snake Game");
        primaryStage.setScene(menuScene);
        primaryStage.setResizable(false);
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
        if(gameOver){
            gameOverScoreLabel.setText("Score: " + score);
            gameOverHighScoreLabel.setText("High Score: " + highScore);
            gameOverBox.setVisible(true);
            if (score > highScore) {
                gameOverSound.play();
                highScore = score;
                Preferences prefs = Preferences.userNodeForPackage(snakeApplication.class);
                prefs.putInt("highScore", highScore);

            }

        }
        // Food Consumption
        if (head.x == food.x && head.y == food.y) {
            eatSound.play();
            snake.add(new Corner(-1, -1)); // Add temporary dummy tail
            score += 1;
            newFood();
        }
        if(activePowerUp == null && random.nextInt(15) == 0) {
            int x = random.nextInt(WIDTH);
            int y = random.nextInt(HEIGHT);
            String type = random.nextBoolean() ? "SPEED" : "INVINCIBLE";
            activePowerUp = new PowerUp(x, y, type, 5_000_000_000L); // 5 secs
        }
        if (activePowerUp != null && head.x == activePowerUp.x && head.y == activePowerUp.y) {
            powerSound.play();
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

        // Clear Background
        gc.setFill(Color.BLACK);
        gc.fillRect(0, 0, WIDTH * CORNER_SIZE, HEIGHT * CORNER_SIZE);

        // Draw Food
        gc.setFill(Color.RED);
        gc.fillOval(food.x * CORNER_SIZE, food.y * CORNER_SIZE, CORNER_SIZE, CORNER_SIZE);

        if(activePowerUp != null && !activePowerUp.isExpired()) {
            gc.setFill(Color.CYAN);
            gc.fillRect(activePowerUp.x * CORNER_SIZE, activePowerUp.y * CORNER_SIZE, CORNER_SIZE -1, CORNER_SIZE - 1);

        }
        // Draw Snake
        for (int i = 0; i < snake.size(); i++) {
            Corner segment = snake.get(i);
            if (i == 0) {
                if(activeEffect == null) {
                    gc.setFill(Color.GREENYELLOW); // Head color
                }
                else if(activeEffect.equals("SPEED")){
                    gc.setFill(Color.WHITE);
                }
                else if(activeEffect.equals("INVINCIBLE")){
                    gc.setFill(Color.DARKSEAGREEN);
                }
            } else {
                gc.setFill(Color.GREEN); // Body color
            }
            gc.fillRect(segment.x * CORNER_SIZE, segment.y * CORNER_SIZE, CORNER_SIZE - 1, CORNER_SIZE - 1);
        }
        gc.setFill(Color.ORANGE);
        // Draw Score
        gc.fillText("Score: " + score, 10, 20);
        gc.fillText("High Score: " + highScore, 250, 20);
        // Draw Active Effect
        gc.fillText("Effect: " + activeEffect, 100, 20);
        // Draw Controls
        gc.fillText("Controls: UP/DOWN/LEFT/RIGHT ARROW = MOVE     ESC = MENU     SPACE = PAUSE", 10, 600);
        // Draw Difficulty
        gc.fillText("Difficulty: " + difficulty, 450, 20);
        if (paused) {
            gc.setFill(Color.WHITE);
            gc.fillText("PAUSED", WIDTH * CORNER_SIZE / 2.5, (double) (HEIGHT * CORNER_SIZE) / 2);
        }
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
        score = 0;
        snake.clear();
        snake.add(new Corner(WIDTH / 2, HEIGHT / 2));
        direction = Direction.RIGHT;
        gameOver = false;
        newFood();
    }

    public static void main(String[] args) {
        launch(args);
    }
}