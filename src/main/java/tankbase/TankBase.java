package tankbase;

import robocode.Event;
import robocode.*;
import tankbase.gun.*;
import tankbase.kdtree.KdTree;

import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.awt.image.BufferedImage.TYPE_INT_RGB;
import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;
import static tankbase.TankUtils.*;

public class TankBase extends AdvancedRobot implements ITank {
    public static double TANK_SIZE = 36;
    public static int TANK_SIZE_INT = (int) TANK_SIZE;
    public static double FIRE_TOLERANCE = TANK_SIZE / 2;
    public static int FIELD_WIDTH;
    public static int FIELD_HEIGHT;
    public static Point.Double BATTLE_FIELD_CENTER;
    public static long FIRE_AGAIN_MIN_TIME;
    public static double DISTANCE_MAX;
    public static FireStat gpStat;
    private static final Map<String, Gunner> gunners = new HashMap<>();
    private static HeadOnGunner headOnGunner = null;
    private final ArrayList<Shell> aimLog = new ArrayList<>();
    private static final Map<String, Enemy> enemies = new HashMap<>();
    private ArrayList<Wave> waves = new ArrayList<>();
    private ArrayList<Shell> virtualShells = new ArrayList<>();
    public double BORDER_OFFSET = TANK_SIZE * 7 / 8;
    public double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS / 2;
    public static double DANGER_DISTANCE_MAX;
    public static int DANGER_WIDTH;
    public static int DANGER_HEIGHT;
    public static int DANGER_SCALE = TANK_SIZE_INT / 2;
    public static double MAX_DANGER_RADIUS = TANK_SIZE * 2 / DANGER_SCALE;
    public static double GUN_COOLING_RATE = 0.1;
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
    double[][] dangerMap;
    double[][] cornerMap;
    boolean drawWave = false;
    boolean drawDanger = true;
    boolean drawAiming = true;
    boolean drawShell = false;
    boolean drawEnemy = false;
    static private RobotCache robotCache;

    double turnRate = 0;
    double accel = 0;

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
        updateEnemies();
        updateWaves();
        updateShells();
        updateDangerMap();
        computeSafePosition();
        computeAiming();
        selectTarget();
        fireVirtualShell();

        turnRadarLeft = getTurnRadar();
        turnLeft = getTurn();
        ahead = getAhead();
        turnGunLeft = getTurnGun();
        fire = (aimingData == null) ? 0 : aimingData.getFirePower();

        if (aimingData != null) {
            doGP();
        }

