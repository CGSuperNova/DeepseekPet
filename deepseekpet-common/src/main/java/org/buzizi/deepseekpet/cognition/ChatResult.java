package org.buzizi.deepseekpet.cognition;

import java.util.List;

/**
 * 一次 AI 回复的结果。
 *
 * @param answer            鲸鱼娘的回复文本
 * @param fromKnowledgeBase 是否基于本地知识库
 * @param sources           引用来源（可能为空）
 * @param mode              {@code "rag"} 或 {@code "free"}
 */
public record ChatResult(String answer, boolean fromKnowledgeBase, List<String> sources, String mode) {
}
