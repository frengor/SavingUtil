/*
 * Copyright 2021 fren_gor
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.fren_gor.savingUtil;

import com.google.common.io.Files;
import org.apache.commons.lang.Validate;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.logging.Logger;

public class SavingUtil<S extends ConfigurationSerializable> {

    private final Logger logger;
    private final Function<S, String> getFileName;
    private final File directory;

    public SavingUtil(@NotNull Plugin plugin, @NotNull Function<S, String> getFileName) {
        this(plugin, getFileName, null);
    }

    public SavingUtil(@NotNull Plugin plugin, @NotNull Function<S, String> getFileName, @Nullable String subDirectory) {
        Validate.notNull(plugin, "Plugin is null.");
        Validate.notNull(getFileName, "Function is null.");
        Validate.isTrue(plugin.isEnabled(), "Plugin isn't enabled.");
        this.logger = plugin.getLogger();
        this.getFileName = getFileName;
        this.directory = subDirectory == null ? plugin.getDataFolder() : new File(plugin.getDataFolder(), subDirectory);
        if (this.directory.exists()) {
            if (!this.directory.isDirectory())
                throw new IllegalArgumentException('\'' + this.directory.getAbsolutePath() + "' is a file.");
        } else {
            directory.mkdirs();
        }
    }

    public void save(@NotNull S s) {
        Validate.notNull(s, "Object to save is null.");

        String name = Objects.requireNonNull(getFileName.apply(s), "Function result is null.");
        File t = new File(directory, name + ".tmp");
        File f = new File(directory, name + ".dat");

        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("obj", s);

        try {
            yaml.save(t);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        try {
            Files.move(t, f);
        } catch (Exception e) {
            e.printStackTrace();
            logger.severe("Trying to save directly to " + f.getName());
            try {
                yaml.save(f);
            } catch (Exception e1) {
                logger.severe("Couldn't save directly to " + f.getName());
                e1.printStackTrace();
                return;
            }
        }

        t.delete();
    }

    public @Nullable S load(@NotNull String fileName) throws IOException, InvalidConfigurationException {
        Validate.notNull(fileName, "File name is null.");

        File f = new File(directory, fileName + ".dat");

        if (!f.exists()) {
            return null;
        }

        YamlConfiguration yaml = new YamlConfiguration();

        yaml.load(f);

        return (S) yaml.get("obj");
    }

    public @NotNull LoadResult<S> loadOrCorrupt(@NotNull String fileName) {
        Validate.notNull(fileName, "File name is null.");

        File f = new File(directory, fileName + ".dat");

        if (!f.exists()) {
            return new LoadResult<S>(null, false);
        }

        YamlConfiguration yaml = new YamlConfiguration();

        @Nullable S s;
        try {
            yaml.load(f);
            s = (S) yaml.get("obj");
        } catch (IOException | InvalidConfigurationException e) {
            logger.severe("Couldn't load file '" + f.getName() + "'. Renaming it '" + f.getName() + ".corrupted'");
            e.printStackTrace();
            moveFileToCorrupted(f.getName());
            return new LoadResult<S>(null, true);
        }

        if (s == null) {
            logger.severe("Couldn't get saved object form '" + f.getName() + "'. Renaming it '" + f.getName() + ".corrupted'");
            moveFileToCorrupted(f.getName());
            return new LoadResult<S>(null, true);
        }

        return new LoadResult<S>(s, false);
    }

    public boolean canLoad(@NotNull String fileName) {
        return new File(directory, Objects.requireNonNull(fileName, "File name is null.") + ".dat").exists();
    }

    public void remove(@NotNull S s) {
        Validate.notNull(s, "Object to remove is null.");
        File f = new File(directory, Objects.requireNonNull(getFileName.apply(s), "Function result is null.") + ".dat");

        if (f.exists()) {
            f.delete();
        }
    }

    public boolean remove(@NotNull String fileName) {
        File f = new File(directory, Objects.requireNonNull(fileName, "File name is null.") + ".dat");
        return f.delete();
    }

    public List<S> loadAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        List<S> list = new LinkedList<>();

        for (File f : directory.listFiles()) {
            if (f.getName().endsWith(".dat")) {
                try {
                    yaml.load(f);
                } catch (Exception e) {
                    logger.severe("Couldn't load file '" + f.getName() + "'. Renaming it '" + f.getName() + ".corrupted'");
                    e.printStackTrace();
                    moveFileToCorrupted(f.getName());
                    continue;
                }

                S s = (S) yaml.get("obj");
                if (s == null) {
                    logger.severe("Couldn't get saved object form '" + f.getName() + "'. Renaming it '" + f.getName() + ".corrupted'");
                    moveFileToCorrupted(f.getName());
                    continue;
                }
                list.add(s);
            }
        }
        return list;
    }

    public List<String> listExistingObjects() {
        List<String> list = new LinkedList<>();
        for (File f : directory.listFiles()) {
            String n = f.getName();
            if (n.endsWith(".dat")) {
                list.add(n.substring(0, n.length() - 4));
            }
        }
        return list;
    }

    public void moveFileToCorrupted(@NotNull String fileName) {
        moveFileToCorrupted(fileName, null);
    }

    public void moveFileToCorrupted(@NotNull String fileName, @Nullable String subFolder) {
        moveFileToCorrupted(fileName, fileName, subFolder);
    }

    public void moveFileToCorrupted(@NotNull String fileName, @NotNull String newFileName, @Nullable String subFolder) {
        Validate.notNull(fileName, "File name is null.");
        Validate.notNull(newFileName, "New file name is null.");

        File f = subFolder == null ? directory : new File(directory, subFolder);
        if (!f.exists()) {
            f.mkdirs();
        }

        File old = new File(directory, fileName.endsWith(".dat") ? fileName : fileName + ".dat");
        File newFile = new File(f, newFileName + ".corrupted");
        try {
            Files.move(old, newFile);
            old.delete();
        } catch (Exception e) {
            logger.severe("Couldn't move '" + old.getPath() + "' to '" + newFile.getPath() + "'.");
            e.printStackTrace();
        }

    }

    public static final class LoadResult<S extends ConfigurationSerializable> {

        @Nullable
        private final S s;
        private final boolean corrupted;

        public LoadResult(@Nullable S s, boolean corrupted) {
            this.s = s;
            this.corrupted = corrupted;
        }

        /**
         * Get the loaded object.
         *
         * @return {@code null} if the requested file doesn't exists, otherwise the loaded object.
         * @throws IllegalStateException If file was corrupted.
         */
        public @Nullable S getObject() throws IllegalStateException {
            if (corrupted)
                throw new IllegalStateException("File was corrupted.");
            return s;
        }

        public boolean isCorrupted() {
            return corrupted;
        }
    }

}
