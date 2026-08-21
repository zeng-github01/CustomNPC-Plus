package noppes.npcs.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import noppes.npcs.NoppesStringUtils;
import noppes.npcs.TextBlock;
import noppes.npcs.controllers.data.Dialog;

/**
 * Client-side text block implementation supporting standard English word-wrapping,
 * CJK character layout, Minecraft formatting code ('§') preservation, and line-start
 * punctuation prohibition (Kinsoku Shori).
 */
public class TextBlockClient extends TextBlock {
    private ChatStyle style;
    public int color = 0xe0e0e0;
    public int titleColor = 0xe0e0e0;
    public int titlePos = 0;
    private String name;
    private ICommandSender sender;

    /**
     * Punctuation marks prohibited at the start of a line (Kinsoku Shori / Line-start prohibition).
     */
    private static final String NO_START_PUNCT = "，。！？；：”’）]}》、,.!?:;)]}";

    public TextBlockClient(ICommandSender sender, Dialog dialog, Object... obs) {
        this(dialog.text, dialog.textWidth, false, obs);
        this.color = dialog.color;
        this.titleColor = dialog.titleColor;
        this.titlePos = dialog.titlePos;
        this.sender = sender;
    }

    public TextBlockClient(String name, String text, int lineWidth, int color, Object... obs) {
        this(text, lineWidth, false, obs);
        this.color = color;
        this.name = name;
    }

    public String getName() {
        if (sender != null)
            return sender.getCommandSenderName();
        return name;
    }

    public TextBlockClient(String text, int lineWidth, boolean mcFont, Object... obs) {
        style = new ChatStyle();
        text = NoppesStringUtils.formatText(text, obs);

        // Normalize newlines across platforms
        String[] rawLines = text.split("\\R", -1);

        // Active state of formatting codes (e.g., "§c§l") to carry over line breaks
        String activeFormatting = "";

        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                addLine("");
                continue;
            }

            StringBuilder currentLine = new StringBuilder();
            String[] words = rawLine.split(" ");

            for (int wIdx = 0; wIdx < words.length; wIdx++) {
                String word = words[wIdx];
                if (word.isEmpty()) {
                    if (wIdx > 0) currentLine.append(" ");
                    continue;
                }

                // Test total width when adding the current word (English word-wrap optimization)
                String prefix = (currentLine.length() == 0) ? "" : " ";
                String testStr = currentLine.toString() + prefix + word;

                if (getStringWidth(activeFormatting + testStr, mcFont) <= lineWidth) {
                    if (currentLine.length() > 0) currentLine.append(" ");
                    currentLine.append(word);
                } else {
                    // Fall back to character-by-character processing for long words or CJK blocks
                    for (int i = 0; i < word.length(); i++) {
                        char c = word.charAt(i);

                        // 1. Prevent splitting Minecraft formatting codes (§x) across lines
                        if (c == '§' && i + 1 < word.length()) {
                            char formatChar = word.charAt(i + 1);
                            currentLine.append('§').append(formatChar);
                            activeFormatting = updateFormatting(activeFormatting, formatChar);
                            i++; // Skip formatting character code
                            continue;
                        }

                        // 2. Measure line width including candidate character
                        String nextStr = currentLine.toString() + c;

                        if (getStringWidth(activeFormatting + nextStr, mcFont) > lineWidth && currentLine.length() > 0) {

                            // 3. Line-start prohibition (Kinsoku Shori):
                            // If character is prohibited at line start, pull down the preceding character
                            if (NO_START_PUNCT.indexOf(c) != -1 && currentLine.length() > 0) {
                                char lastChar = currentLine.charAt(currentLine.length() - 1);

                                // Avoid severing formatting sequences (e.g., '§' followed by a code)
                                if (currentLine.length() >= 2 && currentLine.charAt(currentLine.length() - 2) == '§') {
                                    addLine(activeFormatting + currentLine.toString());
                                    currentLine.setLength(0);
                                    currentLine.append(c);
                                } else {
                                    currentLine.deleteCharAt(currentLine.length() - 1);
                                    addLine(activeFormatting + currentLine.toString());
                                    currentLine.setLength(0);
                                    currentLine.append(lastChar).append(c);
                                }
                            } else {
                                addLine(activeFormatting + currentLine.toString());
                                currentLine.setLength(0);
                                currentLine.append(c);
                            }
                        } else {
                            currentLine.append(c);
                        }
                    }

                    // Re-add trailing spaces between split words
                    if (wIdx < words.length - 1) {
                        currentLine.append(" ");
                    }
                }
            }

            if (currentLine.length() > 0) {
                addLine(activeFormatting + currentLine.toString());
            }
        }
    }

    private int getStringWidth(String text, boolean mcFont) {
        if (text.isEmpty()) return 0;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        return mcFont ? font.getStringWidth(text) : ClientProxy.Font.width(text);
    }

    /**
     * Updates active formatting states based on newly encountered control codes.
     */
    private String updateFormatting(String current, char code) {
        code = Character.toLowerCase(code);
        if (code == 'r') return ""; // Reset formatting

        // Color codes override active colors
        if ("0123456789abcdef".indexOf(code) != -1) {
            return "§" + code;
        }

        // Append style modifiers (bold, italic, underline, etc.)
        if (!current.contains("§" + code)) {
            return current + "§" + code;
        }
        return current;
    }

    private void addLine(String text) {
        ChatComponentText line = new ChatComponentText(text);
        line.setChatStyle(style);
        lines.add(line);
    }
}
