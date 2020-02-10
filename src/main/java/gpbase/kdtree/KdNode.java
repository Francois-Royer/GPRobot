package gpbase.kdtree;

import java.util.Arrays;

/**
 *
 */
class KdNode<T> {
    // All types
    protected int dimensions;
    protected int bucketCapacity;
    protected int size;

    // Leaf only
    protected KdEntry<T>[]points;

    // Stem only
    protected KdNode<T> left, right;
    protected int splitDimension;
    protected double splitValue;

    // Bounds
    protected double[] minBound, maxBound;
    protected boolean singlePoint;

    protected KdNode(int dimensions, int bucketCapacity) {
        // Init base
        this.dimensions = dimensions;
        this.bucketCapacity = bucketCapacity;
        this.size = 0;
        this.singlePoint = true;

        // Init leaf elements
        this.points = new KdEntry[bucketCapacity+1];
    }

    /* -------- SIMPLE GETTERS -------- */

    public int size() {
        return size;
    }

    public boolean isLeaf() {
        return points != null;
    }

    /* -------- OPERATIONS -------- */

    public KdEntry<T> addPoint(double[] point, T value) {
        KdNode<T> cursor = this;
        while (!cursor.isLeaf()) {
            cursor.extendBounds(point);
            cursor.size++;
            if (point[cursor.splitDimension] > cursor.splitValue) {
                cursor = cursor.right;
            }
            else {
                cursor = cursor.left;
            }
        }
        return cursor.addLeafPoint(point, value);
    }

    /* -------- INTERNAL OPERATIONS -------- */
    private KdEntry<T> addLeafPoint(double[] point, T value) {
        // Add the data point
        KdEntry<T> kdEntry = new KdEntry(point, value);
        points[size] = kdEntry;
        extendBounds(point);
        size++;

        if (size == points.length - 1) {
            // If the node is getting too large
            if (calculateSplit()) {
                // If the node successfully had it's split value calculated, split node
                splitLeafNode();
            } else {
                // If the node could not be split, enlarge node
                increaseLeafCapacity();
            }
        }
        return kdEntry;
    }

    private boolean checkBounds(double[] point) {
        for (int i = 0; i < dimensions; i++) {
            if (point[i] > maxBound[i]) return false;
            if (point[i] < minBound[i]) return false;
        }
        return true;
    }

    private void extendBounds(double[] point) {
        if (minBound == null) {
            minBound = Arrays.copyOf(point, dimensions);
            maxBound = Arrays.copyOf(point, dimensions);
            return;
        }

        for (int i = 0; i < dimensions; i++) {
            if (Double.isNaN(point[i])) {
                if (!Double.isNaN(minBound[i]) || !Double.isNaN(maxBound[i])) {
                    singlePoint = false;
                }
                minBound[i] = Double.NaN;
                maxBound[i] = Double.NaN;
            }
            else if (minBound[i] > point[i]) {
                minBound[i] = point[i];
                singlePoint = false;
            }
            else if (maxBound[i] < point[i]) {
                maxBound[i] = point[i];
                singlePoint = false;
            }
        }
    }

    private void increaseLeafCapacity() {
        points = Arrays.copyOf(points, points.length*2);
    }

    private boolean calculateSplit() {
        if (singlePoint) return false;

        double width = 0;
        for (int i = 0; i < dimensions; i++) {
            double dwidth = (maxBound[i] - minBound[i]);
            if (Double.isNaN(dwidth)) dwidth = 0;
            if (dwidth > width) {
                splitDimension = i;
                width = dwidth;
            }
        }

        if (width == 0) {
            return false;
        }

        // Start the split in the middle of the variance
        splitValue = (minBound[splitDimension] + maxBound[splitDimension]) * 0.5;

        // Never split on infinity or NaN
        if (splitValue == Double.POSITIVE_INFINITY) {
            splitValue = Double.MAX_VALUE;
        }
        else if (splitValue == Double.NEGATIVE_INFINITY) {
            splitValue = -Double.MAX_VALUE;
        }

        // Don't let the split value be the same as the upper value as
        // can happen due to rounding errors!
        if (splitValue == maxBound[splitDimension]) {
            splitValue = minBound[splitDimension];
        }

        // Success
        return true;
    }

    private void splitLeafNode() {
        right = new KdNode<T>(dimensions, bucketCapacity);
        left = new KdNode<T>(dimensions, bucketCapacity);

        // Move locations into children
        for (int i = 0; i < size; i++) {
            double[] oldLocation = points[i].getCoordinates();
            T oldData = points[i].getData();
            if (oldLocation[splitDimension] > splitValue) {
                right.addLeafPoint(oldLocation, oldData);
            }
            else {
                left.addLeafPoint(oldLocation, oldData);
            }
        }

        points = null;
    }

    public int getDimensions() {
        return dimensions;
    }
}
