package com.example.foodhistory.bddui;

import io.cucumber.junit.platform.engine.Constants;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;
import org.springframework.test.context.ActiveProfiles;

/**
 * 單獨執行 UI 表單互動測試的測試運行器
 * 僅執行第一個場景：新增食物表單必填欄位驗證
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ActiveProfiles("test")
@ConfigurationParameter(
    key = Constants.FILTER_TAGS_PROPERTY_NAME,
    value = "@ui"
)
@ConfigurationParameter(
    key = Constants.FEATURES_PROPERTY_NAME,
    value = "classpath:features/ui_form_interactions.feature"
)
@ConfigurationParameter(
    key = Constants.GLUE_PROPERTY_NAME,
    value = "com.example.foodhistory.bddui"
)
@ConfigurationParameter(
    key = Constants.PLUGIN_PROPERTY_NAME,
    value = "pretty, html:target/cucumber-reports/single-ui-test.html, json:target/cucumber-reports/single-ui-test.json"
)
public class RunSingleUITest {
}
