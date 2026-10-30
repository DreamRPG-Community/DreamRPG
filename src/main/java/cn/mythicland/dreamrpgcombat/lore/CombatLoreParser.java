package cn.mythicland.dreamrpgcombat.lore;

import cn.mythicland.dreamrpgcombat.config.CombatSettings;
import cn.mythicland.dreamlib.item.LoreAttributeLine;
import cn.mythicland.dreamlib.item.LoreAttributeParseResult;
import cn.mythicland.dreamlib.item.LoreAttributeParser;
import cn.mythicland.dreamlib.item.NumericRange;
import cn.mythicland.dreamlib.text.LegacyText;

import java.util.*;
import java.util.regex.Pattern;

public final class CombatLoreParser {

    private static final double MAX_ATTRIBUTE_VALUE = 1_000_000_000.0D;
    private static final Pattern LEVEL_SUFFIX = Pattern.compile("级$");

    private final Map<String, CombatStat> aliases;
    private final Set<String> levelAliases;

    public CombatLoreParser(CombatSettings settings) {
        CombatSettings validatedSettings = Objects.requireNonNull(settings, "settings");
        Map<String, CombatStat> mutableAliases = new HashMap<>();
        for (CombatStat stat : CombatStat.values()) {
            for (String alias : stat.aliases()) {
                mutableAliases.putIfAbsent(normalize(alias), stat);
            }
            mutableAliases.putIfAbsent(normalize(validatedSettings.labelText(stat)), stat);
        }
        aliases = Map.copyOf(mutableAliases);
        Set<String> mutableLevelAliases = new HashSet<>(List.of("需要等级", "等级", "装备等级", "等级限制"));
        mutableLevelAliases.add(normalize(validatedSettings.levelRequirementLabel()));
        levelAliases = Set.copyOf(mutableLevelAliases);
    }

    private static NumericRange sum(NumericRange first, NumericRange second) {
        return new NumericRange(
                first.minimum() + second.minimum(),
                first.maximum() + second.maximum(),
                first.percent() || second.percent()
        );
    }

    private static String validate(CombatStat stat, NumericRange value) {
        if (!stat.percentageAttribute() && value.percent()) {
            return "该属性不应带百分号";
        }
        if (Math.abs(value.minimum()) > MAX_ATTRIBUTE_VALUE
                || Math.abs(value.maximum()) > MAX_ATTRIBUTE_VALUE) {
            return "属性数值超过安全上限";
        }
        return null;
    }

    private static String levelValidationError(NumericRange value) {
        if (value.percent() || !value.isFixed() || value.minimum() < 0.0D
                || Math.rint(value.minimum()) != value.minimum()) {
            return "需要等级必须是非负整数";
        }
        if (value.maximum() > MAX_ATTRIBUTE_VALUE) return "需要等级超过安全上限";
        return null;
    }

    private static String plainValue(String line) {
        String visible = LegacyText.stripColor(LegacyText.colorize(line)).trim();
        int halfWidthColon = visible.indexOf(':');
        int fullWidthColon = visible.indexOf('：');
        int separator;
        if (halfWidthColon < 0) {
            separator = fullWidthColon;
        } else if (fullWidthColon < 0) {
            separator = halfWidthColon;
        } else {
            separator = Math.min(halfWidthColon, fullWidthColon);
        }
        return separator < 0 ? "" : visible.substring(separator + 1).trim();
    }

    private NumericRange parseLevelValue(String line, LoreAttributeParseResult result) {
        if (result.isValid()) return result.attribute().orElseThrow().value();
        String value = LEVEL_SUFFIX.matcher(plainValue(line)).replaceFirst("").trim();
        return NumericRange.parse(value);
    }

    public CombatItemStats parse(List<String> lore) {
        EnumMap<CombatStat, NumericRange> values = new EnumMap<>(CombatStat.class);
        List<String> invalidLines = new java.util.ArrayList<>();
        long requiredLevel = -1L;
        for (String line : lore) {
            LoreAttributeParseResult result = LoreAttributeParser.parseLine(line);
            if (result.status() == LoreAttributeParseResult.Status.NOT_ATTRIBUTE) continue;

            String label = result.label().map(this::normalize).orElse("");
            CombatStat stat = aliases.get(label);
            if (stat == null && levelAliases.contains(label)) {
                try {
                    NumericRange level = parseLevelValue(line, result);
                    String validationError = levelValidationError(level);
                    if (validationError != null) {
                        invalidLines.add(line + " (" + validationError + ")");
                        continue;
                    }
                    requiredLevel = Math.max(requiredLevel, (long) level.minimum());
                } catch (IllegalArgumentException exception) {
                    invalidLines.add(line + " (" + exception.getMessage() + ")");
                }
                continue;
            }
            if (stat == null) continue;

            if (!result.isValid()) {
                invalidLines.add(line + " (" + result.error().orElse("格式无效") + ")");
                continue;
            }

            LoreAttributeLine attribute = result.attribute().orElseThrow();
            String validationError = validate(stat, attribute.value());
            if (validationError != null) {
                invalidLines.add(line + " (" + validationError + ")");
                continue;
            }
            values.merge(stat, attribute.value(), CombatLoreParser::sum);
        }
        return new CombatItemStats(values, invalidLines, requiredLevel);
    }

    private String normalize(String label) {
        return LoreAttributeParser.normalizeLabel(label).toLowerCase(Locale.ROOT);
    }
}
