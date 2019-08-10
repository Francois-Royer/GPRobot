package sampleex;

import static robocode.util.Utils.*;

import static sampleex.GPUtils.*;

public class EnemyState {
    double x, y, direction, velocity, dist, rDirection, rVelocity, turn;
    long duration, lastFire, aliveCount;

    public EnemyState(Enemy enemy, GPBase robot, long duration) {
        this.x = enemy.getX();
        this.y = enemy.getY();
        this.direction = enemy.rDirection;
        this.velocity = enemy.scanVelocity;
        this.dist = enemy.distance(robot.getCurrentPoint());
        this.rDirection =  normalRelativeAngle(enemy.angle - trigoAngle(robot.getHeadingRadians()));
        this.rVelocity = robot.getVelocity();
        this.turn = enemy.direction - enemy.scanDirection;
        this.duration = duration;
        this.lastFire = robot.getTime()-robot.lastFireTime;
        this.aliveCount = robot.aliveCount();

    }
}
