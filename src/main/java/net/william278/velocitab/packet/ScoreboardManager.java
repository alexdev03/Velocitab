/*
 * This file is part of Velocitab, licensed under the Apache License 2.0.
 *
 *  Copyright (c) William278 <will27528@gmail.com>
 *  Copyright (c) contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.william278.velocitab.packet;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import com.velocitypowered.proxy.tablist.VelocityTabListEntry;
import net.kyori.adventure.text.Component;
import net.william278.velocitab.Velocitab;
import net.william278.velocitab.config.Group;
import net.william278.velocitab.config.Placeholder;
import net.william278.velocitab.player.TabPlayer;
import net.william278.velocitab.sorting.MorePlayersManager;
import net.william278.velocitab.tab.Nametag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.velocitypowered.api.network.ProtocolVersion.*;
import static net.william278.velocitab.sorting.MorePlayersManager.MAX_PLAYERS;

public class ScoreboardManager {

    private PacketRegistration<UpdateTeamsPacket> packetRegistration;
    private final Velocitab plugin;
    private final Set<TeamsPacketAdapter> versions;
    private final Map<UUID, String> createdTeams;
    private final Map<String, Nametag> nametags;
    private final Multimap<UUID, UUID> playerTeams;
    private final MorePlayersManager morePlayersManager;

    public ScoreboardManager(@NotNull Velocitab velocitab) {
        this.plugin = velocitab;
        this.createdTeams = Maps.newConcurrentMap();
        this.nametags = Maps.newConcurrentMap();
        this.versions = Sets.newHashSet();
        this.registerVersions();
        this.playerTeams = Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet);
        this.morePlayersManager = new MorePlayersManager(plugin, this);
    }

    private void registerVersions() {
        try {
            versions.add(new Protocol765Adapter(plugin));
            versions.add(new Protocol735Adapter(plugin));
            versions.add(new Protocol404Adapter(plugin));
            versions.add(new Protocol48Adapter(plugin));
        } catch (NoSuchFieldError e) {
            throw new IllegalStateException("Failed to register scoreboard packet adapters. Try to update velocity to latest build", e);
        }
    }

    private void recalculateMorePlayers(@NotNull Group group, @NotNull Player target, boolean remove) {
        group.getTabPlayers(plugin).forEach(tabPlayer -> recalculateMorePlayers(tabPlayer, target, remove));
    }

    private void recalculateMorePlayers(@NotNull TabPlayer tabPlayer, @NotNull Player target, boolean remove) {
        final Collection<UUID> uuids = playerTeams.get(target.getUniqueId());
        if (uuids.isEmpty()) {
            return;
        }

        final boolean invalid = uuids.size() < MAX_PLAYERS;
        if (invalid && remove) {
            final UUID uuid = morePlayersManager.getUserCache().getOrDefault(target.getUniqueId(), null);
            //should happen when uuids.size == MAX_PLAYERS - 1
            if (uuid != null) {

                if (uuid.equals(target.getUniqueId())) {
                    return;
                }

                final Optional<TabPlayer> tabPlayerOptional = plugin.getTabList().getTabPlayer(uuid);
                target.getTabList().getEntry(uuid).ifPresentOrElse(entry -> {
                    entry.setDisplayName(tabPlayerOptional.map(TabPlayer::getLastDisplayname).orElse(Component.text("Error " + uuid.toString())));
                }, () -> plugin.getLogger().error("Failed to remove more players entry for " + target.getUsername()));
            }
        } else {
            final Map<UUID, String> teams = uuids.stream()
                    .collect(Collectors.toMap(uuid -> uuid, uuid -> createdTeams.getOrDefault(uuid, "")))
                    .entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByValue())
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

            if (teams.size() != uuids.size()) {
                plugin.getLogger().error("Failed to sort more players for " + target.getUsername() + " as teams size != uuids size :" + teams.size() + " != " + uuids.size());
                return;
            }

            final List<UUID> sorted = new ArrayList<>(teams.keySet());
            int index = sorted.indexOf(target.getUniqueId());

            if (index == -1) {
                plugin.getLogger().error("Failed to sort more players for " + target.getUsername() + " as index == -1");
                return;
            }

            morePlayersManager.getUserCache().put(target.getUniqueId(), target.getUniqueId());

            updateMorePlayersEntry(tabPlayer, MAX_PLAYERS, target.getUniqueId());
        }
    }

    private void updateMorePlayersEntry(@NotNull TabPlayer tabPlayer, int count, @NotNull UUID entry) {
        final String text = tabPlayer.getGroup().morePlayers().text().replaceAll("%more_players%", String.valueOf(count));
        Placeholder.replace(text, plugin, tabPlayer).thenAccept(more -> {
            tabPlayer.getPlayer().getTabList().getEntry(entry).ifPresentOrElse(tabListEntry -> {
                tabListEntry.setDisplayName(plugin.getFormatter().format(text, tabPlayer, plugin));
            }, () -> plugin.getLogger().error("Failed to update more players entry for " + tabPlayer.getPlayer().getUsername()));
        }).exceptionally(e -> {
            plugin.log(Level.ERROR, "Failed to update more players for " + tabPlayer.getPlayer().getUsername(), e);
            return null;
        });
    }

    private void recalculateMorePlayers(@NotNull Group group, @NotNull Player target, @NotNull String tag, boolean remove) {
        if (true) {
            recalculateMorePlayers(group, target, remove);
            return;
        }

        final Group.MorePlayers morePlayers = group.morePlayers();
        if (morePlayers == null || !morePlayers.enabled()) {
            return;
        }

        final Set<TabPlayer> tabPlayers = group.getTabPlayers(plugin).stream()
                .filter(TabPlayer::isLoaded)
                .collect(Collectors.toSet());

        tabPlayers.forEach(tabPlayer -> {
            final Player player = tabPlayer.getPlayer();
            final Set<Player> tabEntries = tabPlayers.stream()
                    .map(TabPlayer::getPlayer)
                    .filter(p -> !p.equals(player))
                    .filter(p -> player.getTabList().containsEntry(p.getUniqueId()))
                    .filter(p -> plugin.getVanishManager().canSee(player.getUsername(), p.getUsername()))
                    .collect(Collectors.toSet());
            final boolean invalid = tabEntries.size() <= MAX_PLAYERS;
            final AtomicBoolean newTagHigher = new AtomicBoolean(true);

            final Optional<MorePlayersManager.GroupTabList> old = morePlayersManager.getFakeTeam(group, tabEntries);
            final AtomicBoolean forceRemove = new AtomicBoolean(false);

            old.ifPresent(t -> {
                if (remove) {
                    t.uuids().remove(player.getUniqueId());
                    if (t.uuids().isEmpty()) {
                        morePlayersManager.removeFakeTeam(group, tabEntries);
                        forceRemove.set(true);
                    }
                } else {
                    t.uuids().add(player.getUniqueId());
                }

                newTagHigher.set(isStringLexicographicallyBefore(tag, t.team()));
//                System.out.println("New tag higher: " + newTagHigher.get() + " as " + (newTagHigher.get() ? t.team() + " > " + tag : t.team() + " < " + tag));
                if (player.getTabList().containsEntry(morePlayersManager.getUuid()) && newTagHigher.get()) {
                    final UpdateTeamsPacket packet = UpdateTeamsPacket.removeTeam(plugin, t.team());
                    dispatchPacket(packet, player, player.getUniqueId());
                    if (invalid) {
                        player.getTabList().removeEntry(morePlayersManager.getUuid());
                    }
                    morePlayersManager.removeFakeTeam(group, tabEntries);
                }
            });

            if (invalid || !newTagHigher.get()) {
                return;
            }

            final String text = morePlayers.text().replaceAll("%more_players%", String.valueOf(tabEntries.size() - MAX_PLAYERS));

            Placeholder.replace(text, plugin, tabPlayer).thenAccept(more -> {
                final Component component = plugin.getFormatter().emptyFormat(more);
                final MorePlayersManager.FakePlayer fakePlayer = morePlayersManager.recalucateFakeTeam(group, player, tabEntries);
                if (fakePlayer == null) {
                    return;
                }
                if (target != player && !forceRemove.get() && !remove && old.isPresent() && old.get().team().equals(fakePlayer.team())) {
                    return;
                }
                final UpdateTeamsPacket packet = UpdateTeamsPacket.create(plugin, null, fakePlayer.team(), new Nametag("", ""), fakePlayer.entry().getProfile().getName());
                dispatchPacket(packet, player, player.getUniqueId());
                final VelocityTabListEntry entry = fakePlayer.entry(component);
                player.getTabList().addEntry(entry);
            }).exceptionally(e -> {
                plugin.log(Level.ERROR, "Failed to update more players for " + player.getUsername(), e);
                return null;
            });
        });

        if (remove) {
            morePlayersManager.clean(group, target);
        }
    }

    private boolean isStringLexicographicallyBefore(@NotNull String str1, @NotNull String str2) {
        return str1.compareTo(str2) < 0;
    }

    @NotNull
    public SortedSet<String> getTeams(@NotNull Group group, @NotNull Player player) {
        final Set<UUID> uuids = group.getTabPlayers(plugin).stream()
                .filter(TabPlayer::isLoaded)
                .filter(t -> player.getTabList().containsEntry(t.getPlayer().getUniqueId()))
                .map(t -> t.getPlayer().getUniqueId())
                .collect(Collectors.toSet());
        return new TreeSet<>(createdTeams.entrySet().stream()
                .filter(entry -> uuids.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList());
    }

    @NotNull
    public TeamsPacketAdapter getPacketAdapter(@NotNull ProtocolVersion version) {
        return versions.stream()
                .filter(adapter -> adapter.getProtocolVersions().contains(version))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No adapter found for protocol version " + version));
    }

    public void close() {
        morePlayersManager.close();
        plugin.getServer().getAllPlayers().forEach(this::resetCache);
    }

    public void resetCache(@NotNull Player player) {
        resetCache(player, false);
    }

    public void resetCache(@NotNull Player player, boolean force) {
        final String team = createdTeams.remove(player.getUniqueId());
        if (team != null) {
            final TabPlayer tabPlayer = plugin.getTabList().getTabPlayer(player).orElseThrow();
            dispatchGroupPacket(UpdateTeamsPacket.removeTeam(plugin, team), tabPlayer, player.getUniqueId());
            if (force) {
                recalculateMorePlayers(tabPlayer.getGroup(), player, team, true);
            }
        }
    }

    public void resetCache(@NotNull Player player, @NotNull Group group, boolean force) {
        final String team = createdTeams.remove(player.getUniqueId());
        if (team != null) {
            dispatchGroupPacket(UpdateTeamsPacket.removeTeam(plugin, team), group, player.getUniqueId());
            if (force) {
                plugin.getServer().getScheduler().buildTask(plugin, () -> recalculateMorePlayers(group, player, team, true))
                        .delay(2000, TimeUnit.MILLISECONDS)
                        .schedule();
            } else {
                recalculateMorePlayers(group, player, team, true);
            }
        }
    }

    public void vanishPlayer(@NotNull TabPlayer tabPlayer) {
        this.handleVanish(tabPlayer, true);
    }

    public void unVanishPlayer(@NotNull TabPlayer tabPlayer) {
        this.handleVanish(tabPlayer, false);
    }

    private void handleVanish(@NotNull TabPlayer tabPlayer, boolean vanish) {
        if (!plugin.getSettings().isSortPlayers()) {
            return;
        }

        final Player player = tabPlayer.getPlayer();
        final String teamName = createdTeams.get(player.getUniqueId());
        if (teamName == null) {
            return;
        }
        final List<RegisteredServer> siblings = tabPlayer.getGroup().registeredServers(plugin);

        final Optional<Nametag> cachedTag = Optional.ofNullable(nametags.getOrDefault(teamName, null));
        cachedTag.ifPresent(nametag -> {
            final UpdateTeamsPacket packet = vanish ? UpdateTeamsPacket.removeTeam(plugin, teamName) :
                    UpdateTeamsPacket.create(plugin, tabPlayer, teamName, nametag, player.getUsername());
            siblings.forEach(server -> server.getPlayersConnected().stream().filter(p -> p != player)
                    .filter(p -> vanish && !plugin.getVanishManager().canSee(p.getUsername(), player.getUsername()))
                    .forEach(connected -> dispatchPacket(packet, connected, player.getUniqueId())));
        });
    }

    /**
     * Updates the role of the player in the scoreboard.
     *
     * @param tabPlayer The TabPlayer object representing the player whose role will be updated.
     * @param role      The new role of the player. Must not be null.
     * @param force     Whether to force the update even if the player's nametag is the same.
     * @param startup   Whether the update is being called on startup.
     */
    public void updateRole(@NotNull TabPlayer tabPlayer, @NotNull String role, boolean force, boolean startup) {
        final Player player = tabPlayer.getPlayer();
        if (!player.isActive()) {
            plugin.getTabList().removeOfflinePlayer(player);
            return;
        }

        final String name = player.getUsername();
        tabPlayer.getNametag(plugin).thenAccept(newTag -> {
            if (!createdTeams.getOrDefault(player.getUniqueId(), "").equals(role)) {
                if (createdTeams.containsKey(player.getUniqueId())) {
                    dispatchGroupPacket(
                            UpdateTeamsPacket.removeTeam(plugin, createdTeams.get(player.getUniqueId())),
                            tabPlayer, player.getUniqueId()
                    );
                }

                createdTeams.put(player.getUniqueId(), role);
                this.nametags.put(role, newTag);
                if (!startup) {
                    recalculateMorePlayers(tabPlayer.getGroup(), player, role, false);
                } else {
                    plugin.getServer().getScheduler().buildTask(plugin, () -> recalculateMorePlayers(tabPlayer.getGroup(), player, role, false))
                            .delay(2000, TimeUnit.MILLISECONDS)
                            .schedule();
                }
                dispatchGroupPacket(
                        UpdateTeamsPacket.create(plugin, tabPlayer, role, newTag, name),
                        tabPlayer, player.getUniqueId()
                );
                plugin.getServer().getScheduler().buildTask(plugin, () -> plugin.getTabList().sendUpdateListed(tabPlayer.getPlayer())).delay(300, TimeUnit.MILLISECONDS).schedule();
            } else if (force || (this.nametags.containsKey(role) && !this.nametags.get(role).equals(newTag))) {
                this.nametags.put(role, newTag);
                dispatchGroupPacket(
                        UpdateTeamsPacket.changeNametag(plugin, tabPlayer, role, newTag),
                        tabPlayer, player.getUniqueId()
                );
            }
        }).exceptionally(e -> {
            plugin.log(Level.ERROR, "Failed to update role for " + player.getUsername(), e);
            return null;
        });
    }


    public void resendAllTeams(@NotNull TabPlayer tabPlayer) {
        if (!plugin.getSettings().isSendScoreboardPackets()) {
            return;
        }

        final Player player = tabPlayer.getPlayer();
        final List<RegisteredServer> siblings = tabPlayer.getGroup().registeredServers(plugin);
        final List<Player> players = siblings.stream()
                .map(RegisteredServer::getPlayersConnected)
                .flatMap(Collection::stream)
                .toList();

        final List<String> roles = new ArrayList<>();
        players.forEach(p -> {
            if (p == player || !p.isActive()) {
                return;
            }

            if (!plugin.getVanishManager().canSee(player.getUsername(), p.getUsername())) {
                return;
            }

            final String role = createdTeams.getOrDefault(p.getUniqueId(), "");
            if (role.isEmpty()) {
                return;
            }

            // Prevent duplicate packets
            if (roles.contains(role)) {
                return;
            }
            roles.add(role);

            // Send packet
            final Nametag tag = nametags.get(role);
            if (tag != null) {
                final UpdateTeamsPacket packet = UpdateTeamsPacket.create(
                        plugin, tabPlayer, role, tag, p.getUsername()
                );
                dispatchPacket(packet, player, p.getUniqueId());
            }
        });
    }

    public void dispatchPacket(@NotNull UpdateTeamsPacket packet, @NotNull Player player, @NotNull UUID involved) {
        if (!player.isActive()) {
            plugin.getTabList().removeOfflinePlayer(player);
            return;
        }

        final boolean isRemove = packet.mode() == UpdateTeamsPacket.UpdateMode.REMOVE_TEAM;

        if (isRemove) {
            playerTeams.remove(player.getUniqueId(), involved);
        } else {
            playerTeams.put(player.getUniqueId(), involved);
        }

        try {
            final ConnectedPlayer connectedPlayer = (ConnectedPlayer) player;
            connectedPlayer.getConnection().write(packet);
        } catch (Throwable e) {
            plugin.log(Level.ERROR, "Failed to dispatch packet (unsupported client or server version)", e);
        }
    }

    private void dispatchGroupPacket(@NotNull UpdateTeamsPacket packet, @NotNull Group group, @NotNull UUID involved) {
        group.registeredServers(plugin).forEach(server ->
                server.getPlayersConnected().forEach(connected ->
                        dispatchPacket(packet, connected, involved)));
    }

    private void dispatchGroupPacket(@NotNull UpdateTeamsPacket packet, @NotNull TabPlayer tabPlayer, @NotNull UUID involved) {
        final Player player = tabPlayer.getPlayer();
        final Optional<ServerConnection> optionalServerConnection = player.getCurrentServer();
        if (optionalServerConnection.isEmpty()) {
            return;
        }

        boolean isRemove = packet.mode() == UpdateTeamsPacket.UpdateMode.REMOVE_TEAM;

        final List<RegisteredServer> siblings = tabPlayer.getGroup().registeredServers(plugin);
        siblings.forEach(server -> server.getPlayersConnected().forEach(connected -> {
            try {
                final boolean canSee = plugin.getVanishManager().canSee(connected.getUsername(), player.getUsername());
                if (!canSee) {
                    return;
                }

                if (isRemove) {
                    playerTeams.remove(connected.getUniqueId(), involved);
                } else {
                    playerTeams.put(connected.getUniqueId(), involved);
                }

                final ConnectedPlayer connectedPlayer = (ConnectedPlayer) connected;
                connectedPlayer.getConnection().write(packet);
            } catch (Throwable e) {
                plugin.log(Level.ERROR, "Failed to dispatch packet (unsupported client or server version)", e);
            }
        }));
    }

    public void registerPacket() {
        try {
            packetRegistration = PacketRegistration.of(UpdateTeamsPacket.class)
                    .direction(ProtocolUtils.Direction.CLIENTBOUND)
                    .packetSupplier(() -> new UpdateTeamsPacket(plugin))
                    .stateRegistry(StateRegistry.PLAY)
                    .mapping(0x3E, MINECRAFT_1_8, true)
                    .mapping(0x44, MINECRAFT_1_12_2, true)
                    .mapping(0x47, MINECRAFT_1_13, true)
                    .mapping(0x4B, MINECRAFT_1_14, true)
                    .mapping(0x4C, MINECRAFT_1_15, true)
                    .mapping(0x55, MINECRAFT_1_17, true)
                    .mapping(0x58, MINECRAFT_1_19_1, true)
                    .mapping(0x56, MINECRAFT_1_19_3, true)
                    .mapping(0x5A, MINECRAFT_1_19_4, true)
                    .mapping(0x5C, MINECRAFT_1_20_2, true)
                    .mapping(0x5E, MINECRAFT_1_20_3, true);
            packetRegistration.register();
        } catch (Throwable e) {
            plugin.log(Level.ERROR, "Failed to register UpdateTeamsPacket", e);
        }
    }

    public void unregisterPacket() {
        if (packetRegistration == null) {
            return;
        }
        try {
            packetRegistration.unregister();
        } catch (Throwable e) {
            plugin.log(Level.ERROR, "Failed to unregister UpdateTeamsPacket", e);
        }
    }

    /**
     * Recalculates the vanish status for a specific player.
     * This method updates the player's scoreboard to reflect the vanish status of another player.
     *
     * @param tabPlayer The TabPlayer object representing the player whose scoreboard will be updated.
     * @param target    The TabPlayer object representing the player whose vanish status will be reflected.
     * @param canSee    A boolean indicating whether the player can see the target player.
     */
    public void recalculateVanishForPlayer(TabPlayer tabPlayer, TabPlayer target, boolean canSee) {
        final Player player = tabPlayer.getPlayer();
        final String team = createdTeams.get(target.getPlayer().getUniqueId());
        if (team == null) {
            return;
        }

        final UpdateTeamsPacket removeTeam = UpdateTeamsPacket.removeTeam(plugin, team);
        dispatchPacket(removeTeam, player, target.getPlayer().getUniqueId());

        if (canSee) {
            final Nametag tag = nametags.get(team);
            if (tag != null) {
                final UpdateTeamsPacket addTeam = UpdateTeamsPacket.create(
                        plugin, target, team, tag, target.getPlayer().getUsername()
                );
                dispatchPacket(addTeam, player, target.getPlayer().getUniqueId());
            }
        }
    }

}
