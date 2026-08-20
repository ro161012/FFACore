package dev.ro161012.ffacore.customitem;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Custom items supplied by FFACore. The model-data values are reserved for
 * these items in the companion resource pack.
 */
public enum CustomItemType {
    DASH_SWORD("dash_sword", "DASH SWORD", Material.NETHERITE_SWORD, 910001, 0xB995F5),
    FROST_SWORD("frost_sword", "FROST SWORD", Material.NETHERITE_SWORD, 910002, 0x9DEBFF),
    STRIKE_SWORD("strike_sword", "STRIKE SWORD", Material.NETHERITE_SWORD, 910003, 0xF4D34A),
    LIFESTEALER_SWORD("lifestealer_sword", "LIFESTEALER SWORD", Material.NETHERITE_SWORD, 910004, 0xFF6B78),
    ADRENALINE_BLADE("adrenaline_blade", "ADRENALINE BLADE", Material.NETHERITE_SWORD, 910005, 0x48D7FF),
    FLUX_SWORD("flux_sword", "FLUX SWORD", Material.NETHERITE_SWORD, 910006, 0x55B7FF),
    PIGXALIUR("pigxaliur", "PIGXALIUR", Material.NETHERITE_SWORD, 910007, 0x58E36B),
    ROCKET_SPEAR("rocket_spear", "ROCKET SPEAR", Material.NETHERITE_SPEAR, 910101, 0xFF805A),
    VENOM_SPEAR("venom_spear", "VENOM SPEAR", Material.NETHERITE_SPEAR, 910102, 0x70E85A),
    DASH_SPEAR("dash_spear", "DASH SPEAR", Material.NETHERITE_SPEAR, 910103, 0xFF78C8),
    VAULT_SPEAR("vault_spear", "VAULT SPEAR", Material.NETHERITE_SPEAR, 910104, 0x81C8FF),
    PAXE("paxe", "PAXE", Material.NETHERITE_AXE, 910201, 0xFFB347),
    SEISMIC_AXE("seismic_axe", "SEISMIC AXE", Material.NETHERITE_AXE, 910202, 0xD89224),
    COB_AXE("cob_axe", "COB AXE", Material.NETHERITE_AXE, 910203, 0xE6E6E6),
    MAGMA_AXE("magma_axe", "MAGMA AXE", Material.NETHERITE_AXE, 910204, 0xFF8A3D),
    WHIRL_AXE("whirl_axe", "WHIRL AXE", Material.NETHERITE_AXE, 910205, 0xBFF7EE),
    GRAPPLE_BOW("grapple_bow", "GRAPPLE BOW", Material.BOW, 910301, 0xB886FF),
    VOLLEY_BOW("volley_bow", "VOLLEY BOW", Material.BOW, 910302, 0xFFE070),
    EARTHQUAKE_MACE("earthquake_mace", "EARTHQUAKE", Material.MACE, 910401, 0xE0A51A),
    COB_MACE("cob_mace", "COB MACE", Material.MACE, 910402, 0xE9E9E9),
    WITHER_MACE("wither_mace", "WITHER MACE", Material.MACE, 910403, 0xA68B99),
    DASH_MACE("dash_mace", "DASH MACE", Material.MACE, 910404, 0x69E86A);

    private final String key;
    private final String displayName;
    private final Material material;
    private final int modelData;
    private final int nameColor;

    CustomItemType(final String key, final String displayName, final Material material,
                   final int modelData, final int nameColor) {
        this.key = key;
        this.displayName = displayName;
        this.material = material;
        this.modelData = modelData;
        this.nameColor = nameColor;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public Material material() {
        return material;
    }

    public int modelData() {
        return modelData;
    }

    public int nameColor() {
        return nameColor;
    }

    public static CustomItemType fromKey(final String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        final String normalized = input.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replace(' ', '_');
        for (final CustomItemType type : values()) {
            if (type.key.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
