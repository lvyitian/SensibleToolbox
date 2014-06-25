package me.desht.sensibletoolbox.enderstorage;

import org.bukkit.ChatColor;

import java.io.File;

public class GlobalHolder extends EnderStorageHolder {
    public GlobalHolder(EnderStorageManager manager, int frequency) {
        super(manager, frequency);
    }

    @Override
    public String getInventoryTitle() {
        return ChatColor.DARK_BLUE + "Ender Storage " + ChatColor.DARK_RED + "[G:ƒ " + getFrequency() + "]";
    }

    @Override
    public File getSaveFile() {
        return new File(getManager().getStorageDir(), Integer.toString(getFrequency()));
    }

    @Override
    public String toString() {
        return "Global Ender Storage #" + getFrequency();
    }
}
