package com.example.foodhistory.bddui;

import org.junit.platform.suite.api.*;

import static io.cucumber.junit.platform.engine.Constants.*;

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-reports/ui-cucumber.html, json:target/cucumber-reports/ui-cucumber.json")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.foodhistory.bddui")
@ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@ui and not @skip")
@ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "classpath:features")
public class RunUIBDDTest {
}
