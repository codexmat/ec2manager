package io.github.codexmat;

import hudson.Extension;
import hudson.model.Node;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.StartInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StartInstancesResponse;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.StopInstancesResponse;
import software.amazon.awssdk.regions.Region;

import java.util.logging.Logger;

public class EC2NodeProperty extends NodeProperty<Node> {
    private static final Logger LOGGER = Logger.getLogger(EC2NodeProperty.class.getName());

    private String instanceId;
    private int idleTimeoutMinutes = 30; // Default: 30 minutes
    private boolean autoStart;
    private int checkPeriodMinutes = 5; // ✅ Default to 5 minutes

    @DataBoundConstructor
    public EC2NodeProperty(String instanceId, int idleTimeoutMinutes, boolean autoStart, int checkPeriodMinutes) {
        this.instanceId = instanceId;
        this.idleTimeoutMinutes = idleTimeoutMinutes > 0 ? idleTimeoutMinutes : 30;
        this.autoStart = autoStart;
        setCheckPeriodMinutes(checkPeriodMinutes); // Ensure proper validation
    }

    public String getInstanceId() {
        return instanceId;
    }

    @DataBoundSetter
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    public int getIdleTimeoutMinutes() {
        return idleTimeoutMinutes;
    }

    @DataBoundSetter
    public void setIdleTimeoutMinutes(int idleTimeoutMinutes) {
        this.idleTimeoutMinutes = Math.max(idleTimeoutMinutes, 1); // Ensure a positive value
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    @DataBoundSetter
    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public int getCheckPeriodMinutes() {
        return checkPeriodMinutes;
    }

    @DataBoundSetter
    public void setCheckPeriodMinutes(int checkPeriodMinutes) {
        if (checkPeriodMinutes < 0) {
            LOGGER.warning("⚠️ checkPeriodMinutes cannot be negative. Defaulting to 5 minutes.");
            this.checkPeriodMinutes = 5;
        } else {
            this.checkPeriodMinutes = checkPeriodMinutes;
        }
    }

    @Extension
    public static class DescriptorImpl extends NodePropertyDescriptor {
        @Override
        public String getDisplayName() {
            return "EC2 Instance Management";
        }
    }

    public void startEC2Instance() {
        try (Ec2Client ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .build()) {

            LOGGER.info("Starting EC2 instance: " + instanceId);

            StartInstancesResponse response = ec2Client.startInstances(StartInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build());

            LOGGER.info("✅ Successfully started EC2 instance: " + instanceId);
            response.startingInstances().forEach(instance ->
                LOGGER.info("Instance ID: " + instance.instanceId() +
                            " - Previous State: " + instance.previousState().nameAsString() +
                            " - Current State: " + instance.currentState().nameAsString())
            );

        } catch (Exception e) {
            LOGGER.severe("❌ Failed to start EC2 instance: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopEC2Instance() {
        try (Ec2Client ec2Client = Ec2Client.builder()
                .region(Region.US_EAST_1)
                .credentialsProvider(WebIdentityTokenFileCredentialsProvider.create())
                .build()) {

            LOGGER.info("Stopping EC2 instance: " + instanceId);

            StopInstancesResponse response = ec2Client.stopInstances(StopInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build());

            LOGGER.info("✅ Successfully stopped EC2 instance: " + instanceId);
            response.stoppingInstances().forEach(instance ->
                LOGGER.info("Instance ID: " + instance.instanceId() +
                            " - Previous State: " + instance.previousState().nameAsString() +
                            " - Current State: " + instance.currentState().nameAsString())
            );

        } catch (Exception e) {
            LOGGER.severe("❌ Failed to stop EC2 instance: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
