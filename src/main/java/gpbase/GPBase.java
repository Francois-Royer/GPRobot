package gpbase;

import static gpbase.GPUtils.*;

import gpbase.gun.AimingData;
import gpbase.gun.FireStat;
import gpbase.gun.CircularGunner;
import gpbase.gun.NearestNeighborGunner;
import robocode.*;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static java.lang.Math.*;
import static robocode.util.Utils.*;
import static robocode.Rules.*;

public class GPBase extends AdvancedRobot {
    Point.Double BATTLE_FIELD_CENTER;
    public static double TANK_SIZE = 36;

    public double BORDER_OFFSET = TANK_SIZE * 3 / 2;
    public double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS / 3;
    public static double HIT_RATE_WATER_MARK = 0.3;
    public static double FIRE_POWER_FACTOR = 4;
    public static double FIRE_TOLERANCE = TANK_SIZE / 9;
    public static long FIRE_AGAIN_MIN_TIME;
    public static double waveArcRadians = PI / 16;

    public static double dmin = 100;
    public static double dmax;
    public static Random random = getRandom();

    public int phsz;

    public int aliveCount;

    Point.Double unsafePosition;
    Point.Double safePosition;
    Point.Double waveSafePosition;
    Point.Double targetPred;
    List<Point.Double> targetPreds = new ArrayList<>();
    Point.Double leftWave;
    Point.Double rightWave;

    public double scandirection = 1;
    public double forward = 1;

    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double fire = 0;

    static Map<String, Enemy> enemies = new HashMap<>();
    static List<Wave> waves = new ArrayList<>();

    Enemy target;
    Enemy mostLeft;
    Enemy mostRight;

    public long lastFireTime=-1;
    FireStat globalFireStat = new FireStat();
    FireStat roundFireStat;

    static long fireCount = 0;
    static long bulletHitCount = 0;
    static long battleFireCount = 0;
    static long battlebulletHitCount = 0;

    CircularGunner circularGunner;
    NearestNeighborGunner nearestNeighborGunner;


    @Override
    public void run() {
        circularGunner = new CircularGunner(this);
        nearestNeighborGunner = new NearestNeighborGunner(this);

        BATTLE_FIELD_CENTER = new Point.Double(getBattleFieldWidth() / 2, getBattleFieldHeight() / 2);
        dmax = BATTLE_FIELD_CENTER.distance(TANK_SIZE / 2, TANK_SIZE / 2) * 2;
        phsz = (int) (dmax/getBulletSpeed(MAX_BULLET_POWER) + 2);
        FIRE_AGAIN_MIN_TIME = (long) (Rules.getGunHeat(MIN_BULLET_POWER) / getGunCoolingRate());
        out.println ("dmax="+dmax);
        out.println ("phsz="+phsz);
        out.println ("FIRE_AGAIN_MIN_TIME="+FIRE_AGAIN_MIN_TIME);

        aliveCount = getOthers();

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red, Color.blue, Color.green);
        for (Enemy enemy : enemies.values()) {
            enemy.energy = 0;
            enemy.rotationRate = 0;
            enemy.velocity = enemy.scanVelocity = 0;
            enemy.alive = false;
            enemy.scanLastUpdate = 0;
            enemy.lastFire = 0;
        }

        while (true) {
            doTurn();
            execute();
        }

    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        Enemy enemy = enemies.get(e.getName());

