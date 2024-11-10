import acm.graphics.*;
import acm.program.GraphicsProgram;
import acm.util.RandomGenerator;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

public class BreakoutClient extends GraphicsProgram {

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
    private static final int PORT = 6969;
    private static final String ADDRESS = "192.168.1.188";

    private GRect paddle;
    private GRect serverPaddle;
    private GOval ball;
    private GOval serverBall;
    private double vx, vy = 3.0;
    private RandomGenerator rgen = RandomGenerator.getInstance();
    private double turnsCount = NTURNS;
    private double aliveBricks = NBRICK_ROWS * NBRICKS_PER_ROW;
    private GLabel bricksLeft = null;
    private boolean isDarkModeEnabled = true;
    private GImage switcher;
    private boolean gameStarted = false;
    private GLabel counter;

    private Socket socket = null;
    private DataInputStream input = null;
    private DataOutputStream output = null;


    public void run() {
        initGame();              // Set up the game environment
        addMouseListeners();      // Set up controls
        connectToServer(ADDRESS, PORT);  // Connect to the server

        // Wait until the game starts
        while (!gameStarted) {
            pause(100);
        }

        // Start the game loop once gameStarted is true
        gameLoop();
    }

    // Setting all variables ready for the game
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
    }

    private void createServerBall() {
        serverBall = new GOval(WIDTH / 2 - BALL_RADIUS + SEPERATOR_WIDTH + WIDTH, HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
        serverBall.setFilled(true);
        add(serverBall);
    }

    private void createServerPaddle() {
        serverPaddle = new GRect((WIDTH - PADDLE_WIDTH) / 2 + WIDTH + SEPERATOR_WIDTH, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT, PADDLE_WIDTH, PADDLE_HEIGHT);
        serverPaddle.setFilled(true);
        add(serverPaddle);
    }

    // Each *frame* happens here
    private void gameLoop() {
        while (turnsCount > 0 && aliveBricks > 0) {
            moveBall();
            checkCollisions();
            sendPositionsToServer(0, 0);  // New method to handle sending paddle position
            pause(DELAY);
        }

        // Game ends: close resources
        if (turnsCount == 0) {
            handleGameLoss();
        } else {
            handleGameWin();
        }
        remove(ball);
        closeConnection();  // Close socket and streams when the game ends
    }

    private void sendPositionsToServer(double brickX, double brickY) {
        try {
            double paddleX = paddle.getX();
            double ballX = ball.getX();        // Get ball's x coordinate
            double ballY = ball.getY();        // Get ball's y coordinate

            // Send the paddle and ball coordinates as a single message
            output.writeDouble(paddleX);  // Send paddle X
            output.writeDouble(ballX);    // Send ball X
            output.writeDouble(ballY);    // Send ball Y
            output.writeDouble(brickX);    // Send ball Y
            output.writeDouble(brickY);    // Send ball Y
        } catch (IOException e) {
            System.out.println("Failed to send paddle position: " + e.getMessage());
        }
    }

    private void closeConnection() {
        try {
            if (output != null) output.close();
            if (socket != null) socket.close();
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }

    private void connectToServer(String address, int port) {
        try {
            socket = new Socket(address, port);
            System.out.println("CONNECTED");

            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream());

            new Thread(() -> {
                try {
                    while (true) {
                        if (input.available() > 0) {
                            int countdown = input.readInt();
                            displayCountdown(countdown);

                            if (countdown == 0) {
                                boolean gameStarted = input.readBoolean();
                                if (gameStarted) {
                                    startGameLoop();
                                }
                                break;
                            }
                        }

                        // After the countdown, read game state data from the server
                        if (gameStarted) {
                            double paddleX = input.readDouble();
                            double ballX = input.readDouble();
                            double ballY = input.readDouble();
                            double brickX = input.readDouble();
                            double brickY = input.readDouble();

                            System.out.println(paddleX);
                            // Update game objects based on received data
                            GObject currentEl = getElementAt(brickX + WIDTH + SEPERATOR_WIDTH, brickY);
                            if (currentEl != null) {
                                remove(currentEl);
                            }

                            serverPaddle.setLocation(paddleX + WIDTH + SEPERATOR_WIDTH, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT);
                            serverBall.setLocation(ballX + WIDTH + SEPERATOR_WIDTH, ballY);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Error in communication thread: " + e.getMessage());
                }
            }).start();

        } catch (UnknownHostException e) {
            System.out.println("Unknown host: " + e.getMessage());
        } catch (IOException e) {
            System.out.println("I/O error: " + e.getMessage());
        }
    }

    private void displayCountdown(int count) {
        if (counter != null) {
            remove(counter);
        }

        counter = new GLabel("" + count);
        double centerX = WIDTH + SEPERATOR_WIDTH / 2 + counter.getWidth() / 2;
        double centerY = HEIGHT / 2 - counter.getAscent() / 2;
        counter.setFont(new Font("serif", Font.PLAIN, 25));
        add(counter, centerX, centerY);
    }

    // Method to start the game loop on the client
    private void startGameLoop() {
        gameStarted = true; // Ensure game starts
        // Begin client game loop logic here
    }

    // DVD screensaver like animation, but if it touches the bottom of the screen we record it as a *missed ball*
    private void moveBall() {
        ball.move(vx, vy);
        if (ball.getX() <= 0 || ball.getX() + BALL_RADIUS * 2 >= WIDTH) {
            vx = -vx;
        }
        if (ball.getY() <= 0) {
            vy = -vy;
        } else if (ball.getY() + BALL_RADIUS * 2 >= HEIGHT) {
            handleBallMiss();
        }
    }


    /*
        GAME INIT
     */

    private void renderHearts() {
        double y = 0;
        double x = HEART_OFFSET;

        for (int i = 0; i < turnsCount; i++) {
            GImage heart = new GImage("./heart.png");
            heart.setSize(30, 30);
            add(heart, x, y);
            heart.sendToFront();
            x += HEART_GAP + heart.getWidth();
        }
    }

    private void createBall() {
        ball = new GOval(WIDTH / 2 - BALL_RADIUS, HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
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

    // just draws one row of bricks, self-explanatory, no more comments needed
    private void drawBrickRow(Color color, double y, boolean isForClient) {
        double x = (WIDTH - NBRICKS_PER_ROW * BRICK_WIDTH - (NBRICKS_PER_ROW - 1) * BRICK_SEP) / 2;
        if (isForClient) {
            x += WIDTH + SEPERATOR_WIDTH;
        }
        for (int j = 0; j < NBRICKS_PER_ROW; j++) {
            GRect brick = new GRect(x, y, BRICK_WIDTH, BRICK_HEIGHT);
            brick.setFilled(true);
            brick.setFillColor(color);
            brick.setColor(color);
            add(brick);
            x += BRICK_WIDTH + BRICK_SEP;
        }
    }


    private void createPaddle() {
        paddle = new GRect((WIDTH - PADDLE_WIDTH) / 2, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT, PADDLE_WIDTH, PADDLE_HEIGHT);
        paddle.setFilled(true);
        add(paddle);
    }


    /*
        HANDLE EVENTS
     */


    // Just setting the paddleX based on mouse x
    public void mouseMoved(MouseEvent e) {
        double x = e.getX() - PADDLE_WIDTH / 2;
        double paddleY = paddle.getY();
        if (gameStarted && x >= 0 && x + PADDLE_WIDTH <= WIDTH && !isGameOver()) {
            paddle.setLocation(x, paddleY);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        if (switcher.contains(x, y)) {
            handleThemeChange();
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
        vy = -Math.abs(vy);
        double paddleCenter = paddle.getX() + PADDLE_WIDTH / 2;
        vx = (ball.getX() + BALL_RADIUS - paddleCenter) / PADDLE_SENSITIVITY;
    }

    private void handleGameLoss() {
        renderTextInCenter("You Lost :(((", Color.RED, 30);
    }

    private void handleGameWin() {
        renderTextInCenter("You WONN :))", Color.GREEN, 30);
    }

    /*
        HELPER FUNCTIONS
     */

    private boolean isGameOver() {
        return turnsCount == 0 || aliveBricks == 0;
    }

    // Reset the ball to center
    private void resetBall() {
        ball.setLocation(WIDTH / 2 - BALL_RADIUS, HEIGHT / 2 - BALL_RADIUS);
        vy = Math.abs(vy);
        setRandomVx();
        pause(2000);
    }

    private void setRandomVx() {
        vx = rgen.nextDouble(1.0, 3.0) * (rgen.nextBoolean(0.5) ? -1 : 1);
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

    private void checkCollisions() {
        GObject collider = getBallCollidingObject();
        if (collider == paddle) {
            handlePaddleKick();
        } else if (collider != null && collider instanceof GRect) {
            remove(collider);
            aliveBricks--;
            bricksLeft.setLabel("Bricks : " + (int) aliveBricks);
            vy = -vy;
            sendPositionsToServer(collider.getX(), collider.getY());
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


    private void renderTextInCenter(String str, Color color, int fontSize) {
        GLabel text = new GLabel(str);
        text.setFont(new Font("Serif", Font.PLAIN, fontSize));
        text.setColor(color);
        double x = (WIDTH - text.getWidth()) / 2;
        double y = (HEIGHT - text.getAscent()) / 2;
        add(text, x, y);
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

    private GObject getCurrentHeart() {
        double x = HEART_OFFSET + (turnsCount - 1) * (HEART_WIDTH + HEART_GAP) + HEART_WIDTH / 2;
        GObject heart = getElementAt(x, HEART_WIDTH / 2);
        return heart;
    }

    private void renderThemeSwitcher(boolean darkMode) {
        switcher = darkMode ? new GImage("./light.png") : new GImage("./dark.png");
        switcher.setSize(30, 20);
        switcher.sendToFront();
        double x = bricksLeft.getX() - switcher.getWidth() - 10;
        add(switcher, x, HEART_OFFSET / 2);
    }

    private void handleThemeChange() {
        remove(switcher);
        isDarkModeEnabled = !isDarkModeEnabled;
        renderThemeSwitcher(isDarkModeEnabled);
        ball.setColor(isDarkModeEnabled ? Color.BLACK : Color.WHITE);
        serverBall.setColor(isDarkModeEnabled ? Color.BLACK : Color.WHITE);
        paddle.setColor(isDarkModeEnabled ? Color.BLACK : Color.WHITE);
        serverPaddle.setColor(isDarkModeEnabled ? Color.BLACK : Color.WHITE);
        setBackground(isDarkModeEnabled ? Color.WHITE : Color.BLACK);
    }
}
