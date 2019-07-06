package sampleex;

import robocode.*;
import static robocode.Rules.*;
import java.awt.Color;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class %s extends AdvancedRobot {
    int d=10;
    Point mostDangerousPosition;
    Point center;
    Point safestPosition;
    double forward=1;
    double offset =20;

    class Opponent {
        double x;
        double y;
        double energy;
        double speed;
        double direction;

        public Opponent(double x, double y, double energy, double speed, double direction) {
            this.x = x;
            this.y = y;
            this.energy = energy;
            this.speed = speed;
            this.direction = direction;
        }
    }

    Map<String, Opponent>opponents = new HashMap<>();

    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        center = new Point((int)getBattleFieldWidth()/2, (int)getBattleFieldHeight()/2);
        setColors(Color.red,Color.blue,Color.green);

        while(true) {
            if (getRadarTurnRemainingRadians() == 0)
                setTurnRadarRightRadians(Math.PI * 2);
            else
                execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        updateOpponents(e);

        // --- PHENOME 1 ---
        double ahead = getSafeAhead();
        // --- PHENOME 2 ---
        double turnRadarRight = %s;
        // --- PHENOME 3 ---
        double turnRight = getSafeHeading();
        // --- PHENOME 4 ---
        double turnGunRight = %s;
        // --- PHENOME 5 ---
        double fire = %s;

        out.println(String.format("ahead=%%.2f turnRight=%%.2f", ahead, turnRight));
        //out.println("turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }

    private void robotSetActions(double ahead, double turnRight, double turnGunRight, double turnRadarRight, double fire) {
        setAhead(ahead);
        setTurnRightRadians(turnRight);
        setTurnGunRightRadians(turnGunRight);
        setTurnRadarRightRadians(turnRadarRight);
        setFire(fire);
    }

    public void onRobotDeath(RobotDeathEvent event) {
        opponents.remove(event.getName());
        updatePositions();
    }

    private void updateOpponents(ScannedRobotEvent e) {
        double angle = (e.getBearingRadians() + getHeadingRadians()) * -1 + Math.PI/2;
        double x = getX() + e.getDistance() * Math.cos(angle);
        double y = getY() + e.getDistance() * Math.sin(angle);

        opponents.put(e.getName(), new Opponent(x, y, e.getEnergy(), e.getVelocity(), angle));
        updatePositions();
    }

    private void updatePositions() {
        double totalEnergy = getOpponentsEnergy();
        double x=0;
        double y=0;

        for (Opponent o: opponents.values()) {
            x += o.x * o.energy;
            y += o.y * o.energy;
        }

        mostDangerousPosition = new Point((int) (x/totalEnergy), (int) (y/totalEnergy));

        double a = opposite(getAngle(center, mostDangerousPosition));

        double c = Math.acos(getBattleFieldHeight()/getBattleFieldWidth());
        if (a > -c && a<= c) {
            x = getBattleFieldWidth()-offset;
            y = (Math.sin(a)*(getBattleFieldHeight()-2*offset) + getBattleFieldHeight())/2;
        } else if (a>c && a<Math.PI-c) {
            x = (Math.cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = getBattleFieldHeight()-offset;
        } else if (a> -Math.PI+c && a<=-c) {
            x = (Math.cos(a)*(getBattleFieldWidth()-2*offset) + getBattleFieldWidth())/2;
            y = offset;
        } else {
            x = offset;
            y=(Math.sin(a)*getBattleFieldWidth()+getBattleFieldHeight())/2;
        }
        safestPosition=new Point((int) x,(int) y);
    }

    private double getOpponentsEnergy() {
        double sum=0;
        for (Opponent o: opponents.values()) sum += o.energy;
        return sum;
    }

    private double getAngle(Point s, Point d) {
        double a = Math.acos((d.x-s.x)/s.distance(d));
        if (d.y < s.y)
            a=-a;
        return a;
    }

    private double opposite(double a) {
        if (a>0) return a-Math.PI;
        return a+Math.PI;
    }

    private double getSafeHeading() {
        double sa = getAngle(getCurrentPoint(), safestPosition);
        double ra = (-getHeadingRadians() + Math.PI / 2) %% Math.PI ;

        out.println(String.format("sa=%%.2f ra=%%.2f, heading=%%.2f", sa, ra, getHeadingRadians()));

        if (Math.abs(sa - ra) < Math.PI / 2) {
            forward=1;
            return ra - sa;
        }
        forward=-1;
        return ra - opposite(sa);
    }
    private double getSafeAhead() {
        return forward * safestPosition.distance(getCurrentPoint());
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

    public void onPaint(Graphics2D g) {
        drawCircle(g, Color.RED, mostDangerousPosition);
        drawCircle(g, Color.YELLOW, center);
        drawCircle(g, Color.GREEN, safestPosition);

        for (Opponent o: opponents.values()) {
            drawCircle(g, Color.PINK, getPoint(o));
        }
    }
    public void onHitWall(HitWallEvent e) { forward *= -1; }
    //public void onHitRobot(HitRobotEvent e) { forward *= -1; }
}