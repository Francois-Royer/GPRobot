package tankbase;

import tankbase.enemy.Enemy;
import tankbase.gun.Fire;
import tankbase.wave.Wave;

import java.awt.*;
import java.awt.geom.Point2D;

import static java.lang.Math.*;
import static robocode.Rules.MAX_BULLET_POWER;
import static robocode.util.Utils.normalAbsoluteAngle;
import static tankbase.Constant.TANK_SIZE_INT;
import static tankbase.FieldMap.isFullMap;
import static tankbase.FieldMap.toggleMapMode;
import static tankbase.TankUtils.range;
import static tankbase.wave.WaveLog.getWaves;
import static tankbase.enemy.EnemyDB.filterEnemies;
import static tankbase.gun.log.VirtualFireLog.getVirtualFireLog;

public abstract class AbstractTankDrawingBase extends AbstractTankBase implements ITank {

    public static int INFO_LEVEL = 0;

    boolean drawAiming = true;
    boolean drawAimPoint = false;
    boolean drawMap = true;
    boolean drawEnemy = true;
    boolean drawFire = false;
    boolean drawWave = false;

    public static void drawFillCircle(Graphics2D g, Color c, Point2D.Double p, int d) {
        g.setColor(c);
        drawFillCircle(g, p, d);
    }

    public static void drawFillCircle(Graphics2D g, Point2D.Double p, int d) {
        g.fillArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
    }

    public static void drawCircle(Graphics2D g, Color c, Point2D.Double p, int d) {
        g.setColor(c);
        g.drawArc((int) p.x - d / 2, (int) p.y - d / 2, d, d, 0, 360);
    }

    public static void drawAimCircle(Graphics2D g, Color c, Point2D.Double p, int d) {
        int div = 5;
        drawCircle(g, c, p, d);
        g.drawLine((int) p.x + d / 2, (int) p.y, (int) p.x - d / div, (int) p.y);
        g.drawLine((int) p.x - d / 2, (int) p.y, (int) p.x + d / div, (int) p.y);
        g.drawLine((int) p.x, (int) p.y - d / 2, (int) p.x, (int) p.y - d / div);
        g.drawLine((int) p.x, (int) p.y + d / 2, (int) p.x, (int) p.y + d / div);
    }

    public static void drawWave(Graphics2D g2D, Wave w, long tick) {
        int waveArc = (int) (toDegrees(w.getArc()));
        int d = (int) w.getDistance(tick);
        int a = (450 - (int) (normalAbsoluteAngle(w.direction) * 180 / PI)) % 360;
        int s = d - 5;
        int e = d + 5;
        g2D.drawArc((int) w.x - d, (int) w.y - d, 2 * d, 2 * d, a - waveArc / 2, waveArc);
        g2D.drawLine((int) (w.x /*+ s * cos(w.direction)*/), (int) (w.y /*+ s * sin(w.direction)*/),
                (int) (w.x + e * cos(w.direction)), (int) (w.y + e * sin(w.direction)));
        g2D.drawLine((int) (w.x + s * cos(w.direction + w.getArc() / 2)), (int) (w.y + s * sin(w.direction + w.getArc() / 2)),
                (int) (w.x + e * cos(w.direction + w.getArc() / 2)), (int) (w.y + e * sin(w.direction + w.getArc() / 2)));
        g2D.drawLine((int) (w.x + s * cos(w.direction - w.getArc() / 2)), (int) (w.y + s * sin(w.direction - w.getArc() / 2)),
                (int) (w.x + e * cos(w.direction - w.getArc() / 2)), (int) (w.y + e * sin(w.direction - w.getArc() / 2)));
    }

    @Override
    public void onKeyPressed(java.awt.event.KeyEvent e) {
        if (INFO_LEVEL>0)
            sysout.printf("Key pressed %c%n", e.getKeyChar());

        switch (e.getKeyChar()) {
            case 'a':
                drawAiming = !drawAiming;
                break;
            case 'e':
                drawEnemy = !drawEnemy;
                break;
            case 'f':
                drawFire = !drawFire;
                break;
            case 'i':
                INFO_LEVEL = (INFO_LEVEL + 1) % 4;
                sysout.printf("INFO_LEVEL set to %d%n", INFO_LEVEL);
                break;
            case 'm':
                drawMap = !drawMap;
                break;
            case 'p':
                drawAimPoint = !drawAimPoint;
                break;
            case 't':
                toggleMapMode();
                break;
            case 'w':
                drawWave = !drawWave;
                break;
            default:
                drawWave = drawMap = drawAiming = drawFire = drawEnemy = drawAimPoint = !drawAiming;
                break;
        }
    }

