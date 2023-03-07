package gpbase;

import gpbase.gun.*;
import robocode.*;
import robocode.Event;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static gpbase.GPUtils.*;
import static java.lang.Math.*;
import static robocode.Rules.*;
import static robocode.util.Utils.normalAbsoluteAngle;
import static robocode.util.Utils.normalRelativeAngle;

public class GPBase extends AdvancedRobot {
    public int FIELD_WIDTH;
    public int FIELD_HEIGHT;
    public static Point.Double BATTLE_FIELD_CENTER;
    public static int TANK_SIZE = 36;

    public double BORDER_OFFSET = TANK_SIZE * 7 / 8;
    public double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS / 3;
    public static double FIRE_TOLERANCE = TANK_SIZE / 3;
    public static long FIRE_AGAIN_MIN_TIME;

    public static double dmax;

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

    static Map<String, Gunner> gunners = new HashMap<>();
    static ArrayList<AimingData> aimDatas = new ArrayList<>();
    static Map<String, Enemy> enemies = new HashMap<>();
    static ArrayList<Wave> waves = new ArrayList<>();
    static ArrayList<VShell> vShells = new ArrayList<>();

    public Enemy prevTarget = null;
    public Enemy target;
    public Enemy mostLeft;
    public Enemy mostRight;
    public AimingData aimingData = null;

    public long lastFireTime = -1;
    public long now;

    double dangerMap[][];
    public int DANGER_WIDTH;
    public int DANGER_HEIGHT;

    public double DANGER_DMAX;

    int DANGER_SCALE = TANK_SIZE / 2;

    boolean drawWave = false;
    boolean drawDanger = false;
    boolean drawAiming = true;
    boolean drawVShell = true;
    boolean drawPoint = true;
    boolean drawEnemy = true;

    public GPBase() {
        super();
        setupGunners();
    }

    @Override
    public void run() {
        FIELD_WIDTH = (int) getBattleFieldWidth();
        FIELD_HEIGHT = (int) getBattleFieldHeight();
        BATTLE_FIELD_CENTER = new Point.Double(FIELD_WIDTH / 2, FIELD_HEIGHT / 2);
        DANGER_WIDTH = (FIELD_WIDTH + DANGER_SCALE) / DANGER_SCALE;
        DANGER_HEIGHT = (FIELD_HEIGHT + DANGER_SCALE) / DANGER_SCALE;
        dangerMap = new double[DANGER_WIDTH][DANGER_HEIGHT];

        dmax = new Point2D.Double(0, 0).distance(FIELD_WIDTH, FIELD_HEIGHT);
        DANGER_DMAX = dmax / DANGER_SCALE;

        aimingMoveLogSize = (int) (dmax / getBulletSpeed(MAX_BULLET_POWER) + 2) * 2;
        moveLogMaxSize = aimingMoveLogSize * 100;
        FIRE_AGAIN_MIN_TIME = (long) (Rules.getGunHeat(MIN_BULLET_POWER) / getGunCoolingRate());

        enemyCount = aliveCount = getOthers();

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red, Color.blue, Color.green);

