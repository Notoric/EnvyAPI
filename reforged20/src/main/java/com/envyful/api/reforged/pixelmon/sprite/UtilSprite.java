package com.envyful.api.reforged.pixelmon.sprite;

import com.envyful.api.forge.chat.UtilChatColour;
import com.envyful.api.reforged.pixelmon.config.SpriteConfig;
import com.envyful.api.text.Placeholder;
import com.envyful.api.text.PlaceholderFactory;
import com.envyful.api.text.parse.SimplePlaceholder;
import com.google.common.collect.Lists;
import com.pixelmonmod.api.Flags;
import com.pixelmonmod.pixelmon.api.pokemon.Pokemon;
import com.pixelmonmod.pixelmon.api.pokemon.PokemonBase;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.api.pokemon.species.Stats;
import com.pixelmonmod.pixelmon.api.pokemon.species.gender.Gender;
import com.pixelmonmod.pixelmon.api.pokemon.species.palette.PaletteProperties;
import com.pixelmonmod.pixelmon.api.pokemon.stats.BattleStatsType;
import com.pixelmonmod.pixelmon.api.pokemon.stats.ExtraStats;
import com.pixelmonmod.pixelmon.api.pokemon.stats.IVStore;
import com.pixelmonmod.pixelmon.api.pokemon.stats.extraStats.LakeTrioStats;
import com.pixelmonmod.pixelmon.api.pokemon.stats.extraStats.MewStats;
import com.pixelmonmod.pixelmon.api.registries.PixelmonItems;
import com.pixelmonmod.pixelmon.api.registries.PixelmonSpecies;
import com.pixelmonmod.pixelmon.api.storage.NbtKeys;
import com.pixelmonmod.pixelmon.api.util.helpers.SpriteItemHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.Collections;
import java.util.List;

public class UtilSprite {

    private UtilSprite() {
        throw new UnsupportedOperationException("Static utility class");
    }

    public static ItemStack getPokemonElement(Pokemon pokemon) {
        return getPokemonElement(pokemon, SpriteConfig.DEFAULT);
    }

    public static ItemStack getPokemonElement(Pokemon pokemon, SpriteConfig config, Placeholder... transformers) {
        ItemStack itemStack = getPixelmonSprite(pokemon);
        List<Placeholder> pokemonPlaceholders = getPokemonPlaceholders(pokemon, config, transformers);

        CompoundTag compound = itemStack.getOrCreateTagElement("display");
        ListTag lore = new ListTag();

        for (Component s : getPokemonDesc(config, pokemonPlaceholders)) {
            if (s instanceof MutableComponent) {
                s = ((MutableComponent) s).setStyle(s.getStyle().withItalic(false));
            }

            lore.add(StringTag.valueOf(Component.Serializer.toJson(s)));
        }

        Component colour = PlaceholderFactory.handlePlaceholders(Collections.singletonList(config.getName()), UtilChatColour::colour, pokemonPlaceholders).get(0);

        if (colour instanceof MutableComponent) {
            colour = ((MutableComponent) colour).setStyle(colour.getStyle().withItalic(false));
        }

        compound.put("Name", StringTag.valueOf(Component.Serializer.toJson(colour)));
        compound.put("Lore", lore);
        CompoundTag tag = itemStack.getOrCreateTag();

        tag.put("display", compound);
        itemStack.setTag(tag);

        return itemStack;
    }

    private static List<Placeholder> getPokemonPlaceholders(Pokemon pokemon, SpriteConfig spriteConfig, Placeholder... placeholders) {
        List<Placeholder> placeholderList = Lists.newArrayList(placeholders);

        placeholderList.add((SimplePlaceholder)line -> replacePokemonPlaceholders(line, pokemon, spriteConfig));
        return placeholderList;
    }

    public static ItemStack getPixelmonSprite(Species pokemon) {
        ItemStack itemStack = new ItemStack(PixelmonItems.pixelmon_sprite);
        CompoundTag tagCompound = new CompoundTag();
        itemStack.setTag(tagCompound);
        tagCompound.putShort("ndex", (short)pokemon.getDex());
        tagCompound.putString("form", pokemon.getDefaultForm().getName());
        tagCompound.putByte("gender", (byte)Gender.MALE.ordinal());
        tagCompound.putString("palette", pokemon.getDefaultForm().getDefaultGenderProperties().getDefaultPalette().getName());

        return itemStack;
    }

