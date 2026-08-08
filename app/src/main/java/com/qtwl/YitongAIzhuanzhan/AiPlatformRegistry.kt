package com.qtwl.YitongAIzhuanzhan

/**
 * A supported web-AI platform and the DOM selectors used by the automation bridge.
 *
 * Selectors are deliberately ordered from the most specific/stable attributes to
 * conservative fallbacks. The runner never treats arbitrary page text as an AI
 * response; only elements matched by assistantMessageSelectors are considered.
 */
data class AiPlatformDefinition(
    val id: String,
    val displayName: String,
    val url: String,
    val hosts: Set<String>,
    val inputSelectors: List<String>,
    val sendButtonSelectors: List<String>,
    val assistantMessageSelectors: List<String>,
    val loadingSelectors: List<String>,
    val afterFillDelayMs: Long = 500L
)

object AiPlatformRegistry {
    val supported: List<AiPlatformDefinition> = listOf(
        AiPlatformDefinition(
            id = "doubao",
            displayName = "Doubao / 豆包",
            url = "https://www.doubao.com/chat/",
            hosts = setOf("doubao.com", "www.doubao.com"),
            inputSelectors = listOf(
                "textarea[data-testid='chat_input_input']",
                "textarea[placeholder*='输入消息']",
                "textarea[placeholder*='message']",
                "div[contenteditable='true']",
                "textarea",
                "[role='textbox']"
            ),
            sendButtonSelectors = listOf(
                "#flow-end-msg-send",
                "button[data-testid*='send']",
                "button[aria-label*='发送']",
                "button[aria-label*='Send']",
                "button[class*='send']"
            ),
            assistantMessageSelectors = listOf(
                "[data-testid='message_text_content']",
                "[data-testid*='assistant'] .markdown-body",
                "[data-testid*='assistant'] [class*='content']",
                ".message-bubble .markdown-body",
                "[data-role='assistant']",
                "[class*='message-content']",
                "[class*='chat-message'] [class*='content']"
            ),
            loadingSelectors = listOf(
                "[aria-busy='true']",
                ".loading-spinner",
                "button[aria-label*='停止']",
                "button[aria-label*='Stop']",
                "[class*='streaming']",
                "[class*='generating']"
            ),
            afterFillDelayMs = 800L
        ),
        AiPlatformDefinition(
            id = "yuanbao",
            displayName = "Yuanbao / 元宝",
            url = "https://yuanbao.tencent.com/",
            hosts = setOf("yuanbao.tencent.com"),
            inputSelectors = listOf(
                "[data-slate-editor='true']",
                "[data-slate-editor]",
                "[contenteditable='true'][data-slate-node]",
                "[role='textbox']",
                "textarea"
            ),
            sendButtonSelectors = listOf(
                "button[data-testid*='send']",
                "button[aria-label*='发送']",
                "button[aria-label*='Send']",
                "button[class*='send']",
                "button[type='submit']"
            ),
            assistantMessageSelectors = listOf(
                "[data-role='assistant']",
                "[data-testid*='assistant']",
                ".agent-chat__conv--ai .hyc-content-text",
                ".agent-chat__conv--ai [class*='markdown']",
                ".chat-message[class*='assistant']"
            ),
            loadingSelectors = listOf(
                "[data-testid*='loading']",
                "[aria-busy='true']",
                "[class*='loading']",
                "button[aria-label*='停止']"
            ),
            afterFillDelayMs = 700L
        ),
        AiPlatformDefinition(
            id = "tongyi",
            displayName = "Qwen / 通义千问",
            url = "https://www.qianwen.com/",
            hosts = setOf("www.qianwen.com", "qianwen.com", "tongyi.aliyun.com", "qianwen.aliyun.com"),
            inputSelectors = listOf(
                "textarea",
                "[contenteditable='true']",
                "[role='textbox']",
                ".ProseMirror"
            ),
            sendButtonSelectors = listOf(
                "button[aria-label*='发送']",
                "button[aria-label*='Send']",
                "button[class*='send']",
                "button[data-testid*='send']",
                "button[type='submit']"
            ),
            assistantMessageSelectors = listOf(
                "[class*='message-select-wrapper-answer']",
                "[class*='message-select-content-inner']",
                "[class*='message-select-content']",
                "[data-role='assistant']",
                "[data-message-author-role='assistant']",
                ".qwen-markdown",
                ".tongyi-markdown",
                "[class*='assistant'] [class*='markdown']"
            ),
            loadingSelectors = listOf(
                "[aria-busy='true']",
                "[class*='loading']",
                "button[aria-label*='停止']",
                "button[aria-label*='Stop']"
            )
        ),
        AiPlatformDefinition(
            id = "deepseek",
            displayName = "DeepSeek",
            url = "https://chat.deepseek.com/",
            hosts = setOf("chat.deepseek.com", "deepseek.com"),
            inputSelectors = listOf(
                "textarea",
                "[contenteditable='true']",
                "[role='textbox']"
            ),
            sendButtonSelectors = listOf(
                "button[aria-label*='发送']",
                "button[aria-label*='Send']",
                "button[data-testid*='send']",
                "button[class*='send']",
                "button[type='submit']"
            ),
            assistantMessageSelectors = listOf(
                "[data-message-author-role='assistant']",
                "[data-role='assistant']",
                ".ds-markdown",
                "[class*='assistant'] .ds-markdown",
                "[class*='assistant'] [class*='markdown']"
            ),
            loadingSelectors = listOf(
                "[aria-busy='true']",
                "button[aria-label*='停止']",
                "button[aria-label*='Stop']",
                "[class*='loading']"
            )
        ),
        AiPlatformDefinition(
            id = "kimi",
            displayName = "Kimi",
            url = "https://www.kimi.com/",
            hosts = setOf("www.kimi.com", "kimi.com", "kimi.moonshot.cn", "moonshot.cn"),
            inputSelectors = listOf(
                ".chat-input-editor[contenteditable='true']",
                ".chat-input-editor[role='textbox']",
                "textarea",
                "[contenteditable='true']",
                "[role='textbox']",
                ".ProseMirror"
            ),
            sendButtonSelectors = listOf(
                "button[aria-label*='发送']",
                "button[aria-label*='Send']",
                "button[data-testid*='send']",
                "button[class*='send']",
                "button[type='submit']"
            ),
            assistantMessageSelectors = listOf(
                "[data-role='assistant']",
                "[data-message-author-role='assistant']",
                ".segment-assistant [class*='markdown']",
                ".segment-assistant",
                "[class*='assistant'] [class*='markdown']"
            ),
            loadingSelectors = listOf(
                "[aria-busy='true']",
                "[class*='loading']",
                "button[aria-label*='停止']",
                "button[aria-label*='Stop']"
            ),
            afterFillDelayMs = 900L
        )
    )

