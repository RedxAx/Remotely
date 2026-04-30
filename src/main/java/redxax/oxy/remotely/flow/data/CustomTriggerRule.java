package redxax.oxy.remotely.flow.data;

import java.util.ArrayList;
import java.util.List;

public class CustomTriggerRule {
    private boolean enabled = true;
    private int priority = 0;
    private String cooldownScope = "player";
    private int cooldownTicks = 0;
    private String permission = "";
    private boolean cancelEvent = false;
    private boolean consumeEvent = false;
    private boolean requireSneaking = false;
    private boolean requireOnGround = false;
    private String handFilter = "any";
    private String targetFilter = "any";
    private List<String> allowedWorlds = new ArrayList<>();
    private List<String> deniedWorlds = new ArrayList<>();
    private double chancePercent = 100.0;
    private int maxActivationsPerTick = 0;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public String getCooldownScope() { return cooldownScope; }
    public void setCooldownScope(String cooldownScope) { this.cooldownScope = cooldownScope; }
    public int getCooldownTicks() { return cooldownTicks; }
    public void setCooldownTicks(int cooldownTicks) { this.cooldownTicks = cooldownTicks; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public boolean isCancelEvent() { return cancelEvent; }
    public void setCancelEvent(boolean cancelEvent) { this.cancelEvent = cancelEvent; }
    public boolean isConsumeEvent() { return consumeEvent; }
    public void setConsumeEvent(boolean consumeEvent) { this.consumeEvent = consumeEvent; }
    public boolean isRequireSneaking() { return requireSneaking; }
    public void setRequireSneaking(boolean requireSneaking) { this.requireSneaking = requireSneaking; }
    public boolean isRequireOnGround() { return requireOnGround; }
    public void setRequireOnGround(boolean requireOnGround) { this.requireOnGround = requireOnGround; }
    public String getHandFilter() { return handFilter; }
    public void setHandFilter(String handFilter) { this.handFilter = handFilter; }
    public String getTargetFilter() { return targetFilter; }
    public void setTargetFilter(String targetFilter) { this.targetFilter = targetFilter; }
    public List<String> getAllowedWorlds() { return allowedWorlds; }
    public void setAllowedWorlds(List<String> allowedWorlds) { this.allowedWorlds = allowedWorlds != null ? allowedWorlds : new ArrayList<>(); }
    public List<String> getDeniedWorlds() { return deniedWorlds; }
    public void setDeniedWorlds(List<String> deniedWorlds) { this.deniedWorlds = deniedWorlds != null ? deniedWorlds : new ArrayList<>(); }
    public double getChancePercent() { return chancePercent; }
    public void setChancePercent(double chancePercent) { this.chancePercent = chancePercent; }
    public int getMaxActivationsPerTick() { return maxActivationsPerTick; }
    public void setMaxActivationsPerTick(int maxActivationsPerTick) { this.maxActivationsPerTick = maxActivationsPerTick; }
}
