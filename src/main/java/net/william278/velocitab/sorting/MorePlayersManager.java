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
import com.velocitypowered.api.util.GameProfile;
import com.velocitypowered.proxy.tablist.VelocityTabListEntry;
import net.kyori.adventure.text.Component;
import net.william278.velocitab.Velocitab;
import net.william278.velocitab.config.Group;
import net.william278.velocitab.packet.ScoreboardManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MorePlayersManager {

    public static final int MAX_PLAYERS = 30;
    private VelocityTabListEntry fakePlayer;
    private final Velocitab plugin;
    private Map<Group, String> fakeTeams;
    private final ScoreboardManager scoreboardManager;
    private final UUID uuid;

    public MorePlayersManager(@NotNull Velocitab plugin, @NotNull ScoreboardManager scoreboardManager) {
        this.plugin = plugin;
        this.fakeTeams = Maps.newConcurrentMap();
        this.scoreboardManager = scoreboardManager;
        this.uuid = UUID.randomUUID();
        this.createFakePlayer();
    }

    public void close() {
        plugin.getServer().getAllPlayers().forEach(player -> player.getTabList().removeEntry(uuid));
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

    public Optional<FakePlayer> getFakeTeam(@NotNull Group group) {
        return Optional.ofNullable(fakeTeams.get(group))
                .map(team -> new FakePlayer(team, fakePlayer));
    }

    @Nullable
    public FakePlayer recalucateFakeTeam(@NotNull Group group) {
        final SortedSet<String> teamNames = scoreboardManager.getTeams(group);
        final List<String> sortedList = new ArrayList<>(teamNames);
        final String fakeTeam = createStringAtPosition(MAX_PLAYERS - 1, sortedList);
        if (fakeTeam.isEmpty()) {
            return null;
        }
        fakeTeams.put(group, fakeTeam);
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

    public void removeFakeTeam(Group group) {
        fakeTeams.remove(group);
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


}
