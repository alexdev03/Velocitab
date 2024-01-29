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
import com.velocitypowered.proxy.connection.client.ConnectedPlayer;
import com.velocitypowered.proxy.protocol.ProtocolUtils;
import com.velocitypowered.proxy.protocol.StateRegistry;
import lombok.Getter;
import net.william278.velocitab.Velocitab;
import net.william278.velocitab.config.Group;
import net.william278.velocitab.player.TabPlayer;
import net.william278.velocitab.sorting.MorePlayersManager;
import net.william278.velocitab.tab.Nametag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.velocitypowered.api.network.ProtocolVersion.*;

public class ScoreboardManager {

    private PacketRegistration<UpdateTeamsPacket> packetRegistration;
    private final Velocitab plugin;
    private final Set<TeamsPacketAdapter> versions;
    @Getter
    private final Map<UUID, String> createdTeams;
    private final Map<String, Nametag> nametags;
    @Getter
    private final Multimap<UUID, UUID> playerTeams;
    @Getter
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
            throw new IllegalStateException("Failed to register Scoreboard Teams packets." +
                    " Velocitab probably does not (yet) support your Proxy version.", e);
        }
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
        final String team = createdTeams.remove(player.getUniqueId());
        if (team != null) {
            final TabPlayer tabPlayer = plugin.getTabList().getTabPlayer(player).orElseThrow();
            dispatchGroupPacket(UpdateTeamsPacket.removeTeam(plugin, team), tabPlayer, player.getUniqueId());
            morePlayersManager.recalculateMorePlayers(tabPlayer.getGroup(), player, true);
        }
    }

    public void resetCache(@NotNull Player player, @NotNull Group group, boolean force) {
        final String team = createdTeams.remove(player.getUniqueId());
        if (team != null) {
            dispatchGroupPacket(UpdateTeamsPacket.removeTeam(plugin, team), group, player.getUniqueId());
            if (force) {
                plugin.getServer().getScheduler().buildTask(plugin, () -> morePlayersManager.recalculateMorePlayers(group, player, true))
                        .delay(2000, TimeUnit.MILLISECONDS)
                        .schedule();
            } else {
                morePlayersManager.recalculateMorePlayers(group, player, true);
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

                dispatchGroupPacket(
                        UpdateTeamsPacket.create(plugin, tabPlayer, role, newTag, name),
                        tabPlayer, player.getUniqueId()
                );
                if (!startup) {
                    morePlayersManager.recalculateMorePlayers(tabPlayer.getGroup(), player, false);
                } else {
                    plugin.getServer().getScheduler().buildTask(plugin, () -> morePlayersManager.recalculateMorePlayers(tabPlayer.getGroup(), player, false))
                            .delay(100, TimeUnit.MILLISECONDS)
                            .schedule();
                }
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
