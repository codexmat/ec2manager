package io.github.codexmat;

import hudson.Extension;
import hudson.model.Computer;
import hudson.model.PeriodicWork;
import hudson.model.Queue;
import hudson.slaves.OfflineCause;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.DataBoundSetter;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.logging.Logger;

@Extension
public class EC2IdleMonitor extends PeriodicWork {
    private static final Logger LOGGER = Logger.getLogger(EC2IdleMonitor.class.getName());

    private static final long IDLE_TIMEOUT = 30 * 60 * 1000; // 30-minute idle timeout
    private static final ZoneId EASTERN_TIME = ZoneId.of("America/New_York");

    private int checkPeriodMinutes = 5; // Default: 5 minutes

    @Override
    public long getRecurrencePeriod() {
        return checkPeriodMinutes > 0 ? checkPeriodMinutes * 60 * 1000 : Long.MAX_VALUE; // Skip execution if checkPeriodMinutes = 0
    }

    /**
     * Allow users to set the check period dynamically.
     */
    public int getCheckPeriodMinutes() {
        return checkPeriodMinutes;
    }

    @DataBoundSetter
    public void setCheckPeriodMinutes(int minutes) {
        if (minutes < 0) {
            LOGGER.warning("⚠️ checkPeriodMinutes cannot be negative. Defaulting to 5 minutes.");
            this.checkPeriodMinutes = 5;
        } else {
            this.checkPeriodMinutes = minutes;
        }
        LOGGER.info("✅ EC2IdleMonitor check period set to: " + this.checkPeriodMinutes + " minutes.");
    }

    @Override
    protected void doRun() {
        if (checkPeriodMinutes == 0) {
            LOGGER.info("⏳ EC2IdleMonitor is disabled (checkPeriodMinutes = 0). Skipping execution.");
            return; // Exit early to prevent unnecessary API calls
        }

        LOGGER.info("🔍 Running EC2 idle check every " + checkPeriodMinutes + " minutes...");

        LocalDateTime now = LocalDateTime.now(EASTERN_TIME);
        boolean isBusinessDay = now.getDayOfWeek() != DayOfWeek.SATURDAY && now.getDayOfWeek() != DayOfWeek.SUNDAY;
        boolean isStartTime = now.getHour() == 8 && now.getMinute() >= 30 && now.getMinute() < 35; // 8:30 - 8:35 AM ET

        for (Computer computer : Jenkins.get().getComputers()) {
            if (computer == null || computer.getNode() == null) continue;

            EC2NodeProperty prop = computer.getNode().getNodeProperties().get(EC2NodeProperty.class);
            if (prop == null || prop.getInstanceId().isEmpty()) continue;

            try {
                long currentTime = System.currentTimeMillis();
                long idleTime = currentTime - computer.getIdleStartMilliseconds();
                boolean isIdle = computer.isIdle();
                boolean hasExecutors = computer.countExecutors() > 0;
                boolean isOffline = computer.isOffline();
                boolean autoStart = prop.isAutoStart();

                LOGGER.info("🖥 Node: " + computer.getName() +
                        " | Idle: " + isIdle +
                        " | Executors: " + computer.countExecutors() +
                        " | Busy: " + computer.countBusy() +
                        " | Offline: " + isOffline +
                        " | Idle Time: " + (idleTime / 1000) + " sec" +
                        " | autoStart: " + autoStart);

                // 🛑 Stop EC2 if idle for 30 minutes
                if (isIdle && hasExecutors && idleTime >= IDLE_TIMEOUT) {
                    LOGGER.info("⏳ Stopping EC2 instance due to 30-minute idle timeout: " + prop.getInstanceId());
                    prop.stopEC2Instance();
                    computer.setTemporarilyOffline(true, new OfflineCause.ByCLI("Instance stopped due to idleness"));
                    LOGGER.info("✅ EC2 instance stopped: " + prop.getInstanceId());
                }

                // 🚀 Auto-start EC2 at 8:30 AM on business days **only if autoStart is enabled**
                else if (isBusinessDay && isStartTime && isOffline && autoStart) {
                    LOGGER.info("⏰ It's 8:30 AM ET on a business day. AutoStart is enabled. Starting EC2 instance: " + prop.getInstanceId());
                    prop.startEC2Instance();
                    computer.setTemporarilyOffline(false, null);
                    LOGGER.info("✅ EC2 instance started: " + prop.getInstanceId());
                }

                // 🚀 Auto-start EC2 when a job is waiting in the queue (regardless of autoStart)
                else if (isOffline && isJobQueuedForAgent(computer)) {
                    LOGGER.info("📢 Job is waiting that requires agent: " + computer.getName() + ". Starting EC2: " + prop.getInstanceId());
                    prop.startEC2Instance();
                    computer.setTemporarilyOffline(false, null);
                    LOGGER.info("✅ EC2 instance started: " + prop.getInstanceId());
                }

            } catch (Exception e) {
                LOGGER.severe("❌ Error managing EC2 instance " + prop.getInstanceId() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * 🔍 Checks if a job is waiting in the queue that needs this agent.
     */
    private boolean isJobQueuedForAgent(Computer computer) {
        Queue queue = Jenkins.get().getQueue();
        return java.util.Arrays.stream(queue.getItems())
                .anyMatch(item -> item.getAssignedLabel() != null &&
                        item.getAssignedLabel().getNodes().stream()
                                .anyMatch(node -> node.getNodeName().equals(computer.getName())));
    }
}
