package com.memorylane.memory;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 消息预过滤 — 仿 ChatLab isValidMessage 模式。
 *
 * <p>在 LLM 调用前过滤无效消息，减少 token 浪费和噪音记忆：
 * <ul>
 *   <li>空消息 / 纯空白</li>
 *   <li>过短无意义消息（≤2 字符）</li>
 *   <li>纯 emoji / 符号</li>
 *   <li>微信占位符（[图片]/[语音]/[视频]/[表情] 等）</li>
 *   <li>系统消息（邀请/退出/撤回等）</li>
 * </ul>
 *
 * <p>过滤策略偏保守：宁可漏过一条噪音，不能误杀真实消息。
 */
public final class MessageFilter {

    private MessageFilter() {}

    /** 微信/QQ 媒体占位符 — 导入管线生成的替代文本 */
    private static final Set<String> PLACEHOLDERS = Set.of(
            "[图片]", "[语音]", "[视频]", "[表情]", "[红包]", "[文件]",
            "[链接]", "[聊天记录]", "[小程序]", "[动画表情]", "[引用]",
            "[转账]", "[位置]", "[名片]", "[音乐]", "[笔记]",
            "[image]", "[voice]", "[video]", "[sticker]"
    );

    /** 有意义的短中文词 — 过短但不应被过滤的白名单 */
    private static final Set<String> MEANINGFUL_SHORT = Set.of(
            "好的", "不是", "是的", "可以", "不行", "不对", "对了",
            "还行", "没事", "好吧", "嗯嗯", "等你", "收到", "明白",
            "懂了", "谢谢", "再见", "晚安", "早安", "来了", "走了"
    );

    /** 纯 emoji/符号行 */
    private static final Pattern EMOJI_ONLY = Pattern.compile(
            "^[\\p{So}\\p{Cn}\\s\\[\\]【】\\-—…～~]+$"
    );

    /** 微信系统消息模式 */
    private static final Pattern SYSTEM_MSG = Pattern.compile(
            "^(你|我|您|对方|\\\".*\\\")" +
            "(已?)?(邀请|加入|退出|创建|修改|撤回|删除|移出|移除|踢出)" +
            ".*"
    );

    /**
     * 判断一条消息是否值得送入 LLM 处理。
     *
     * @param content 消息原文（含说话人前缀格式 "speaker:content" 时请先剥离前缀）
     * @return true 如果消息有效，应送入 LLM
     */
    public static boolean isValid(String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return false;

        // 纯占位符
        if (PLACEHOLDERS.contains(trimmed)) return false;

        // 过短
        if (trimmed.length() <= 2 && !MEANINGFUL_SHORT.contains(trimmed)) {
            return false;
        }

        // 纯 emoji/符号
        if (EMOJI_ONLY.matcher(trimmed).matches()) return false;

        // 系统消息
        if (SYSTEM_MSG.matcher(trimmed).matches()) return false;

        return true;
    }

    /**
     * 从 "id:speaker:content" 格式中提取纯内容用于过滤。
     * 输入如 "42:用户名:好的好的" → 提取 "好的好的" 做校验。
     */
    public static boolean isValidLabeled(String labeled) {
        int secondColon = labeled.indexOf(':', labeled.indexOf(':') + 1);
        if (secondColon < 0) return isValid(labeled);
        return isValid(labeled.substring(secondColon + 1));
    }
}
