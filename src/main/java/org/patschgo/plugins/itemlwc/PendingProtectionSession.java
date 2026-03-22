package org.patschgo.plugins.itemlwc;

final class PendingProtectionSession {
    enum Step {
        CHOOSE_TYPE,
        ENTER_PASSWORD
    }

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;
    private final long createdAt;
    private Step step;

    PendingProtectionSession(String worldName, int x, int y, int z, long createdAt) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.createdAt = createdAt;
        this.step = Step.CHOOSE_TYPE;
    }

    String getWorldName() {
        return worldName;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    int getZ() {
        return z;
    }

    long getCreatedAt() {
        return createdAt;
    }

    Step getStep() {
        return step;
    }

    void setStep(Step step) {
        this.step = step;
    }
}