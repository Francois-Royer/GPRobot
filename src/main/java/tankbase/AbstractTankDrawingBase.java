package tankbase;

import tankbase.gun.Shell;

import java.awt.*;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.toDegrees;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.TankUtils.range;

public class AbstractTankDrawingBase extends AbstractTankBase implements ITank {
    boolean drawWave = false;
    boolean drawDanger = true;
    boolean drawAiming = true;
    boolean drawShell = false;
    boolean drawEnemy = true;

    @Override
    public void onKeyPressed(java.awt.event.KeyEvent e) {
        switch (e.getKeyChar()) {
            case 'w':
                drawWave = !drawWave;
                break;
            case 'd':
                drawDanger = !drawDanger;
                break;
            case 'a':
                drawAiming = !drawAiming;
                break;
            case 's':
                drawShell = !drawShell;
                break;
            case 'e':
                drawEnemy = !drawEnemy;
                break;
        }
    }

    @Override
    public void onPaint(Graphics2D g2D) {
        if (drawEnemy) {
            int de = TANK_SIZE_INT * ((drawDanger) ? 2 : 1);
            getEnemys().forEach(e -> {
                if (e.isAlive()) {
                    if (e == target)
                        drawAimCircle(g2D, Color.CYAN, e.getState().getPosition(), de);
                    else if (e == mostLeft)
                        drawCircle(g2D, Color.GREEN, e.getState().getPosition(), de);
                    else if (e == mostRight)
                        drawCircle(g2D, Color.RED, e.getState().getPosition(), de);
                    else
                        drawCircle(g2D, Color.PINK, e.getState().getPosition(), de);
                }});
            drawCircle(g2D, Color.green, getPosition(), TANK_SIZE_INT);
        }

        if (aimingData != null && drawAiming) {
            for (Point.Double p : aimingData.getExpectedMoves())
                drawFillCircle(g2D, Color.yellow, p, 5);

            drawAimCircle(g2D, Color.WHITE, aimingData.getFiringPosition(), 20);
        }

        if (drawWave)
            for (Wave w : getWaves())
                drawWave(g2D, Color.ORANGE, w, getTime());

        if (drawShell)
            for (Shell vs : getVirtualShells()) {
                int d = 2 + (int) (5 * (vs.getAimingData().getFirePower() / MAX_BULLET_POWER));
                drawFillCircle(g2D, vs.getGunner().getColor(), vs.getPosition(getTime()), d);
                drawAimCircle(g2D, vs.getGunner().getColor(), vs.getAimingData().getFiringPosition(), 5);
            }


        if (drawDanger) {
            drawDangerMap(g2D);
            if (safePosition != null) {
                drawFillCircle(g2D, Color.GREEN, safePosition, 10);
                g2D.drawLine((int) getX(), (int) getY(), (int) safePosition.getX(), (int) safePosition.getY());
            }
        }
    }

    private void drawDangerMap(Graphics2D g2D) {
        Color dc = Color.RED;
        int r = dc.getRed();
        int g = dc.getGreen();
        int b = dc.getBlue();
        for (int y = 0; y < DANGER_HEIGHT; y++)
            for (int x = 0; x < DANGER_WIDTH; x++) {
                int alpha = (int) range(dangerMap[x][y], 0, 1, 0, 128);
                Color c = new Color(r, g, b, alpha);
                g2D.setColor(c);
                g2D.fillRect(x * DANGER_SCALE, y * DANGER_SCALE, DANGER_SCALE, DANGER_SCALE);
            }
    }

    public static void drawFillCircle(Graphics2D g, Color c, Point.Double p, int d) {
        g.setColor(c);
        g.fillArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
    }

    public static void drawCircle(Graphics2D g, Color c, Point.Double p, int d) {
        g.setColor(c);
        g.drawArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
    }

    public static void drawAimCircle(Graphics2D g, Color c, Point.Double p, int d) {
        int div = 5;
        g.setColor(c);
        g.drawArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
        g.drawLine((int) p.x + d / 2, (int) p.y, (int) p.x - d / div, (int) p.y);
        g.drawLine((int) p.x - d / 2, (int) p.y, (int) p.x + d / div, (int) p.y);
        g.drawLine((int) p.x, (int) p.y - d / 2, (int) p.x, (int) p.y - d / div);
        g.drawLine((int) p.x, (int) p.y + d / 2, (int) p.x, (int) p.y + d / div);
    }

    public static void drawWave(Graphics2D g2D, Color c, Wave w, long tick) {
        g2D.setColor(c);
        int waveArc = (int) (toDegrees(w.arc));
        int d = (int) w.getDistance(tick);
        int a = (450 - (int) (normalAbsoluteAngle(w.direction) * 180 / PI)) % 360;
        int s = d - 5;
        int e = d + 5;
        g2D.drawArc((int) w.x - d, (int) w.y - d, 2 * d, 2 * d, a - waveArc / 2, waveArc);
        g2D.drawLine((int) (w.x /*+ s * cos(w.direction)*/), (int) (w.y /*+ s * sin(w.direction)*/),
                     (int) (w.x + e * cos(w.direction)), (int) (w.y + e * sin(w.direction)));
        g2D.drawLine((int) (w.x + s * cos(w.direction + w.arc / 2)), (int) (w.y + s * sin(w.direction + w.arc / 2)),
                     (int) (w.x + e * cos(w.direction + w.arc / 2)), (int) (w.y + e * sin(w.direction + w.arc / 2)));
        g2D.drawLine((int) (w.x + s * cos(w.direction - w.arc / 2)), (int) (w.y + s * sin(w.direction - w.arc / 2)),
                     (int) (w.x + e * cos(w.direction - w.arc / 2)), (int) (w.y + e * sin(w.direction - w.arc / 2)));
    }
}