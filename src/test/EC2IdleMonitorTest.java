package io.github.codexmat;

import hudson.model.Computer;
import hudson.model.Queue;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EC2IdleMonitorTest {

    private EC2IdleMonitor idleMonitor;

    @Mock private Jenkins jenkins;
    @Mock private Computer mockComputer;
    @Mock private EC2NodeProperty mockProperty;
    @Mock private Queue mockQueue;

    private static final String INSTANCE_ID = "i-0384a25e81d536702";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        idleMonitor = new EC2IdleMonitor();

        when(Jenkins.get()).thenReturn(jenkins);
        when(jenkins.getComputers()).thenReturn(new Computer[]{mockComputer});
        when(jenkins.getQueue()).thenReturn(mockQueue);

        when(mockComputer.getNode()).thenReturn(mock(Node.class));
        when(mockComputer.getIdleStartMilliseconds()).thenReturn(System.currentTimeMillis() - (31 * 60 * 1000)); // 31 min idle
        when(mockComputer.isIdle()).thenReturn(true);
        when(mockComputer.countExecutors()).thenReturn(1);
        when(mockComputer.isOffline()).thenReturn(false);
        when(mockComputer.getName()).thenReturn("test-node");

        when(mockComputer.getNode().getNodeProperties()).thenReturn(Collections.singletonList(mockProperty));
        when(mockProperty.getInstanceId()).thenReturn(INSTANCE_ID);
        when(mockProperty.getIdleTimeoutMinutes()).thenReturn(30);
        when(mockProperty.isAutoStart()).thenReturn(true);
    }

    @Test
    public void testEC2StopsWhenIdleFor30Minutes() {
        idleMonitor.doRun();
        verify(mockProperty, times(1)).stopEC2Instance();
        verify(mockComputer, times(1)).setTemporarilyOffline(eq(true), any());
    }

    @Test
    public void testEC2StartsAtBusinessHourWhenOffline() {
        when(mockComputer.isOffline()).thenReturn(true);
        setMockTime(DayOfWeek.MONDAY, 8, 30); // 8:30 AM ET on a business day
        idleMonitor.doRun();
        verify(mockProperty, times(1)).startEC2Instance();
        verify(mockComputer, times(1)).setTemporarilyOffline(eq(false), isNull());
    }

    @Test
    public void testEC2StartsWhenJobIsQueued() {
        when(mockComputer.isOffline()).thenReturn(true);
        when(mockQueue.getItems()).thenReturn(new Queue.Item[]{mock(Queue.Item.class)});
        idleMonitor.doRun();
        verify(mockProperty, times(1)).startEC2Instance();
        verify(mockComputer, times(1)).setTemporarilyOffline(eq(false), isNull());
    }

    @Test
    public void testNoCheckWhenCheckPeriodIsZero() {
        idleMonitor.setCheckPeriodMinutes(0);
        idleMonitor.doRun();
        verify(mockProperty, never()).stopEC2Instance();
        verify(mockProperty, never()).startEC2Instance();
    }

    private void setMockTime(DayOfWeek day, int hour, int minute) {
        LocalDateTime mockTime = LocalDateTime.of(2025, 2, 7, hour, minute);
        ZoneId zone = ZoneId.of("America/New_York");
        when(LocalDateTime.now(zone)).thenReturn(mockTime);
    }
}
