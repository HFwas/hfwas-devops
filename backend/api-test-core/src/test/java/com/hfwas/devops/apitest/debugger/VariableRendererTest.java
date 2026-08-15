package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.variable.VariableRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * VariableRenderer — 变量渲染引擎单元测试
 * <p>
 * 覆盖：
 * - 基础变量渲染
 * - 默认值语法（{{varName:defaultValue}}）
 * - 临时变量优先级
 * - renderMap 方法
 * - 特殊场景（多点号、特殊字符、空格占位符）
 *
 * @author hfwas
 */
@DisplayName("VariableRenderer — 变量渲染引擎")
@ExtendWith(MockitoExtension.class)
class VariableRendererTest {

    @InjectMocks
    private VariableRenderer variableRenderer;

    @Nested
    @DisplayName("render — 基础变量渲染")
    class RenderBasic {

        @Test
        @DisplayName("标准变量替换")
        void renderNormalVariable() {
            Map<String, String> vars = new HashMap<>();
            vars.put("base_url", "http://localhost:8080");
            String result = variableRenderer.render("{{base_url}}/api/users", vars);
            assertThat(result).isEqualTo("http://localhost:8080/api/users");
        }

        @Test
        @DisplayName("变量不存在时保留原占位符")
        void undefinedVariableKeepsOriginal() {
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{base_url}}/api/users", vars);
            assertThat(result).isEqualTo("{{base_url}}/api/users");
        }

        @Test
        @DisplayName("无变量占位符时返回原字符串")
        void noVariablesReturnsOriginal() {
            Map<String, String> vars = new HashMap<>();
            vars.put("base_url", "http://localhost");
            String result = variableRenderer.render("/api/users", vars);
            assertThat(result).isEqualTo("/api/users");
        }

        @Test
        @DisplayName("null 模板返回 null")
        void nullTemplateReturnsNull() {
            assertThat(variableRenderer.render(null, new HashMap<>())).isNull();
        }

        @Test
        @DisplayName("null 变量时保留原占位符")
        void nullVariablesKeepsOriginal() {
            String result = variableRenderer.render("{{base_url}}/api/users", null);
            assertThat(result).isEqualTo("{{base_url}}/api/users");
        }

        @Test
        @DisplayName("空字符串模板返回空字符串")
        void emptyTemplateReturnsEmpty() {
            Map<String, String> vars = new HashMap<>();
            vars.put("key", "value");
            String result = variableRenderer.render("", vars);
            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("模板全为变量占位符")
        void fullTemplateIsVariable() {
            Map<String, String> vars = new HashMap<>();
            vars.put("key", "value");
            String result = variableRenderer.render("{{key}}", vars);
            assertThat(result).isEqualTo("value");
        }

        @Test
        @DisplayName("占位符前后有空格时仍能匹配")
        void variableWithSpaces() {
            Map<String, String> vars = new HashMap<>();
            vars.put("key", "value");
            String result = variableRenderer.render("{{ key }}", vars);
            assertThat(result).isEqualTo("value");
        }
    }

    @Nested
    @DisplayName("render — 多个变量")
    class RenderMultipleVariables {

        @Test
        @DisplayName("模板中包含多个变量占位符")
        void multipleVariables() {
            Map<String, String> vars = new HashMap<>();
            vars.put("scheme", "https");
            vars.put("host", "api.example.com");
            vars.put("path", "/v1/users");
            String result = variableRenderer.render("{{scheme}}://{{host}}{{path}}", vars);
            assertThat(result).isEqualTo("https://api.example.com/v1/users");
        }

        @Test
        @DisplayName("混合存在和缺失的变量")
        void mixedExistingAndMissing() {
            Map<String, String> vars = new HashMap<>();
            vars.put("host", "api.example.com");
            String result = variableRenderer.render("{{scheme}}://{{host}}/api", vars);
            assertThat(result).isEqualTo("{{scheme}}://api.example.com/api");
        }

        @Test
        @DisplayName("连续的两个变量占位符")
        void consecutiveVariables() {
            Map<String, String> vars = new HashMap<>();
            vars.put("a", "hello");
            vars.put("b", "world");
            String result = variableRenderer.render("{{a}}{{b}}", vars);
            assertThat(result).isEqualTo("helloworld");
        }
    }

    @Nested
    @DisplayName("render — 默认值语法")
    class RenderDefaultValue {

        @Test
        @DisplayName("变量存在时使用变量值，不使用默认值")
        void variableExistsIgnoreDefault() {
            Map<String, String> vars = new HashMap<>();
            vars.put("page", "1");
            String result = variableRenderer.render("{{page:default}}", vars);
            assertThat(result).isEqualTo("1");
        }

        @Test
        @DisplayName("变量不存在时使用默认值")
        void variableMissingUseDefault() {
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{page:20}}", vars);
            assertThat(result).isEqualTo("20");
        }

