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

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.tablist.VelocityTabListEntry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Accessors;
import net.kyori.adventure.text.Component;
import net.william278.velocitab.Velocitab;
import net.william278.velocitab.config.Group;
import net.william278.velocitab.packet.ScoreboardManager;
import net.william278.velocitab.packet.UpdateTeamsPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class MorePlayersManager {

    public static final int MAX_PLAYERS = 80;
    private VelocityTabListEntry fakePlayer;
    private final Velocitab plugin;
    private Multimap<Group, GroupTabList> fakeTeams;
    private final ScoreboardManager scoreboardManager;
    @Getter
    private final UUID uuid;

    public MorePlayersManager(@NotNull Velocitab plugin, @NotNull ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.fakeTeams = Multimaps.newSetMultimap(Maps.newConcurrentMap(), Sets::newConcurrentHashSet);
        this.scoreboardManager = scoreboardManager;
        this.uuid = UUID.fromString("44b96d71-3ef7-41e5-83b7-f1813ba100ab");
        this.createFakePlayer();
    }

    public void close() {
        plugin.getServer().getAllPlayers().forEach(player -> {
            player.getTabList().removeEntry(uuid);
            Optional<GroupTabList> groupTabList = fakeTeams.values().stream().filter(groupTabList1 -> groupTabList1.uuids().contains(player.getUniqueId())).findFirst();
            groupTabList.ifPresent(groupTabList1 -> scoreboardManager.dispatchPacket(UpdateTeamsPacket.removeTeam(plugin, groupTabList1.team()), player));
        });

    }

    private void createFakePlayer() {
        fakePlayer = new VelocityTabListEntry(
                null,
                new GameProfile(uuid, "velocitab", List.of()),
                Component.text("test"),
                0,
                0,
                null,
                true
        );
    }

    public void clean(@NotNull Group group, @NotNull Player player) {
        for (GroupTabList groupTabList : fakeTeams.get(group)) {
            boolean stop = false;
            if (groupTabList.players().contains(player)) {
                groupTabList.players().remove(player);
                if (groupTabList.players().isEmpty()) {
                    fakeTeams.remove(group, groupTabList);
                    stop = true;
                }
            }
            if (groupTabList.uuids().contains(player.getUniqueId())) {
                groupTabList.uuids().remove(player.getUniqueId());
                if (groupTabList.uuids().isEmpty()) {
                    fakeTeams.remove(group, groupTabList);
                    stop = true;
                }
            }
            if (stop) {
                return;
            }
        }
    }

    public Optional<GroupTabList> getFakeTeam(@NotNull Group group, @NotNull Set<Player> players) {
        for (GroupTabList groupTabList : fakeTeams.get(group)) {
            if (groupTabList.players().equals(players)) {
                return Optional.of(groupTabList);
            }
        }
        return Optional.empty();
    }

    @Nullable
    public FakePlayer recalucateFakeTeam(@NotNull Group group, @NotNull Player player, @NotNull Set<Player> players) {
        final SortedSet<String> teamNames = scoreboardManager.getTeams(group, player);
        final List<String> sortedList = new ArrayList<>(teamNames);
        final String fakeTeam = createStringAtPosition(MAX_PLAYERS - 1, sortedList);
        if (fakeTeam.isEmpty()) {
            return null;
        }
        fakeTeams.put(group, new GroupTabList(players, fakeTeam));
        return new FakePlayer(fakeTeam, fakePlayer);
    }

    @NotNull
    private String createStringAtPosition(int position, @NotNull List<String> sortedList) {
        if (position <= 0 || position > sortedList.size()) {
            return "";
            //throw new IllegalArgumentException("Position must be between 1 and " + (sortedList.size() - 1));
        }

        final String pre = sortedList.get(position - 1);
        final String between = generateStringBetween(pre);
        sortedList.add(position, between);
        return between;
    }

    @NotNull
    public String generateStringBetween(@NotNull final String str1) {
        char lastChar = str1.charAt(str1.length() - 1);
        int nextChar = lastChar + 1;
        return str1.substring(0, str1.length() - 1) + (char) nextChar;

    }

    public void removeFakeTeam(@NotNull Group group, @NotNull Set<Player> players) {
        fakeTeams.get(group).removeIf(groupTabList -> groupTabList.players().equals(players));
    }

    public record FakePlayer(String team, VelocityTabListEntry entry) {


        public VelocityTabListEntry entry(@NotNull Component displayName) {
            return new VelocityTabListEntry(
                    null,
                    entry.getProfile(),
                    displayName,
                    entry.getGameMode(),
                    entry.getLatency(),
                    entry.getChatSession(),
                    true
            );
        }
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    @Accessors(fluent = true)
    public static final class GroupTabList {
        private final @NotNull Set<Player> players;
        private final @NotNull String team;
        private Set<UUID> uuids = ConcurrentHashMap.newKeySet();
    }


}