    public static ItemStack getPixelmonSprite(Pokemon pokemon) {
        return SpriteItemHelper.getPhoto(pokemon);
    }

    public static List<Component> getPokemonDesc(SpriteConfig config, List<Placeholder> placeholders) {
        return PlaceholderFactory.handlePlaceholders(config.getLore(), UtilChatColour::colour, placeholders);
    }

    public static String replacePokemonPlaceholders(String line, Pokemon pokemon, SpriteConfig config) {
        IVStore iVs = pokemon.getIVs();
        float ivHP = iVs.getStat(BattleStatsType.HP);
        float ivAtk = iVs.getStat(BattleStatsType.ATTACK);
        float ivDef = iVs.getStat(BattleStatsType.DEFENSE);
        float ivSpeed = iVs.getStat(BattleStatsType.SPEED);
        float ivSAtk = iVs.getStat(BattleStatsType.SPECIAL_ATTACK);
        float ivSDef = iVs.getStat(BattleStatsType.SPECIAL_DEFENSE);
        int percentage = Math.round(((ivHP + ivDef + ivAtk + ivSpeed + ivSAtk + ivSDef) / 186f) * 100);
        float evHP = pokemon.getEVs().getStat(BattleStatsType.HP);
        float evAtk = pokemon.getEVs().getStat(BattleStatsType.ATTACK);
        float evDef = pokemon.getEVs().getStat(BattleStatsType.DEFENSE);
        float evSpeed = pokemon.getEVs().getStat(BattleStatsType.SPEED);
        float evSAtk = pokemon.getEVs().getStat(BattleStatsType.SPECIAL_ATTACK);
        float evSDef = pokemon.getEVs().getStat(BattleStatsType.SPECIAL_DEFENSE);
        ExtraStats extraStats = pokemon.getExtraStats();

        line = line
                .replace("%nickname%", pokemon.getDisplayName())
                .replace("%held_item%", pokemon.getHeldItem().getHoverName().getString())
                .replace("%palette%", pokemon.getPalette().getLocalizedName())
                .replace("%species_name%", pokemon.isEgg() ? "Egg" : pokemon.getSpecies().getLocalizedName())
                .replace("%level%", pokemon.getPokemonLevel() + "")
                        .replace("%gender%", pokemon.getGender() == Gender.MALE ? config.getMaleFormat() :
                                pokemon.getGender() == Gender.NONE ? config.getNoneFormat() :
                                        config.getFemaleFormat())
                        .replace("%breedable%", pokemon.hasFlag(Flags.UNBREEDABLE) ?
                                config.getUnbreedableFalseFormat() : config.getUnbreedableTrueFormat())
                        .replace("%nature%", config.getNatureFormat()
                                .replace("%nature_name%",
                                        pokemon.getMintNature() != null ?
                                                pokemon.getBaseNature().getLocalizedName() :
                                                pokemon.getNature().getLocalizedName())
                                .replace("%mint_nature%", pokemon.getMintNature() != null ?
                                        config.getMintNatureFormat().replace("%mint_nature_name%", pokemon.getMintNature().getLocalizedName()) : ""))
                        .replace("%ability%", config.getAbilityFormat()
                                .replace("%ability_name%", pokemon.getAbility().getLocalizedName())
                                .replace("%ability_ha%", pokemon.hasHiddenAbility() ? config.getHaFormat() : ""))
                        .replace("%friendship%", pokemon.getFriendship() + "")
                        .replace("%untradeable%", pokemon.hasFlag("untradeable") ?
                                config.getUntrdeableTrueFormat() : config.getUntradeableFalseFormat())
                        .replace("%iv_percentage%", percentage + "")
                        .replace("%iv_hp%", getColour(config, iVs, BattleStatsType.HP) + ((int) ivHP) + "")
                        .replace("%iv_attack%", getColour(config, iVs, BattleStatsType.ATTACK) + ((int) ivAtk) + "")
                        .replace("%iv_defence%", getColour(config, iVs, BattleStatsType.DEFENSE) + ((int) ivDef) + "")
                        .replace("%iv_spattack%", getColour(config, iVs, BattleStatsType.SPECIAL_ATTACK) + ((int) ivSAtk) + "")
                        .replace("%iv_spdefence%", getColour(config, iVs, BattleStatsType.SPECIAL_DEFENSE) + ((int) ivSDef) + "")
                        .replace("%iv_speed%", getColour(config, iVs, BattleStatsType.SPEED) + ((int) ivSpeed) + "")
                        .replace("%ev_hp%", ((int) evHP) + "")
                        .replace("%ev_attack%", ((int) evAtk) + "")
                        .replace("%ev_defence%", ((int) evDef) + "")
                        .replace("%ev_spattack%", ((int) evSAtk) + "")
                        .replace("%ev_spdefence%", ((int) evSDef) + "")
                        .replace("%ev_speed%", ((int) evSpeed) + "")
                        .replace("%move_1%", getMove(pokemon, 0))
                        .replace("%move_2%", getMove(pokemon, 1))
                        .replace("%move_3%", getMove(pokemon, 2))
                        .replace("%move_4%", getMove(pokemon, 3))
                        .replace("%shiny%", pokemon.isShiny() ? config.getShinyTrueFormat() : config.getShinyFalseFormat())
                        .replace("%form%", pokemon.getForm().getLocalizedName())
                        .replace("%size%", pokemon.getGrowth().getLocalizedName())
                        .replace("%friendship%", pokemon.getFriendship() + "");

        if (extraStats instanceof MewStats) {
            line = line.replace("%mew_cloned%", config.getMewClonedFormat()
                    .replace("%cloned%", ((MewStats) extraStats).numCloned + ""));
        } else {
            if (line.contains("%mew_cloned%") || line.contains("%cloned%")) {
                line = null;
            }
        }

        if (line != null) {
            if (extraStats instanceof LakeTrioStats) {
                line = line.replace("%trio_gemmed%", config.getGemmedFormat()
                        .replace("%gemmed%", ((LakeTrioStats)extraStats).numEnchanted + ""));
            } else {
                if (line.contains("%trio_gemmed%") || line.contains("%gemmed%")) {
                    line = null;
                }
            }
        }

        return line;
    }

