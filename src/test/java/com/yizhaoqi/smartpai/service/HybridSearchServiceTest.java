package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HybridSearchServiceTest {

    private final HybridSearchService service = new HybridSearchService();

    @Test
    void expandsReservoirLocationQueryForRecall() {
        String expanded = service.expandQueryForRecall("水库位置");

        assertThat(expanded)
                .contains("水库位置")
                .contains("位于")
                .contains("所在")
                .contains("坝址")
                .contains("支流");
    }

    @Test
    void expandsWatershedQueryForRecall() {
        String expanded = service.expandQueryForRecall("水库流域情况");

        assertThat(expanded)
                .contains("水库流域情况")
                .contains("流域")
                .contains("支流")
                .contains("发源")
                .contains("注入")
                .contains("面积");
    }

    @Test
    void expandsPrincipleQueryForRecall() {
        String expanded = service.expandQueryForRecall("水库有哪些调度原则？");

        assertThat(expanded)
                .contains("水库有哪些调度原则？")
                .contains("调度原则")
                .contains("运行原则")
                .contains("控制原则")
                .contains("总控制原则")
                .contains("分期控制原则")
                .contains("防洪为主")
                .contains("兼顾发电")
                .doesNotContain("开闸")
                .doesNotContain("放水")
                .doesNotContain("泄洪")
                .doesNotContain("预泄")
                .doesNotContain("放空管")
                .doesNotContain("电站满发");
    }

    @Test
    void expandsWaterLevelQueryForRecall() {
        String expanded = service.expandQueryForRecall("汛限水位是多少？");

        assertThat(expanded)
                .contains("汛限水位是多少？")
                .contains("汛限水位")
                .contains("限制水位")
                .contains("梅汛期")
                .contains("台汛期")
                .contains("非汛期")
                .contains("正常蓄水位")
                .contains("死水位");
    }

    @Test
    void expandsOperationQueryForRecall() {
        String expanded = service.expandQueryForRecall("台汛期接到台风暴雨预报后如何调度？");

        assertThat(expanded)
                .contains("台汛期接到台风暴雨预报后如何调度？")
                .contains("开闸")
                .contains("放水")
                .contains("泄洪")
                .contains("预泄")
                .contains("放空管")
                .contains("电站满发");
    }

    @Test
    void expandsEntityDetailQueryForRecall() {
        String expanded = service.expandQueryForRecall("木瓜水库的巡查责任人是谁？联系方式？");

        assertThat(expanded)
                .contains("木瓜水库的巡查责任人是谁？联系方式？")
                .contains("防洪保护对象")
                .contains("行政村")
                .contains("巡查责任人")
                .contains("联系方式");
    }

    @Test
    void doesNotAddDomainExpansionWithoutTrigger() {
        String expanded = service.expandQueryForRecall("木瓜水库总库容是多少？");

        assertThat(expanded).isEqualTo("木瓜水库总库容是多少？");
    }
}