        robotSetActions();
        if (target != null)
            prevTarget = target;
    }

    private double getTurnRadar() {
        int oc = getOthers();
        if (aliveCount() == oc && oc > 0) {
            long lastupdateDelta = updateLeftRightEnemies();
            // One enemy is not scan since 5 turns, we need to scan all around
            if (lastupdateDelta > 5) return 2 * PI;
            double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
            return scanLeftRight(ra, mostLeft.getAngle(), mostRight.getAngle());
        }
        return 2 * PI;
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
        List<Shell> newShells = virtualShells.stream().filter(vs -> {
            Point.Double p = vs.getPosition(now);
            AimingData aimingData = vs.getAimingData();
            if (pointInBattleField(p)) {
                ITank target = vs.getTarget();
                Point.Double o = vs.getPosition(now + 1);

                boolean hit = collisionCercleSeg(target.getPosition(), TANK_SIZE / 2, o, p);
                if (hit) {
                    //out.printf("%s hit %s\n", vs.getGunner().getName(), target.getName());
                    vs.getGunner().getEnemyRoundFireStat(aimingData.getTarget()).hit(Rules.getBulletDamage(vs.getAimingData().getFirePower()));
                }

                return !hit;
            } else if (!vs.getTarget().isAlive())
                vs.getGunner().getEnemyRoundFireStat(aimingData.getTarget()).unFire(vs.getAimingData().getFirePower());

            return false;
        }).collect(Collectors.toList());

        virtualShells = new ArrayList<>(newShells);
    }

    private void updateWaves() {
        List<Wave> newWaves = waves.stream().filter(w ->
                w.getDistance(now) < w.distance(getPosition()) + TANK_SIZE / 2).collect(Collectors.toList());
        waves = new ArrayList<>(newWaves);
    }

    private void updateDangerMap() {
        double maxDamageMe = getEmenmiesMaxDamageMe();
        double maxDanger = 0;
        for (int x = 0; x < DANGER_WIDTH; x++)
            for (int y = 0; y < DANGER_HEIGHT; y++) {
                double danger = 0;
                for (Enemy enemy : enemies.values())
                    if (enemy.isAlive() && enemy.getLastScan() > 0)
                        danger += enemy.getDanger(x, y, maxDamageMe);
                for (Wave wave : waves)
                        danger +=  wave.getDanger(x, y, now) / waves.size();
                maxDanger = max(danger ,maxDanger);
                dangerMap[x][y] = danger;
            }
        for (int x = 0; x < DANGER_WIDTH; x++)
            for (int y = 0; y < DANGER_HEIGHT; y++) {
                dangerMap[x][y] /= maxDanger;
                dangerMap[x][y] = max(cornerMap[x][y], dangerMap[x][y]);
            }
    }

    private void conersDanger() {
        for (int x = 0; x < DANGER_WIDTH; x++)
            System.arraycopy(cornerMap[x], 0, dangerMap[x], 0, DANGER_HEIGHT);
    }

    public static double ROTATION_FACTOR = 2.4 * PI;
    public static double RADIUS_STEP = .5;

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
        long lastUpdateDelta = 0;
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
            lastUpdateDelta = max(lastUpdateDelta, enemy.getLastUpdateDelta());
        }
        return lastUpdateDelta;
    }

    private void moveEnemy(Enemy enemy, long now) {
        if (enemy.getEnergy() == 0)
            return;
        enemy.move(1);

    }

    private void selectTarget() {
        Enemy newTarget = prevTarget;
        double minDistance = Double.POSITIVE_INFINITY;
        double prevTurnGun = 2 * PI;

        for (Enemy e : enemies.values()) {
            if (!e.isAlive()) continue;
            double distance = getPosition().distance(e);
            double heading = getPointAngle(getPosition(), e.getPosition());
            double gunTurn = heading-getGunHeadingRadians();
            if (e.getEnergy() == 0 && abs(gunTurn) < GUN_TURN_RATE_RADIANS) {
                newTarget = e;
                minDistance = distance;
                continue;
            }
            if (e.getTurnAimDatas().isEmpty() && newTarget != null) continue;
            if (distance > minDistance) continue;

            // new target should be 2/3 closer than previous to avoid target switch to often
            if (prevTarget != null && prevTarget.isAlive() && prevTarget.getFEnergy() > 0 &&
                    distance > prevTarget.distance(getPosition()) * 2 / 3 && abs(gunTurn) > GUN_TURN_RATE_RADIANS)
                continue;



            newTarget = e;
            minDistance = distance;
        }
        //out.printf("prev=%s, new=%s\n", prevTarget!= null ? prevTarget.getName(): "null",
                //newTarget!= null ? newTarget.getName():"null");

        if (newTarget != null)
            target = newTarget;

        if (target != null) {
            if (target.getEnergy() == 0)
                aimingData = headOnGunner.aim(target);
            else
                aimingData = target.getBestAiming(getNextPosition(), getGunHeadingRadians());

            /*if (aimingData != null)
                out.printf("aiming: %s->%s at x=%f y=%f \n",
                        aimingData.getGunner().getName(), aimingData.getTarget().getName(),
                        aimingData.getFiringPosition().getX(),
                        aimingData.getFiringPosition().getY());
            else
                out.println("no aiming data");
            */
        }
    }

    private void computeAiming() {
        for (Enemy target : enemies.values()) {
            if (target.isAlive())
                computeAimingTarget(target);
        }
    }

    private void computeAimingTarget(Enemy target) {
        ArrayList<AimingData> aimings = new ArrayList<>();
        for (Gunner gunner : gunners.values()) {
            AimingData ad = gunner.aim(target);
            if (ad != null) {
                aimings.add(ad);
            }
        }
        target.setTurnAimDatas(aimings);
    }

    private void updateEnemies() {
        for (Enemy e : enemies.values()) {
            moveEnemy(e, now);
        }
    }

    private double getEmenmiesEnergy() {
        return enemies.values().stream().mapToDouble(e -> e.getEnergy()).sum();
    }

    public double getEmenmiesMaxDamageMe() {
        double max = 0;
        for (Enemy enemy : enemies.values()) if (enemy.isAlive()) max = max(max, enemy.getDamageMe());
        return max;
    }

    private void computeSafePosition() {
        int dist = min(DANGER_WIDTH, DANGER_HEIGHT) / 3;

        Point gp = new Point((int) getX() / DANGER_SCALE, (int) getY() / DANGER_SCALE);
        List<Point> points = TankUtils.listClosePoint(gp, dist, DANGER_WIDTH, DANGER_HEIGHT);
        double danger = Double.MAX_VALUE;
        for (Point p : points) {
            double d = TankUtils.computeMoveDanger(gp, p, dangerMap);
            double x = max(BORDER_OFFSET, min(FIELD_WIDTH - BORDER_OFFSET, p.getX() * DANGER_SCALE + DANGER_SCALE / 2));
            double y = max(BORDER_OFFSET, min(FIELD_HEIGHT - BORDER_OFFSET, p.getY() * DANGER_SCALE + DANGER_SCALE / 2));
            Point2D.Double position = new Point.Double(x, y);
            for (Enemy e : enemies.values())
                if (e.isAlive() && collisionCercleSeg(e.getPosition(), TANK_SIZE, getPosition(), position))
                    d += 100;
            if (d < danger) {
                danger = d;
                safePosition = position;
            }
        }
        //out.printf("danger=%.02f, x=%.02f, y=%.02f\n", danger, safePosition.getX(), safePosition.getY());

        Point sp = getMaxPoint(dangerMap);
        double x = sp.getX() * DANGER_SCALE + DANGER_SCALE / 2;
        double y = sp.getY() * DANGER_SCALE + DANGER_SCALE / 2;
        unSafePosition = new Point.Double(x, y);
    }

    private double getTurn() {
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

    private double getAhead() {
        if (safePosition == null || getPosition() == null)
            return 0;

        return forward * safePosition.distance(getPosition());
    }

    private void fireTargetIfPossible(double firePower) {
        if (getGunHeat() > 0 || firePower == 0 || target == null || !target.isAlive() || aimingData == null ||
                (target.getFEnergy() < 0 && getFireLog(target.getName()).size() > 0)) {
            /*if (getGunHeat() == 0) {
                if (firePower == 0) out.println("no fire");
                if (target == null) out.println("no target");
                if (aimingData == null) out.println("no aiming data");
            }*/

            return;
        }

        double a = getPointAngle(getPosition(), aimingData.getFiringPosition());
        if (getPosition().distance(aimingData.getFiringPosition()) * abs(tan(getGunHeadingRadians() - a)) >= FIRE_TOLERANCE) {
            //out.printf("Fire on %s rejected by tolerance, turn remaining=%.02f,  offset=%.02f\n", target.getName(), getTurnGun(), getGunHeadingRadians()-a);
            return;
        }
        /*out.printf("aiming: %s->%s at x=%f y=%f \n",
                aimingData.getGunner().getName(), aimingData.getTarget().getName(),
                aimingData.getFiringPosition().getX(),
                aimingData.getFiringPosition().getY());*/

        firePower = aimingData.getFirePower();
        setFire(firePower);
        gpStat.fire(firePower);
        target.addFEnergy(-getBulletDamage(firePower));
        lastFireTime = now;
        aimingData.setDirection(getGunHeadingRadians());
        //out.printf("%s fire on %s, damage=%.02f, power=%.02f\n", aimingData.getGunner().getName(), target.getName(), getBulletDamage(fire), fire);
        aimLog.add(new Shell(getPosition(), aimingData, now));
    }

    private void fireVirtualShell() {
        enemies.values().stream().filter(Enemy::isAlive).forEach(e -> {
            e.getTurnAimDatas().forEach(ad -> {
                ad.getGunner().getEnemyRoundFireStat(ad.getTarget()).fire(ad.getFirePower());
                virtualShells.add(new Shell(getPosition(), ad, now));
            });
        });
    }

    private double getTurnGun() {
        if (target == null)
            return 0;

        if (aimingData == null || getGunHeat() !=0 && aliveCount == 1) {
            return computeTurnGun2TargetNextPos(this, target);
        }

        return computeTurnGun2Target(getNextPosition(), aimingData.getNextPosition(), getGunHeadingRadians());
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
        gpStat.hit(bhe.getEnergy());
        Enemy e = enemies.get(bhe.getName());
        if (e != null) {
            e.setEnergy(bhe.getEnergy(), true);
            Shell shell = getShellByAngle(trigoAngle(bhe.getBullet().getHeadingRadians()));
            if (shell != null) {
                shell.getTarget().addFEnergy(getBulletDamage(bhe.getBullet().getPower()));
                aimLog.remove(shell);
            }
        }
    }

    @Override
    public void onBulletMissed(BulletMissedEvent bme) {
        onEvent(bme);
        Shell shell = getShellByAngle(trigoAngle(bme.getBullet().getHeadingRadians()));
        if (shell != null) {
            shell.getTarget().addFEnergy(getBulletDamage(bme.getBullet().getPower()));
            aimLog.remove(shell);
        }
    }

    @Override
    public void onBulletHitBullet(BulletHitBulletEvent bhbe) {
        onEvent(bhbe);
        Bullet b = bhbe.getHitBullet();
        Point.Double p = new Point.Double(b.getX(), b.getY());
        Optional<Wave> ow = waves.stream().filter(w -> w.target.getName() == b.getName())
                .min(new WaveComparator(p, now));
        ow.ifPresent(wave -> waves.remove(wave));

        Shell shell = getShellByAngle(trigoAngle(bhbe.getBullet().getHeadingRadians()));
        if (shell != null) {
            aimLog.remove(shell);
            shell.getTarget().addFEnergy(getBulletDamage(bhbe.getBullet().getPower()));
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        onEvent(hbbe);

        Enemy e = enemies.get(hbbe.getName());
        if (e == null) return;

        e.setEnergy(e.getEnergy() + getBulletHitBonus(hbbe.getPower()), true);
        e.damageMe(getBulletDamage(hbbe.getPower()));

        Optional<Wave> ow = waves.stream().filter(w -> w.getSource().getName().equals(hbbe.getName()))
                .min(new WaveComparator(getPosition(), now));

        ow.ifPresent(wave -> {
            double bulletHeading = trigoAngle(hbbe.getHeadingRadians());
            double headOn = getPointAngle(wave, wave.head);
            double circular = getPointAngle(wave, wave.circular);

            if (abs(headOn - bulletHeading) < abs(circular - bulletHeading))
                e.fireHead();
            else
                e.fireCircular();


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
        aliveCount--;
    }

    @Override
    public void onPaint(Graphics2D g2D) {
        if (drawEnemy) {
            int de = TANK_SIZE_INT * ((drawDanger) ? 2 : 1);
            for (Enemy e : enemies.values())
                if (e.isAlive()) {
                    if (e == target)
                        drawAimCircle(g2D, Color.CYAN, e, de);
                    else if (e == mostLeft)
                        drawCircle(g2D, Color.GREEN, e, de);
                    else if (e == mostRight)
                        drawCircle(g2D, Color.RED, e, de);
                    else
                        drawCircle(g2D, Color.PINK, e, de);
                }
            drawCircle(g2D, Color.green, getPosition(), TANK_SIZE_INT);
        }

        if (aimingData != null && drawAiming) {
            for (Point.Double p : aimingData.getExpectedMoves())
                drawFillCircle(g2D, Color.yellow, p, 5);

            drawAimCircle(g2D, Color.CYAN, aimingData.getFiringPosition(), 20);
        }

        if (drawWave)
            for (Wave w : waves)
                drawWave(g2D, w.kdangle ? Color.GREEN : Color.ORANGE, w, now);

        if (drawShell)
            for (Shell vs : virtualShells) {
                int d = 2 + (int) (5 * (vs.getAimingData().getFirePower() / MAX_BULLET_POWER));
                drawFillCircle(g2D, vs.getGunner().getColor(), vs.getPosition(now), d);
            }


        if (drawDanger) {
            drawDangerMap(g2D);
            if (safePosition != null) {
                drawFillCircle(g2D, Color.GREEN, safePosition, 10);
                g2D.drawLine((int) getX(), (int) getY(), (int) safePosition.getX(), (int) safePosition.getY());
            }
        }
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
                int alpha = (int) range(dangerMap[x][y], 0, 1, 0, 128);
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
        virtualShells.stream().forEach(shell -> {
            shell.getAimingData().getGunner().getEnemyRoundFireStat(shell.getTarget()).unFire(shell.getAimingData().getFirePower());
        });
        printStat();
        aimLog.clear();
        waves.clear();
        virtualShells.clear();
        mostLeft = mostRight = null;
        target = prevTarget = null;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    private void resetRoundStat() {
        gunners.values().forEach(gunner -> gunner.resetRoundStat());
    }

    private void printStat() {
        gunners.values().forEach(gunner -> {
            out.printf("==== %s ====\n", gunner.getName());
            enemies.values().forEach(enemy -> {
                FireStat fs = gunner.getEnemyRoundFireStat(enemy);
                out.printf("    %s hitrate = %.0f%% / %d, dmg/cost=%.0f%%\n", enemy.getName(), fs.getHitRate() * 100, fs.getFireCount(), fs.getDommageCostRatio()*100);
            });
        });
        resetRoundStat();
    }

    public Point.Double getNextPosition() {
        double a = getHeadingRadians();
        a += min(abs(getTurnRemaining()), getTurnRateRadians(getVelocity())) * signum(getTurnRemaining());
        return new Point.Double(getX() + getVelocity() * cos(a), getY() + getVelocity() * sin(a));
    }

    public static Stream<Enemy> getEnemys() {
        return enemies.values().stream();
    }

    private void setupGunners() {
        if (gunners.values().size() == 0) {
            headOnGunner = new HeadOnGunner(this);
            putGunner(new CircularGunner(this));
            putGunner(new PatternGunner(this));
            putGunner(new SurferGunner(this));
        }
    }

    private void putGunner(Gunner gunner) {
        gunners.put(gunner.getName(), gunner);
    }

    private Shell getShellByAngle(double angle) {
        Optional<Shell> opt = aimLog.stream().filter(aimingData -> aimingData.getDirection() == angle).findFirst();

        return opt.isPresent() ? opt.get() : null;
    }

    @Override
    public List<Shell> getFireLog(String name) {

        return aimLog.stream()
                .filter(aimingData -> aimingData.getTarget().getName().equals(name))
                .sorted(new ShellDateComparator())
                .collect(Collectors.toList());
    }

    class ShellDateComparator implements Comparator<Shell> {
        @Override
        public int compare(Shell s1, Shell s2) {
            return (int) (s2.getStart() - s1.getStart());
        }

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

    class AimComparator implements Comparator<AimingData> {
        ITank target;

        public AimComparator(ITank target) {
            this.target = target;
        }

        @Override
        public int compare(AimingData a1, AimingData a2) {
            double d1 = a1.getGunner().getEnemyRoundFireStat(target).getHitRate();
            double d2 = a2.getGunner().getEnemyRoundFireStat(target).getHitRate();
            return Double.compare(d1, d2);
        }
    }

    private void updateRobotCache() {
        if (robotCache == null || robotCache.date < now) {
            RobotCache prev = robotCache;
            robotCache = new RobotCache(
                    super.getX(), super.getY(),
                    trigoAngle(super.getHeadingRadians()), trigoAngle(super.getGunHeadingRadians()),
                    super.getTurnRemaining(), super.getVelocity(),
                    super.getGunHeat(), super.getEnergy(), super.getOthers(), now);

            if (prev != null) {
                turnRate = (robotCache.getTurnRemaining() - prev.getHeadingRadians()) / (now - prev.date);
                accel = (robotCache.getVelocity() - prev.getVelocity()) / (now - prev.date);
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
    public long getDate() {
        return robotCache.getDate();
    }

    @Override
    public long getLastStop() {
        return 0;
    }

    @Override
    public long getLastChangeDirection() {
        return 0;
    }

    @Override
    public long getLastScan() {
        return 0;
    }

    @Override
    public int getOthers() {
        return robotCache.getOthers();
    }
}