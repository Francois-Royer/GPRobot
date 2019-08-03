package sampleex;

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
    public static double TANK_SIZE=36;

    public double BORDER_OFFSET =TANK_SIZE * 3 / 2;
    public double SCAN_OFFSET = RADAR_TURN_RATE_RADIANS/4;
    public static double HIT_RATE_WATER_MARK = 0.3;
    public static double FIRE_POWER_FACTOR = 3;
    public static double FIRE_TOLERANCE = TANK_SIZE / 8;
    public static long FIRE_STEP = 3;

    public double dmin= 100;
    public double dmax;

    public int enemyCount;

    Point.Double unsafePosition;
    Point.Double safePosition;
    Point.Double targetPred;
    List<Point.Double> targetPreds=new ArrayList<>();

    public double scandirection=1;
    public double forward=1;

    public double turnLeft = 0;
    public double turnGunLeft = 0;
    public double turnRadarLeft = 0;
    public double ahead = 0;
    public double fire = 0;

    static Map<String, Enemy> enemies = new HashMap<>();
    Enemy target;
    Enemy mostLeft;
    Enemy mostRight;

    static long fireCount = 0;
    static long bulletHitCount = 0;
    static long battleFireCount = 0;
    static long battlebulletHitCount = 0;
    boolean hitwallX = false;
    boolean hitwallY = false;

    @Override
    public void run() {
        BATTLE_FIELD_CENTER = new Point.Double(getBattleFieldWidth()/2, getBattleFieldHeight()/2);
        dmax= BATTLE_FIELD_CENTER.distance(TANK_SIZE/2, TANK_SIZE/2) * 2;
        enemyCount = getOthers();

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setColors(Color.red,Color.blue,Color.green);

        while(true) {
            doTurn();
            execute();
        }

    }

    @Override
    public void onScannedRobot(ScannedRobotEvent e) {
        updateEnemy(e);
    }

    @Override
    public void onBulletHit(BulletHitEvent bhe) {
        bulletHitCount++;
        Enemy e = enemies.get(bhe.getName());
        if (e != null)
            e.bulletHitCount++;
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
        Enemy o = enemies.get(event.getName());
        o.energy = 0;
        o.alive = false;
    }

    @Override
    public void onPaint(Graphics2D g) {
        if (safePosition != null)
            drawFillCircle(g, Color.GREEN, safePosition, 10);

        for (Enemy e: enemies.values())
            if (e.alive)
                drawFillCircle(g, Color.PINK, e, 10);

        for (Point.Double p:targetPreds) 
            drawFillCircle(g, Color.yellow, p, 5);

        if (targetPred != null)
            drawAimCircle(g, Color.CYAN, targetPred, 20);

        if (mostLeft != null && mostRight != null) {
            drawCircle(g, Color.RED, mostLeft, (int)TANK_SIZE*4/3);
            drawCircle(g, Color.GREEN, mostRight, (int)TANK_SIZE*4/3);
        }
    }

    @Override
    public void onSkippedTurn(SkippedTurnEvent event) {
        out.printf("Skip turn: %d %d\n", event.getSkippedTurn(), event.getPriority());
    }
    //public void onHitWall(HitWallEvent e) { forward *= -1; }
    //
    
    public void onHitRobot(HitRobotEvent e) { 
        forward *= -1; 
        setTurnLeftRadians(PI/2);
        setAhead(forward*TANK_SIZE*2);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    // GPBase logic
    void doGP() {

    }

    public void doTurn() {
        updatePositions();
        int oc = getOthers();
        if (oc > 0 && enemies.values().stream().filter(e -> e.alive).count() >= oc) {
            updateLeftRightEnemies();
            double ra = normalAbsoluteAngle(trigoAngle(getRadarHeadingRadians()));
            turnRadarLeft = scanLeftRight(ra, mostLeft.angle, mostRight.angle);
            //out.printf("ra=%.02f, ml=%.02f, mr=%.02f, scandir=%.0f, turn=%.02f\n", ra, mostLeft.angle, mostRight.angle, scandirection, turnRadarLeft);
        } else
            turnRadarLeft = 2*PI;
        
        turnLeft = getTurn2Safe();
        ahead = getSafeAhead();
        turnGunLeft = getTurn2Targert();
        fire = fireTargetIfPossible();
        doGP();
        robotSetActions();
    }

    class Enemy extends Point.Double {
        String name;

        double energy;
        double velocity;
        double direction;
        double angle;

        double rotationRate = 0;
        double accel = 0;
        long lastUpdate;
        double vMax;
        double vMin;
        double vVar =0;
        double rvar =0;

        long fireCount = 0;
        long bulletHitCount = 0;

        long scan_count = 1;
        double scan_direction;
        double scan_velocity;

        Boolean alive;

        public Enemy(final String name, final double x, final double y, final double energy, final double velocity,
                     final double direction, final double angle,  final long lastUpdate) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.energy = energy;
            this.velocity = velocity;
            this.scan_velocity = velocity;
            this.direction = direction;
            this.scan_direction = direction;
            this.angle = angle;
            this.lastUpdate = lastUpdate;
            this.alive = true;
            Enemy old = enemies.get(name);

            if (old != null) {
                this.scan_count = old.scan_count + 1;
                this.fireCount = old.fireCount;
                this.bulletHitCount = old.bulletHitCount;

                if (old.alive) {
                    if (lastUpdate == old.lastUpdate) {
                        this.rotationRate = old.rotationRate;
                    } else {
                        this.rotationRate = normalRelativeAngle(this.scan_direction - old.scan_direction) / (lastUpdate - old.lastUpdate);
                    }
                    this.rotationRate = checkMinMax(this.rotationRate, -MAX_TURN_RATE_RADIANS, MAX_TURN_RATE_RADIANS);

                    if (velocity == 0 || energy == 0) {
                        accel = 0;
                        this.velocity = 0;
                    } else
                        accel = velocity - old.velocity;

                    this.vVar = (energy > 0) ? (abs(velocity - old.velocity) + old.vVar) / scan_count : old.vVar;
                    this.vMax = max(old.vMax, velocity);
                    this.vMin = min(old.vMin, velocity);
                } else {
                    this.rotationRate = 0;
                    this.vVar = old.vVar;
                    this.vMin = old.vMin;
                    this.vMax = old.vMax;
                }

            } else {
                this.vMax = max(0, velocity);
                this.vMin = min(0, velocity);
            }
        }

        double getHitRate() {
            return (this.fireCount == 0) ? 0 : (double) this.bulletHitCount / this.fireCount;
        }
    }

    private void robotSetActions() {
        /*out.printf("robotSetActions turnRadarLeft=%.2f turnLeft=%.2f ahead=%.2f turnGunLeft=%.2f fire=%.2f\n",
            turnRadarLeft, turnLeft, ahead, turnGunLeft, fire);*/

        setAhead(ahead);
        setTurnLeftRadians(turnLeft);
        setTurnGunLeftRadians(turnGunLeft);
        setTurnRadarLeftRadians(turnRadarLeft);
        if (fire > 0 && getGunHeat() == 0) {
            fireCount++;
            target.fireCount++;
            setFire(fire);
        }
    }

    private double scanLeftRight(double ra, double ml, double mr) {
        if ((ra >= ml && ra < ml + 2*SCAN_OFFSET) || (ra < 2*SCAN_OFFSET+ml-2*PI && ml > 2*PI - 2*SCAN_OFFSET))
            scandirection = -1;
        else if ((ra < mr && ra > mr - 2*SCAN_OFFSET) || (ra > 2*PI - 2*SCAN_OFFSET + mr && mr < 2*SCAN_OFFSET)) 
            scandirection = 1;
    
        if (scandirection == 1)
            return ml + SCAN_OFFSET - ((ml >= ra) ? ra : ra - 2*PI);
        else 
            return mr - SCAN_OFFSET - ((mr <= ra) ? ra : ra + 2*PI);
    }

    private void updateLeftRightEnemies() {
        List<Enemy> sEnemies = enemies.values().stream().filter(e -> e.alive).collect(Collectors.toList());
        sEnemies.sort(new Comparator<Enemy>() {
            @Override
            public int compare(final Enemy  e1, final Enemy e2) {
                if (e1.angle < e2.angle) return -1;
                if (e1.angle > e2.angle) return 1;
                return 0;
            }
         });

         Enemy prev = mostRight = sEnemies.get(0);
         mostLeft = sEnemies.get(sEnemies.size() - 1);
         double ba = abs(normalRelativeAngle(mostLeft.angle) - normalRelativeAngle(mostRight.angle));
         for (Enemy enemy:sEnemies) {
            final double a = enemy.angle - prev.angle;
            if ( a > ba ) {
                mostRight = enemy;
                mostLeft = prev;
                ba = a;
            }
            prev = enemy;
         }
    }

    private void updateEnemy(ScannedRobotEvent e) {
        double angle = trigoAngle(getHeadingRadians() + e.getBearingRadians());
        double direction = trigoAngle(e.getHeadingRadians());
        double x = getX() + e.getDistance() * cos(angle);
        double y = getY() + e.getDistance() * sin(angle);
        long time = getTime();

        enemies.put(e.getName(), new Enemy(e.getName(), x, y, e.getEnergy(), e.getVelocity(), direction, normalAbsoluteAngle(angle), time));
    }


    private void moveEnemy(Enemy e, long now) {
        if (e.lastUpdate >= now || e.energy == 0) return;
        long time = now-e.lastUpdate;
        for (long i=0; i<time; i++) {
            e.x = ensureXInBatleField(e.x  + e.velocity * cos(e.direction));
            e.y = ensureYInBatleField(e.y + e.velocity * sin(e.direction));
            e.direction += e.rotationRate;
            e.velocity = checkMinMax(e.velocity + e.accel, e.vMin, e.vMax);
        }
        e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        e.lastUpdate=now;
    }

    private void updatePositions() {
        long now = getTime();
        double totalEnergy = getEmenmiesEnergy();
        double x=0;
        double y=0;
        double d=Double.POSITIVE_INFINITY;

        for (Enemy e: enemies.values()) {
            moveEnemy(e, now);

            x += e.x * e.energy;
            y += e.y * e.energy;

            double od = getCurrentPoint().distance(e);
            if (od < d && e.alive) {
                // target closet alive opponent
                target = e;
                d=od;
            }
            e.angle = normalAbsoluteAngle(getAngle(getCurrentPoint(), e));
        }

        if (totalEnergy == 0)
            return;

        unsafePosition = new Point.Double(x/totalEnergy, y/totalEnergy);
        double a = oppositeAngle(getAngle(BATTLE_FIELD_CENTER, unsafePosition));
        double c = acos(getBattleFieldHeight()/getBattleFieldWidth());

        if (a < c && a> -c) { // Right
            x = getBattleFieldWidth()- BORDER_OFFSET;
            y = (sin(a)*(getBattleFieldHeight()-2* BORDER_OFFSET) + getBattleFieldHeight())/2;
        } else if (a>c && a<PI-c) { // top
            x = (cos(a)*(getBattleFieldWidth()-2* BORDER_OFFSET) + getBattleFieldWidth())/2;
            y = getBattleFieldHeight()- BORDER_OFFSET;
        } else if (a< -c && a>=-PI+c) { // bottom
            x = (cos(a)*(getBattleFieldWidth()-2* BORDER_OFFSET) + getBattleFieldWidth())/2;
            y = BORDER_OFFSET;
        } else { // left
            x = BORDER_OFFSET;
            y=(sin(a)*getBattleFieldHeight()+getBattleFieldHeight())/2;
        }
        safePosition=new Point.Double( x, y);
    }

    private double getEmenmiesEnergy() {
        double sum = 0;
        for (Enemy o : enemies.values()) sum += o.energy;
        return sum;
    }

    private double getTurn2Safe() {
        if (safePosition == null)
            return 0;

        double sa = getAngle(getCurrentPoint(), safePosition);
        double ra = trigoAngle(getHeadingRadians());

        if (abs(normalRelativeAngle(sa-ra)) <= (PI / 2)) {
            forward = 1;
            return normalRelativeAngle(sa-ra);
        }

        forward = -1;
        return normalRelativeAngle(oppositeAngle(sa) - ra);
    }
    private double getSafeAhead() {
        if (safePosition == null)
            return 0;

        return forward * safePosition.distance(getCurrentPoint());
    }

    private double fireTargetIfPossible() {
        if (targetPred == null|| getGunHeat() > 0 || target.alive == false)
            return 0;

        if ( getCurrentPoint().distance(targetPred) * sin(abs(getGunTurnRemainingRadians())) > FIRE_TOLERANCE)
            return 0;

       return getFirePower(targetPred);
    }

    private double getFirePower(Point.Double p) {
        if (target.energy == 0)
            return MAX_BULLET_POWER;

            double d = getCurrentPoint().distance(p);
        double power = range(d , dmin, dmax, MAX_BULLET_POWER, MIN_BULLET_POWER);

        power += power * FIRE_POWER_FACTOR * (target.getHitRate() - HIT_RATE_WATER_MARK);

        return checkMinMax(power, MIN_BULLET_POWER, MAX_BULLET_POWER);
    }

    private double getTurn2Targert() {
        if (target == null) return 0;

        //out.printf("%20s: rotationRate=%.02f%% velocity=%.02f accel=%.02f\n", target.name, target.rotationRate, target.velocity, target.accel);
        targetPred = getPoint(target);
        targetPreds = new ArrayList<>();
        if (target.energy > 0) {
            double firePower = getFirePower(targetPred);
            double bulletSpeed = getBulletSpeed(firePower);
            long time = (long) (getCurrentPoint().distance(targetPred) / bulletSpeed);
            for (int i = 0; i < 5; i++) {
                targetPred = getPoint(target);
                double direction = target.direction;
                targetPreds = new ArrayList<>();
                double v = target.velocity;

                /*out.printf("target %s hitRate=%.2f%% vvar=%f\n", target.name,
                target.getHitRate()*100,
                target.vVar);*/

                if (target.fireCount > 10 && target.getHitRate() < 0.2 && getCurrentPoint().distance(targetPred) > 400)
                    time = time * (target.fireCount % FIRE_STEP) / (FIRE_STEP-1);

                for (long t = 0; t < time; t++) {

                    v = checkMinMax(v + target.accel, target.vMin, target.vMax);
                    double turnRate = min(abs(target.rotationRate),getTurnRateRadians(v));
                    if (target.rotationRate<0)
                        turnRate *= -1;
                    direction += turnRate;
                    double x=ensureXInBatleField(targetPred.x + v * cos(direction));
                    double y=ensureYInBatleField(targetPred.y + v * sin(direction));

                    if (!(hitwallX || hitwallY)) {
                        targetPred.x = x;
                        targetPred.y = y;
                    }
                    targetPreds.add(new Point.Double(targetPred.x, targetPred.y));
                }
                bulletSpeed = getBulletSpeed(firePower);
                firePower = getFirePower(targetPred);
                time = (long) (getCurrentPoint().distance(targetPred) / bulletSpeed);
            }
        }
        double ga = trigoAngle(getGunHeadingRadians());
        double ta = getAngle(getCurrentPoint(), targetPred);

        if (abs(ta-ga) <= PI) {
            return ta - ga;
        }

        return ga-ta;
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    // Utils

    private Point.Double getCurrentPoint() {
        return new Point.Double(getX(),getY());
    }

    static private Point.Double getPoint(Enemy o) {
        return new Point.Double( o.x, o.y);
    }

    // -PI -> PI
    private static double getAngle(Point.Double s, Point.Double d) {
        double a = acos((d.x-s.x)/s.distance(d));
        if (d.y < s.y)
            a= 2*PI-a;
        return normalRelativeAngle(a);
    }

    // -PI -> PI
    public static double oppositeAngle(double a) {
        return normalRelativeAngle(a+PI);
    }

    // convert robocode angle to trigonometric angle
    public static double trigoAngle(double roboAngle) {
        return normalRelativeAngle(PI/2-roboAngle);
    }

    public double ensureXInBatleField(double x) {
        double nx = max(TANK_SIZE/2, min(getBattleFieldWidth()-TANK_SIZE/2, x));
        hitwallX = abs(nx-x) > 2;
        return nx;
    }

    public double ensureYInBatleField(double y) {
        double ny = max(TANK_SIZE/2, min(getBattleFieldHeight()-TANK_SIZE/2, y));
        hitwallY = abs(ny-y) > 2;
        return ny;
    }

    static public double checkMinMax(double v, double min, double max) {
        return max(min, min(max, v));
    }

    static double range(double v, double minv, double maxv, double minr, double maxr) {
        return (v-minv) / (maxv-minv) *(maxr-minr)+ minr;
    }

    static void drawFillCircle(Graphics2D g, Color c, Point.Double p, int d) {
        g.setColor(c);
        g.fillArc((int) p.x-d/2, (int) p.y-d/2, d, d, 0, 360);
    }

    static void drawCircle(Graphics2D g, Color c, Point.Double p, int d) {
        g.setColor(c);
        g.drawArc((int) p.x-d/2, (int) p.y-d/2, d, d, 0, 360);
    }
    
    static void drawAimCircle(Graphics2D g, Color c, Point.Double p, int d) {
        int div=5;
        g.setColor(c);
        g.drawArc((int) p.x-d/2, (int) p.y-d/2, d, d, 0, 360);
        g.drawLine((int) p.x+d/2, (int) p.y,(int) p.x-d/div, (int) p.y);
        g.drawLine((int) p.x-d/2, (int) p.y,(int) p.x+d/div, (int) p.y);
        g.drawLine((int) p.x, (int) p.y-d/2,(int) p.x, (int) p.y-d/div);
        g.drawLine((int) p.x, (int) p.y+d/2,(int) p.x, (int) p.y+d/div);
    }
    
}