        @Test
        @DisplayName("默认值中包含特殊字符")
        void defaultValueWithSpecialChars() {
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{token:default-token-123}}", vars);
            assertThat(result).isEqualTo("default-token-123");
        }

        @Test
        @DisplayName("默认值为空字符串")
        void defaultValueIsEmpty() {
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{key:}}", vars);
            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("默认值中包含冒号")
        void defaultValueWithColon() {
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{url:http://localhost:8080}}", vars);
            assertThat(result).isEqualTo("http://localhost:8080");
        }

        @Test
        @DisplayName("默认值中包含 {{}} 文本")
        void defaultValueWithBraces() {
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{format:json}}", vars);
            assertThat(result).isEqualTo("json");
        }
    }

    @Nested
    @DisplayName("render — 临时变量")
    class RenderTemporaryVariables {

        @Test
        @DisplayName("设置临时变量后渲染成功")
        void setTemporaryVariable() {
            variableRenderer.setTemporaryVariable("token", "tmp-123");
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{token}}", vars);
            assertThat(result).isEqualTo("tmp-123");
            variableRenderer.clearTemporaryVariables();
        }

        @Test
        @DisplayName("临时变量优先级高于环境变量")
        void temporaryVariableOverridesEnv() {
            variableRenderer.setTemporaryVariable("key", "tmp-value");
            Map<String, String> vars = new HashMap<>();
            vars.put("key", "env-value");
            String result = variableRenderer.render("{{key}}", vars);
            assertThat(result).isEqualTo("tmp-value");
            variableRenderer.clearTemporaryVariables();
        }

        @Test
        @DisplayName("清空临时变量后不再生效")
        void clearTemporaryVariables() {
            variableRenderer.setTemporaryVariable("token", "tmp-123");
            variableRenderer.clearTemporaryVariables();
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{token}}", vars);
            assertThat(result).isEqualTo("{{token}}");
        }

        @Test
        @DisplayName("批量设置临时变量")
        void setTemporaryVariables() {
            Map<String, String> tmpVars = new HashMap<>();
            tmpVars.put("a", "1");
            tmpVars.put("b", "2");
            variableRenderer.setTemporaryVariables(tmpVars);
            Map<String, String> vars = new HashMap<>();
            String result = variableRenderer.render("{{a}}-{{b}}", vars);
            assertThat(result).isEqualTo("1-2");
            variableRenderer.clearTemporaryVariables();
        }
    }

    @Nested
    @DisplayName("render — 特殊场景")
    class RenderSpecialCases {

        @Test
        @DisplayName("变量值包含空格")
        void variableValueWithSpaces() {
            Map<String, String> vars = new HashMap<>();
            vars.put("name", "hello world");
            String result = variableRenderer.render("{{name}}", vars);
            assertThat(result).isEqualTo("hello world");
        }

        @Test
        @DisplayName("变量值包含 JSON")
        void variableValueWithJson() {
            Map<String, String> vars = new HashMap<>();
            vars.put("json", "{\"key\":\"value\"}");
            String result = variableRenderer.render("{{json}}", vars);
            assertThat(result).isEqualTo("{\"key\":\"value\"}");
        }

        @Test
        @DisplayName("变量名带点号")
        void variableNameWithDot() {
            Map<String, String> vars = new HashMap<>();
            vars.put("user.name", "张三");
            String result = variableRenderer.render("{{user.name}}", vars);
            assertThat(result).isEqualTo("张三");
        }

        @Test
        @DisplayName("变量值包含 URL 编码")
        void variableValueWithUrlEncoding() {
            Map<String, String> vars = new HashMap<>();
            vars.put("q", "hello%20world");
            String result = variableRenderer.render("{{q}}", vars);
            assertThat(result).isEqualTo("hello%20world");
        }
    }

    @Nested
    @DisplayName("renderMap — Map 批量渲染")
    class RenderMap {

        @Test
        @DisplayName("正常渲染 Map 中的所有值")
        void renderMapNormal() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("url", "{{base_url}}/api/users");
            map.put("token", "Bearer {{token}}");
            map.put("static", "hello");

            Map<String, String> vars = new HashMap<>();
            vars.put("base_url", "http://localhost");
            vars.put("token", "abc123");

            Map<String, String> result = variableRenderer.renderMap(map, vars);
            assertThat(result)
                    .containsEntry("url", "http://localhost/api/users")
                    .containsEntry("token", "Bearer abc123")
                    .containsEntry("static", "hello");
        }

        @Test
        @DisplayName("null Map 返回 null")
        void nullMapReturnsNull() {
            assertThat(variableRenderer.renderMap(null, new HashMap<>())).isNull();
        }

        @Test
        @DisplayName("Map 中值为 null 时返回 null")
        void mapWithNullValue() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("key", null);
            Map<String, String> vars = new HashMap<>();
            vars.put("key", "value");
            Map<String, String> result = variableRenderer.renderMap(map, vars);
            assertThat(result).containsKey("key");
            assertThat(result.get("key")).isNull();
        }

        @Test
        @DisplayName("空 Map 返回空 Map")
        void emptyMap() {
            Map<String, String> map = new LinkedHashMap<>();
            Map<String, String> result = variableRenderer.renderMap(map, new HashMap<>());
            assertThat(result).isEmpty();
        }
    }
}