    private val generic = AiPlatformDefinition(
        id = "generic",
        displayName = "Web AI",
        url = "about:blank",
        hosts = emptySet(),
        inputSelectors = listOf(
            "textarea",
            "[contenteditable='true']",
            "[role='textbox']",
            "[data-slate-editor]",
            ".ProseMirror"
        ),
        sendButtonSelectors = listOf(
            "button[data-testid*='send']",
            "button[aria-label*='发送']",
            "button[aria-label*='Send']",
            "button[class*='send']",
            "button[type='submit']"
        ),
        assistantMessageSelectors = listOf(
            "[data-role='assistant']",
            "[data-message-author-role='assistant']",
            "[data-testid*='assistant']",
            ".ai-message",
            ".bot-message",
            "[class*='assistant'] [class*='markdown']"
        ),
        loadingSelectors = listOf(
            "[aria-busy='true']",
            "[class*='loading']",
            "button[aria-label*='Stop']",
            "button[aria-label*='停止']"
        )
    )

    fun get(id: String): AiPlatformDefinition? = supported.firstOrNull { it.id == id }

    fun require(id: String): AiPlatformDefinition = get(id)
        ?: throw IllegalArgumentException("Unsupported AI platform: $id")

    fun detect(url: String?): AiPlatformDefinition {
        if (url.isNullOrBlank()) return generic
        val normalised = url.lowercase()
        return supported.firstOrNull { platform ->
            platform.hosts.any { host -> normalised.contains(host) }
        } ?: generic
    }

    fun generic(): AiPlatformDefinition = generic
}
