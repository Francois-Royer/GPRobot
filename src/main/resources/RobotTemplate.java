package sampleex;

import robocode.*;
import static robocode.Rules.*;
import java.awt.Color;

import static java.lang.Math.*;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;


public class %s extends AdvancedRobot {
    int d=10;
    Point unsafePosition;
    Point safePosition;
    Point center;

    double forward=1;
    double offset =30;

    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        center = new Point((int)getBattleFieldWidth()/2, (int)getBattleFieldHeight()/2);
        setColors(Color.red,Color.blue,Color.green);

        while(true) {
            if (getRadarTurnRemainingRadians() == 0)
                setTurnRadarRightRadians(PI * 2);
            else
                execute();
            updatePositions();
            setTurnRightRadians(getSafeTurn());
            setAhead(getSafeAhead());
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        updateOpponents(e);

        // --- PHENOME 1 ---
        double turnRadarRight = %s;
        // --- PHENOME 2 ---
        double turnRight = getSafeTurn();
        // --- PHENOME 3 ---
        double ahead = getSafeAhead();
        // --- PHENOME 4 ---
        double turnGunRight = %s;
        // --- PHENOME 5 ---
        double fire = %s;

        //out.println(String.format("ahead=%%.2f turnRight=%%.2f", ahead, turnRight));
        //out.println("turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }
    public void onRobotDeath(RobotDeathEvent event) {
        opponents.remove(event.getName());
        updatePositions();
    }

    public void onPaint(Graphics2D g) {
        drawCircle(g, Color.RED, unsafePosition);
        drawCircle(g, Color.YELLOW, center);
        drawCircle(g, Color.GREEN, safePosition);

        for (Opponent o: opponents.values()) {
            drawCircle(g, Color.PINK, getPoint(o));
        }
    }
    //public void onHitWall(HitWallEvent e) { forward *= -1; }
    //public void onHitRobot(HitRobotEvent e) { forward *= -1; }

    class Opponent {
        double x;
        double y;
        double energy;
        double speed;
        double direction;
        long lastUpdate;

        public Opponent(double x, double y, double energy, double speed, double direction, long lastUpdate) {
            this.x = x;
            this.y = y;
            this.energy = energy;
            this.speed = speed;
            this.direction = direction;
            this.lastUpdate = lastUpdate;
        }
    }

    Map<String, Opponent>opponents = new HashMap<>();

    private void robotSetActions(double ahead, double turnRight, double turnGunRight, double turnRadarRight, double fire) {
        setAhead(ahead);
        setTurnRightRadians(turnRight);
        setTurnGunRightRadians(turnGunRight);
        setTurnRadarRightRadians(turnRadarRight);
        setFire(fire);
    }

    private void updateOpponents(ScannedRobotEvent e) {
        double angle = (e.getBearingRadians() + getHeadingRadians()) * -1 + Math.PI/2;
        double x = getX() + e.getDistance() * cos(angle);
        double y = getY() + e.getDistance() * sin(angle);

        opponents.put(e.getName(), new Opponent(x, y, e.getEnergy(), e.getVelocity(), mod2PI(PI/2 - e.getHeadingRadians()), getTime()));
        updatePositions();
    }

    private void moveOponent(Opponent o, long now) {
        if (o.lastUpdate >= now) return;
        Double d  =  o.speed * (now-o.lastUpdate);
        o.x += d*cos(o.direction);
        o.y += d*sin(o.direction);
        o.lastUpdate=now;
    }

    private void updatePositions() {
        long now = getTime();
        double totalEnergy = getOpponentsEnergy();
        double x=0;
        double y=0;

        for (Opponent o: opponents.values()) {
            moveOponent(o, now);
            x += o.x * o.energy;
            y += o.y * o.energy;
        }

        unsafePosition = new Point((int) (x/totalEnergy), (int) (y/totalEnergy));

        double a = opposite(getAngle(center, unsafePosition));

        //out.println(String.format("a=%%.2f ", a));

        double c = acos(getBattleFieldHeight()/getBattleFieldWidth());

        if (a < c && a> -c) { // right
            x = getBattleFieldWidth()-offset;
            y = (sin(a)*(getBattleFieldHeight()-2*offset) + getBattleFieldHeight())/2;
        } else if (a>c && a<=PI-c) { // top
            x = (cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = getBattleFieldHeight()-offset;
        } else if (a< -c && a>=-PI+c) { // bottom
            x = (cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = offset;
        } else { // left
            x = offset;
            y=(sin(a)*getBattleFieldWidth()+getBattleFieldHeight())/2;
        }
        safePosition=new Point((int) x,(int) y);
        //out.println(String.format("safe x=%%d y=%%d", safePosition.x, safePosition.y));
    }

    private double getOpponentsEnergy() {
        double sum=0;
        for (Opponent o: opponents.values()) sum += o.energy;
        return sum;
    }

    // -PI -> PI
    private double getAngle(Point s, Point d) {
        double a = acos((d.x-s.x)/s.distance(d));
        if (d.y < s.y)
            a= 2*PI-a;
        return mod2PI(a);
    }

    // -PI -> PI
    private double opposite(double a) {
        return mod2PI(a+PI);
    }

    // -PI -> PI
    private double mod2PI(double a) {
        double o = a;
        while (o>PI)
            o-=2*PI;
        while (o<-PI)
            o+=2*PI;
        return o;
    }

    private double getSafeTurn() {
        double sa = getAngle(getCurrentPoint(), safePosition);
        double ra = mod2PI(PI/2 - getHeadingRadians());

        //out.println(String.format("sa=%%.2f ra=%%.2f, heading=%%.2f", sa, ra, getHeadingRadians()));

        if (abs(ra - sa) < (PI / 2) || (abs(ra - sa) >(PI *3/2))) {
            forward = 1;
            return mod2PI(ra - sa);
        }

        forward = -1;
        return ra - opposite(sa);
    }
    private double getSafeAhead() {
        return forward * safePosition.distance(getCurrentPoint());
    }

    private Point getCurrentPoint() {
        return new Point((int) getX(),(int) getY());
    }

    private Point getPoint(Opponent o) {
        return new Point((int) o.x,(int) o.y);
    }

    private void drawCircle(Graphics2D g, Color c, Point p) {
        g.setColor(c);
        g.fillArc(p.x-d/2, p.y-d/2, d, d, 0, 360);
    }

}