    @Override
    public void onPaint(Graphics2D g2D) {
        if (drawEnemy) paintEnemies(g2D);
        if (aiming != null && drawAiming) paintAiming(g2D);
        if (drawWave) paintWaves(g2D);
        if (drawFire) paintShells(g2D);
        if (drawAimPoint) paintFirePoints(g2D);
        if (drawMap) paintMap(g2D);
    }

    private void paintEnemies(Graphics2D g2D) {
        int de = TANK_SIZE_INT*2;
        filterEnemies(Enemy::isAlive).forEach(e -> {
            TankState state = e.getState();
            if (e == target)
                drawAimCircle(g2D, Color.CYAN, state, de);
            else if (e == mostLeft)
                drawCircle(g2D, Color.GREEN, state, de);
            else if (e == mostRight)
                drawCircle(g2D, Color.YELLOW, state, de);
            else
                drawCircle(g2D, Color.PINK, state, de);
        });
        drawCircle(g2D, Color.MAGENTA, getState(), TANK_SIZE_INT);
    }

    private void paintAiming(Graphics2D g2D) {
        g2D.setColor(Color.YELLOW);
        for (Point2D.Double p : aiming.getExpectedMoves())
            drawFillCircle(g2D, p, 5);
        drawAimCircle(g2D, aiming.getGun().getColor(), aiming.getFiringPosition(), 20);
    }

    private void paintWaves(Graphics2D g2D) {
        g2D.setColor(Color.ORANGE);
        for (Wave w : getWaves())
            drawWave(g2D, w, getTime());
    }

    private void paintShells(Graphics2D g2D) {
        for (Fire vs : getVirtualFireLog()) {
            int d = 2 + (int) (5 * (vs.getAimingData().getFirePower() / MAX_BULLET_POWER));
            drawFillCircle(g2D, vs.getGunner().getColor(), vs.getPosition(getTime()), d);
        }
    }

    private void paintFirePoints(Graphics2D g2D) {
        for (Fire vs : getVirtualFireLog()) {
            drawAimCircle(g2D, vs.getGunner().getColor(), vs.getAimingData().getFiringPosition(), 5);
        }
    }

    private void paintMap(Graphics2D g2D) {
        drawDangerMap(g2D);
        if (destination != null) {
            drawFillCircle(g2D, Color.GREEN, destination, 10);
            g2D.drawLine((int) getX(), (int) getY(), (int) destination.getX(), (int) destination.getY());
        }
    }

    private void drawSearchPath(Graphics2D g2D) {
        if (BIG_BATTLE_FIELD) {
            g2D.setColor(Color.WHITE);
            g2D.setFont(new Font("TimesRoman", Font.PLAIN, 32));
            for (SearchPoint s:searchPoints)
                g2D.drawString(Integer.toString(s.visited()), (int) s.getX(), (int) s.getY());

        }
    }

    private void drawDangerMap(Graphics2D g2D) {
        Color dc = Color.RED;
        int r = dc.getRed();
        int g = dc.getGreen();
        int b = dc.getBlue();
        double[][] map = FieldMap.getMap();
        int width = FieldMap.getWidth();
        int height = FieldMap.getHeight();
        double scaleX = FIELD_WIDTH / width;
        double scaleY = FIELD_HEIGHT / height;

        if (!(filterEnemies(Enemy::isScanned).isEmpty() && BIG_BATTLE_FIELD)) {
            if (isFullMap())
                for (int y = 0; y < height; y++)
                    for (int x = 0; x < width; x++) {
                        int alpha = (int) range(map[x][y], 0, 1, 0, 128);
                        if (alpha > 0) {
                            Color c = new Color(r, g, b, alpha);
                            g2D.setColor(c);
                            g2D.fillRect((int) (scaleX * x), (int) (scaleY * y), (int) scaleX, (int) scaleY);
                        }
                    }
            else
                FieldMap.getDangerMapPoints().forEach(p -> {
                    int alpha = (int) range(map[p.x][p.y], 0, 1, 0, 128);
                    if (alpha > 0) {
                        Color c = new Color(r, g, b, alpha);
                        g2D.setColor(c);
                        g2D.fillRect((int) (scaleX * p.x), (int) (scaleY * p.y), (int) scaleX, (int) scaleY);
                    }
                });
        } else
            drawSearchPath(g2D);
    }
}