package com.diybracelet.mall.recommendation.application;

import java.util.List;
import java.util.regex.Pattern;


/** 过滤确定性运势和健康效果承诺，约束推荐文案。 */
final class RecommendationCopySafetyPolicy {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE
        | Pattern.UNICODE_CASE
        | Pattern.DOTALL;
    private static final String NEAR = ".{0,12}";
    private static final String CERTAINTY =
        "(?:保证|确保|承诺|必然|必定|肯定|绝对|注定|"
            + "百分(?:之)?百|100\\s*%|一定(?:会|能|可以)?)";
    private static final String FORTUNE_ACTION =
        "(?:带来|获得|实现|助(?:你|您)?|让(?:你|您)?|使(?:你|您)?|"
            + "促成|迎来|提升|增强|增加)";
    private static final String FORTUNE_RESULT =
        "(?:好运|财运|财富|发财|发大财|赚大钱|财源滚滚|转运|姻缘|"
            + "桃花运|事业运|成功)";
    private static final String WEARING_TRIGGER =
        "(?:佩戴|戴上|带上|使用|拥有)";
    private static final String OUTCOME_MODAL =
        "(?:会|能|可(?:以)?|能够|就能|即可|将)";
    private static final String MEDICAL_ACTION =
        "(?:治疗|治愈|治好|医治|医好|预防|防治|缓解|减轻|改善|"
            + "控制|降低|降|消除|对抗)";
    private static final String HEALTH_CONDITION =
        "(?:疾病|病症|症状|疼痛|(?:偏)?头(?:痛|疼)|失眠|焦虑|抑郁|"
            + "血压|血糖|血脂|癌症|肿瘤|炎症)";
    private static final String PROHIBITED_RESULT =
        "(?:" + FORTUNE_RESULT + "|" + MEDICAL_ACTION + "|"
            + HEALTH_CONDITION + ")";

    private static final List<Rule> RULES = List.of(
        rule(
            Category.CERTAIN_OUTCOME,
            CERTAINTY + NEAR + PROHIBITED_RESULT
        ),
        rule(
            Category.FORTUNE_OUTCOME,
            FORTUNE_ACTION + NEAR + FORTUNE_RESULT
        ),
        rule(
            Category.FORTUNE_OUTCOME,
            WEARING_TRIGGER + NEAR + OUTCOME_MODAL + NEAR + FORTUNE_RESULT
        ),
        rule(
            Category.FORTUNE_OUTCOME,
            "(?:好运|财运|财富|转运|姻缘|桃花运|事业运).{0,12}"
                + "(?:到来|降临|成真|实现|提升|增强|增加)"
        ),
        rule(
            Category.MEDICAL_CLAIM,
            MEDICAL_ACTION + NEAR + HEALTH_CONDITION
        ),
        rule(
            Category.MEDICAL_CLAIM,
            HEALTH_CONDITION + NEAR
                + "(?:得到|获得|可以|能够|有助于)?" + MEDICAL_ACTION
        ),
        rule(
            Category.MEDICAL_CLAIM,
            "(?:疗效|药效|抗癌|防癌|降血压|降血糖|降血脂)"
        ),
        rule(
            Category.FINANCIAL_CLAIM,
            "(?:稳赚|包赚|稳赚不赔|必发财)"
        )
    );

    boolean isAllowed(String text) {
        return text != null && RULES.stream()
            .noneMatch(rule -> rule.pattern().matcher(text).find());
    }

    private static Rule rule(Category category, String expression) {
        return new Rule(category, Pattern.compile(expression, FLAGS));
    }

    private enum Category {
        CERTAIN_OUTCOME,
        FORTUNE_OUTCOME,
        MEDICAL_CLAIM,
        FINANCIAL_CLAIM
    }

    private record Rule(Category category, Pattern pattern) {
    }
}
