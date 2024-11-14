import acm.graphics.*;
import acm.program.GraphicsProgram;
import acm.util.RandomGenerator;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class BreakoutServer extends GraphicsProgram {

    public static final int DELAY = 7;
    public static final int SEPARATOR_WIDTH = 300;
    private static final int WIDTH = 400;
    private static final int HEIGHT = 600;
    public static final int APPLICATION_WIDTH = WIDTH * 2 + SEPARATOR_WIDTH;
    public static final int APPLICATION_HEIGHT = 600;
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
    private static final int START_BUTTON_WIDTH = WIDTH / 3;
    private static final int START_BUTTON_HEIGHT = 50;
    private static final Color START_BUTTON_COLOR = Color.GREEN;
    private static final Color startButtonLabelText = Color.WHITE;
    private static final int PORT = 6969;

    private GRect paddle;
    private GRect clientPaddle;
    private GOval ball;
    private GOval clientBall;
    private double vx, vy = 3.0;
    private final RandomGenerator rgen = RandomGenerator.getInstance();
    private double turnsCount = NTURNS;
    private double aliveBricks = NBRICK_ROWS * NBRICKS_PER_ROW;
    private GLabel bricksLeft = null;
    private boolean isDarkModeEnabled = true;
    private GImage switcher;
    private GRect startButton;
    private GLabel startButtonLabel;
    private static final String startButtonText = "Start Game";
    private boolean gameStarted = false;
    private GLabel counter;
    private GLine separator1;
    private GLine separator2;

    private Socket socket = null;
    private ServerSocket server = null;
    private DataInputStream in = null;
    private DataOutputStream out = null;

    private boolean connectionActive = false;


    public void run() {
        initGame();
        addMouseListeners();
        waitForConnection();
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
        createClientPaddle();
        createBall();
        createClientBall();
        renderStartMenu();
        renderSeparator();
    }

    // Each *frame* happens here
    private void gameLoop() {
        while (turnsCount > 0 && aliveBricks > 0 && connectionActive) {
            moveBall();
            checkCollisions();
            sendPositionsToClient(0, 0);
            pause(DELAY);
        }
        if (turnsCount == 0) {
            handleGameLoss(1);
        } else if (aliveBricks == 0) {
            handleGameWin(1);
        }
        remove(ball);
    }


    // ----------- SOCKETS -----------------
    private void waitForConnection() {
        try {
            server = new ServerSocket(PORT);
            System.out.println("Waiting for connection...");
            socket = server.accept();
            System.out.println("Connection accepted");
            connectionActive = true;

            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            // We need a new thread so that gameloop doesnt stop
            new Thread(() -> {
                while (true) {
                    try {
                        receiveData();
                    } catch (IOException e) {
                        System.out.println(e.getMessage());
                        break;
                    }
                }
            }).start();

        } catch (IOException e) {
            closeConnection();
            System.out.println("Error establishing server connection: " + e.getMessage());
        }
    }

    private void receiveData() throws IOException {
        int messageType = in.readInt();
        if (messageType == 0) {
            receiveAndProcessGameVariables();
        } else if (messageType == 1) {
            receiveGameEndEvent();
        }
    }

    private void receiveGameEndEvent() throws IOException {
        boolean statusMessage = in.readBoolean();
        if (statusMessage) {
            handleGameLoss(0);
        } else {
            handleGameWin(0);
        }
    }

    private void receiveAndProcessGameVariables() throws IOException {
        double paddleX = in.readDouble();
        double ballX = in.readDouble();
        double ballY = in.readDouble();
        double brickX = in.readDouble();
        double brickY = in.readDouble();

        GObject currentEl = getElementAt(brickX + WIDTH + SEPARATOR_WIDTH, brickY);
        if (currentEl instanceof GRect) {
            remove(currentEl);
        }

        clientPaddle.setLocation(paddleX + WIDTH + SEPARATOR_WIDTH, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT);
        clientBall.setLocation(ballX + WIDTH + SEPARATOR_WIDTH, ballY);
    }

    private void sendPositionsToClient(double brickX, double brickY) {
        try {
            double paddleX = paddle.getX();
            double ballX = ball.getX();
            double ballY = ball.getY();
            out.writeInt(0);
            out.writeDouble(paddleX);
            out.writeDouble(ballX);
            out.writeDouble(ballY);
            out.writeDouble(brickX);
            out.writeDouble(brickY);
        } catch (IOException e) {
            System.out.println(e);
            closeConnection();
        }
    }

    private void sendWinEvent() {
        try {
            out.writeInt(1);
            out.writeBoolean(true);
        } catch (IOException e) {
            closeConnection();
            System.out.println(e);
        }
    }

    private void sendLoseEvent() {
        try {
            out.writeInt(1);
            out.writeBoolean(false);
        } catch (IOException e) {
            closeConnection();
            System.out.println(e);
        }
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
            connectionActive = false;
            System.out.println("Connection closed.");
        } catch (IOException e) {
            System.out.println("Error closing connection: " + e.getMessage());
        }
    }


    // ----------- HELPERS -------------

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

    private void checkCollisions() {
        GObject collider = getBallCollidingObject();
        if (collider == paddle) {
            handlePaddleKick();
        } else if (collider instanceof GRect) {
            remove(collider);
            aliveBricks--;
            bricksLeft.setLabel("Bricks : " + (int) aliveBricks);
            vy = -vy;
            sendPositionsToClient(collider.getX(), collider.getY());
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

    private void startCountdown() {
        new Thread(() -> {
            try {
                for (int i = 3; i >= 0; i--) {
                    out.writeInt(i);
                    displayCountdown(i);
                    Thread.sleep(1000);
                }
                gameStarted = true;
                remove(counter);
                out.writeBoolean(true);
            } catch (IOException | InterruptedException e) {
                closeConnection();
            }
        }).start();
    }


    // ----------- LISTENERS ----------------

    // Just setting the paddleX based on mouse x
    @Override
    public void mouseMoved(MouseEvent e) {
        double x = e.getX() - (double) PADDLE_WIDTH / 2;
        double paddleY = paddle.getY();
        if (gameStarted && x >= 0 && x + PADDLE_WIDTH <= WIDTH && !isGameOver()) {
            paddle.setLocation(x, paddleY);
        }
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        double x = e.getX();
        double y = e.getY();

        if (startButton.contains(x, y)) {
            remove(startButton);
            remove(startButtonLabel);
            startCountdown(); // Start the countdown instead of directly starting the game
        } else if (switcher.contains(x, y)) {
            handleThemeChange();
        }
    }


    // ------------ GETTERS ---------------

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
        double x = HEART_OFFSET + (turnsCount - 1) * (HEART_WIDTH + HEART_GAP) + (double) HEART_WIDTH / 2;
        return getElementAt(x, (double) HEART_WIDTH / 2);
    }


    // ----------- HANDLERS ------------------

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
        double paddleCenter = paddle.getX() + (double) PADDLE_WIDTH / 2;
        vx = (ball.getX() + BALL_RADIUS - paddleCenter) / PADDLE_SENSITIVITY;
    }

    private void handleGameLoss(int iLost) {
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
        clientBall.setColor(secondaryColor);
        paddle.setColor(secondaryColor);
        separator1.setColor(secondaryColor);
        separator2.setColor(secondaryColor);
        clientPaddle.setColor(secondaryColor);
        setBackground(primaryColor);
    }


    // ---------- RENDERERS ----------------

    private void displayCountdown(int count) {
        if (counter != null) {
            remove(counter);
        }
        counter = new GLabel("" + count);
        double counterX = WIDTH + (double) SEPARATOR_WIDTH / 2 + counter.getWidth() / 2;
        double counterY = (double) HEIGHT / 2 - counter.getAscent() / 2;
        counter.setColor(isDarkModeEnabled ? Color.BLACK : Color.WHITE);
        counter.setFont(new Font("serif", Font.PLAIN, 25));
        add(counter, counterX, counterY);
    }

    private void renderSeparator() {
        separator1 = new GLine(WIDTH, 0, WIDTH, HEIGHT);
        separator2 = new GLine(WIDTH + SEPARATOR_WIDTH, 0, WIDTH + SEPARATOR_WIDTH, HEIGHT);
        add(separator1);
        add(separator2);
    }

    private void renderStartMenu() {
        startButton = new GRect(START_BUTTON_WIDTH, START_BUTTON_HEIGHT);
        startButton.setFilled(true);
        startButton.setColor(START_BUTTON_COLOR);
        startButtonLabel = new GLabel(startButtonText);
        startButtonLabel.setFont(new Font("Serif", Font.PLAIN, 20));
        startButtonLabel.setColor(startButtonLabelText);
        double startButtonX = (APPLICATION_WIDTH - startButton.getWidth()) / 2;
        double startButtonY = (HEIGHT - startButton.getHeight()) / 2;
        double startButtonLabelX = startButtonX + (START_BUTTON_WIDTH - startButtonLabel.getWidth()) / 2;
        double startButtonLabelY = startButtonY + (START_BUTTON_HEIGHT + startButtonLabel.getAscent() / 2) / 2;
        add(startButton, startButtonX, startButtonY);
        add(startButtonLabel, startButtonLabelX, startButtonLabelY);
    }

    private void renderThemeSwitcher(boolean darkMode) {
        switcher = darkMode ? new GImage("./light.png") : new GImage("./dark.png");
        switcher.setSize(30, 20);
        switcher.sendToFront();
        double x = bricksLeft.getX() - switcher.getWidth() - 10;
        add(switcher, x, (double) HEART_OFFSET / 2);
    }

    private void renderTextInCenter(String str, Color color, int fontSize) {
        GLabel text = new GLabel(str);
        text.setFont(new Font("Serif", Font.PLAIN, fontSize));
        text.setColor(color);
        double x = (APPLICATION_WIDTH - text.getWidth()) / 2;
        double y = (APPLICATION_HEIGHT - text.getAscent()) / 2;
        add(text, x, y);
    }


    private void renderBricksLeft() {
        bricksLeft = new GLabel("Bricks: " + (int) aliveBricks);
        bricksLeft.setFont(new Font("Serif", Font.PLAIN, 17));
        bricksLeft.setColor(Color.ORANGE);
        bricksLeft.sendToFront();
        double x = WIDTH - bricksLeft.getWidth() - 20;
        double y = HEART_OFFSET + bricksLeft.getAscent() / 2;
        add(bricksLeft, x, y);
    }

    private void createBall() {
        ball = new GOval((double) WIDTH / 2 - BALL_RADIUS, (double) HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
        ball.setFilled(true);
        add(ball);
    }

    private void createClientBall() {
        clientBall = new GOval((double) WIDTH / 2 - BALL_RADIUS + SEPARATOR_WIDTH + WIDTH, (double) HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
        clientBall.setFilled(true);
        add(clientBall);
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
        double x = (double) (WIDTH - NBRICKS_PER_ROW * BRICK_WIDTH - (NBRICKS_PER_ROW - 1) * BRICK_SEP) / 2;
        if (isForClient) {
            x += WIDTH + SEPARATOR_WIDTH;
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

    private void createClientPaddle() {
        clientPaddle = new GRect((double) (WIDTH - PADDLE_WIDTH) / 2 + WIDTH + SEPARATOR_WIDTH, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT, PADDLE_WIDTH, PADDLE_HEIGHT);
        clientPaddle.setFilled(true);
        add(clientPaddle);
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
}