    private static String getColour(SpriteConfig config, IVStore ivStore, BattleStatsType statsType) {
        if (ivStore.isHyperTrained(statsType)) {
            return config.getHyperIvColour();
        }

        return config.getNormalIvColour();
    }

    private static String getMove(Pokemon pokemon, int pos) {
        if (pokemon.getMoveset() == null) {
            return "";
        }

        if (pokemon.getMoveset().attacks.length <= pos) {
            return "";
        }

        if (pokemon.getMoveset().attacks[pos] == null) {
            return "";
        }

        return pokemon.getMoveset().attacks[pos].getActualMove().getLocalizedName();
    }

    public static Pokemon getPokemon(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        if (!tag.contains(SpriteItemHelper.NDEX)) {
            return null;
        }

        boolean isEgg = tag.contains(NbtKeys.EGG_CYCLES);
        int eggCycles = isEgg ? tag.getInt(NbtKeys.EGG_CYCLES) : -1;
        Species species = PixelmonSpecies.fromNationalDex((int) tag.getShort(SpriteItemHelper.NDEX));

        if(species == null) {
            return null;
        }

        Stats form = species.getForm(tag.getString(SpriteItemHelper.FORM));
        Gender gender = Gender.values()[tag.getByte(SpriteItemHelper.GENDER)];

        if(form == null || gender == null) {
            return null;
        }

        PaletteProperties palette = form.getGenderProperties(gender).getPalette(tag.getString(SpriteItemHelper.PALETTE));

        if(palette == null) {
            return null;
        }

        PokemonBase base = new PokemonBase(species, form, palette, gender);

        if(isEgg) {
            base.setEggCycles(eggCycles);
        }

        return base.toPokemon();
    }
}
