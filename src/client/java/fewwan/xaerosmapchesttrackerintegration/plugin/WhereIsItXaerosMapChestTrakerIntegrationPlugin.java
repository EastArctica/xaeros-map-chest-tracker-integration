package fewwan.xaerosmapchesttrackerintegration.plugin;

import fewwan.xaerosmapchesttrackerintegration.util.CountUtils;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import red.jackf.chesttracker.api.memory.Memory;
import red.jackf.chesttracker.api.memory.MemoryBank;
import red.jackf.chesttracker.api.providers.ProviderUtils;
import red.jackf.chesttracker.impl.memory.MemoryBankAccessImpl;
import red.jackf.whereisit.api.SearchRequest;
import red.jackf.whereisit.api.SearchResult;
import red.jackf.whereisit.client.api.WhereIsItClientPlugin;
import red.jackf.whereisit.client.api.events.OnResult;
import red.jackf.whereisit.client.api.events.OnResultsCleared;
import red.jackf.whereisit.client.api.events.SearchInvoker;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;
import xaero.common.minimap.waypoints.Waypoint;
import xaero.hud.minimap.waypoint.set.WaypointSet;
import xaero.hud.minimap.world.MinimapWorld;
import xaero.hud.minimap.world.MinimapWorldManager;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

public class WhereIsItXaerosMapChestTrakerIntegrationPlugin implements WhereIsItClientPlugin {
    private static final String SUFFIX = " [CT]";
    private static final int COLOR_ID = 0;
    private static boolean running = false;
    private static Item searchItem = null;

    private static Item getItemFromSearchRequest(SearchRequest request) {
        NbtList criterionList = request.toTag();

        for (NbtElement element : criterionList) {
            if (element instanceof NbtCompound compound) {
                if (compound.getString("type", "").equals("whereisit:item")) {
                    if (compound.contains("item")) {
                        String itemId = compound.getString("item", "minecraft:missing-item-id");
                        Identifier itemIdentifier = Identifier.tryParse(itemId);
                        if (itemIdentifier != null) {
                            return Registries.ITEM.get(itemIdentifier);
                        }
                    }
                }
            }
        }

        return null;
    }

    public static boolean doSearch(SearchRequest request, Consumer<Collection<SearchResult>> collectionConsumer) {
        searchItem = getItemFromSearchRequest(request);
        return true;
    }

    private static void onResults(Collection<SearchResult> results) {
        if (searchItem == null) return;
        running = true;

        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return;

        MinimapWorldManager worldManager = minimapSession.getWorldManager();
        if (worldManager == null) return;

        MinimapWorld minimapWorld = worldManager.getCurrentWorld();
        if (minimapWorld == null) return;

        WaypointSet waypointSet = minimapWorld.getCurrentWaypointSet();
        if (waypointSet == null) return;

        Optional<Identifier> key = ProviderUtils.getPlayersCurrentKey();
        Optional<MemoryBank> optionalMemoryBank = MemoryBankAccessImpl.INSTANCE.getLoaded();
        if (optionalMemoryBank.isEmpty()) return;
        MemoryBank memoryBank = optionalMemoryBank.get();

        for (SearchResult result : results) {
            BlockPos pos = result.pos();

            Optional<Memory> optionalMemory = memoryBank.getMemory(key, pos);
            if (optionalMemory.isEmpty()) continue;

            Memory memory = optionalMemory.get();
            int totalItemCount = CountUtils.countItemsOf(memory.items(), searchItem);

            String itemName = Text.translatable(searchItem.getTranslationKey()).getString();
            String waypointName = totalItemCount + " " + itemName + SUFFIX;
            String waypointLabel = totalItemCount <= 99 ? String.valueOf(totalItemCount) : "99";

            // TODO: waypoint is deprecated i guess
            waypointSet.add(new Waypoint(pos.getX(), pos.getY(), pos.getZ(),
                    waypointName, waypointLabel, COLOR_ID, 0, true));
        }
    }

    private static void onResultsCleared() {
        if (!running) return;
        running = false;
        searchItem = null;

        MinimapSession minimapSession = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (minimapSession == null) return;

        MinimapWorldManager worldManager = minimapSession.getWorldManager();
        if (worldManager == null) return;

        MinimapWorld minimapWorld = worldManager.getCurrentWorld();
        if (minimapWorld == null) return;

        WaypointSet waypointSet = minimapWorld.getCurrentWaypointSet();
        if (waypointSet == null) return;

        List<Waypoint> waypointsToRemove = new ArrayList<>();
        for (Waypoint waypoint : waypointSet.getWaypoints()) {
            if (waypoint != null && waypoint.isTemporary()) {
                waypointsToRemove.add(waypoint);
            }
        }
        waypointSet.removeAll(waypointsToRemove);
    }

    @Override
    public void load() {
        SearchInvoker.EVENT.register(WhereIsItXaerosMapChestTrakerIntegrationPlugin::doSearch);
        OnResult.EVENT.register(WhereIsItXaerosMapChestTrakerIntegrationPlugin::onResults);
        OnResultsCleared.EVENT.register(WhereIsItXaerosMapChestTrakerIntegrationPlugin::onResultsCleared);
    }
}
