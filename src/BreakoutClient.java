/*
 * Filename: BreakoutClient.java
 * Description: This file is part of the BreakoutOnline game, allowing two players to connect from different devices on the same network and play against each other.
 *
 * Instructions:
 * - Both BreakoutServer and BreakoutClient need to be running on the same local network.
 * - To connect, specify the ADDRESS (local IP of the server device) and PORT (an available port on the server) in the client code.
 * - The game will synchronize gameplay between the client and server, enabling real-time multiplayer.
 *
 * Note:
 * Ensure server is running before running this file, ensure the firewall settings allow connections on the chosen PORT, and that both devices are on the same network.
 * There may be tons of ways to refactor the code better way, but I had enough of it
 *
 * Took some examples from geeksforgeeks (https://www.geeksforgeeks.org/socket-programming-in-java)
 * Audios from https://pixabay.com/sound-effects
 *
 * Known Bugs:
 * - bricks destroyed by the opponent appear correctly on their screen but do not update on this player's screen.
 *   This causes the ball to appear as though it passes through an intact brick, even though it has already been destroyed.
 * (Deadline :(( )
 */

import acm.graphics.*;
import acm.program.GraphicsProgram;
import acm.util.MediaTools;
import acm.util.RandomGenerator;

import java.applet.AudioClip;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class BreakoutClient extends GraphicsProgram {

    private static final int PORT = 6969;
    private static final String ADDRESS = "192.168.1.148";


    public static final int DELAY = 7;
    public static final int SEPERATOR_WIDTH = 300;
    private static final int WIDTH = 400;
    public static final int APPLICATION_WIDTH = WIDTH * 2 + SEPERATOR_WIDTH;
    public static final int APPLICATION_HEIGHT = 600;
    private static final int HEIGHT = 600;
    private static final int PADDLE_WIDTH = 60;
    private static final int PADDLE_HEIGHT = 10;
    private static final int PADDLE_Y_OFFSET = 30;
    private static final int NBRICKS_PER_ROW = 10;
    private static final int NBRICK_ROWS = 10;
    private static final int BRICK_SEP = 4;
    private static final int BRICK_WIDTH =
            (WIDTH - (NBRICKS_PER_ROW - 1) * BRICK_SEP) / NBRICKS_PER_ROW;
    private static final int BRICK_HEIGHT = 8;
    private static final int BALL_RADIUS = 10;
    private static final int BRICK_Y_OFFSET = 70;
    private static final int NTURNS = 3;
    private static final int PADDLE_SENSITIVITY = 8;
    private static final int HEART_OFFSET = 10;
    private static final int HEART_GAP = 5;
    private static final int HEART_WIDTH = 30;
    private int turnsCount = NTURNS;
    private int aliveBricks = NBRICK_ROWS * NBRICKS_PER_ROW;
    private boolean isDarkModeEnabled = true;
    private boolean gameStarted = false;


    // Audio
    AudioClip bgMusic = MediaTools.loadAudioClip("background_music.au");
    AudioClip destroySound = MediaTools.loadAudioClip("destroy.au");
    AudioClip winSound = MediaTools.loadAudioClip("victory.au");
    AudioClip loseSound = MediaTools.loadAudioClip("lose.au");
    AudioClip paddleKickSound = MediaTools.loadAudioClip("kick.au");
    AudioClip countdownSound = MediaTools.loadAudioClip("countdown.au");

    // GObjects
    private GRect paddle;
    private GRect serverPaddle;
    private GOval ball;
    private GOval serverBall;
    private GImage switcher;
    private GLine seperator1;
    private GLine seperator2;

    // Labels
    private GLabel bricksLeft = null;
    private GLabel counter;

    private RandomGenerator rgen = RandomGenerator.getInstance();

    // Network things
    private Socket socket = null;
    private DataInputStream input = null;
    private DataOutputStream output = null;

    private double vx, vy = 3.0;
    private boolean connectionActive = false;
    private boolean shouldPlayCountdownMusic = true;

    public void run() {
        initGame();
        addMouseListeners();
        connectToServer();
        while (!gameStarted) {
            pause(100);
        }
        gameLoop();
    }

    private void initGame() {
        drawBricks();
        setRandomVx();
        renderHearts();
        renderBricksLeft();
        renderThemeSwitcher(true);
        createPaddle();
        createServerPaddle();
        createBall();
        createServerBall();
        renderSeparator();
    }


    // Each *frame* happens here
    private void gameLoop() {
        bgMusic.loop();
        while (turnsCount > 0 && aliveBricks > 0 && connectionActive) {
            moveBall();
            checkCollisions();
            sendPositionsToServer(0, 0);
            pause(DELAY);
        }
        if (turnsCount == 0) {
            handleGameLoss(1);
        } else if (aliveBricks == 0) {
            handleGameWin(1);
        }
        remove(ball);
    }


    // --------------- SOCKET FUNCTIONS -------------------

    private void sendPositionsToServer(double brickX, double brickY) {
        try {
            double paddleX = paddle.getX();
            double ballX = ball.getX();
            double ballY = ball.getY();
            // We are sending game variables, so here comes PROTOCOL 0
            output.writeInt(0);

            output.writeDouble(paddleX);
            output.writeDouble(ballX);
            output.writeDouble(ballY);
            output.writeDouble(brickX);
            output.writeDouble(brickY);
        } catch (IOException e) {
            closeConnection();
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(ADDRESS, PORT);
            System.out.println("CONNECTED");
            connectionActive = true;

            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            // We need new thread so that gameloop doesn't stop
            new Thread(() -> {
                handleCountdownEvent();
                receiveData();
            }).start();

        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O error: " + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            if (output != null) output.close();
            if (socket != null) socket.close();
            connectionActive = false;
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }

    private void receiveData() {
        while (true) {
            try {
                if (gameStarted) {
                    int messageType = input.readInt();

                    // There are 2 kinds of *protocols*, one starts with 0, the second one starts with 1, protocol 0 is for receiveing game variables like paddle and ball location, protocol 1 is for finishing the game, happens when one of the players lost/won the game
                    if (messageType == 0) {
                        receiveAndProcessGameVariables();
                    } else if (messageType == 1) {
                        receiveGameEndEvent();
                    }
                }
            } catch (IOException e) {
                closeConnection();
                return;
            }
        }
    }

    private void receiveGameEndEvent() throws IOException {
        boolean statusMessage = input.readBoolean();
        if (statusMessage) {
            handleGameLoss(0);
        } else {
            handleGameWin(0);
        }
    }

    private void sendLoseEvent() {
        try {
            // We are finishing the game, so PROTOCOL 1
            output.writeInt(1);
            output.writeBoolean(false);
        } catch (IOException e) {
            closeConnection();
            System.out.println(e);
        }
    }

    private void sendWinEvent() {
        try {
            // We are finishing the game, so PROTOCOL 1
            output.writeInt(1);
            output.writeBoolean(true);
        } catch (IOException e) {
            closeConnection();
            System.out.println(e);
        }
    }

    private void receiveAndProcessGameVariables() throws IOException {
        double paddleX = input.readDouble();
        double ballX = input.readDouble();
        double ballY = input.readDouble();
        double brickX = input.readDouble();
        double brickY = input.readDouble();
        GObject currentEl = getElementAt(brickX + WIDTH + SEPERATOR_WIDTH, brickY);
        if (currentEl instanceof GRect) {
            remove(currentEl);
        }
        serverPaddle.setLocation(paddleX + WIDTH + SEPERATOR_WIDTH, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT);
        serverBall.setLocation(ballX + WIDTH + SEPERATOR_WIDTH, ballY);
    }

    // --------------- LISTENERS ---------------------
    @Override
    public void mouseMoved(MouseEvent e) {
        if (isGameOver()) {
            return;
        }
        double x = e.getX() - (double) PADDLE_WIDTH / 2;
        double paddleY = paddle.getY();
        if (gameStarted && x >= 0 && x + PADDLE_WIDTH <= WIDTH) {
            paddle.setLocation(x, paddleY);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        // If user clicked theme switcher...
        if (switcher.contains(x, y)) {
            handleThemeChange();
        }
    }


    // --------------- HELPERS ------------------

    // probably didnt need separate method
    private void startGameLoop() {
        gameStarted = true;
    }

    // DVD screensaver like animation, but if it touches the bottom of the screen we record it as a *missed ball*
    private void moveBall() {
        ball.move(vx, vy);
        if (ball.getX() <= 0 || ball.getX() + BALL_RADIUS * 2 >= WIDTH) {
            // ball was sticking to edges and had to use the absolute value of vx
            if (ball.getX() <= 0) {
                vx = Math.abs(vx);
            } else {
                vx = -Math.abs(vx);
            }
        }
        if (ball.getY() <= 0) {
            vy = -vy;
        } else if (ball.getY() + BALL_RADIUS * 2 >= HEIGHT) {
            handleBallMiss();
        }
    }

    // Reset the ball to center
    private void resetBall() {
        ball.setLocation((double) WIDTH / 2 - BALL_RADIUS, (double) HEIGHT / 2 - BALL_RADIUS);
        vy = Math.abs(vy);
        setRandomVx();
        pause(2000);
    }

    private void setRandomVx() {
        vx = rgen.nextDouble(1.0, 3.0) * (rgen.nextBoolean(0.5) ? -1 : 1);
    }

    private void checkCollisions() {
        GObject collider = getBallCollidingObject();
        if (collider == paddle) {
            handlePaddleKick();
        } else if (collider instanceof GRect) {
            destroySound.play();
            remove(collider);
            aliveBricks--;
            bricksLeft.setLabel("Bricks : " + (int) aliveBricks);
            vy = -vy;
            sendPositionsToServer(collider.getX(), collider.getY());
        }
    }
    // ------------ GETTERS ------------------

    private boolean isGameOver() {
        return turnsCount == 0 || aliveBricks == 0;
    }

    private Color getBrickColor(int row) {
        if (row >= 8) {
            return Color.CYAN;
        } else if (row >= 6) {
            return Color.GREEN;
        } else if (row >= 4) {
            return Color.YELLOW;
        } else if (row >= 2) {
            return Color.ORANGE;
        } else {
            return Color.RED;
        }
    }

    // Returns either NULL or object the ball is colliding
    private GObject getBallCollidingObject() {
        double leftX = ball.getX();
        double rightX = leftX + BALL_RADIUS * 2;
        double topY = ball.getY();
        double bottomY = topY + BALL_RADIUS * 2;

        // Checking bottom left corner of the collider
        GObject collider = getElementAt(leftX, bottomY);
        if (collider != null) {
            return collider;
        }

        // Checking bottom right of the collider
        collider = getElementAt(rightX, bottomY);
        if (collider != null) {
            return collider;
        }

        // Checking top left corner of the collider
        collider = getElementAt(leftX, topY);
        if (collider != null) {
            return collider;
        }

        // Or the top right corner, no need to check if its null cause if its null we already checked other corners so no object are being collided
        collider = getElementAt(rightX, topY);
        return collider;
    }


    private GObject getCurrentHeart() {
        double x = HEART_OFFSET + (turnsCount - 1) * (HEART_WIDTH + HEART_GAP) + HEART_WIDTH / 2;
        return getElementAt(x, HEART_WIDTH / 2);
    }


    // ----------------- HANDLERS ----------------------
    private void handleCountdownEvent() {
        while (true) {
            try {
                if (input.available() > 0) {
                    // Need this variable so that countdown doesn't play multiple times
                    if (shouldPlayCountdownMusic) {
                        countdownSound.play();
                        // Set it to false
                        shouldPlayCountdownMusic = false;
                    }
                    int countdown = input.readInt();
                    displayCountdown(countdown);

                    if (countdown == 0) {
                        boolean gameStarted = input.readBoolean();
                        if (gameStarted) {
                            startGameLoop();
                            remove(counter);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            }
        }
    }

    // If the paddle misses the ball... RIP
    private void handleBallMiss() {
        GObject heart = getCurrentHeart();
        remove(heart);
        turnsCount--;
        if (turnsCount > 0) {
            resetBall();
        }

    }

    // We estimate the VX of the ball based on how far it was from the center of the paddle (we can try different values of sensitivity)
    private void handlePaddleKick() {
        paddleKickSound.play();
        vy = -Math.abs(vy);
        double paddleCenter = paddle.getX() + (double) PADDLE_WIDTH / 2;
        vx = (ball.getX() + BALL_RADIUS - paddleCenter) / PADDLE_SENSITIVITY;
    }

    private void handleGameLoss(int iLost) {
        bgMusic.stop();
        loseSound.play();
        sendLoseEvent();
        removeAll();
        if (iLost == 1) {
            renderTextInCenter("You Lost :(((", Color.RED, 30);
        } else {
            renderTextInCenter("Opponent won :((", Color.RED, 30);
        }
        closeConnection();
    }

    private void handleGameWin(int iWon) {
        bgMusic.stop();
        winSound.play();
        sendWinEvent();
        removeAll();
        if (iWon == 1) {
            renderTextInCenter("You WON :))", Color.GREEN, 30);
        } else {
            renderTextInCenter("Opponent lost :))", Color.GREEN, 30);
        }
        closeConnection();
    }

    private void handleThemeChange() {
        remove(switcher);
        isDarkModeEnabled = !isDarkModeEnabled;
        renderThemeSwitcher(isDarkModeEnabled);

        Color primaryColor = isDarkModeEnabled ? Color.WHITE : Color.BLACK;
        Color secondaryColor = isDarkModeEnabled ? Color.BLACK : Color.WHITE;

        if (counter != null) {
            counter.setColor(secondaryColor);
        }
        ball.setColor(secondaryColor);
        serverBall.setColor(secondaryColor);
        paddle.setColor(secondaryColor);
        seperator1.setColor(secondaryColor);
        seperator2.setColor(secondaryColor);
        serverPaddle.setColor(secondaryColor);
        setBackground(primaryColor);
    }


    // -------------------- RENDERERS ------------------------
    private void renderSeparator() {
        seperator1 = new GLine(WIDTH, 0, WIDTH, HEIGHT);
        seperator2 = new GLine(WIDTH + SEPERATOR_WIDTH, 0, WIDTH + SEPERATOR_WIDTH, HEIGHT);
        add(seperator1);
        add(seperator2);
    }

    private void renderThemeSwitcher(boolean darkMode) {
        switcher = darkMode ? new GImage("./light.png") : new GImage("./dark.png");
        switcher.setSize(30, 20);
        switcher.sendToFront();
        double x = bricksLeft.getX() - switcher.getWidth() - 10;
        add(switcher, x, (double) HEART_OFFSET / 2);
    }

    private void renderBricksLeft() {
        bricksLeft = new GLabel("Bricks: " + (int) aliveBricks);
        bricksLeft.setFont(new Font("Serif", Font.PLAIN, 17));
        bricksLeft.setColor(Color.ORANGE);
        bricksLeft.sendToFront();
        double x = WIDTH - bricksLeft.getWidth();
        double y = HEART_OFFSET + bricksLeft.getAscent() / 2;
        add(bricksLeft, x, y);
    }

    private void renderTextInCenter(String str, Color color, int fontSize) {
        GLabel text = new GLabel(str);
        text.setFont(new Font("Serif", Font.PLAIN, fontSize));
        text.setColor(color);
        double x = (APPLICATION_WIDTH - text.getWidth()) / 2;
        double y = (APPLICATION_HEIGHT - text.getAscent()) / 2;
        add(text, x, y);
    }

    private void renderHearts() {
        double y = 0;
        double x = HEART_OFFSET;

        for (int i = 0; i < turnsCount; i++) {
            renderSingleHeart(x, y);
            x += HEART_GAP + HEART_WIDTH;
        }
    }

    private void renderSingleHeart(double x, double y) {
        GImage heart = new GImage("./heart.png");
        heart.setSize(HEART_WIDTH, HEART_WIDTH);
        add(heart, x, y);
        heart.sendToFront();
    }

    private void createBall() {
        ball = new GOval((double) WIDTH / 2 - BALL_RADIUS, (double) HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
        ball.setFilled(true);
        add(ball);
    }

    private void drawBricks() {
        double startingY = BRICK_Y_OFFSET;
        for (int i = 0; i < NBRICK_ROWS; i++) {
            drawBrickRow(getBrickColor(i), startingY, false);
            startingY += BRICK_HEIGHT + BRICK_SEP;
        }
        startingY = BRICK_Y_OFFSET;
        for (int i = 0; i < NBRICK_ROWS; i++) {
            drawBrickRow(getBrickColor(i), startingY, true);
            startingY += BRICK_HEIGHT + BRICK_SEP;
        }
    }

    private void drawBrickRow(Color color, double y, boolean isForClient) {
        double x = (double) (WIDTH - NBRICKS_PER_ROW * BRICK_WIDTH - (NBRICKS_PER_ROW - 1) * BRICK_SEP) / 2;
        if (isForClient) {
            x += WIDTH + SEPERATOR_WIDTH;
        }
        for (int j = 0; j < NBRICKS_PER_ROW; j++) {
            drawBrick(color, x, y);
            x += BRICK_WIDTH + BRICK_SEP;
        }
    }

    private void drawBrick(Color color, double x, double y) {
        GRect brick = new GRect(x, y, BRICK_WIDTH, BRICK_HEIGHT);
        brick.setFilled(true);
        brick.setFillColor(color);
        brick.setColor(color);
        add(brick);
    }

    private void createPaddle() {
        paddle = new GRect((double) (WIDTH - PADDLE_WIDTH) / 2, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT, PADDLE_WIDTH, PADDLE_HEIGHT);
        paddle.setFilled(true);
        add(paddle);
    }

    private void displayCountdown(int count) {
        if (counter != null) {
            remove(counter);
        }

        counter = new GLabel("" + count);
        double centerX = WIDTH + (double) SEPERATOR_WIDTH / 2 + counter.getWidth() / 2;
        double centerY = (double) HEIGHT / 2 - counter.getAscent() / 2;
        counter.setFont(new Font("serif", Font.PLAIN, 25));
        counter.setColor(isDarkModeEnabled ? Color.BLACK : Color.WHITE);
        add(counter, centerX, centerY);
    }

    private void createServerBall() {
        serverBall = new GOval((double) WIDTH / 2 - BALL_RADIUS + SEPERATOR_WIDTH + WIDTH, (double) HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
        serverBall.setFilled(true);
        add(serverBall);
    }

    private void createServerPaddle() {
        serverPaddle = new GRect((double) (WIDTH - PADDLE_WIDTH) / 2 + WIDTH + SEPERATOR_WIDTH, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT, PADDLE_WIDTH, PADDLE_HEIGHT);
        serverPaddle.setFilled(true);
        add(serverPaddle);
    }
}
