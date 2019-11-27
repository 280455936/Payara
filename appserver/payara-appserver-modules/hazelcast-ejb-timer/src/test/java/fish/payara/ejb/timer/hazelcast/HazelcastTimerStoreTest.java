package fish.payara.ejb.timer.hazelcast;

import java.util.Collection;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static java.util.Arrays.asList;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class HazelcastTimerStoreTest extends HazelcastTimerStoreTestBase {
  @Mock
  private HZTimer timer1, timer2, timer3;

  private Collection<HZTimer> timers;

  @Before
  public void setUpTimers() {
    timers = asList(timer1, timer2, timer3);
    when(timer1.getMemberName()).thenReturn("jb");
    when(timer2.getMemberName()).thenReturn("hz");
    when(timer3.getMemberName()).thenReturn("jb");
  }


  @Test
  public void twoTimersForTheSameMemberNameShallBeCountedForTheSameServerId() {
    String [] counts = callListTimers(timers, "jb");

    assertEquals("2", counts[0]);
  }

  @Test
  public void countOneTimer() {
    String [] counts = callListTimers(timers, "hz");

    assertEquals("1", counts[0]);
  }

  @Test
  public void noNullsExpectedInCountsForMissingTimers() {
    String [] counts = callListTimers(timers, "jb", "ltd", "hz");

    for (String count : counts) {
      assertNotNull("Even for missing timers/server ids no null is expected but rather some representation of zero", count);
    }
  }

  @Test
  public void countersShallFollowServerIdOrder() {
    String [] counts = callListTimers(timers, "hz", "ltd", "jb");

    assertEquals("1", counts[0]);
    assertEquals("0", counts[1]);
    assertEquals("2", counts[2]);
  }
}

