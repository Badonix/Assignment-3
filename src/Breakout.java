/*
 * File: Breakout.java
 * -------------------
 * Name:
 * Section Leader:
 *
 * This file will eventually implement the game of Breakout.
 */

import acm.graphics.GObject;
import acm.graphics.GOval;
import acm.graphics.GRect;
import acm.program.GraphicsProgram;
import acm.util.RandomGenerator;

import java.awt.*;
import java.awt.event.MouseEvent;

public class Breakout extends GraphicsProgram {


    public static final int DELAY = 1;
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

    private GRect paddle = null;
    private GOval ball = null;
    private double vx;
    private double vy = 3.0;
    private RandomGenerator rgen = RandomGenerator.getInstance();
    private double paddleY = HEIGHT - PADDLE_Y_OFFSET - PADDLE_HEIGHT;

    public void run() {
        initGame();
        addMouseListeners();
        while (true) {
            moveBall();
            checkBallCollisions();
            pause(DELAY);
        }
    }

    private void initGame() {
        drawBlocks();
        generatePaddle();
        addBall();
        vx = rgen.nextDouble(1.0, 3.0);
        if (rgen.nextBoolean(0.5)) vx = -vx;
    }

    private void moveBall() {
        ball.move(vx, vy);
        if (ball.getX() <= 0 || ball.getX() + BALL_RADIUS * 2 >= WIDTH) {
            vx = -vx;
        }
        if (ball.getY() <= 0 || ball.getY() + BALL_RADIUS * 2 >= HEIGHT) {
            vy = -vy;
        }
    }

    private void addBall() {
        double ballX = WIDTH / 2 - BALL_RADIUS;
        double ballY = HEIGHT / 2 - BALL_RADIUS;
        ball = new GOval(ballX, ballY, BALL_RADIUS * 2, BALL_RADIUS * 2);
        ball.setFilled(true);
        add(ball);
    }

    private void drawBlocks() {
        double startingX = (WIDTH - NBRICKS_PER_ROW * BRICK_WIDTH - (NBRICKS_PER_ROW - 1) * BRICK_SEP) / 2;
        double startingY = BRICK_Y_OFFSET;
        for (int i = 0; i < NBRICK_ROWS; i++) {
            double x = startingX;
            double y = startingY;
            startingY += BRICK_HEIGHT + BRICK_SEP;
            for (int j = 0; j < NBRICKS_PER_ROW; j++) {
                drawBlock(getBrickRowColor(i), x, y);
                x += BRICK_WIDTH + BRICK_SEP;
            }
        }
    }

    private void drawBlock(Color color, double x, double y) {
        GRect rect = new GRect(x, y, BRICK_WIDTH, BRICK_HEIGHT);
        rect.setFilled(true);
        rect.setFillColor(color);
        rect.setColor(color);
        add(rect);
    }

    private Color getBrickRowColor(int row) {
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

    private void generatePaddle() {
        int x = (WIDTH - PADDLE_WIDTH) / 2;
        paddle = new GRect(x, paddleY, PADDLE_WIDTH, PADDLE_HEIGHT);
        paddle.setFilled(true);
        add(paddle);
    }

    public void mouseMoved(MouseEvent e) {
        double x = e.getX() - PADDLE_WIDTH / 2;
        double paddleY = paddle.getY();
        if (x >= 0 && x + PADDLE_WIDTH <= WIDTH) {
            paddle.setLocation(x, paddleY);
        }
    }

    private void checkBallCollisions() {
        double leftX = ball.getX();
        double rightX = leftX + BALL_RADIUS * 2;
        double topY = ball.getY();
        double bottomY = topY + BALL_RADIUS * 2;

        GObject collisionObject;

        collisionObject = getElementAt(leftX, bottomY);
        if (collisionObject == null) {
            collisionObject = getElementAt(rightX, bottomY);
        }
        if (collisionObject != null) {
            vy = -Math.abs(vy);
            if (collisionObject.getY() != paddleY) {
                remove(collisionObject);
            }
            return;
        }

        collisionObject = getElementAt(leftX, topY);
        if (collisionObject == null) {
            collisionObject = getElementAt(rightX, topY);
        }
        if (collisionObject != null) {
            vy = Math.abs(vy);

            if (collisionObject.getY() != paddleY) {
                // collisionObject.setVisible(false);
                remove(collisionObject);
            }
            return;
        }

        if (collisionObject == null) {
            collisionObject = getElementAt(leftX, bottomY);
        }
        if (collisionObject != null) {
            vx = Math.abs(vx);

            if (collisionObject.getY() != paddleY) {
                collisionObject.setVisible(false);
            }
            return;
        }

        collisionObject = getElementAt(rightX, topY);
        if (collisionObject == null) {
            collisionObject = getElementAt(rightX, bottomY);
        }
        if (collisionObject != null) {

            if (collisionObject.getY() != paddleY) {
                collisionObject = null;
            }
            vx = -Math.abs(vx);
        }
    }
}
