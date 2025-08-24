package tankbase;

import tankbase.gun.Aiming;
import tankbase.gun.Fire;

import java.awt.geom.Point2D;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static tankbase.Constant.TANK_SIZE;
import static tankbase.TankUtils.collisionCircleSegment;

public class VirtualFireLog {

    public static Collection<Fire> getVirtualFireLog() {
        return Collections.unmodifiableCollection(log);
    }

    public static void updateVirtualFires(long now) {
        List<Fire> toRemove = log.stream().filter(fire -> {
            Point2D.Double p = fire.getPosition(now);
            Aiming aiming = fire.getAimingData();
            ITank target = fire.getTarget();

            boolean remove = true;
            if (target.isAlive()) {
                if (fire.distance(p) < fire.distance(target.getState().getPosition())+ TANK_SIZE / 2) {
                    Point2D.Double o = fire.getPosition(now + 1);

                    remove = collisionCircleSegment(target.getState().getPosition(), TANK_SIZE / 2, o, p);
                    if (remove)
                        fire.getGunner().getEnemyRoundFireStat(aiming.getTarget()).hit(fire.getAimingData().getFirePower());
                } else
                    fire.getGunner().getEnemyRoundFireStat(aiming.getTarget()).miss(fire.getAimingData().getFirePower());
            }

            return remove;
        }).toList();

        toRemove.forEach(log::remove);
    }


    public static boolean logVirtualFire(Fire fire) {
        return log.add(fire);
    }

    public static void clearVirtualFireLog() {
        log.clear();
    }

    private static final Set<Fire> log = new HashSet<>();

    private VirtualFireLog() {}
}
