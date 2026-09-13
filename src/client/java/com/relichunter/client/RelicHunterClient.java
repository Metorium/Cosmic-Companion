package com.relichunter.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

public class RelicHunterClient implements ClientModInitializer {

    private static final Identifier RELIC_HUD_ID =
            Identifier.fromNamespaceAndPath(
                    "relic-hunter",
                    "relic_notification"
            );

    private static final Identifier COUNTER_HUD_ID =
            Identifier.fromNamespaceAndPath(
                    "relic-hunter",
                    "relic_counters"
            );

    private static long relicDisplayUntil = 0;

    // =========================================================
    // SESSION COUNTERS
    // =========================================================

    private static int luckyBlockCount = 0;
    private static int enchantRelicCount = 0;
    private static int prestigeRelicCount = 0;

    // =========================================================
    // RELIC HUNTER DETECTION
    // =========================================================

    // Detects Relic Hunter I through Relic Hunter X
    private static final Pattern ENCHANT_RELIC_PATTERN =
            Pattern.compile(
                    "Relic Hunter (I|II|III|IV|V|VI|VII|VIII|IX|X) proc!"
            );

    // =========================================================
    // GUI POSITION
    // =========================================================

    private static int counterX = 20;
    private static int counterY = 20;

    // Saved position file
    private static final Path CONFIG_FILE =
            Paths.get(
                    System.getProperty("user.dir"),
                    "config",
                    "relic-hunter-position.txt"
            );

    // =========================================================
    // GUI EDIT MODE
    // =========================================================

    private static boolean editMode = false;
    private static boolean dragging = false;

    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;

    private static boolean previousLeftPressed = false;

    // =========================================================
    // KEY CATEGORY
    // =========================================================

    private static final KeyMapping.Category RELIC_HUNTER_CATEGORY =
            KeyMapping.Category.register(
                    Identifier.fromNamespaceAndPath(
                            "relic-hunter",
                            "controls"
                    )
            );

    // =========================================================
    // L = MOVE GUI
    // =========================================================

    private static final KeyMapping MOVE_GUI_KEY =
            KeyBindingHelper.registerKeyBinding(
                    new KeyMapping(
                            "key.relic-hunter.move_gui",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_L,
                            RELIC_HUNTER_CATEGORY
                    )
            );

    // =========================================================
    // R = RESET COUNTERS
    // =========================================================

    private static final KeyMapping RESET_SESSION_KEY =
            KeyBindingHelper.registerKeyBinding(
                    new KeyMapping(
                            "key.relic-hunter.reset_session",
                            InputConstants.Type.KEYSYM,
                            GLFW.GLFW_KEY_R,
                            RELIC_HUNTER_CATEGORY
                    )
            );

    // =========================================================
    // INITIALIZE
    // =========================================================

    @Override
    public void onInitializeClient() {

        // Load saved GUI position
        loadGuiPosition();

        // =====================================================
        // CHAT / GAME MESSAGE DETECTION
        // =====================================================

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {

            String text = message.getString();

            // -------------------------------------------------
            // RELIC HUNTER I-X
            // -------------------------------------------------

            if (ENCHANT_RELIC_PATTERN.matcher(text).find()) {

                enchantRelicProc();

            }

            // -------------------------------------------------
            // PRESTIGE RELIC
            // -------------------------------------------------

            else if (
                    text.contains(
                            "You discovered a Relic while mining!"
                    )
                            && !text.trim().endsWith("(Relic Hunter)")
            ) {

                prestigeRelicProc();

            }

            // -------------------------------------------------
            // LUCKY BLOCK
            // -------------------------------------------------

            else if (
                    text.contains(
                            "Your pickaxe uncovered a Lucky Block!"
                    )
            ) {

                luckyBlockProc();
            }
        });

        // =====================================================
        // RELIC NOTIFICATION HUD
        // =====================================================

        HudElementRegistry.addLast(
                RELIC_HUD_ID,
                RelicHunterClient::renderRelic
        );

        // =====================================================
        // COUNTER HUD
        // =====================================================

        HudElementRegistry.addLast(
                COUNTER_HUD_ID,
                RelicHunterClient::renderCounters
        );