        for (Enemy enemy : enemies.values()) {
            enemy.setEnergy(0);
            enemy.rotationRate = 0;
            enemy.velocity = enemy.scanVelocity = 0;
            enemy.alive = false;
            enemy.scanLastUpdate = 0;
            enemy.lastFire = 0;
            enemy.fEnergy = 0;
        }

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
                drawVShell = !drawVShell;
                break;
            case 'e':
                drawEnemy = !drawEnemy;
                break;
            case 'p':
                drawPoint = !drawPoint;
                break;
        }
    }

    private void onEvent(Event e) {
        xx = super.getX();
        yy = super.getY();
        now = getTime();
    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        onEvent(e);
        String name = e.getName();
        Enemy enemy = enemies.get(name);

        if (enemy == null)
            enemies.put(name, new Enemy(e, name, this));
        else
            enemy.update(e);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        onEvent(bhe);
        Enemy e = enemies.get(bhe.getName());
        if (e != null) {
            e.setEnergy(bhe.getEnergy(), getBulletDamage(bhe.getBullet().getPower()));

            AimingData ad = getAimingDataByAngle(bhe.getBullet().getHeadingRadians());
            if (ad != null) {
                if (e.getName() == ad.getTarget().getName()) {
                    ad.getGunner().hit(ad.getTarget());
                }
                ///else
                //out.printf("%s hit %s but was aiming to %s...\n",
                //ad.getGunner().getName(), e.getName(), ad.getTarget().getName());
                ad.getTarget().fEnergy += getBulletDamage(ad.getFirePower());
                aimDatas.remove(ad);
            }
        }
    }

    @Override
    public void onBulletMissed(BulletMissedEvent bme) {
        onEvent(bme);
        AimingData ad = getAimingDataByAngle(bme.getBullet().getHeadingRadians());
        if (ad != null) {
            //out.printf("miss %s\n", ad.getTarget().name);
            aimDatas.remove(ad);
            //ad.getTarget().miss(ad.getKdPoint());
            ad.getTarget().fEnergy += getBulletDamage(ad.getFirePower());
            //out.printf("restored energy %f\n", ad.getTarget().fEnergy);
        }
    }


    @Override
    public void onBulletHitBullet(BulletHitBulletEvent bhbe) {
        onEvent(bhbe);
        if (waves.size() > 0) {
            Bullet b = bhbe.getHitBullet();
            Point.Double p = new Point.Double(b.getX(), b.getY());
            Optional<Wave> ow = waves.stream().filter(w -> w.name == b.getName())
                    .sorted(new WaveComparator(p, now)).findFirst();

            if (ow.isPresent())
                waves.remove(ow.get());
        }

        AimingData ad = getAimingDataByAngle(bhbe.getBullet().getHeadingRadians());
        if (ad != null) {
            //out.printf("%s fire %s but bullet hit by bullet...\n",
            //ad.getGunner().getName(), ad.getTarget().getName());
            aimDatas.remove(ad);
            ad.getTarget().fEnergy += getBulletDamage(ad.getFirePower());
        }
    }

    @Override
    public void onHitByBullet(HitByBulletEvent hbbe) {
        onEvent(hbbe);
        Enemy e = enemies.get(hbbe.getName());
        if (e != null)
            e.setEnergy(e.getEnergy() + getBulletHitBonus(hbbe.getPower()));
        e.hitMe++;

        if (waves.size() > 0) {
            Optional<Wave> ow = waves.stream().filter(w -> w.name == hbbe.getName())
                    .sorted(new WaveComparator(getCurrentPoint(), now)).findFirst();

            if (ow.isPresent())
                waves.remove(ow.get());
        }
    }

    @Override
    public void onRoundEnded(RoundEndedEvent event) {
        printStat();
        aimDatas.clear();
        waves.clear();
        vShells.clear();
        enemies.forEach((s, enemy) -> enemy.lastUpdate=0);
    }



    @Override
    public void onRobotDeath(RobotDeathEvent event) {
        onEvent(event);
        Enemy enemy = enemies.get(event.getName());
        enemy.setEnergy(0);
        enemy.fEnergy = 0;
        enemy.rotationRate = 0;
        enemy.velocity = enemy.scanVelocity = 0;
        enemy.alive = false;
        enemy.scanLastUpdate = 0;
        enemy.lastFire = 0;
        enemy.lifeTime += now;
        aliveCount--;
    }

    @Override
    public void onPaint(Graphics2D g2D) {
        if (safePosition != null && drawPoint) {
            drawFillCircle(g2D, Color.GREEN, safePosition, 10);
            drawFillCircle(g2D, Color.RED, unSafePosition, 10);
        }

        if (drawEnemy)
            for (Enemy e : enemies.values())
                if (e.alive)
                    drawCircle(g2D, Color.PINK, e, TANK_SIZE);

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
                drawWave(g2D, Color.ORANGE, w, now);
            if (waves.size() > 0) {
                Wave w = waves.stream().sorted(new WaveComparator(safePosition, now)).findFirst().get();
                drawWave(g2D, Color.RED, w, now);
            }
        }
        if (drawVShell)
            for (VShell vs : vShells)
                drawFillCircle(g2D, Color.MAGENTA, vs.getPosition(now), 5);

        if (drawDanger)
            drowDangerMap(g2D);
    }

    private void drowDangerMap(Graphics2D g2D) {
        int r = Color.PINK.getRed();
        int g = Color.PINK.getGreen();
        int b = Color.PINK.getBlue();
        double min = minA2(dangerMap);
        double max = maxA2(dangerMap);
        for (int x, y = 0; y < DANGER_HEIGHT; y++) {
            int alpha = 0;
            for (x = 0; x < DANGER_WIDTH; x++) {
                alpha = (int) range(dangerMap[x][y], min, max, 0, 100);
                Color c = new Color(r, g, b, alpha);
                g2D.setColor(c);
                g2D.fillRect(x * DANGER_SCALE, y * DANGER_SCALE, DANGER_SCALE, DANGER_SCALE);
            }
        }
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d\n", event.getSkippedTurn(), event.getPriority());
    }
    // public void onHitWall(HitWallEvent e) { forward *= -1; }
    //

    @Override
    public void onHitRobot(HitRobotEvent e) {
        // Try escape backward and turn PI/2
        forward *= -1;
        setTurnLeftRadians(PI / 2);
        setAhead(forward * TANK_SIZE * 2);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic
    public void doGP() {

    }

    public void doTurn() {
        now = getTime();
        xx = super.getX();
        yy = super.getY();
        updatePositions();
        updateWaves();
        updateVShells();
        updateDangerMap();
        computeSafePosition();

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
        fire = (aimingData == null) ? 0 : aimingData.getFirePower();
        if (aimingData != null) {
            doGP();
        }
        robotSetActions();
        if (target != null)
            prevTarget = target;
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
        setTurnRadarLeftRadians(turnRadarLeft);
        setTurnLeftRadians(turnLeft);
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

    private void updateVShells() {
        List<VShell> newVShells = vShells.stream().filter(vs -> {
            Point.Double p = vs.getPosition(now);
            AimingData aimingData = getAimingDataByAngle(vs.getDirection());
            if (aimingData==null)
                return false;
            Enemy enemy = aimingData.getTarget();
            if (p.x >= 0 && p.x <= getBattleFieldWidth() && p.y >= 0 && p.y <= getBattleFieldHeight()) {
                Enemy e = vs.getTarget();
                boolean hit = false;

                if (p.distance(e) < TANK_SIZE / 2)
                    hit = true;
                else {
                    Point.Double pp = vs.getPosition(now - 0.1);
                    if (pp.distance(e) < p.distance(e))
                        for (double t = now - 1; t < now; t += 0.1) {
                            pp = vs.getPosition(t);
                            if (pp.distance(e) < TANK_SIZE / 2) {
                                hit = true;
                                break;
                            }
                        }
                }

                if (hit) {
                    vs.getGunner().hit(aimingData.getTarget());
                }
                return !hit;
            }

            return false;
        }).collect(Collectors.toList());

        vShells = new ArrayList<>(newVShells);
    }

    private void updateWaves() {
        List<Wave> newWaves = waves.stream().filter(w -> waveInBattleField(w, now)).collect(Collectors.toList());
        waves = new ArrayList<>(newWaves);
    }

    private void updateDangerMap() {
        Arrays.stream(dangerMap).forEach(a -> Arrays.fill(a, 0));
        enemies.values().stream().filter(e -> e.alive && e.scanLastUpdate>0).forEach(enemy -> enemyDanger(enemy));
        waves.stream().forEach(wave -> waveDanger(wave));
        conersDanger();
    }

    private void conersDanger() {
        double[][] cornerMap = new double[DANGER_WIDTH][DANGER_HEIGHT];

        double dmax = 1;
        double rmax = DANGER_DMAX / 2;
        for (double r = DANGER_HEIGHT / 2; r < rmax; r += .5) {
            double danger = range(r, DANGER_HEIGHT / 2, rmax, 0, dmax);
            int num = (int) (r * 2.5 * PI);
            for (int i = 0; i < num; i++) {
                double a = i * PI / num * 2;
                int h = DANGER_WIDTH / 2 + (int) (r * cos(a) * DANGER_WIDTH / DANGER_HEIGHT);
                int v = DANGER_HEIGHT / 2 + (int) (r * sin(a));
                if (h >= 0 && v >= 0 && h < DANGER_WIDTH && v < DANGER_HEIGHT)
                    cornerMap[h][v] = danger;
            }
        }
        for (int x = 0; x < DANGER_WIDTH; x++)
            for (int y = 0; y < DANGER_HEIGHT; y++)
                dangerMap[x][y] = max(dangerMap[x][y], cornerMap[x][y]);

    }

    private void enemyDanger(Enemy enemy) {
        double[][] enemyMap = new double[DANGER_WIDTH][DANGER_HEIGHT];
        int x = (int) enemy.getX() / DANGER_SCALE;
        int y = (int) enemy.getY() / DANGER_SCALE;
        for (double r = DANGER_DMAX - .5; r > 0; r -= .5) {
            int num = (int) (r * 2.5 * PI);
            for (int i = 0; i < num; i++) {
                double a = i * PI / num * 2;
                int h = x + (int) (r * cos(a));
                int v = y + (int) (r * sin(a));
                if (h >= 0 && v >= 0 && h < DANGER_WIDTH && v < DANGER_HEIGHT)
                    enemyMap[h][v] = Math.pow((DANGER_DMAX - r + TANK_SIZE * 2 / DANGER_DMAX) / (DANGER_DMAX), 4);
            }
        }

        if (x>=0 && x<DANGER_WIDTH && y>=0 && y<DANGER_HEIGHT)
            enemyMap[x][y] = 1;
        for (x = 0; x < DANGER_WIDTH; x++)
            for (y = 0; y < DANGER_HEIGHT; y++)
                dangerMap[x][y] = max(dangerMap[x][y], enemyMap[x][y]);
    }

    double bfa = 0;

    private void waveDanger(Wave wave) {
        double[][] waveMap = new double[DANGER_WIDTH][DANGER_HEIGHT];
        double d = wave.getDistance(now) / DANGER_SCALE;
        double median = normalAbsoluteAngle(wave.direction);
        double deviation = wave.arc / 4;
        int x = (int) wave.getX() / DANGER_SCALE;
        int y = (int) wave.getY() / DANGER_SCALE;

        for (double r = (DANGER_DMAX - d - .5); r > 0; r -= .5) {
            int num = max((int) ((r + d) * wave.arc * 1.25), 1);
            for (int i = 0; i < num; i++) {
                double a = median - ((num == 1) ? 0 : wave.arc / 2 - wave.arc * i / num);
                int h = x + (int) ((r + d) * cos(a));
                int v = y + (int) ((r + d) * sin(a));
                if (h >= 0 && v >= 0 && h < DANGER_WIDTH && v < DANGER_HEIGHT) {
                    double fa = normalDistrib(a, median, deviation) / normalDistrib(median, median, deviation);
                    waveMap[h][v] = fa * wave.getPower() / MAX_BULLET_POWER * Math.pow((DANGER_DMAX - r) / (DANGER_DMAX), 2);
                    ;
                }
            }
        }

        for (x = 0; x < DANGER_WIDTH; x++)
            for (y = 0; y < DANGER_HEIGHT; y++)
                dangerMap[x][y] = max(dangerMap[x][y], waveMap[x][y]);
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
        if (e.lastUpdate >= now || e.getEnergy() == 0)
            return;
        long iteration = now - e.lastUpdate;
        for (long i = 0; i < iteration; i++) {
            e.velocity = checkMinMax(e.velocity + e.accel, e.vMin, e.vMax);
            e.direction += min(abs(e.rotationRate), getTurnRateRadians(e.velocity)) * signum(e.rotationRate);
            try {
                double x = ensureXInBatleField(e.x + e.velocity * cos(e.direction));
                double y = ensureYInBatleField(e.y + e.velocity * sin(e.direction));
                e.x = x;
                e.y = y;
            } catch (Exception ex) {
                // HitWall
            }
        }
        e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        e.lastUpdate = now;
    }

    private void updatePositions() {
        double totalEnergy = getEmenmiesEnergy();
        double x = 0;
        double y = 0;
        double d = Double.POSITIVE_INFINITY;

        target = null;
        for (Enemy e : enemies.values()) {
            moveEnemy(e, now);

            if (e.getEnergy() > 0) {
                x += e.x * e.getEnergy();
                y += e.y * e.getEnergy();
            }

            double od = getCurrentPoint().distance(e);
            if (od < d && e.alive) {
                // target closet alive opponent
                target = e;
                d = od;
            }
            //out.printf("target is %s\n", target != null ? target.getName()+ "( " + target.getEnergy() + " , "+target.fEnergy + " )": "None");
            e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        }


    }

    private double getEmenmiesEnergy() {
        return enemies.values().stream().mapToDouble(e -> e.getEnergy()).sum();
    }

    private void computeSafePosition() {
        computeSafePositionDangerMap();
    }

    private void computeSafePositionBaryCentric() {
        double x = 0;
        double y = 0;
        double div = getEmenmiesEnergy();

        for (Enemy e : enemies.values()) {
            //out.printf("%s x=%.0f, y=%.0f, nrj=%.0f\n", e.name, e.getX(), e.getY(), e.getEnergy());
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
        int dist = (int) MAX_VELOCITY * 30 / DANGER_SCALE;
        //int dist = Math.max(2*TANK_SIZE/DANGER_SCALE,(int)(DANGER_DMAX*aliveCount/enemyCount/4));
        Point gp = new Point((int) getX() / DANGER_SCALE, (int) getY() / DANGER_SCALE);

        List<Point> points = GPUtils.listClosePoint(gp, dist, DANGER_WIDTH, DANGER_HEIGHT);

        double danger = Double.MAX_VALUE;
        for (Point p : points) {
            double d = GPUtils.computeMoveDanger(gp, p, dangerMap);
            //double d = dangerMap[p.x][p.y];
            if (d < danger) {
                danger = d;
                double x = max(BORDER_OFFSET, min(FIELD_WIDTH - BORDER_OFFSET, p.getX() * DANGER_SCALE + DANGER_SCALE / 2));
                double y = max(BORDER_OFFSET, min(FIELD_HEIGHT - BORDER_OFFSET, p.getY() * DANGER_SCALE + DANGER_SCALE / 2));
                safePosition = new Point.Double(x, y);
            }
        }
        /*Point sp = getMinPointClose(dangerMap, (int) getX()/DANGER_SCALE, (int) getY()/DANGER_SCALE, Math.max(2,(int)(DANGER_DMAX/4*aliveCount)));
        double x = max(BORDER_OFFSET, min(FIELD_WIDTH-BORDER_OFFSET, sp.getX()*DANGER_SCALE+DANGER_SCALE/2));
        double y = max(BORDER_OFFSET, min(FIELD_HEIGHT-BORDER_OFFSET, sp.getY()*DANGER_SCALE+DANGER_SCALE/2));
        safePosition =  new Point.Double(x, y);*/
        Point sp = getMaxPoint(dangerMap);
        double x = sp.getX() * DANGER_SCALE + DANGER_SCALE / 2;
        double y = sp.getY() * DANGER_SCALE + DANGER_SCALE / 2;
        unSafePosition = new Point.Double(x, y);
    }

    private double getTurn2Safe() {
        if (safePosition == null)
            return 0;

        double sa = getAngle(getCurrentPoint(), safePosition);
        double ra = trigoAngle(getHeadingRadians());

        if (abs(normalRelativeAngle(sa - ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa - ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }

    private double getSafeAhead() {
        if (safePosition == null || getCurrentPoint() == null)
            return 0;

        return forward * safePosition.distance(getCurrentPoint());
    }

    private void fireTargetIfPossible(double fire) {
        if (aimingData == null || getGunHeat() > 0 || target.alive == false || fire == 0 || target.fEnergy < 0)
            return;

        if (getCurrentPoint().distance(aimingData.getFiringPosition()) * sin(abs(getGunTurnRemainingRadians())) > FIRE_TOLERANCE ||
                abs(getGunTurnRemainingRadians()) > PI / 2) {
            return;
        }

        setFire(fire);
        //out.printf("target is %s(x=%.0f, y = %.0f) energy(%.02f, %.02f)\n", target.getName(), target.getX(), target.getY(), target.getEnergy(), target.fEnergy);
        //out.printf("Gunner is %s fire at x=%.0f, y = %.0f, turnRemain= %f\n", aimingData.getGunner().getName(),
        //aimingData.getFiringPosition().getX(), aimingData.getFiringPosition().getY(), getGunTurnRemainingRadians());
        lastFireTime = now;
        aimingData.getGunner().fire(target);
        target.fEnergy -= getBulletDamage(fire);

        aimingData.setAngle(getGunHeadingRadians());
        aimDatas.add(aimingData);

        fireVirtualShell();
        //out.printf("Shooting %s, power %.02f, gunner %s confidence=%.02f\n",
        //target.name, fire, aimingData.getGunner().getName(), aimingData.getConfidence());
    }

    private void fireVirtualShell() {
        gunners.values().forEach(gunner -> {
            if (gunner != aimingData.getGunner()) {
                final AimingData ad = gunner.aim(aimingData.getTarget());
                if (ad != null) {
                    aimDatas.add(ad);
                    gunner.fire(aimingData.getTarget());
                    vShells.add(new VShell(
                            getCurrentPoint(),
                            Rules.getBulletSpeed(ad.getFirePower()),
                            ad.getAngle(),
                            now,
                            ad.getTarget(),
                            ad.getGunner()));
                }
            }
        });
    }

    private double getTurn2Targert() {
        aimingData = null;

        if (target == null)
            return 0;

        if (target.getEnergy() == 0)
            aimingData = gunners.get("HeadOnGunner").aim(target);
        else
            for (Gunner gunner : gunners.values()) {
                AimingData temp = gunner.aim(target);
                if (aimingData == null ||
                        (temp != null && temp.getGunner().hitRate(target) > aimingData.getGunner().hitRate(target)))
                    aimingData = temp;
            }
        if (aimingData != null) {
            fire = aimingData.getFirePower();
            return computeTurnGun2Target(this, aimingData.getFiringPosition());
        }

        fire = 0;
        return 0;
    }

    public long aliveCount() {
        return enemies.values().stream().filter(e -> e.alive).count();
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    private void resetRoundStat() {
        gunners.values().forEach(gunner -> {
            gunner.resetStat();
            enemies.values().forEach(enemy -> {
                gunner.resetStat(enemy);
            });
        });
    }

    private void printStat() {
        gunners.values().forEach(gunner -> {
            out.printf("%s hitrate = %.0f%% / %d\n", gunner.getName(), gunner.hitRate() * 100, gunner.fireCount());
            enemies.values().forEach(enemy -> {
                out.printf("    %s hitrate = %.0f%% / %d\n", enemy.getName(), gunner.hitRate(enemy) * 100, gunner.fireCount(enemy));
            });
        });
    }

    private Point.Double getBorderPoint(Point.Double p, double angle) {
        double a = normalRelativeAngle(angle);
        if (a == 0)
            return new Point.Double(getBattleFieldWidth() - BORDER_OFFSET, p.y);
        if (a == PI / 2)
            return new Point.Double(p.x, getBattleFieldHeight() - BORDER_OFFSET);
        if (abs(a) == PI)
            return new Point.Double(BORDER_OFFSET, p.y);
        if (a == -PI / 2)
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
        return ensureXInBatleField(x, 2);
    }

    public double ensureYInBatleField(double y) throws Exception {
        return ensureYInBatleField(y, 2);
    }

    public double ensureXInBatleField(double x, double d) throws Exception {
        if (x < TANK_SIZE / d) throw new Exception("hit wall x");
        if (x > BATTLE_FIELD_CENTER.getX() * 2 - TANK_SIZE / d) throw new Exception("hit wall x");
        return x;
    }

    public double ensureYInBatleField(double y, double d) throws Exception {
        if (y < TANK_SIZE / d) throw new Exception("hit wall y");
        if (y > BATTLE_FIELD_CENTER.getY() * 2 - TANK_SIZE / d) throw new Exception("hit wall y");
        return y;
    }

    public double conerDistance(Point.Double p) {
        return sqrt(pow(min(p.x, BATTLE_FIELD_CENTER.x * 2 - p.x), 2) +
                pow(min(p.y, BATTLE_FIELD_CENTER.y * 2 - p.y), 2));
    }

    public double wallDistance(Point.Double p) {
        return min(min(p.x, BATTLE_FIELD_CENTER.x * 2 - p.x),
                min(p.y, BATTLE_FIELD_CENTER.y * 2 - p.y));
    }

    private void setupGunners() {
        if (gunners.values().size() == 0) {
            putGunner(new HeadOnGunner(this));
            //putGunner(new RandomHeadOnGunner(this));
            //putGunner(new OccilatorGunner(this));
            putGunner(new CircularGunner(this));
            putGunner(new NearestNeighborGunner(this));
        }
    }

    private void putGunner(Gunner gunner) {
        gunners.put(gunner.getName(), gunner);
    }

    private AimingData getAimingDataByAngle(double angle) {
        Optional<AimingData> opt = aimDatas.stream().filter(aimingData -> aimingData.getAngle() == angle).findFirst();

        return opt.isPresent() ? opt.get() : null;
    }

    boolean waveInBattleField(Wave wave, long now) {
        double d = wave.getDistance(now);
        int num = max((int) ((d) * wave.arc * 1.25), 1);
        for (int i = 0; i < num; i++) {
            double a = wave.direction - ((num == 1) ? 0 : wave.arc / 2 - wave.arc * (i + (((double) num + 1) % 2) / 2) / num);
            int h = (int) (wave.x + (int) (d * cos(a)));
            int v = (int) (wave.y + (int) (d * sin(a)));
            if (pointInBattleField(new Point2D.Double(h, v)))
                return true;
        }
        return false;
    }

    boolean pointInBattleField(Point2D.Double p) {
        return p.x >= 0 && p.x <= getBattleFieldWidth() && p.y >= 0 && p.y <= getBattleFieldHeight();
    }

    private double xx;

    public double getX() {
        return xx;
    }

    private double yy;

    public double getY() {
        return yy;
    }
}