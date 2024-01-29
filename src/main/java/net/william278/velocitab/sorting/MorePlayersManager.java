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

package net.william278.velocitab.sorting;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.william278.velocitab.Velocitab;
import net.william278.velocitab.config.Group;
import net.william278.velocitab.config.Placeholder;
import net.william278.velocitab.packet.ScoreboardManager;
import net.william278.velocitab.player.TabPlayer;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MorePlayersManager {

    public static final int MAX_PLAYERS = 80;
    private final Velocitab plugin;
    private final ScoreboardManager scoreboardManager;
    private final Map<UUID, UUID> userCache;

    public MorePlayersManager(@NotNull Velocitab plugin, @NotNull ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.scoreboardManager = scoreboardManager;
        this.userCache = Maps.newConcurrentMap();
    }

    public void close() {
        userCache.forEach((uuid, uuid2) -> {
            final Optional<TabPlayer> tabPlayerOptional = plugin.getTabList().getTabPlayer(uuid);
            tabPlayerOptional.ifPresent(tabPlayer -> {
                final Optional<TabPlayer> tabPlayerOptional2 = plugin.getTabList().getTabPlayer(uuid2);
                tabPlayerOptional2.ifPresent(tabPlayer2 -> {
                    tabPlayer.getPlayer().getTabList().getEntry(uuid2).ifPresentOrElse(tabListEntry -> {
                        tabListEntry.setDisplayName(tabPlayer2.getLastDisplayName());
                    }, () -> plugin.getLogger().error("Failed to remove more players entry for " + tabPlayer.getPlayer().getUsername()));
                });
            });
        });
    }

    public void clean(@NotNull UUID uuid) {
        userCache.remove(uuid);
    }

    public void recalculateMorePlayers(@NotNull Group group, @NotNull Player target, boolean remove) {
        if (!group.morePlayers().enabled()) {
            return;
        }
        group.getTabPlayers(plugin).forEach(tabPlayer -> recalculateMorePlayers(tabPlayer, target, remove));
    }

    private void recalculateMorePlayers(@NotNull TabPlayer tabPlayer, @NotNull Player target, boolean remove) {
        final List<UUID> uuids = new ArrayList<>(scoreboardManager.getPlayerTeams().get(tabPlayer.getPlayer().getUniqueId()));
        if (uuids.isEmpty()) {
            return;
        }

        if(tabPlayer.getPlayer().equals(target)) {
            return;
        }


        final boolean invalid = uuids.size() < MAX_PLAYERS;
        if (invalid && remove) {
            final UUID uuid = userCache.getOrDefault(target.getUniqueId(), null);
            //should happen when uuids.size == MAX_PLAYERS - 1
            if (uuid != null) {

                if (uuid.equals(target.getUniqueId())) {
                    return;
                }

                final Optional<TabPlayer> tabPlayerOptional = plugin.getTabList().getTabPlayer(uuid);
                target.getTabList().getEntry(uuid).ifPresentOrElse(entry -> {
                    entry.setDisplayName(tabPlayerOptional.map(TabPlayer::getLastDisplayName).orElse(Component.text("Error " + uuid.toString())));
                }, () -> plugin.getLogger().error("Failed to remove more players entry for " + target.getUsername()));
            }
        } else if (!invalid && !remove) {
//            final Map<UUID, String> teams = uuids.stream()
//                    .collect(Collectors.toMap(uuid -> uuid, uuid -> scoreboardManager.getCreatedTeams().getOrDefault(uuid, "")))
//                    .entrySet()
//                    .stream()
//                    .sorted(Map.Entry.comparingByValue())
//                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
//
//            if (teams.size() != uuids.size()) {
//                plugin.getLogger().error("Failed to sort more players for " + target.getUsername() + " as teams size != uuids size :" + teams.size() + " != " + uuids.size());
//                return;
//            }
//
//            final List<UUID> sorted = new ArrayList<>(teams.keySet());
//            int index = sorted.indexOf(target.getUniqueId());
//
//            if (index == -1) {
//                plugin.getLogger().error("Failed to sort more players for " + target.getUsername() + " as index == -1");
//                return;
//            }
            final List<UUID> sorted = getSortedUUIDs(tabPlayer, target, uuids);
            int index = sorted.indexOf(target.getUniqueId());

            if(sorted.isEmpty() || index == -1) {
                return;
            }

            final UUID uuid = sorted.get(index);
            final UUID old = userCache.get(tabPlayer.getPlayer().getUniqueId());
            if (old != null && !old.equals(sorted.get(MAX_PLAYERS - 1))) {
                resetMorePlayersEntry(tabPlayer, old);
            }
            userCache.put(tabPlayer.getPlayer().getUniqueId(), sorted.get(MAX_PLAYERS - 1));

            if (uuids.size() - MAX_PLAYERS <= 0) {
                resetMorePlayersEntry(tabPlayer, uuid);
                return;
            }

            updateMorePlayersEntry(tabPlayer, uuids.size() - MAX_PLAYERS, sorted.get(MAX_PLAYERS - 1));
        } else if (!invalid) {
            final UUID uuid = userCache.get(tabPlayer.getPlayer().getUniqueId());
            if (uuid == null) {
                resetMorePlayersEntry(tabPlayer, uuids.get(MAX_PLAYERS - 1));
                return;
            }
            if (uuid.equals(target.getUniqueId())) {
                updateMorePlayersEntry(tabPlayer, uuids.size() - MAX_PLAYERS - 1, uuid);
                if (tabPlayer.getPlayer().getUsername().equals("AlexDev_")) {
                    plugin.getLogger().info("Setting AlexDev_ to here 1 " + uuid);
                }
            } else {
//                if (sorted.size() <= MAX_PLAYERS + 1 ) {
//                    System.out.println("sorted size <= MAX_PLAYERS + 1 " + sorted.size() + " " + MAX_PLAYERS + " " + uuids.size() + " " + sorted.indexOf(uuid));
//                    return;
//                }

                final List<UUID> current = Lists.newArrayList(scoreboardManager.getPlayerTeams().get(tabPlayer.getPlayer().getUniqueId()));
//                current.remove(target.getUniqueId());
                final List<UUID> sorted = getSortedUUIDs(tabPlayer, target, current);
                final UUID old = sorted.get(MAX_PLAYERS - 1);
                resetMorePlayersEntry(tabPlayer, uuid);
                updateMorePlayersEntry(tabPlayer, uuids.size() - MAX_PLAYERS, old);
                if (tabPlayer.getPlayer().getUsername().equals("AlexDev_")) {
                    plugin.getLogger().info("Setting AlexDev_ to here 2 " + uuid + " " + (uuids.size() - MAX_PLAYERS) + " " + sorted.indexOf(uuid) + " " + old + " " + sorted.indexOf(old));
                    plugin.getLogger().info(String.valueOf(current.equals(uuids)));
                }

                plugin.getServer().getScheduler().buildTask(plugin, () -> {

                }).delay(50, TimeUnit.MILLISECONDS).schedule();

            }
        }
    }

    private List<UUID> getSortedUUIDs(@NotNull TabPlayer tabPlayer, @NotNull Player target, @NotNull Collection<UUID> uuids) {
        final Map<UUID, String> teams = uuids.stream()
                .collect(Collectors.toMap(uuid -> uuid, uuid -> scoreboardManager.getCreatedTeams().getOrDefault(uuid, "")))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        if (teams.size() != uuids.size()) {
//            plugin.getLogger().error("Failed to sort more players for " + target.getUsername() + " as teams size != uuids size :" + teams.size() + " != " + uuids.size());
            return List.of();
        }

        final List<UUID> sorted = new ArrayList<>(teams.keySet());
//        int index = sorted.indexOf(target.getUniqueId());
//
//        if (index == -1) {
//            plugin.getLogger().error("Failed to sort more players for " + target.getUsername() + " as index == -1");
//            return List.of();
//        }

        return sorted;
    }

    private void updateMorePlayersEntry(@NotNull TabPlayer tabPlayer, int count, @NotNull UUID entry) {
        if (count <= 0) {
            if (tabPlayer.getPlayer().getUsername().equals("AlexDev_")) {
                System.out.println("Reset");
            }
            resetMorePlayersEntry(tabPlayer, entry);
            return;
        }
        String whoCalled = Thread.currentThread().getStackTrace()[2].getClassName() + "." + Thread.currentThread().getStackTrace()[2].getMethodName() + ":" + Thread.currentThread().getStackTrace()[2].getLineNumber();
        final String text = tabPlayer.getGroup().morePlayers().text().replaceAll("%more_players%", String.valueOf(count));
        Placeholder.replace(text, plugin, tabPlayer).thenAccept(more -> {
            tabPlayer.getPlayer().getTabList().getEntry(entry).ifPresentOrElse(tabListEntry -> {
                if (tabPlayer.getPlayer().getUsername().equals("AlexDev_")) {
                    System.out.println("Setting AlexDev_ to here 3 " + entry + " " + whoCalled + " " + PlainTextComponentSerializer.plainText().serialize(tabListEntry.getDisplayNameComponent().get()));
                }
                tabListEntry.setDisplayName(plugin.getFormatter().format(text, tabPlayer, plugin));
            }, () -> plugin.getLogger().error("Failed to update more players entry for " + tabPlayer.getPlayer().getUsername() + " " + whoCalled));
        }).exceptionally(e -> {
            plugin.log(Level.ERROR, "Failed to update more players for " + tabPlayer.getPlayer().getUsername(), e);
            return null;
        });
    }

    private void resetMorePlayersEntry(@NotNull TabPlayer tabPlayer, @NotNull UUID entry) {
        plugin.getTabList().getTabPlayer(entry).ifPresent(t -> {
            tabPlayer.getPlayer().getTabList().getEntry(entry).ifPresentOrElse(tabListEntry -> {
                tabListEntry.setDisplayName(t.getLastDisplayName());
            }, () -> plugin.getLogger().error("Failed to reset more players entry for " + tabPlayer.getPlayer().getUsername()));
        });
    }


}
