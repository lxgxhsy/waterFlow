package com.yizhaoqi.smartpai.service.impl;

import com.yizhaoqi.smartpai.dto.IntentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentRecognitionServiceImplTest {

    private final IntentRecognitionServiceImpl service = new IntentRecognitionServiceImpl();

    @Test
    void reservoirIntroductionUsesRagSearch() {
        IntentResult result = service.recognize("介绍一下木瓜水库");

        assertThat(result.getIntentType()).isEqualTo(IntentResult.IntentType.RAG_ONLY);
        assertThat(result.needRag()).isTrue();
        assertThat(result.needKg()).isFalse();
        assertThat(result.getSlots()).containsEntry("reservoir", "木瓜水库");
    }

    @Test
    void reservoirLocationUsesRagSearch() {
        IntentResult result = service.recognize("水库位置");

        assertThat(result.getIntentType()).isEqualTo(IntentResult.IntentType.RAG_ONLY);
        assertThat(result.needRag()).isTrue();
        assertThat(result.needKg()).isFalse();
    }

    @Test
    void reservoirRelationshipUsesRagAndKg() {
        IntentResult result = service.recognize("木瓜水库上下游关系");

        assertThat(result.getIntentType()).isEqualTo(IntentResult.IntentType.RAG_AND_KG);
        assertThat(result.needRag()).isTrue();
        assertThat(result.needKg()).isTrue();
        assertThat(result.getSlots()).containsEntry("reservoir", "木瓜水库");
    }

    @Test
    void relationshipWithoutEntityUsesKgOnly() {
        IntentResult result = service.recognize("上下游关系");

        assertThat(result.getIntentType()).isEqualTo(IntentResult.IntentType.KG_ONLY);
        assertThat(result.needRag()).isFalse();
        assertThat(result.needKg()).isTrue();
    }
}
