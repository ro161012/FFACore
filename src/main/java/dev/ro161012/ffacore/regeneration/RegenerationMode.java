package dev.ro161012.ffacore.regeneration;

public enum RegenerationMode {
    STANDARD("Standard", "Places all blocks at once"),
    PHASED("Phased", "Restores blocks in timed phases"),
    SELECTIVE("Selective", "Only restores blocks that changed"),
    WAVE("Wave", "Restores blocks in a wave pattern"),
    WORLD_EDIT("WorldEdit", "Uses WorldEdit for fast restore");

    private final String displayName;
    private final String description;

    RegenerationMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public static RegenerationMode fromString(String s) {
        if (s == null) return STANDARD;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STANDARD;
        }
    }
}
