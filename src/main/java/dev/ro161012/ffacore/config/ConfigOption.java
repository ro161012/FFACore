package dev.ro161012.ffacore.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;
import java.util.Locale;

/**
 * Describes a single editable entry of the FFACore configuration.
 *
 * <p>Each option maps a dialog input key to a {@code config.yml} path and
 * knows how to read the current value so the in-game dialog can prefill it.
 * Values are read back from the {@code DialogResponseView} by kind on save.
 */
public final class ConfigOption {

    /**
     * The kind of value an option holds, which drives both the dialog input
     * widget and the read-back from the response view.
     */
    public enum Kind {
        BOOLEAN, INTEGER, STRING, ENUM
    }

    private final String path;
    private final Kind kind;
    private final String label;
    private final String description;
    private final float min;
    private final float max;
    private final List<String> choices;
    private final Object defaultValue;

    /**
     * Creates a numeric option.
     *
     * @param path        config.yml path
     * @param kind        INTEGER or BOOLEAN-style numeric kind
     * @param label       dialog label
     * @param description tooltip text
     * @param min         minimum slider value
     * @param max         maximum slider value
     * @param defaultValue fallback when the key is missing
     */
    private ConfigOption(final String path, final Kind kind, final String label,
                         final String description, final float min, final float max,
                         final List<String> choices, final Object defaultValue) {
        this.path = path;
        this.kind = kind;
        this.label = label;
        this.description = description;
        this.min = min;
        this.max = max;
        this.choices = choices;
        this.defaultValue = defaultValue;
    }

    /**
     * Creates a boolean option.
     *
     * @param path        config.yml path
     * @param label       dialog label
     * @param description tooltip text
     * @param defaultValue fallback when the key is missing
     * @return the option
     */
    public static ConfigOption bool(final String path, final String label,
                                    final String description, final boolean defaultValue) {
        return new ConfigOption(path, Kind.BOOLEAN, label, description, 0f, 0f,
                List.of(), defaultValue);
    }

    /**
     * Creates an integer option.
     *
     * @param path        config.yml path
     * @param label       dialog label
     * @param description tooltip text
     * @param min         minimum value
     * @param max         maximum value
     * @param defaultValue fallback when the key is missing
     * @return the option
     */
    public static ConfigOption integer(final String path, final String label,
                                       final String description, final int min,
                                       final int max, final int defaultValue) {
        return new ConfigOption(path, Kind.INTEGER, label, description, min, max,
                List.of(), defaultValue);
    }

    /**
     * Creates a free-text option.
     *
     * @param path        config.yml path
     * @param label       dialog label
     * @param description tooltip text
     * @param defaultValue fallback when the key is missing
     * @return the option
     */
    public static ConfigOption string(final String path, final String label,
                                      final String description, final String defaultValue) {
        return new ConfigOption(path, Kind.STRING, label, description, 0f, 0f,
                List.of(), defaultValue);
    }

    /**
     * Creates an enum option rendered as a single-choice selector.
     *
     * @param path        config.yml path
     * @param label       dialog label
     * @param description tooltip text
     * @param choices     allowed values, in display order
     * @param defaultValue fallback when the key is missing
     * @return the option
     */
    public static ConfigOption enumOption(final String path, final String label,
                                          final String description,
                                          final List<String> choices,
                                          final String defaultValue) {
        return new ConfigOption(path, Kind.ENUM, label, description, 0f, 0f,
                List.copyOf(choices), defaultValue);
    }

    /**
     * Returns a dialog-safe input key derived from the config path. Only
     * lowercase letters, digits and underscores are kept - Minecraft's
     * dialog inputs reject any other character (including hyphens) with
     * "key must be a valid input name".
     *
     * @return the input key
     */
    public String key() {
        return path.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase(Locale.ROOT);
    }

    /**
     * Returns the current value of this option from the given configuration.
     *
     * @param config the plugin configuration
     * @return the current value
     */
    public Object currentValue(final FileConfiguration config) {
        return switch (kind) {
            case BOOLEAN -> config.getBoolean(path, (Boolean) defaultValue);
            case INTEGER -> config.getInt(path, ((Number) defaultValue).intValue());
            case STRING, ENUM -> config.getString(path, (String) defaultValue);
        };
    }

    public String path() {
        return path;
    }

    public Kind kind() {
        return kind;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public List<String> choices() {
        return choices;
    }

    public Object defaultValue() {
        return defaultValue;
    }
}
