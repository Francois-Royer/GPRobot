package tankbase;

import tankbase.gun.*;
import robocode.Event;
import robocode.*;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static tankbase.TankUtils.*;
import static tankbase.TankUtils.drawAimCircle;
import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;

public class TankBase extends AdvancedRobot implements ITank {
    public static double TANK_SIZE = 36;
    public static int TANK_SIZE_INT = (int) TANK_SIZE;
    public static double FIRE_TOLERANCE = TANK_SIZE / 3;
    public static int FIELD_WIDTH;
    public static int FIELD_HEIGHT;
    public static Point.Double BATTLE_FIELD_CENTER;
    public static long FIRE_AGAIN_MIN_TIME;
    public static double DISTANCE_MAX;
    public static FireStat gpStat;
    private static Map<String, Gunner> gunners = new HashMap<>();
    private static List<Gunner> allGunners = new ArrayList<>();
    private static HeadOnGunner headOnGunner = null;
    private ArrayList<AimingData> aimLog = new ArrayList<>();
    private ArrayList<AimingData> turnAimDatas = new ArrayList<>();
    private static Map<String, Enemy> enemies = new HashMap<>();
    private ArrayList<Wave> waves = new ArrayList<>();
    private ArrayList<Shell> shells = new ArrayList<>();
    public double BORDER_OFFSET = TANK_SIZE * 7 / 8;
    public double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS / 3;
    public static double DANGER_DISTANCE_MAX;
    public static int DANGER_WIDTH;
    public  static int DANGER_HEIGHT;
    public  static int DANGER_SCALE = TANK_SIZE_INT/4;
    public  static double MAX_DANGER_RADIUS = TANK_SIZE * 2 / DANGER_SCALE;
    public static double GUN_COOLING_RATE=0.1;
    public int aimingMoveLogSize;
    public int moveLogMaxSize;
    public int aliveCount;
    public int enemyCount;
    public Point.Double safePosition;
    public Point.Double unSafePosition;
    public double scandirection = 1;
    public double forward = 1;
    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double fire = 0;


    public Enemy prevTarget = null;
    public Enemy target;
    public Enemy mostLeft;
    public Enemy mostRight;
    public AimingData aimingData = null;
    public long lastFireTime = -1;
    public long now;
    public boolean defenseFire = false;
    double[][] dangerMap;
    double[][] cornerMap;
    boolean drawWave = false;
    boolean drawDanger = false;
    boolean drawPoint = false;
    boolean drawAiming = true;
    boolean drawShell = true;
    boolean drawEnemy = true;
    static private RobotCache robotCache;

    double turnRate=0;
    double accel=0;

