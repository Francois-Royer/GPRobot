package sampleex;

import robocode.*;
import static robocode.Rules.*;
import java.awt.Color;

public class %s extends AdvancedRobot {

    public void run() {

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        setColors(Color.red,Color.blue,Color.green);
        while(true)
            turnRadarRight(Double.POSITIVE_INFINITY);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // --- PHENOME 1 ---
        double ahead = %s;
        // --- PHENOME 2 ---
        double turnRadarRight = %s;
        // --- PHENOME 3 ---
        double turnRight = %s;
        // --- PHENOME 4 ---
        double turnGunRight = %s;
        // --- PHENOME 5 ---
        double fire = %s;

        //out.println("ahead=" +ahead+ ", fire=" + fire);
        //out.println("osr turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        out.println("turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }

    /*public void onHitByBullet(HitByBulletEvent e) {
        // --- PHENOME 6 ---
        double ahead = %s;
        // --- PHENOME 7 ---
        double turnRadarRight = %s;
        // --- PHENOME 8 ---
        double turnRight = %s;
        // --- PHENOME 9 ---
        double turnGunRight = %s;
        // --- PHENOME 10 ---
        double fire = %s;

        //out.println("ohbb ahead=" +ahead+ ", fire=" + fire);
        //out.println("ohbb turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);
    }

    public void onHitRobot(HitRobotEvent e) {
        // --- PHENOME 11 ---
        double ahead = %s;
        // --- PHENOME 12 ---
        double turnRadarRight = %s;
        // --- PHENOME 13 ---
        double turnRight = %s;
        // --- PHENOME 14 ---
        double turnGunRight = turnRadarRight + %s;
        // --- PHENOME 15 ---
        double fire = %s;

        //out.println("ohbb ahead=" +ahead+ ", fire=" + fire);
        //out.println("ohbb turnRight=" +turnRight+ ", turnGunRight=" + turnGunRight + ", turnRadarRight=" + turnRadarRight);
        robotSetActions(ahead, turnRight, turnGunRight, turnRadarRight, fire);*/
    }

    private void robotSetActions(double ahead, double turnRight, double turnGunRight, double turnRadarRight, double fire) {
        setAhead(ahead);
        setTurnRightRadians(turnRight);
        setTurnGunRightRadians(turnGunRight);
        setTurnRadarRightRadians(turnRadarRight);
        setFire(fire);
    }
}