        if (enemy == null)
            enemies.put(e.getName(), new Enemy(e, this));
        else
            enemy.update(e, this);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        bulletHitCount++;
        Enemy e = enemies.get(bhe.getName());
        if (e != null) {
            e.bulletHitCount++;
            e.energy = bhe.getEnergy();
        }
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent bhbe) {
        if (waves.size() > 0) {
            Bullet b = bhbe.getHitBullet();
            Point.Double p = new Point.Double(b.getX(), b.getY());
            Optional<Wave> ow = waves.stream().filter(w -> w.name == b.getName())
                    .sorted(new WaveComparator(p, getTime())).findFirst();

            if (ow.isPresent())
                waves.remove(ow.get());
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        Enemy e = enemies.get(hbbe.getName());
        if (e != null)
            e.energy += getBulletHitBonus(hbbe.getPower());

        if (waves.size() > 0) {
            Optional<Wave> ow = waves.stream().filter(w -> w.name == hbbe.getName())
                    .sorted(new WaveComparator(getCurrentPoint(), getTime())).findFirst();

            if (ow.isPresent())
                waves.remove(ow.get());
        }
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        battlebulletHitCount += bulletHitCount;
        battleFireCount += fireCount;
        out.printf("Round hit rate = %.02f%%\n", 100D * bulletHitCount / fireCount);
        out.printf("Battle hit rate = %.02f%%\n", 100D * battlebulletHitCount / battleFireCount);
        for (Enemy e : enemies.values())
            out.printf("%20s: hit rate = %.02f%%\n", e.name, 100D * e.getHitRate());
        bulletHitCount = 0;
        fireCount = 0;
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        Enemy enemy = enemies.get(event.getName());
        enemy.energy = 0;
        enemy.rotationRate = 0;
        enemy.velocity = enemy.scanVelocity = 0;
        enemy.alive = false;
        enemy.scanLastUpdate = 0;
        enemy.lastFire = 0;
        aliveCount--;
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (safePosition != null) {
            drawFillCircle(g, Color.GREEN, safePosition, 10);
            drawCircle(g, Color.GREEN, waveSafePosition, 15);
        }

        for (Enemy e : enemies.values())
            if (e.alive)
                drawFillCircle(g, Color.PINK, e, 10);

        for (Point.Double p : targetPreds)
            drawFillCircle(g, Color.yellow, p, 5);

        if (targetPred != null)
            drawAimCircle(g, Color.CYAN, targetPred, 20);

        if (mostLeft != null && mostRight != null) {
            drawCircle(g, Color.RED, mostLeft, (int) TANK_SIZE * 4 / 3);
            drawCircle(g, Color.GREEN, mostRight, (int) TANK_SIZE * 4 / 3);
        }

        long now = getTime();
        for (Wave w : waves)
            drawWave(g, Color.ORANGE, w, now);
        if (waves.size() > 0) {
            Wave w = waves.stream().sorted(new WaveComparator(safePosition, now)).findFirst().get();
            drawWave(g, Color.RED, w, now);
            if (leftWave != null) {
                drawFillCircle(g, Color.RED, leftWave, 5);
                drawFillCircle(g, Color.BLUE, rightWave, 5);
            }
        }
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d\n", event.getSkippedTurn(), event.getPriority());
    }
    // public void onHitWall(HitWallEvent e) { forward *= -1; }
    //

    public void onHitRobot(HitRobotEvent e) {
        forward *= -1;
        setTurnLeftRadians(PI / 2);
        setAhead(forward * TANK_SIZE * 2);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic
    void doGP() {

    }

    public void doTurn() {
        updatePositions();
        updateWaves();
        int oc = getOthers();
        if (oc > 0 && aliveCount() >= oc) {
            updateLeftRightEnemies();
            double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
            turnRadarLeft = scanLeftRight(ra, mostLeft.angle, mostRight.angle);
        } else
            turnRadarLeft = 2 * PI;

        turnLeft = getTurn2Safe();
        ahead = getSafeAhead();
        turnGunLeft = getTurn2Targert();
        fire = fireTargetIfPossible();
        doGP();
        robotSetActions();
    }

    class WaveComparator implements Comparator<Wave> {
        Point.Double p;
        long tick;

        public WaveComparator(Point.Double p, long tick) {
            this.p = p;
            this.tick = tick;
        }

        @Override
        public int compare(Wave w1, Wave w2) {
            return (int) (p.distance(w1.getPosition(tick)) - p.distance(w2.getPosition(tick)));
        }
    }

    private void robotSetActions() {
        setAhead(ahead);
        setTurnLeftRadians(turnLeft);
        setTurnGunLeftRadians(turnGunLeft);
        setTurnRadarLeftRadians(turnRadarLeft);

        if (fire > 0 && getGunHeat() == 0) {
            fireCount++;
            lastFireTime = getTime();
            target.fireCount++;
            setFire(fire);
        }
    }

    private double scanLeftRight(double ra, double ml, double mr) {
        if ((ra >= ml && ra < ml + 2 * SCAN_OFFSET)
                || (ra < 2 * SCAN_OFFSET + ml - 2 * PI && ml > 2 * PI - 2 * SCAN_OFFSET))
            scandirection = -1;
        else if ((ra < mr && ra > mr - 2 * SCAN_OFFSET) || (ra > 2 * PI - 2 * SCAN_OFFSET + mr && mr < 2 * SCAN_OFFSET))
            scandirection = 1;

        if (scandirection == 1)
            return ml + SCAN_OFFSET - ((ml >= ra) ? ra : ra - 2 * PI);
        else
            return mr - SCAN_OFFSET - ((mr <= ra) ? ra : ra + 2 * PI);
    }

    private void updateWaves() {
        long now = getTime();
        List<Wave> newWaves = waves.stream().filter(w -> {
            Point.Double p = w.getPosition(now);
            return p.x >= 0 && p.x <= getBattleFieldWidth() && p.y >= 0 && p.y <= getBattleFieldHeight()
                    && w.distance(getCurrentPoint()) >= w.distance(p);
        }).collect(Collectors.toList());

        waves = newWaves;

        if (waves.size() > 0) {
            Point.Double p;
            leftWave = rightWave = null;
            for (Wave wave: waves) {
                p = getBorderPoint(wave, normalRelativeAngle(wave.direction + wave.arc));
                if (leftWave == null || p.distance(rightWave) > leftWave.distance(rightWave))
                    leftWave = p;
                p = getBorderPoint(wave, normalRelativeAngle(wave.direction - wave.arc));
                if (rightWave == null || p.distance(leftWave) > rightWave.distance(leftWave))
                    rightWave = p;
            }
            Wave closest = waves.stream().sorted(new WaveComparator(getCurrentPoint(), now)).findFirst().get();
            leftWave = getBorderPoint(closest, normalRelativeAngle(closest.direction + closest.arc));
            rightWave = getBorderPoint(closest, normalRelativeAngle(closest.direction - closest.arc));

            p = middle(leftWave, rightWave);;
            if (getCurrentPoint().distance(safePosition) > leftWave.distance(rightWave)/1.8 ||
                    safePosition.distance(getCurrentPoint()) > leftWave.distance(rightWave)/1.8)
                waveSafePosition = safePosition;
            else {
                waveSafePosition = aliveCount > 1 ? safePosition :
                        getCurrentPoint().distance(leftWave) > getCurrentPoint().distance(rightWave ) ? rightWave: leftWave;
            }
        } else {
            waveSafePosition = safePosition;
            leftWave = rightWave = null;
        }
    }

    private void updateLeftRightEnemies() {
        List<Enemy> sEnemies = enemies.values().stream().filter(e -> e.alive).collect(Collectors.toList());
        sEnemies.sort(new Comparator<Enemy>() {
            @Override
            public int compare(final Enemy e1, final Enemy e2) {
                if (e1.angle < e2.angle)
                    return -1;
                if (e1.angle > e2.angle)
                    return 1;
                return 0;
            }
        });

        Enemy prev = mostRight = sEnemies.get(0);
        mostLeft = sEnemies.get(sEnemies.size() - 1);
        double ba = abs(normalRelativeAngle(mostLeft.angle) - normalRelativeAngle(mostRight.angle));
        for (Enemy enemy : sEnemies) {
            final double a = enemy.angle - prev.angle;
            if (a > ba) {
                mostRight = enemy;
                mostLeft = prev;
                ba = a;
            }
            prev = enemy;
        }
    }

    private void moveEnemy(Enemy e, long now) {
        if (e.lastUpdate >= now || e.energy == 0)
            return;
        long time = now - e.lastUpdate;
        for (long i = 0; i < time; i++) {
            e.velocity = checkMinMax(e.velocity + e.accel, e.vMin, e.vMax);
            e.direction += min(abs(target.rotationRate), getTurnRateRadians(e.velocity)) * signum(e.rotationRate);
            try {
                double x = ensureXInBatleField(e.x + e.velocity * cos(e.direction));
                double y = ensureYInBatleField(e.y + e.velocity * sin(e.direction));
                e.x = x;
                e.y = y;
            } catch (Exception ex){
                // HitWall
            }
        }
        e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        e.lastUpdate = now;
    }

    private void updatePositions() {
        long now = getTime();
        double totalEnergy = getEmenmiesEnergy();
        double x = 0;
        double y = 0;
        double d = Double.POSITIVE_INFINITY;

        for (Enemy e : enemies.values()) {
            moveEnemy(e, now);

            x += e.x * e.energy;
            y += e.y * e.energy;

            double od = getCurrentPoint().distance(e);
            if (od < d && e.alive) {
                // target closet alive opponent
                target = e;
                d = od;
            }
            e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        }

        if (totalEnergy == 0)
            return;

        unsafePosition = new Point.Double(x / totalEnergy, y / totalEnergy);
        safePosition = getOppositeFarPoint(unsafePosition);
    }

    private double getEmenmiesEnergy() {
        double sum = 0;
        for (Enemy o : enemies.values())
            sum += o.energy;
        return sum;
    }

    private double getTurn2Safe() {
        if (safePosition == null)
            return 0;

        double sa = getAngle(getCurrentPoint(), waveSafePosition);
        double ra = trigoAngle(getHeadingRadians());

        if (abs(normalRelativeAngle(sa - ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa - ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }

    private double getSafeAhead() {
        if (safePosition == null)
            return 0;

        return forward * waveSafePosition.distance(getCurrentPoint());
    }

    private double fireTargetIfPossible() {
        if (targetPred == null || getGunHeat() > 0 || target.alive == false)
            return 0;

        if (getCurrentPoint().distance(targetPred) * sin(abs(getGunTurnRemainingRadians())) > FIRE_TOLERANCE)
            return 0;

        return fire;
    }


    private double getTurn2Targert() {
        if (target == null)
            return 0;

        targetPred = clonePoint(target);
        targetPreds = new ArrayList<>();

        if (target.energy > 0) {
            AimingData c = circularGunner.aim(target);
            AimingData nn = nearestNeighborGunner.aim(target);

            if (c.getConfidence() >= nn.getConfidence()) {
                targetPred = c.getFiringPosition();
                targetPreds = c.getExpectedMoves();
                fire = c.getFirePower();

            } else {
                targetPred = nn.getFiringPosition();
                targetPreds = nn.getExpectedMoves();
                fire = nn.getFirePower();

            }
        } else
            fire = MIN_BULLET_POWER;

        double ga = trigoAngle(getGunHeadingRadians());
        double ta = getAngle(getCurrentPoint(), targetPred);

        if (abs(ta - ga) <= PI) {
            return ta - ga;
        }

        return ga - ta;
    }

    public long aliveCount(){
        return enemies.values().stream().filter(e -> e.alive).count();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    private Point.Double getBorderPoint(Point.Double p, double angle) {
        double a = normalRelativeAngle(angle);
        if (a == 0)
            return new Point.Double(getBattleFieldWidth() - BORDER_OFFSET, p.y);
        if (a == PI / 2)
            return new Point.Double(p.x, getBattleFieldHeight() - BORDER_OFFSET);
        if (abs(a) == PI)
            return new Point.Double(BORDER_OFFSET, p.y);
        if (a == -PI/2)
            return new Point.Double(p.y, BORDER_OFFSET);

        double dx;
        double dy;

        if (0 < a && a < PI / 2) {
            dx = (getBattleFieldWidth() - BORDER_OFFSET - p.x) / cos(a);
            dy = (getBattleFieldHeight() - BORDER_OFFSET - p.y) / sin(a);
        } else if (PI / 2 < a && a < PI) {
            dx = (p.x - BORDER_OFFSET) / cos(a);
            dy = (getBattleFieldHeight() - BORDER_OFFSET - p.y) / sin(a);
        } else if ((PI < a && a < 3 * PI / 2) || (-PI < a && a < -PI / 2)) {
            dx = (p.x - BORDER_OFFSET) / cos(a);
            dy = (p.y - BORDER_OFFSET) / sin(a);
        } else {
            dx = (getBattleFieldWidth() - BORDER_OFFSET - p.x) / cos(a);
            dy = (p.y - BORDER_OFFSET) / sin(a);
        }

        // out.printf("a=%.02f dx=%.02f dy=%.02f\n", a, dx ,dy);

        double d = abs((abs(dx) >= abs(dy)) ? dy : dx);
        return new Point.Double(p.x + d * cos(a), p.y + d * sin(a));
    }

    public Point.Double getOppositeFarPoint(Point.Double point) {
        double a = oppositeAngle(getAngle(BATTLE_FIELD_CENTER, point));
        double c = acos(getBattleFieldHeight() / getBattleFieldWidth());
        double x, y;
        if (a < c && a > -c) { // Right
            x = getBattleFieldWidth() - BORDER_OFFSET;
            y = (sin(a) * (getBattleFieldHeight() - 2 * BORDER_OFFSET) + getBattleFieldHeight()) / 2;
        } else if (a > c && a < PI - c) { // top
            x = (cos(a) * (getBattleFieldWidth() - 2 * BORDER_OFFSET) + getBattleFieldWidth()) / 2;
            y = getBattleFieldHeight() - BORDER_OFFSET;
        } else if (a < -c && a >= -PI + c) { // bottom
            x = (cos(a) * (getBattleFieldWidth() - 2 * BORDER_OFFSET) + getBattleFieldWidth()) / 2;
            y = BORDER_OFFSET;
        } else { // left
            x = BORDER_OFFSET;
            y = (sin(a) * getBattleFieldHeight() + getBattleFieldHeight()) / 2;
        }
        return new Point.Double(x, y);
    }

    public Point.Double getCurrentPoint() {
        return new Point.Double(getX(), getY());
    }

    public double ensureXInBatleField(double x) throws Exception {
        double nx = max(TANK_SIZE / 2, min(getBattleFieldWidth() - TANK_SIZE / 2, x));
        if (abs(nx - x) > 2) throw new Exception("hit wall x");
        return nx;
    }

    public double ensureYInBatleField(double y) throws Exception {
        double ny = max(TANK_SIZE / 2, min(getBattleFieldHeight() - TANK_SIZE / 2, y));
        if (abs(ny - y) > 2) throw new Exception("hit wall y");
        return ny;
    }

    public double wallDistance(Point.Double p) {
        return min(p.x ,BATTLE_FIELD_CENTER.x*2-p.x)+
               min(p.y, BATTLE_FIELD_CENTER.y*2-p.y);
    }
}