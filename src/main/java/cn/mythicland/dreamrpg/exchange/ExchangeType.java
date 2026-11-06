package cn.mythicland.dreamrpg.exchange;

/**
 * Display wording only; every type uses the same item exchange operation.
 */
public enum ExchangeType {
    TASK("任务", "任务所需:", "点击提交!", "提交成功!"),
    EXCHANGE("兑换", "兑换所需:", "点击兑换!", "兑换成功!"),
    UPGRADE("升级", "升级所需:", "点击升级!", "升级成功!");

    private final String displayName;
    private final String requirements;
    private final String action;
    private final String success;

    ExchangeType(String displayName, String requirements, String action, String success) {
        this.displayName = displayName;
        this.requirements = requirements;
        this.action = action;
        this.success = success;
    }

    public String displayName() {
        return displayName;
    }

    public String requirements() {
        return requirements;
    }

    public String action() {
        return action;
    }

    public String success() {
        return success;
    }

    public ExchangeType next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
