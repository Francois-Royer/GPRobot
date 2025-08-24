package tankbase;

import tankbase.gun.Fire;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class FireLog {

    public static List<Fire> getFireLog(String targetName) {
        return log.stream()
                  .filter(s -> s.getTarget().getName().equals(targetName))
                  .sorted((s1, s2) -> (int) (s2.getStart() - s1.getStart()))
                  .toList();
    }

    public static Optional<Fire> getFireByDirection(double direction) {
        return log.stream().filter(s -> s.getDirection() == direction).findFirst();
    }

    public static boolean logFire(Fire fire) {
        return log.add(fire);
    }

    public static boolean removeFire(Fire fire) {
        return log.remove(fire);
    }

    public static void clearFireLog() {
        log.clear();
    }

    private static final Set<Fire> log = new HashSet<>();

    private FireLog() {}
}
