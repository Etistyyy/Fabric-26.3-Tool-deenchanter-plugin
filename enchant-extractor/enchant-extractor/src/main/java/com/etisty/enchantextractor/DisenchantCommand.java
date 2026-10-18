package com.etisty.enchantextractor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * /disenchant            - lists the enchantments on the held item (click one to extract it)
 * /disenchant <name>     - removes that enchantment from the held item and gives it back as an enchanted book
 */
public final class DisenchantCommand {
	private DisenchantCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
			Commands.literal("disenchant")
				.executes(ctx -> list(ctx.getSource()))
				.then(Commands.argument("enchantment", StringArgumentType.greedyString())
					.suggests(DisenchantCommand::suggest)
					.executes(ctx -> extract(ctx.getSource(), StringArgumentType.getString(ctx, "enchantment"))))
		);
	}

	private static ItemEnchantments enchantmentsOf(ItemStack stack) {
		return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
	}

	private static String shortId(String id) {
		return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
	}

	private static int list(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack held = player.getMainHandItem();

		if (held.isEmpty()) {
			source.sendFailure(Component.literal("Hold the item you want to take an enchantment from."));
			return 0;
		}
		if (held.is(Items.ENCHANTED_BOOK)) {
			source.sendFailure(Component.literal("Enchanted books can't be disenchanted. Use an anvil or grindstone."));
			return 0;
		}

		ItemEnchantments enchantments = enchantmentsOf(held);
		if (enchantments.isEmpty()) {
			source.sendFailure(Component.literal("That item has no enchantments."));
			return 0;
		}

		source.sendSuccess(() -> Component.literal("Click an enchantment to move it onto a book:")
			.withStyle(ChatFormatting.GOLD), false);

		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			Holder<Enchantment> holder = entry.getKey();
			int level = entry.getIntValue();
			String id = holder.getRegisteredName();

			Style style = Style.EMPTY
				.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.RunCommand("/disenchant " + shortId(id)))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Extract " + shortId(id))));

			source.sendSuccess(() -> Component.literal("[Extract] ")
				.withStyle(style)
				.append(Enchantment.getFullname(holder, level)), false);
		}
		return enchantments.size();
	}

	private static int extract(CommandSourceStack source, String input) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ItemStack held = player.getMainHandItem();

		if (held.isEmpty()) {
			source.sendFailure(Component.literal("Hold the item you want to take an enchantment from."));
			return 0;
		}
		if (held.is(Items.ENCHANTED_BOOK)) {
			source.sendFailure(Component.literal("Enchanted books can't be disenchanted. Use an anvil or grindstone."));
			return 0;
		}

		String wanted = input.trim().toLowerCase();
		if (!wanted.contains(":")) {
			wanted = "minecraft:" + wanted;
		}

		ItemEnchantments enchantments = enchantmentsOf(held);
		Holder<Enchantment> target = null;
		int level = 0;
		for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
			if (entry.getKey().getRegisteredName().equals(wanted)) {
				target = entry.getKey();
				level = entry.getIntValue();
				break;
			}
		}

		if (target == null) {
			source.sendFailure(Component.literal("The item in your hand doesn't have '" + shortId(wanted) + "'. Run /disenchant to see what it has."));
			return 0;
		}

		// 1. Remove the enchantment from the held item.
		final String targetId = target.getRegisteredName();
		ItemEnchantments.Mutable remaining = new ItemEnchantments.Mutable(enchantments);
		remaining.removeIf(h -> h.getRegisteredName().equals(targetId));
		ItemEnchantments updated = remaining.toImmutable();
		if (updated.isEmpty()) {
			held.remove(DataComponents.ENCHANTMENTS);
		} else {
			held.set(DataComponents.ENCHANTMENTS, updated);
		}

		// 2. Build an enchanted book carrying only that enchantment at the same level.
		ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
		ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		stored.set(target, level);
		book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());

		// 3. Hand it over (drops at the player's feet if the inventory is full).
		if (!player.getInventory().add(book)) {
			player.drop(book, false);
		}
		final Holder<Enchantment> shown = target;
		final int shownLevel = level;
		source.sendSuccess(() -> Component.literal("Moved ")
			.append(Enchantment.getFullname(shown, shownLevel))
			.append(Component.literal(" onto an enchanted book.")), false);
		return 1;
	}

	private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player != null) {
			ItemStack held = player.getMainHandItem();
			if (!held.isEmpty() && !held.is(Items.ENCHANTED_BOOK)) {
				List<String> names = new ArrayList<>();
				for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantmentsOf(held).entrySet()) {
					names.add(shortId(entry.getKey().getRegisteredName()));
				}
				return SharedSuggestionProvider.suggest(names, builder);
			}
		}
		return builder.buildFuture();
	}
}