    public TankBase() {
        super();
        setupGunners();
        gpStat = new FireStat();
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic

    // GP Robot overide this method
    public void doGP() {

    }

    public void doTurn() {
        now = getTime();
        updateRobotCache();

        updatePositions();
        updateWaves();
        updateShells();
        updateDangerMap();
        computeSafePosition();

        int oc = getOthers();
        if (aliveCount() == oc && oc > 0) {
            long lastupdateDelta = updateLeftRightEnemies();
            if (lastupdateDelta > 5) turnRadarLeft = 2*PI;
            else {
                double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
                turnRadarLeft = scanLeftRight(ra, mostLeft.getAngle(), mostRight.getAngle());
            }

        } else
            turnRadarLeft = 2 * PI;

        turnLeft = getTurn2Safe();
        ahead = getSafeAhead();
        turnGunLeft = getTurn2Targert();
        fire = (aimingData == null) ? 0 : aimingData.getFirePower();

        if (aimingData != null) {
            doGP();
        }

        robotSetActions();
        if (target != null)
            prevTarget = target;
        defenseFire = false;
    }

    public List<Wave> getWave(String targetname) {
        return waves.stream().filter(w -> w.target.getName() == targetname).collect(Collectors.toList());
    }

    private void robotSetActions() {
        setTurnRadarLeftRadians(turnRadarLeft);
        setTurnLeftRadians(turnLeft);
        setAhead(ahead);
        setTurnGunLeftRadians(turnGunLeft);
        fireTargetIfPossible(fire);
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

    private void updateShells() {
        List<Shell> newShells = shells.stream().filter(vs -> {
            Point.Double p = vs.getPosition(now);
            AimingData aimingData = vs.getAimingData();
            if (pointInBattleField(p)) {
                ITank target = vs.getTarget();
                Point.Double o = vs.getPosition(now + 1);

                boolean hit = collisionCercleSeg(target.getPosition(), TANK_SIZE / 2, o, p);
                if (hit)
                    vs.getGunner().getEnemyRoundFireStat(aimingData.getTarget()).hit();

                return !hit;
            } else if (!vs.getTarget().isAlive())
                vs.getGunner().getEnemyRoundFireStat(aimingData.getTarget()).unFire();

            return false;
        }).collect(Collectors.toList());

        shells = new ArrayList<>(newShells);
    }

    private void updateWaves() {
        List<Wave> newWaves = waves.stream().filter(w ->
                w.getDistance(now) < w.distance(getPosition())+TANK_SIZE/2 ).collect(Collectors.toList());
        waves = new ArrayList<>(newWaves);
    }

    private void updateDangerMap() {
        conersDanger();
        int maxHitMe = getEmenmiesMaxHitMe();

        for (int x=0; x<DANGER_WIDTH; x++)
            for (int y=0; y<DANGER_HEIGHT; y++) {
                double danger = dangerMap[x][y];
                for (Enemy enemy : enemies.values())
                    if (enemy.isAlive() && enemy.getPrevUpdate() > 0 && danger<1)
                        danger = max(danger, enemy.getDanger(x, y, maxHitMe));
                for (Wave wave : waves)
                    if (danger<1)
                        danger = max(danger, wave.getDanger(x, y, now));
                dangerMap[x][y]=danger;
            }
    }

    private void conersDanger() {
        for (int x = 0; x < DANGER_WIDTH; x++)
            System.arraycopy(cornerMap[x], 0, dangerMap[x], 0, DANGER_HEIGHT);
    }

    public static double ROTATION_FACTOR=2.4*PI;
    public static double RADIUS_STEP=.5;
    private void computeCornerDangerMap() {
        double rmax = DANGER_DISTANCE_MAX / 2;
        for (double r = DANGER_HEIGHT / 2; r < rmax; r += RADIUS_STEP) {
            double danger = range(r, DANGER_HEIGHT / 2, rmax, 0, 1);
            int num = (int) (r * ROTATION_FACTOR);
            for (int i = 0; i < num; i++) {
                double a = i * 2 * PI / num;
                int h = DANGER_WIDTH / 2 + (int) (r * cos(a) * DANGER_WIDTH / DANGER_HEIGHT);
                int v = DANGER_HEIGHT / 2 + (int) (r * sin(a));
                if (h >= 0 && v >= 0 && h < DANGER_WIDTH && v < DANGER_HEIGHT)
                    cornerMap[h][v] = danger;
            }
        }
    }
    private long updateLeftRightEnemies() {
        long lastUpdateDelta=0;
        List<Enemy> sEnemies = enemies.values().stream().filter(e -> e.isAlive()).collect(Collectors.toList());
        sEnemies.sort(new Comparator<Enemy>() {
            @Override
            public int compare(final Enemy e1, final Enemy e2) {
                double a1 = e1.getAngle();
                double a2 = e2.getAngle();
                if (a1 < a2)
                    return -1;
                if (a1 > a2)
                    return 1;
                return 0;
            }
        });

        Enemy prev = mostRight = sEnemies.get(0);
        mostLeft = sEnemies.get(sEnemies.size() - 1);
        double ba = mostLeft.getAngle() - mostRight.getAngle();
        if (ba > PI) ba = 2 * PI - ba;
        for (Enemy enemy : sEnemies) {
            final double a = enemy.getAngle() - prev.getAngle();
            if (a > ba) {
                mostRight = enemy;
                mostLeft = prev;
                ba = a;
            }
            prev = enemy;
            lastUpdateDelta = max(lastUpdateDelta,enemy.getLastUpdateDelta());
        }
        return lastUpdateDelta;
    }

    private void moveEnemy(Enemy enemy, long now) {
        if (enemy.getLastUpdate() >= now || enemy.getEnergy() == 0)
            return;
        enemy.move(now - enemy.getLastUpdate());
    }

    private void updatePositions() {
        double minDistance = Double.POSITIVE_INFINITY;

        Enemy newTarget = null;
        for (Enemy e : enemies.values()) {
            moveEnemy(e, now);

            double distance = getPosition().distance(e);
            if (e.isAlive() && (distance < minDistance|| e.getEnergy()==0) &&
                    e.getLastUpdateDelta() < 3 && e.getFEnergy()>=0) {
                // target closest alive opponent
                newTarget = e;
                minDistance = distance;
            }
        }
        if (newTarget != null) {
            if (newTarget.getEnergy() != 0 && prevTarget != null && prevTarget.isAlive() && prevTarget.getFEnergy()>=0 &&
                target.distance(getPosition()) < prevTarget.distance(getPosition())*2/3)
                target = prevTarget;
            else
                target = newTarget;
        }
    }

    private double getEmenmiesEnergy() {
        return enemies.values().stream().mapToDouble(e -> e.getEnergy()).sum();
    }

    public int getEmenmiesMaxHitMe() {
        int max = 0;
        for (Enemy enemy : enemies.values()) if (enemy.isAlive()) max = max(max, enemy.getHitMe());
        return max;
    }

    private void computeSafePosition() {
        computeSafePositionDangerMap();
    }

    private void computeSafePositionBaryCentric() {
        double x = 0;
        double y = 0;
        double div = getEmenmiesEnergy();

        for (Enemy e : enemies.values()) {
            double nrj = e.getEnergy();
            if (nrj > 0) {
                x += e.x * nrj;
                y += e.y * nrj;
            }
        }
        unSafePosition = new Point.Double(x / div, y / div);

        if (waves.size() > 0) {
            x = 0;
            y = 0;
            div = 0;
            for (Wave w : waves) {
                double power = w.getPower();
                div += power;
                Point.Double p = w.getPosition(now);
                x += p.x * power;
                y += p.y * power;
            }

            unSafePosition = new Point.Double(
                    (unSafePosition.getX() * aliveCount + x / div * waves.size()) / (aliveCount + waves.size()),
                    (unSafePosition.getY() * aliveCount + y / div * waves.size()) / (aliveCount + waves.size()));
        }
        safePosition = getOppositeFarPoint(unSafePosition);
    }

    private void computeSafePositionDangerMap() {
        int dist = (int) MAX_VELOCITY * 15 / DANGER_SCALE;

        Point gp = new Point((int) getX() / DANGER_SCALE, (int) getY() / DANGER_SCALE);
        List<Point> points = TankUtils.listClosePoint(gp, dist, DANGER_WIDTH, DANGER_HEIGHT);
        //List<Point> points = GPUtils.listCirclePoint(gp, dist, DANGER_WIDTH, DANGER_HEIGHT);
        double danger = Double.MAX_VALUE;
        for (Point p : points) {
            double d = TankUtils.computeMoveDanger(gp, p, dangerMap);
            if (d < danger) {
                danger = d;
                double x = max(BORDER_OFFSET, min(FIELD_WIDTH - BORDER_OFFSET, p.getX() * DANGER_SCALE + DANGER_SCALE / 2));
                double y = max(BORDER_OFFSET, min(FIELD_HEIGHT - BORDER_OFFSET, p.getY() * DANGER_SCALE + DANGER_SCALE / 2));
                safePosition = new Point.Double(x, y);
            }
        }

        Point sp = getMaxPoint(dangerMap);
        double x = sp.getX() * DANGER_SCALE + DANGER_SCALE / 2;
        double y = sp.getY() * DANGER_SCALE + DANGER_SCALE / 2;
        unSafePosition = new Point.Double(x, y);
    }

    private double getTurn2Safe() {
        if (safePosition == null)
            return 0;

        double sa = getPointAngle(getPosition(), safePosition);
        double ra = getHeadingRadians();

        if (abs(normalRelativeAngle(sa - ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa - ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }

    private double getSafeAhead() {
        if (safePosition == null || getPosition() == null)
            return 0;

        return forward * safePosition.distance(getPosition());
    }

    private void fireTargetIfPossible(double fire) {
        if (getGunHeat() > 0 || getEnergy() == 0 || fire == 0 || target == null || aimingData == null ||
                !target.isAlive() || target.getFEnergy() < 0)
            return;

        if (getPosition().distance(aimingData.getFiringPosition()) * abs(tan(getGunTurnRemainingRadians()))
                > FIRE_TOLERANCE) {
            //out.printf("Fire on %s rejected by tolerance, turn remaining=%.0f , %.0f\n", target.getName(), getGunTurnRemaining());
            return;
        }

        setFire(fire);
        gpStat.fire();
        target.addFEnergy(-getBulletDamage(fire));
        lastFireTime = now;
        aimingData.setDirection(getGunHeadingRadians());
        //out.printf("%s fire on %s, damage=%.02f, power=%.02f\n", aimingData.getGunner().getName(), target.getName(), getBulletDamage(fire), fire);
        aimLog.add(aimingData);
        fireVirtualShell();
    }

    private void fireVirtualShell() {
        turnAimDatas.forEach(ad -> {
            ad.getGunner().getEnemyRoundFireStat(aimingData.getTarget()).fire();
            shells.add(new Shell(getPosition(), ad, now));
        });
    }

    private double getTurn2Targert() {
        aimingData = null;

        if (target == null)
            return 0;

        if (getGunHeat() > 0) {
            fire = 0;
            return computeTurnGun2TargetNextPos(this, target);
        }

        turnAimDatas = new ArrayList<>();

        gunners.values().forEach(gunner -> {
            AimingData temp = gunner.aim(target);
            if (temp != null) {
                turnAimDatas.add(temp);
                if (aimingData == null || temp.hitRate() > aimingData.hitRate())
                    aimingData = temp;
            }
        });

        if (target.getEnergy() == 0 || defenseFire) {
            aimingData = headOnGunner.aim(target);
            turnAimDatas.add(aimingData);
        }

        if (aimingData != null) {
            fire = aimingData.getFirePower();
            return computeTurnGun2Target(this, aimingData.getFiringPosition());
        }

        fire = 0;
        return computeTurnGun2TargetNextPos(this, target);
    }

    public long aliveCount() {
        return enemies.values().stream().filter(e -> e.isAlive()).count();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // AdvancedRobot overrides
    @Override
    public void run() {
        FIELD_WIDTH = (int) getBattleFieldWidth();
        FIELD_HEIGHT = (int) getBattleFieldHeight();
        BATTLE_FIELD_CENTER = new Point.Double(FIELD_WIDTH / 2, FIELD_HEIGHT / 2);
        DANGER_WIDTH = (FIELD_WIDTH + DANGER_SCALE) / DANGER_SCALE;
        DANGER_HEIGHT = (FIELD_HEIGHT + DANGER_SCALE) / DANGER_SCALE;
        GUN_COOLING_RATE = getGunCoolingRate();
        dangerMap = new double[DANGER_WIDTH][DANGER_HEIGHT];
        cornerMap = new double[DANGER_WIDTH][DANGER_HEIGHT];
        DISTANCE_MAX = new Point2D.Double(0, 0).distance(FIELD_WIDTH, FIELD_HEIGHT);
        DANGER_DISTANCE_MAX = DISTANCE_MAX / DANGER_SCALE;
        computeCornerDangerMap();

        aimingMoveLogSize = (int) (DISTANCE_MAX / getBulletSpeed(MAX_BULLET_POWER) + 1);
        moveLogMaxSize = aimingMoveLogSize;
        FIRE_AGAIN_MIN_TIME = (long) (Rules.getGunHeat(MIN_BULLET_POWER) / getGunCoolingRate());

        enemyCount = aliveCount = super.getOthers();

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red, Color.blue, Color.green);

        enemies.values().stream().forEach(Enemy::die);
        robotCache = null;

        while (true) {
            doTurn();
            execute();
        }
    }

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
            case 'p':
                drawPoint = !drawPoint;
                break;
        }
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        onEvent(e);
        String name = e.getName();
        Enemy enemy = enemies.get(name);

        if (enemy == null)
            enemies.put(name, new Enemy(e, name, this, waves));
        else
            enemy.update(e, this, waves);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        onEvent(bhe);
        gpStat.hit();
        Enemy e = enemies.get(bhe.getName());
        if (e != null) {
            e.setEnergy(bhe.getEnergy(), true);
            AimingData ad = getAimingDataByAngle(trigoAngle(bhe.getBullet().getHeadingRadians()));
            if (ad != null) {
                ad.getTarget().addFEnergy(getBulletDamage(bhe.getBullet().getPower()));
                aimLog.remove(ad);
            }
        }
    }

    @Override
    public void onBulletMissed(BulletMissedEvent bme) {
        onEvent(bme);
        AimingData ad = getAimingDataByAngle(trigoAngle(bme.getBullet().getHeadingRadians()));
        if (ad != null) {
            ad.getTarget().addFEnergy(getBulletDamage(bme.getBullet().getPower()));
            aimLog.remove(ad);
        }
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent bhbe) {
        onEvent(bhbe);
        gpStat.hitByBullet();
        Bullet b = bhbe.getHitBullet();
        Point.Double p = new Point.Double(b.getX(), b.getY());
        Optional<Wave> ow = waves.stream().filter(w -> w.target.getName() == b.getName())
                .min(new WaveComparator(p, now));
        ow.ifPresent(wave -> waves.remove(wave));

        AimingData ad = getAimingDataByAngle(trigoAngle(bhbe.getBullet().getHeadingRadians()));
        if (ad != null) {
            aimLog.remove(ad);
            ad.getTarget().addFEnergy(getBulletDamage(bhbe.getBullet().getPower()));

            ad.getGunner().getEnemyRoundFireStat(ad.getTarget()).hitByBullet();
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        Enemy e = enemies.get(hbbe.getName());
        if (e == null) return;

        onEvent(hbbe);
        e.setEnergy(e.getEnergy() + getBulletHitBonus(hbbe.getPower()), true);
        e.hitMe();

        Optional<Wave> ow = waves.stream().filter(w -> w.target.getName() == hbbe.getName())
                .min(new WaveComparator(getPosition(), now));

        ow.ifPresent(wave -> {
            waves.remove(wave);
        });
    }

    @Override
    public void onDeath(DeathEvent event) {
        onEvent(event);

        resetRoundDataPrintStat();
    }

    @Override
    public void onWin(WinEvent event) {
        onEvent(event);

        resetRoundDataPrintStat();
    }

    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        onEvent(event);
        Enemy enemy = enemies.get(event.getName());
        if (enemy != null) // If robot die before we scan it
            enemy.die();
        aliveCount--;
    }

    @Override
    public void onPaint(Graphics2D g2D) {
        if (safePosition != null && drawPoint) {
            drawFillCircle(g2D, Color.GREEN, safePosition, 10);
            drawFillCircle(g2D, Color.RED, unSafePosition, 10);
        }

        if (drawEnemy) {
            for (Enemy e : enemies.values())
                if (e.isAlive()) {
                    if (e == target)
                        drawAimCircle(g2D, Color.CYAN, e, TANK_SIZE_INT);
                    else if (e == mostLeft)
                        drawCircle(g2D, Color.GREEN, e, TANK_SIZE_INT);
                    else if (e == mostRight)
                        drawCircle(g2D, Color.RED, e, TANK_SIZE_INT);
                    else
                        drawCircle(g2D, Color.PINK, e, TANK_SIZE_INT);
                }
            drawCircle(g2D, Color.green, getPosition(), TANK_SIZE_INT);
        }

        if (aimingData != null && drawAiming) {
            for (Point.Double p : aimingData.getExpectedMoves())
                drawFillCircle(g2D, Color.yellow, p, 5);

            drawAimCircle(g2D, Color.CYAN, aimingData.getFiringPosition(), 20);
        }

        /*if (mostLeft != null && mostRight != null) {
            drawCircle(g2D, Color.RED, mostLeft, TANK_SIZE * 4 / 3);
            drawCircle(g2D, Color.GREEN, mostRight, TANK_SIZE * 4 / 3);
        }*/

        if (drawWave) {
            for (Wave w : waves)
                drawWave(g2D, w.kdangle ? Color.GREEN : Color.ORANGE, w, now);
        }

        if (drawShell) {
            for (Shell vs : shells) {
                drawFillCircle(g2D, vs.getGunner().getColor(), vs.getPosition(now), 5);
                /*Point2D.Double p = vs.getPosition(now + (int) (DISTANCE_MAX / vs.getVelocity()));
                g2D.drawLine((int) vs.getX(), (int) vs.getY(), (int) p.getX(), (int) p.getY());*/
            }
        }

        if (drawDanger)
            drawDangerMap(g2D);
    }

    private void onEvent(Event e) {
        now = e.getTime();
        updateRobotCache();
    }

    private void drawDangerMap(Graphics2D g2D) {
        Color dc = Color.RED;
        int r = dc.getRed();
        int g = dc.getGreen();
        int b = dc.getBlue();
        BufferedImage img = new BufferedImage(FIELD_WIDTH, FIELD_HEIGHT, TYPE_INT_RGB);
        for (int y = 0; y < DANGER_HEIGHT; y++)
            for (int x = 0; x < DANGER_WIDTH; x++) {
                int alpha = (int) range(dangerMap[x][y], 0, 3, 0, 100);
                Color c = new Color(r, g, b, alpha);
                g2D.setColor(c);
                g2D.fillRect(x * DANGER_SCALE, y * DANGER_SCALE, DANGER_SCALE, DANGER_SCALE);
            }

    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d\n", event.getSkippedTurn(), event.getPriority());
    }

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Try escape backward and turn PI/2
        //forward *= -1;
        //setTurnLeftRadians(PI / 2);
        //setAhead(forward * TANK_SIZE * 2);
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Static methods
    static public double ensureXInBatleField(double x) throws Exception {
        return ensureXInBatleField(x, 2.1);
    }

    static public double ensureYInBatleField(double y) throws Exception {
        return ensureYInBatleField(y, 2.1);
    }

    static public double ensureXInBatleField(double x, double d) throws Exception {
        if (x < TANK_SIZE / d) throw new Exception("hit wall x");
        if (x > FIELD_WIDTH - TANK_SIZE / d) throw new Exception("hit wall x");
        return x;
    }

    static public double ensureYInBatleField(double y, double d) throws Exception {
        if (y < TANK_SIZE / d) throw new Exception("hit wall y");
        if (y > FIELD_HEIGHT - TANK_SIZE / d) throw new Exception("hit wall y");
        return y;
    }

    private void resetRoundDataPrintStat() {
        shells.stream().forEach(shell -> {
            shell.getAimingData().getGunner().getEnemyRoundFireStat(shell.getTarget()).unFire();
        });
        printStat();
        aimLog.clear();
        waves.clear();
        shells.clear();
        turnAimDatas.clear();
        mostLeft = mostRight = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    private void resetRoundStat() {
        gunners.values().forEach(gunner -> gunner.resetRoundStat());
    }

    private void printStat() {
        allGunners.forEach(gunner -> {
            out.printf("==== %s ====\n", gunner.getName());
            enemies.values().forEach(enemy -> {
                FireStat fs = gunner.getEnemyRoundFireStat(enemy);
                out.printf("    %s hitrate = %.0f%% / %d\n", enemy.getName(), fs.getHitRate() * 100, fs.getFireCount());
            });
        });
        resetRoundStat();
    }

    public Point.Double getOppositeFarPoint(Point.Double point) {
        double a = oppositeAngle(getPointAngle(BATTLE_FIELD_CENTER, point));
        double c = acos(FIELD_HEIGHT / FIELD_WIDTH);
        double x, y;
        if (a < c && a > -c) { // Right
            x = FIELD_WIDTH - BORDER_OFFSET;
            y = (sin(a) * (FIELD_HEIGHT - 2 * BORDER_OFFSET) + FIELD_HEIGHT) / 2;
        } else if (a > c && a < PI - c) { // top
            x = (cos(a) * (FIELD_WIDTH - 2 * BORDER_OFFSET) + FIELD_WIDTH) / 2;
            y = FIELD_HEIGHT - BORDER_OFFSET;
        } else if (a < -c && a >= -PI + c) { // bottom
            x = (cos(a) * (FIELD_WIDTH - 2 * BORDER_OFFSET) + FIELD_WIDTH) / 2;
            y = BORDER_OFFSET;
        } else { // left
            x = BORDER_OFFSET;
            y = (sin(a) * FIELD_HEIGHT + FIELD_HEIGHT) / 2;
        }
        return new Point.Double(x, y);
    }

    public Point.Double getNextPosition() {
        double a = getHeadingRadians();
        a += min(abs(getTurnRemaining()), getTurnRateRadians(getVelocity())) * signum(getTurnRemaining());
        return new Point.Double(getX() + getVelocity() * cos(a), getY() + getVelocity() * sin(a));
    }

    public boolean isDefenseFire() {
        return defenseFire;
    }

    private void setupGunners() {
        if (gunners.values().size() == 0) {
            headOnGunner = new HeadOnGunner(this);
            allGunners.add(headOnGunner);
            putGunner(new CircularGunner(this));
            putGunner(new PatternGunner(this));
            putGunner(new SurferGunner(this));
        }
    }

    private void putGunner(Gunner gunner) {
        gunners.put(gunner.getName(), gunner);
        allGunners.add(gunner);
    }

    private AimingData getAimingDataByAngle(double angle) {
        Optional<AimingData> opt = aimLog.stream().filter(aimingData -> aimingData.getDirection() == angle).findFirst();

        return opt.isPresent() ? opt.get() : null;
    }

    @Override
    public List<AimingData> getAimingLog(String name) {
        return aimLog.stream().filter(aimingData -> aimingData.getTarget().getName().equals(name))
                .collect(Collectors.toList());
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

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Robot values caching to avoid too much call to .getXX()
    class RobotCache {
        double x;
        double y;
        double headingRadians;
        double gunHeadingRadians;
        double turnRemaining;
        double velocity;
        double gunHeat;
        double energy;
        int others;

        long date;

        public RobotCache(double x, double y, double headingRadians, double gunHeadingRadians, double turnRemaining, double velocity,
                          double gunHeat, double energy, int others, long date) {
            this.x = x;
            this.y = y;
            this.headingRadians = headingRadians;
            this.gunHeadingRadians = gunHeadingRadians;
            this.turnRemaining = turnRemaining;
            this.velocity = velocity;
            this.gunHeat = gunHeat;
            this.energy = energy;
            this.others = others;
            this.date = date;
        }

        public double getX() {
            return x;
        }

        public double getY() {
            return y;
        }

        public double getHeadingRadians() {
            return headingRadians;
        }

        public double getGunHeadingRadians() {
            return gunHeadingRadians;
        }

        public double getTurnRemaining() {
            return turnRemaining;
        }

        public double getVelocity() {
            return velocity;
        }

        public double getGunHeat() {
            return gunHeat;
        }

        public double getEnergy() {
            return energy;
        }

        public int getOthers() {
            return others;
        }

        public long getDate() { return date; }

    }
    private void updateRobotCache() {
        if (robotCache == null || robotCache.date<now) {
            RobotCache prev = robotCache;
            robotCache = new RobotCache(
                    super.getX(), super.getY(),
                    trigoAngle(super.getHeadingRadians()),trigoAngle(super.getGunHeadingRadians()),
                    super.getTurnRemaining(), super.getVelocity(),
                    super.getGunHeat(), super.getEnergy(), super.getOthers(), now);

            if (prev != null) {
                turnRate = (robotCache.getTurnRemaining()-prev.getHeadingRadians())/(now- prev.date);
                accel = (robotCache.getVelocity()-prev.getVelocity())/(now- prev.date);
            }
        }
    }

    @Override
    public double getX() {
        return robotCache.getX();
    }

    @Override
    public double getY() {
        return robotCache.getY();
    }

    @Override
    public double getHeadingRadians() {
        return robotCache.getHeadingRadians();
    }

    @Override
    public double getGunHeadingRadians() {
        return robotCache.getGunHeadingRadians();
    }

    @Override
    public double getTurnRemaining() {
        return robotCache.getTurnRemaining();
    }

    @Override
    public double getVelocity() {
        return robotCache.getVelocity();
    }

    @Override
    public double getGunHeat() {
        return robotCache.getGunHeat();
    }

    @Override
    public double getEnergy() {
        return robotCache.getEnergy();
    }

    @Override
    public double getVMax() {
        return MAX_VELOCITY;
    }

    @Override
    public double getVMin() {
        return -MAX_VELOCITY;
    }

    @Override
    public double getTurnRate() {
        return turnRate;
    }

    @Override
    public double getAccel() {
        return accel;
    }

    @Override
    public Point.Double getPosition() {
        return new Point.Double(getX(), getY());
    }

    @Override
    public double getMovingDirection() {
        return getVelocity() >= 0 ? getHeadingRadians() : -getHeadingRadians();
    }

    @Override
    public boolean isDecelerate() {
        return isDecelerate();
    }

    @Override
    public boolean isAlive() {
        return true;
    }

    @Override
    public KdTree<List<Move>> getPatternKdTree() {
        return null;
    }

    @Override
    public KdTree<List<Move>> getSurferKdTree() {
        return null;
    }

    @Override
    public List<Move> getMoveLog() {
        return null;
    }

    @Override
    public double hit() {
        return 0;
    }

    @Override
    public void addFEnergy(double energy) {
        //NOOP
    }

    @Override
    public double getFEnergy() {
        return getEnergy();
    }

    @Override
    public int getAliveCount() {
        return aliveCount;
    }

    @Override
    public int getOthers() {
        return robotCache.getOthers();
    }
}