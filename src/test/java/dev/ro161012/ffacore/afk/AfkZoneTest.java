package dev.ro161012.ffacore.afk;

import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MockBukkit tests for {@link AfkZone} region containment and sizing.
 */
class AfkZoneTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void containsLocationsInsideRegion() {
        final AfkZone zone = new AfkZone("lounge",
                new Location(world, 0, 0, 0), new Location(world, 10, 10, 10));

        assertTrue(zone.contains(new Location(world, 5, 5, 5)), "center should be inside");
        assertTrue(zone.contains(new Location(world, 0, 0, 0)), "corners should be inside");
        assertTrue(zone.contains(new Location(world, 10, 10, 10)), "opposite corner should be inside");
        assertFalse(zone.contains(new Location(world, 11, 5, 5)), "outside X should not match");
    }

    @Test
    void containsRejectsOtherWorlds() {
        final WorldMock other = server.addSimpleWorld("other");
        final AfkZone zone = new AfkZone("lounge",
                new Location(world, 0, 0, 0), new Location(world, 10, 10, 10));

        assertFalse(zone.contains(new Location(other, 5, 5, 5)), "other world must not match");
    }

    @Test
    void computesBlockCount() {
        final AfkZone zone = new AfkZone("lounge",
                new Location(world, 0, 0, 0), new Location(world, 9, 9, 9));

        assertEquals(1000, zone.getBlockCount(), "10x10x10 region should be 1000 blocks");
    }
}
