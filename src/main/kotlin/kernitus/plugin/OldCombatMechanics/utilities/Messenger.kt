/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import java.util.*
import java.util.logging.Level

object Messenger {
    val HORIZONTAL_BAR: String =
        ChatColor.STRIKETHROUGH.toString() + "----------------------------------------------------"
    private lateinit var plugin: OCMMain

    private var DEBUG_ENABLED = false
    private var PREFIX = "&6[OCM]&r"

    fun initialise(plugin: OCMMain) {
        Messenger.plugin = plugin
    }

    fun reloadConfig(debugEnabled: Boolean, prefix: String) {
        DEBUG_ENABLED = debugEnabled
        PREFIX = prefix
    }

    fun info(message: String, vararg args: Any?) {
        plugin.logger.info(TextUtils.stripColour(String.format(message, *args)))
    }

    @JvmStatic
    fun warn(e: Throwable?, message: String, vararg args: Any?) {
        plugin.logger.log(Level.WARNING, TextUtils.stripColour(String.format(message, *args)), e)
    }

    fun warn(message: String, vararg args: Any?) {
        plugin.logger.log(Level.WARNING, TextUtils.stripColour(String.format(message, *args)))
    }

    /**
     * This will format any ampersand (&) color codes,
     * format any args passed to it using [String.format],
     * and then send the message to the specified [CommandSender].
     *
     * @param sender  The [CommandSender] to send the message to.
     * @param message The message to send.
     * @param args    The args to format the message with.
     */
    @JvmStatic
    fun sendNoPrefix(sender: CommandSender, message: String, vararg args: Any?) {
        Objects.requireNonNull(sender, "sender cannot be null!")
        Objects.requireNonNull(message, "message cannot be null!")
        // Prevents sending of individual empty messages, allowing for selective message disabling.
        if (message.isEmpty()) return
        sender.sendMessage(TextUtils.colourise(String.format(message, *args)))
    }

    /**
     * This will add the prefix to the message, format any ampersand (&) color codes,
     * format any args passed to it using [String.format],
     * and then send the message to the specified [CommandSender].
     *
     * @param sender  The [CommandSender] to send the message to.
     * @param message The message to send.
     * @param prefix  The prefix to the message
     * @param args    The args to format the message with.
     */
    private fun sendWithPrefix(sender: CommandSender, message: String, prefix: String, vararg args: Any) {
        // Prevents sending of individual empty messages, allowing for selective message disabling.
        if (message.isEmpty()) return
        sendNoPrefix(sender, "$prefix $message", *args)
    }

    @JvmStatic
    fun send(sender: CommandSender, message: String, vararg args: Any) {
        sendWithPrefix(sender, message, PREFIX, *args)
    }

    private fun sendDebugMessage(sender: CommandSender, message: String, vararg args: Any) {
        sendWithPrefix(sender, message, "&1[Debug]&r", *args)
    }

    fun debug(message: String?, throwable: Throwable?) {
        if (DEBUG_ENABLED) plugin.logger.log(Level.INFO, message, throwable)
    }

    @JvmStatic
    fun debug(message: String, vararg args: Any?) {
        if (DEBUG_ENABLED) info(
            "[DEBUG] $message", *args
        )
    }

    fun debug(sender: CommandSender, message: String, vararg args: Any) {
        if (DEBUG_ENABLED) sendDebugMessage(sender, message, *args)
    }
}
