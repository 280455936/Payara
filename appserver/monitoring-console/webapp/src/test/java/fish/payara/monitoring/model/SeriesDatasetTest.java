package fish.payara.monitoring.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;

import org.junit.Test;

import fish.payara.monitoring.model.EmptyDataset;
import fish.payara.monitoring.model.Series;
import fish.payara.monitoring.model.SeriesDataset;

/**
 * Tests the basic correctness of {@link SeriesDataset} implementation, in particular the correctness of the sliding
 * window mechanism.
 * 
 * @author Jan Bernitt
 */
public class SeriesDatasetTest {

    private static final Series SERIES = new Series("test");

    @Test
    public void fillToCapacity() {
        int capacity = 3;
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        assertEquals(capacity, set.capacity());
        assertEquals(0, set.size());
        set = set.add(1, 1);
        assertValues(set, 1);
        set = set.add(2, 2);
        assertValues(set, 1, 2);
        set = set.add(3, 3);
        assertValues(set, 1, 2, 3);
    }

    @Test
    public void fillAndSlideByCapacity() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        set = set.add(1, 1);
        set = set.add(2, 2);
        set = set.add(3, 3);
        // now capacity is reached
        assertValues(set, 1, 2, 3);
        set = set.add(4, 4);
        assertValues(set, 2, 3, 4);
        set = set.add(5, 5);
        assertValues(set, 3, 4, 5);
        set = set.add(6, 6);
        assertValues(set, 4, 5, 6);
    }

    @Test
    public void fillAndSlideOverCapacity() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        set = set.add(1, 1);
        SeriesDataset set1 = set;
        set = set.add(2, 2);
        set = set.add(3, 3);
        // capacity reached
        set = set.add(4, 4);
        set = set.add(5, 5);
        set = set.add(6, 6);
        assertFalse(set1.isOutdated());
        assertValues(set, 4, 5, 6);
        // did slide by capacity 
        set = set.add(7, 7);
        assertTrue(set.isOutdated());
        assertValues(set, 5, 6, 7);
        set = set.add(8, 8);
        assertValues(set, 6, 7, 8);
        set = set.add(9, 9);
        assertValues(set, 7, 8, 9);
        set = set.add(10, 10);
        assertValues(set, 8, 9, 10);
    }

    @Test
    public void fillAndSlideManyTimesOverCapacity() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        for (int i = 0; i < 100; i++) {
            set = set.add(i, i);
        }
        assertValues(set, 97, 98, 99);
        assertEquals(100, set.getObservedValues());
        assertEquals(100, set.getObservedValueChanges());
        assertEquals(BigInteger.valueOf(49), set.getObservedAvg());
        assertEquals(99L, set.getObservedMax());
        assertEquals(0L, set.getObservedMin());
    }

    @Test
    public void constantToPartialDataset() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        assertEquals(0, set.size());
        long t = 0;
        set = set.add(t++, 5);
        assertValues(set, false, 5);
        for (int i = 0; i < 20; i++) {
            set = set.add(t++, 5);
        }
        assertValues(set, false, 5, 5);
        assertEquals(0, set.getStableSince());
        assertEquals(21, set.getStableCount());
        assertEquals(5, set.getObservedMin());
        assertEquals(5, set.getObservedMax());
        assertEquals(BigInteger.valueOf(5), set.getObservedAvg());
        set = set.add(t++, 6);
        assertValues(set, false, 5, 5, 6);
        assertEquals(21, set.getStableSince());
        assertEquals(1, set.getStableCount());
        assertEquals(5, set.getObservedMin());
        assertEquals(6, set.getObservedMax());
        assertEquals(BigInteger.valueOf(5), set.getObservedAvg());
        assertEquals(22, set.getObservedValues());
        assertEquals(2, set.getObservedValueChanges());
        assertEquals(3, set.capacity());
    }

    @Test
    public void constantDataset() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        assertEquals(0, set.size());
        long t = 0;
        set = set.add(t++, 5);
        assertValues(set, false, 5);
        for (int i = 0; i < 20; i++) {
            set = set.add(t++, 5);
            assertEquals(2, set.size());
        }
        assertValues(set, false, 5, 5);
        assertEquals(0, set.getStableSince());
        assertEquals(21, set.getStableCount());
        assertEquals(20, set.points()[2]);
        assertEquals(21, set.getObservedValues());
        assertEquals(1, set.getObservedValueChanges());
        assertEquals(BigInteger.valueOf(5), set.getObservedAvg());
        assertEquals(5, set.getObservedMin());
        assertEquals(5, set.getObservedMax());
        set = set.add(20, 5);
        assertEquals(20, set.points()[2]);
        assertEquals(3, set.capacity());
    }

    @Test
    public void partialToStableDataset() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        set = set.add(1, 1);
        set = set.add(2, 2);
        set = set.add(3, 3);
        assertEquals(3, set.size());
        set = set.add(4, 3);
        assertEquals(3, set.size());
        set = set.add(5, 3);
        assertEquals(3, set.size());
        set = set.add(6, 3);
        assertEquals(3, set.size());
        set = set.add(7, 3);
        assertEquals(2, set.size());
        assertEquals(3, set.getStableSince());
        assertEquals(5, set.getStableCount());
        assertEquals(3, set.capacity());
    }

    @Test
    public void stableToPartialDataset() {
        SeriesDataset set = new EmptyDataset(SERIES, 3);
        set = set.add(1, 1);
        set = set.add(2, 2);
        // at this point we are partial
        set = set.add(3, 3);
        set = set.add(4, 3);
        set = set.add(5, 3);
        set = set.add(6, 3);
        set = set.add(7, 3);
        // now stable again
        assertEquals(2, set.size());
        assertEquals(3, set.capacity());
        assertValues(set.add(8, 3), false, 3, 3);
        assertValues(set.add(8, 4), false, 3, 3, 4);
        assertEquals(3, set.add(8, 4).capacity());
    }

    private static void assertValues(SeriesDataset set, long... values) {
        assertValues(set, true, values);
    }

    private static void assertValues(SeriesDataset set, boolean timesAsWell, long... values) {
        int size = set.size();
        assertEquals(values.length, size);
        long[] points = set.points();
        for (int i = 0; i < size; i++) {
            if (timesAsWell) {
                assertEquals(values[i], points[i*2]);
            }
            assertEquals(values[i], points[i*2+1]);
        }
    }
}
