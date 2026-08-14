package dev.ro161012.ffacore.storage;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Stores a snapshot of a single block for regeneration.
 * Uses a compact binary format:
 *   int x (relative), int y (relative), int z (relative)
 *   short materialId
 *   String blockData (compressed)
 *   String inventoryData (nullable, base64)
 *   String tileEntityData (nullable, base64)
 */
public class BlockSnapshot {

    private final int relX;
    private final int relY;
    private final int relZ;
    private final Material material;
    private final String blockData;
    private final String inventoryData;    // Base64-encoded Bukkit serialization
    private final String tileEntityData;   // Reserved for future use

    public BlockSnapshot(int relX, int relY, int relZ, Material material,
                         String blockData, String inventoryData, String tileEntityData) {
        this.relX = relX;
        this.relY = relY;
        this.relZ = relZ;
        this.material = material;
        this.blockData = blockData;
        this.inventoryData = inventoryData;
        this.tileEntityData = tileEntityData;
    }

    /**
     * Create a snapshot from a Block.
     */
    public static BlockSnapshot fromBlock(Block block, Location origin) {
        int relX = block.getX() - origin.getBlockX();
        int relY = block.getY() - origin.getBlockY();
        int relZ = block.getZ() - origin.getBlockZ();

        Material mat = block.getType();

        // Handle void/air efficiently - don't save block data for air
        String bd = block.getBlockData().getAsString();

        // Serialize inventory if present
        String inv = null;
        BlockState state = block.getState();
        if (state instanceof BlockInventoryHolder) {
            Inventory inventory = ((BlockInventoryHolder) state).getInventory();
            inv = inventoryToBase64(inventory.getContents());
        }

        return new BlockSnapshot(relX, relY, relZ, mat, bd, inv, null);
    }

    /**
     * Restore this snapshot to the world at the given origin.
     */
    public void restore(Location origin) {
        Location loc = origin.clone().add(relX, relY, relZ);
        Block block = loc.getBlock();

        // Set block data
        BlockData bd = org.bukkit.Bukkit.createBlockData(blockData);
        block.setBlockData(bd, false);

        // Restore inventory
        if (inventoryData != null) {
            BlockState state = block.getState();
            if (state instanceof BlockInventoryHolder) {
                Inventory inv = ((BlockInventoryHolder) state).getInventory();
                ItemStack[] contents = base64ToInventory(inventoryData);
                if (contents != null) {
                    inv.setContents(contents);
                }
                state.update(true, false);
            }
        }
    }

    public int getRelX() { return relX; }
    public int getRelY() { return relY; }
    public int getRelZ() { return relZ; }
    public Material getMaterial() { return material; }
    public String getBlockData() { return blockData; }
    public String getInventoryData() { return inventoryData; }

    /**
     * Serialize BlockData to a compact byte array.
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);

        dos.writeInt(relX);
        dos.writeInt(relY);
        dos.writeInt(relZ);
        dos.writeUTF(material.name());
        dos.writeUTF(blockData);
        dos.writeBoolean(inventoryData != null);
        if (inventoryData != null) {
            dos.writeUTF(inventoryData);
        }

        return bos.toByteArray();
    }

    /**
     * Deserialize from byte array.
     */
    public static BlockSnapshot deserialize(byte[] data) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        int x = dis.readInt();
        int y = dis.readInt();
        int z = dis.readInt();
        Material mat = Material.valueOf(dis.readUTF());
        String blockData = dis.readUTF();
        String inv = null;
        if (dis.readBoolean()) {
            inv = dis.readUTF();
        }

        return new BlockSnapshot(x, y, z, mat, blockData, inv, null);
    }

    private static String inventoryToBase64(ItemStack[] contents) {
        if (contents == null || contents.length == 0) return null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(bos);
            dos.writeInt(contents.length);
            for (ItemStack item : contents) {
                if (item == null || item.getType() == Material.AIR) {
                    dos.writeBoolean(false);
                } else {
                    dos.writeBoolean(true);
                    byte[] itemBytes = item.serializeAsBytes();
                    dos.writeInt(itemBytes.length);
                    dos.write(itemBytes);
                }
            }
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    private static ItemStack[] base64ToInventory(String base64) {
        if (base64 == null) return new ItemStack[0];
        try {
            byte[] data = Base64.getDecoder().decode(base64);
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int length = dis.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                if (dis.readBoolean()) {
                    int itemLen = dis.readInt();
                    byte[] itemBytes = new byte[itemLen];
                    dis.readFully(itemBytes);
                    items[i] = ItemStack.deserializeBytes(itemBytes);
                }
            }
            return items;
        } catch (IOException e) {
            return new ItemStack[0];
        }
    }
}
