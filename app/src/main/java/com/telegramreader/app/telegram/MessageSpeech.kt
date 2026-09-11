package com.telegramreader.app.telegram

import org.drinkless.tdlib.TdApi

/** Turns a Telegram message's content into something worth reading aloud, or null if there's nothing to say. */
object MessageSpeech {

    fun textOf(content: TdApi.MessageContent): String? {
        val raw: String? = when (content) {
            is TdApi.MessageText -> content.text.text
            is TdApi.MessagePhoto -> withPrefix("Photo", content.caption)
            is TdApi.MessageVideo -> withPrefix("Video", content.caption)
            is TdApi.MessageAnimation -> withPrefix("GIF", content.caption)
            is TdApi.MessageDocument -> withPrefix("File ${content.document.fileName}", content.caption)
            is TdApi.MessageAudio -> withPrefix("Audio", content.caption)
            is TdApi.MessageVoiceNote -> withPrefix("Voice message", content.caption)
            is TdApi.MessageVideoNote -> "Video message"
            is TdApi.MessageSticker -> null
            is TdApi.MessagePoll -> pollText(content.poll)
            is TdApi.MessageLocation -> "Location"
            is TdApi.MessageContact -> "Contact: ${content.contact.firstName} ${content.contact.lastName}".trim()
            else -> null
        }
        return raw?.let(::clean)?.takeIf { it.isNotBlank() }
    }

    private fun withPrefix(prefix: String, caption: TdApi.FormattedText?): String {
        val c = caption?.text?.trim().orEmpty()
        return if (c.isEmpty()) prefix else "$prefix. $c"
    }

    private fun pollText(poll: TdApi.Poll): String {
        val opts = poll.options.joinToString(". ") { it.text.text }
        return "Poll: ${poll.question.text}. Options: $opts"
    }

    private val urlRegex = Regex("""https?://\S+""")
    private val hashtagRegex = Regex("""#(\w+)""")
    private val mentionRegex = Regex("""@(\w+)""")
    private val markdownJunk = Regex("""[*_~`>|]+""")
    private val multiSpace = Regex("""[ \t]+""")
    private val multiNewline = Regex("""\n{2,}""")

    /** Strip things a TTS engine reads badly (URLs, markdown, emoji/pictographs). */
    fun clean(text: String): String = stripPictographs(text)
        .replace(urlRegex, " link ")
        .replace(hashtagRegex) { it.groupValues[1] }
        .replace(mentionRegex) { it.groupValues[1] }
        .replace(markdownJunk, " ")
        .replace(multiSpace, " ")
        .replace(multiNewline, "\n")
        .trim()

    private fun stripPictographs(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val keep = when (Character.getType(cp).toByte()) {
                Character.OTHER_SYMBOL, Character.SURROGATE, Character.UNASSIGNED -> false
                else -> cp != 0xFE0F && cp != 0x200D // variation selector / zero-width joiner
            }
            if (keep) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return sb.toString()
    }

    /** Splits text into chunks below [maxLen], preferring sentence/line boundaries. */
    fun chunk(text: String, maxLen: Int): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (piece in text.split(Regex("""(?<=[.!?\n])\s+"""))) {
            if (piece.length > maxLen) {
                if (sb.isNotEmpty()) { out += sb.toString(); sb.clear() }
                piece.chunked(maxLen).forEach { out += it }
                continue
            }
            if (sb.length + piece.length + 1 > maxLen) { out += sb.toString(); sb.clear() }
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(piece)
        }
        if (sb.isNotEmpty()) out += sb.toString()
        return out
    }
}
