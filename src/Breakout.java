import acm.graphics.GLabel;
import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRect;
import acm.program.GraphicsProgram;
import acm.util.RandomGenerator;

import java.awt.*;
import java.awt.event.MouseEvent;

public class Breakout extends GraphicsProgram {

    public static final int DELAY = 7;
    public static final int APPLICATION_WIDTH = 400;
    public static final int APPLICATION_HEIGHT = 600;
    private static final int WIDTH = APPLICATION_WIDTH;
    private static final int HEIGHT = APPLICATION_HEIGHT;
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

    private GRect paddle;
    private GOval ball;
    private double vx, vy = 3.0;
    private RandomGenerator rgen = RandomGenerator.getInstance();
    private double turnsCount = NTURNS;
    private double aliveBricks = NBRICK_ROWS * NBRICKS_PER_ROW;

    public void run() {
        initGame();
        gameLoop();
    }

    // Setting all variables ready for the game
    private void initGame() {
        addMouseListeners();
        drawBricks();
        setRandomVx();
        createPaddle();
        createBall();
    }

    // Each *frame* happens here
    private void gameLoop() {
        while (turnsCount > 0 && aliveBricks > 0) {
            moveBall();
            checkCollisions();
            pause(DELAY);
        }
        if (turnsCount == 0) {
            handleGameLoss();
        } else {
            handleGameWin();
        }
        remove(ball);
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


    /*
        GAME INIT
     */
    private void createBall() {
        ball = new GOval(WIDTH / 2 - BALL_RADIUS, HEIGHT / 2 - BALL_RADIUS, BALL_RADIUS * 2, BALL_RADIUS * 2);
        ball.setFilled(true);
        add(ball);
    }

    private void drawBricks() {
        double startingY = BRICK_Y_OFFSET;
        for (int i = 0; i < NBRICK_ROWS; i++) {
            drawBrickRow(getBrickColor(i), startingY);
            startingY += BRICK_HEIGHT + BRICK_SEP;
        }
    }

    // just draws one row of bricks, self-explanatory, no more comments needed
    private void drawBrickRow(Color color, double y) {
        double x = (WIDTH - NBRICKS_PER_ROW * BRICK_WIDTH - (NBRICKS_PER_ROW - 1) * BRICK_SEP) / 2;
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
        paddle = new GRect((WIDTH - PADDLE_WIDTH) / 2, HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT, PADDLE_WIDTH, PADDLE_HEIGHT);
        paddle.setFilled(true);
        add(paddle);
    }


    /*
        HANDLE EVENTS
     */


    // Just setting the paddleX based on mouse x
    public void mouseMoved(MouseEvent e) {
        if (isGameOver()) {
            return;
        }
        double x = e.getX() - PADDLE_WIDTH / 2;
        double paddleY = paddle.getY();
        if (x >= 0 && x + PADDLE_WIDTH <= WIDTH) {
            paddle.setLocation(x, paddleY);
        }
    }

    // If the paddle misses the ball... RIP
    private void handleBallMiss() {
        turnsCount--;
        if (turnsCount > 0) {
            resetBall();
        }
    }

    // We estimate the VX of the ball based on how far it was from the center of the paddle (we can try different values of sensitivity)
    private void handlePaddleKick() {

        // vy must be based on absolute volume because we want the vy to be always negative, it was getting stuck in paddle coz it was changing the direcion all the time
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
        } else if (collider != null) {
            remove(collider);
            aliveBricks--;
            vy = -vy;
        }
    }

    // Returns either NULL or object the ball is colliding
    // I tried checking the 2 corners of each side but i think this version looks smoother
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

}