        // =====================================================
        // KEY HANDLING
        // =====================================================

        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {

            // -------------------------------------------------
            // L = TOGGLE EDIT MODE
            // -------------------------------------------------

            while (MOVE_GUI_KEY.consumeClick()) {

                editMode = !editMode;
                dragging = false;
                previousLeftPressed = false;

                if (minecraft.player != null) {

                    if (editMode) {

                        minecraft.mouseHandler.releaseMouse();
                        minecraft.mouseHandler.setIgnoreFirstMove();

                        minecraft.player.displayClientMessage(
                                Component.literal(
                                        "§dGUI EDIT MODE"
                                ),
                                true
                        );

                    } else {

                        // Save position when leaving edit mode
                        saveGuiPosition();

                        minecraft.mouseHandler.grabMouse();

                        minecraft.player.displayClientMessage(
                                Component.literal(
                                        "§aGUI LOCKED"
                                ),
                                true
                        );
                    }
                }
            }

            // -------------------------------------------------
            // R = RESET COUNTERS
            // -------------------------------------------------

            while (RESET_SESSION_KEY.consumeClick()) {

                luckyBlockCount = 0;
                enchantRelicCount = 0;
                prestigeRelicCount = 0;

                if (minecraft.player != null) {

                    minecraft.player.displayClientMessage(
                            Component.literal(
                                    "§dRelic counters reset!"
                            ),
                            true
                    );
                }
            }

            // -------------------------------------------------
            // HANDLE GUI DRAGGING
            // -------------------------------------------------

            handleDragging(minecraft);
        });
    }

    // =========================================================
    // LUCKY BLOCK
    // =========================================================

    private static void luckyBlockProc() {

        luckyBlockCount++;

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player != null) {

            // End Portal opens sound
            minecraft.player.playSound(
                    SoundEvents.END_PORTAL_SPAWN,
                    1.0F,
                    1.0F
            );
        }
    }

    // =========================================================
    // ENCHANT RELIC
    // =========================================================

    private static void enchantRelicProc() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player != null) {

            enchantRelicCount++;

            showRelicNotification(minecraft);
        }
    }

    // =========================================================
    // PRESTIGE RELIC
    // =========================================================

    private static void prestigeRelicProc() {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player != null) {

            prestigeRelicCount++;

            showRelicNotification(minecraft);
        }
    }

    // =========================================================
    // RELIC NOTIFICATION + SOUND
    // =========================================================

    private static void showRelicNotification(
            Minecraft minecraft
    ) {

        // Show RELIC! for 2 seconds
        relicDisplayUntil =
                System.currentTimeMillis() + 2000;

        // End Portal opens sound
        minecraft.player.playSound(
                SoundEvents.END_PORTAL_SPAWN,
                1.0F,
                1.0F
        );
    }

    // =========================================================
    // RENDER RELIC NOTIFICATION
    // =========================================================

    private static void renderRelic(
            GuiGraphics graphics,
            DeltaTracker tickCounter
    ) {

        if (
                System.currentTimeMillis()
                        > relicDisplayUntil
        ) {

            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            return;
        }

        String text = "RELIC!";

        int screenWidth =
                graphics.guiWidth();

        int screenHeight =
                graphics.guiHeight();

        float scale = 6.0F;

        graphics.pose().pushMatrix();

        graphics.pose().scale(
                scale,
                scale
        );

        int centerX =
                (int)
                        (
                                screenWidth
                                        / 2.0F
                                        / scale
                        );

        int centerY =
                (int)
                        (
                                screenHeight
                                        / 2.0F
                                        / scale
                        )
                        - 12;

        int textWidth =
                minecraft.font.width(text);

        int textX =
                centerX
                        - textWidth / 2;

        int textY =
                centerY;

        graphics.drawString(
                minecraft.font,
                Component.literal(text),
                textX,
                textY,
                0xFFFF1493
        );

        graphics.pose().popMatrix();
    }

    // =========================================================
    // RENDER COUNTERS
    // =========================================================

    private static void renderCounters(
            GuiGraphics graphics,
            DeltaTracker tickCounter
    ) {

        Minecraft minecraft =
                Minecraft.getInstance();

        if (minecraft.player == null) {

            return;
        }

        int totalRelics =
                enchantRelicCount
                        + prestigeRelicCount;

        // -----------------------------------------------------
        // LUCKY BLOCKS
        // -----------------------------------------------------

        String luckyBlockText =
                "Lucky Blocks: "
                        + luckyBlockCount;

        graphics.drawString(
                minecraft.font,
                Component.literal(
                        luckyBlockText
                ),
                counterX,
                counterY,
                0xFFFFFF00
        );

        // -----------------------------------------------------
        // ENCHANT
        // -----------------------------------------------------

        String enchantText =
                "Relic (Enchant): "
                        + enchantRelicCount;

        graphics.drawString(
                minecraft.font,
                Component.literal(
                        enchantText
                ),
                counterX,
                counterY + 12,
                0xFFFF1493
        );

        // -----------------------------------------------------
        // PRESTIGE
        // -----------------------------------------------------

        String prestigeText =
                "Relic (Prestige): "
                        + prestigeRelicCount;

        graphics.drawString(
                minecraft.font,
                Component.literal(
                        prestigeText
                ),
                counterX,
                counterY + 24,
                0xFFFF69B4
        );

        // -----------------------------------------------------
        // TOTAL
        // -----------------------------------------------------

        String totalText =
                "Total: "
                        + totalRelics;

        graphics.drawString(
                minecraft.font,
                Component.literal(
                        totalText
                ),
                counterX,
                counterY + 36,
                0xFFFFFFFF
        );

        // -----------------------------------------------------
        // EDIT MODE
        // -----------------------------------------------------

        if (editMode) {

            graphics.drawString(
                    minecraft.font,
                    Component.literal(
                            "DRAG ME"
                    ),
                    counterX,
                    counterY + 48,
                    0xFFFFFFFF
            );
        }
    }

    // =========================================================
    // HANDLE GUI DRAGGING
    // =========================================================

    private static void handleDragging(
            Minecraft minecraft
    ) {

        if (
                !editMode
                        || minecraft.player == null
        ) {

            previousLeftPressed = false;
            dragging = false;

            return;
        }

        long window =
                minecraft.getWindow().handle();

        // -----------------------------------------------------
        // GET MOUSE POSITION DIRECTLY FROM GLFW
        // -----------------------------------------------------

        double[] mouseXBuffer =
                new double[1];

        double[] mouseYBuffer =
                new double[1];

        GLFW.glfwGetCursorPos(
                window,
                mouseXBuffer,
                mouseYBuffer
        );

        double rawMouseX =
                mouseXBuffer[0];

        double rawMouseY =
                mouseYBuffer[0];

        int windowWidth =
                minecraft.getWindow()
                        .getWidth();

        int windowHeight =
                minecraft.getWindow()
                        .getHeight();

        int guiWidth =
                minecraft.getWindow()
                        .getGuiScaledWidth();

        int guiHeight =
                minecraft.getWindow()
                        .getGuiScaledHeight();

        int mouseX =
                (int)
                        (
                                rawMouseX
                                        * guiWidth
                                        / windowWidth
                        );

        int mouseY =
                (int)
                        (
                                rawMouseY
                                        * guiHeight
                                        / windowHeight
                        );

        // -----------------------------------------------------
        // LEFT MOUSE BUTTON
        // -----------------------------------------------------

        boolean leftPressed =
                GLFW.glfwGetMouseButton(
                        window,
                        GLFW.GLFW_MOUSE_BUTTON_LEFT
                )
                        == GLFW.GLFW_PRESS;

        // -----------------------------------------------------
        // CALCULATE GROUP WIDTH
        // -----------------------------------------------------

        String luckyBlockText =
                "Lucky Blocks: "
                        + luckyBlockCount;

        String enchantText =
                "Relic (Enchant): "
                        + enchantRelicCount;

        String prestigeText =
                "Relic (Prestige): "
                        + prestigeRelicCount;

        String totalText =
                "Total: "
                        + (
                        enchantRelicCount
                                + prestigeRelicCount
                );

        int luckyBlockWidth =
                minecraft.font.width(
                        luckyBlockText
                );

        int enchantWidth =
                minecraft.font.width(
                        enchantText
                );

        int prestigeWidth =
                minecraft.font.width(
                        prestigeText
                );

        int totalWidth =
                minecraft.font.width(
                        totalText
                );

        int groupWidth =
                Math.max(
                        luckyBlockWidth,
                        Math.max(
                                enchantWidth,
                                Math.max(
                                        prestigeWidth,
                                        totalWidth
                                )
                        )
                );

        // Entire group is approximately 58 pixels tall
        int groupHeight = 58;

        // -----------------------------------------------------
        // START DRAGGING
        // -----------------------------------------------------

        if (
                leftPressed
                        && !previousLeftPressed
        ) {

            if (
                    mouseX >= counterX
                            && mouseX
                            <= counterX + groupWidth
                            && mouseY >= counterY
                            && mouseY
                            <= counterY + groupHeight
            ) {

                dragging = true;

                dragOffsetX =
                        mouseX - counterX;

                dragOffsetY =
                        mouseY - counterY;
            }
        }

        // -----------------------------------------------------
        // MOVE GROUP
        // -----------------------------------------------------

        if (
                dragging
                        && leftPressed
        ) {

            counterX =
                    mouseX - dragOffsetX;

            counterY =
                    mouseY - dragOffsetY;
        }

        // -----------------------------------------------------
        // STOP DRAGGING
        // -----------------------------------------------------

        if (!leftPressed) {

            if (dragging) {

                // Save position when mouse is released
                saveGuiPosition();
            }

            dragging = false;
        }

        previousLeftPressed =
                leftPressed;
    }

    // =========================================================
    // SAVE GUI POSITION
    // =========================================================

    private static void saveGuiPosition() {

        try {

            Path parent =
                    CONFIG_FILE.getParent();

            if (parent != null) {

                Files.createDirectories(
                        parent
                );
            }

            Files.writeString(
                    CONFIG_FILE,
                    counterX
                            + "\n"
                            + counterY
            );

        } catch (IOException e) {

            System.err.println(
                    "Relic Hunter: Could not save GUI position."
            );
        }
    }

    // =========================================================
    // LOAD GUI POSITION
    // =========================================================

    private static void loadGuiPosition() {

        try {

            if (!Files.exists(CONFIG_FILE)) {

                return;
            }

            java.util.List<String> lines =
                    Files.readAllLines(
                            CONFIG_FILE
                    );

            if (lines.size() >= 2) {

                counterX =
                        Integer.parseInt(
                                lines.get(0).trim()
                        );

                counterY =
                        Integer.parseInt(
                                lines.get(1).trim()
                        );
            }

        } catch (Exception e) {

            System.err.println(
                    "Relic Hunter: Could not load GUI position. "
                            + "Using default position."
            );

            counterX = 20;
            counterY = 20;
        }
    }
}