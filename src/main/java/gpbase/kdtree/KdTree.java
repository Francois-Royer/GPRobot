package gpbase.kdtree;

import java.util.LinkedList;

/**
 *
 */
public class KdTree<T> extends KdNode<T> {

    LinkedList<KdEntry<T>> stack = new  LinkedList<>();

    public KdTree(int dimensions) {
        this(dimensions, 24);
    }

    public KdTree(int dimensions, int bucketCapacity) {
        super(dimensions, bucketCapacity);
    }

    public NearestNeighborIterator<T> getNearestNeighborIterator(double[] searchPoint, int maxPointsReturned, DistanceFunction distanceFunction) {
        return new NearestNeighborIterator<T>(this, searchPoint, maxPointsReturned, distanceFunction);
    }

    public MaxHeap<KdEntry<T>> findNearestNeighbors(double[] searchPoint, int maxPointsReturned, DistanceFunction distanceFunction) {
        BinaryHeap.Min<KdNode<T>> pendingPaths = new BinaryHeap.Min<>();
        BinaryHeap.Max<KdEntry<T>> evaluatedPoints = new BinaryHeap.Max<>();
        int pointsRemaining = Math.min(maxPointsReturned, size());
        pendingPaths.offer(0, this);

        while (pendingPaths.size() > 0 && (evaluatedPoints.size() < pointsRemaining || (pendingPaths.getMinKey() < evaluatedPoints.getMaxKey()))) {
            nearestNeighborSearchStep(pendingPaths, evaluatedPoints, pointsRemaining, distanceFunction, searchPoint);
        }

        return evaluatedPoints;
    }

    protected static <T> void nearestNeighborSearchStep (
        MinHeap<KdNode<T>> pendingPaths, MaxHeap<KdEntry<T>> evaluatedPoints, int desiredPoints,
        DistanceFunction distanceFunction, double[] searchPoint) {
        // If there are pending paths possibly closer than the nearest evaluated point, check it out
        KdNode<T> cursor = pendingPaths.getMin();
        pendingPaths.removeMin();

        // Descend the tree, recording paths not taken
        while (!cursor.isLeaf()) {
            KdNode<T> pathNotTaken;
            if (searchPoint[cursor.splitDimension] > cursor.splitValue) {
                pathNotTaken = cursor.left;
                cursor = cursor.right;
            }
            else {
                pathNotTaken = cursor.right;
                cursor = cursor.left;
            }
            double otherDistance = distanceFunction.distanceToRect(searchPoint, pathNotTaken.minBound, pathNotTaken.maxBound);
            // Only add a path if we either need more points or it's closer than furthest point on list so far
            if (evaluatedPoints.size() < desiredPoints || otherDistance <= evaluatedPoints.getMaxKey()) {
                pendingPaths.offer(otherDistance, pathNotTaken);
            }
        }

        if (cursor.singlePoint && cursor.size()>0) {
            double nodeDistance = distanceFunction.distance(getCoordinates(cursor, 0), searchPoint);
            // Only add a point if either need more points or it's closer than furthest on list so far
            if (evaluatedPoints.size() < desiredPoints || nodeDistance <= evaluatedPoints.getMaxKey()) {
                for (int i = 0; i < cursor.size(); i++) {
                    KdEntry<T> kdEntry = cursor.points[i];

                    // If we don't need any more, replace max
                    if (evaluatedPoints.size() == desiredPoints) {
                        evaluatedPoints.replaceMax(nodeDistance, kdEntry);
                    } else {
                        evaluatedPoints.offer(nodeDistance, kdEntry);
                    }
                }
            }
        } else {
            // Add the points at the cursor
            for (int i = 0; i < cursor.size(); i++) {
                double[] point = cursor.points[i].getCoordinates();
                KdEntry<T> kdEntry = cursor.points[i];
                double distance = distanceFunction.distance(point, searchPoint);
                // Only add a point if either need more points or it's closer than furthest on list so far
                if (evaluatedPoints.size() < desiredPoints) {
                    evaluatedPoints.offer(distance, kdEntry);
                } else if (distance < evaluatedPoints.getMaxKey()) {
                    evaluatedPoints.replaceMax(distance, kdEntry);
                }
            }
        }
    }

    static private double[] getCoordinates(KdNode cursor, int i) {
        if (cursor.points[i] == null) return new double[0];
        return cursor.points[i].getCoordinates();
    }

    @Override
    public KdEntry<T> addPoint(double[] point, T data) {
        KdEntry<T> kdEntry = super.addPoint(point, data);
        stack.addFirst(kdEntry);
        return  kdEntry;
    }

    public LinkedList<KdEntry<T>> getStack() {
        return  stack;
    }
}
