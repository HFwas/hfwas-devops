package com.hfwas.devops.apitest.curl;

import com.hfwas.devops.apitest.curl.dto.CurlParseResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * cURL 命令解析器
 * <p>
 * 将 cURL 命令字符串解析为结构化的请求参数。
 * 支持常见的 cURL 命令行选项，兼容 Postman 导出的 cURL 格式。
 *
 * @author hfwas
 */
@Slf4j
@Component
public class CurlParserService {

    // 匹配单引号/双引号包裹的字符串或裸字符串
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "'(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\"|\\S+"
    );

    // 匹配 URL（http/https 开头，或裸域名/路径）
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^(https?://|//?)[^\\s'\"]+"
    );

    // 匹配 -X / --request 选项后的方法
    private static final Pattern METHOD_PATTERN = Pattern.compile(
            "-X\\s+(\\S+)|--request\\s+(\\S+)", Pattern.CASE_INSENSITIVE
    );

    // 匹配 -H / --header 选项后的 header 值
    private static final Pattern HEADER_PATTERN = Pattern.compile(
            "-H\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\1|--header\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\3",
            Pattern.CASE_INSENSITIVE
    );

    // 匹配 -d / --data / --data-raw 选项后的 body
    private static final Pattern DATA_PATTERN = Pattern.compile(
            "-d\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\1|--data\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\3|--data-raw\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\5",
            Pattern.CASE_INSENSITIVE
    );

    // 匹配 --data-urlencode
    private static final Pattern DATA_URLENCODE_PATTERN = Pattern.compile(
            "--data-urlencode\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\1",
            Pattern.CASE_INSENSITIVE
    );

    // 匹配 -F / --form
    private static final Pattern FORM_PATTERN = Pattern.compile(
            "-F\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\1|--form\\s+(['\"])((?:[^\\\\]|\\\\.)*?)\\3",
            Pattern.CASE_INSENSITIVE
    );

    // 匹配 -u / --user
    private static final Pattern USER_PATTERN = Pattern.compile(
            "-u\\s+(\\S+)|--user\\s+(\\S+)", Pattern.CASE_INSENSITIVE
    );

    // 匹配 -L / --location
    private static final Pattern LOCATION_PATTERN = Pattern.compile(
            "-L|--location"
    );

    private static final Set<String> KNOWN_OPTIONS = new HashSet<>(Arrays.asList(
            "-X", "--request",
            "-H", "--header",
            "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode",
            "-F", "--form",
            "-u", "--user",
            "-L", "--location",
            "-s", "--silent", "-S", "--show-error",
            "-k", "--insecure",
            "-i", "--include",
            "-v", "--verbose",
            "-o", "--output",
            "-b", "--cookie",
            "-c", "--cookie-jar",
            "-e", "--referer",
            "-A", "--user-agent",
            "--compressed",
            "-0", "--http1.0",
            "--http1.1", "--http2",
            "-T", "--upload-file",
            "-O", "--remote-name",
            "--retry", "--connect-timeout", "--max-time",
            "--progress-bar", "--no-keepalive",
            "-n", "--netrc", "--netrc-file",
            "--proxy", "--noproxy",
            "--tlsv1", "--tlsv1.2", "--tlsv1.3",
            "--ciphers", "--cert", "--key", "--cacert",
            "--resolve",
            "--limit-rate",
            "--ipv4", "--ipv6",
            "--globoff", "-g",
            "--path-as-is",
            "--max-redirs"
    ));

    // 选项后跟参数值的选项
    private static final Set<String> OPTIONS_WITH_VALUE = new HashSet<>(Arrays.asList(
            "-X", "--request",
            "-H", "--header",
            "-d", "--data", "--data-raw", "--data-binary", "--data-urlencode",
            "-F", "--form",
            "-u", "--user",
            "-o", "--output",
            "-b", "--cookie",
            "-c", "--cookie-jar",
            "-e", "--referer",
            "-A", "--user-agent",
            "-T", "--upload-file",
            "-O", "--remote-name",
            "--retry", "--connect-timeout", "--max-time",
            "--proxy", "--noproxy",
            "--netrc-file",
            "--tlsv1", "--tlsv1.2", "--tlsv1.3",
            "--ciphers", "--cert", "--key", "--cacert",
            "--resolve",
            "--limit-rate",
            "--max-redirs"
    ));

    /**
     * 解析 cURL 命令
     *
     * @param curlCommand 原始 cURL 命令字符串
     * @return 解析结果
     */
    public CurlParseResultVO parse(String curlCommand) {
        if (curlCommand == null || curlCommand.isBlank()) {
            return CurlParseResultVO.builder()
                    .method("GET")
                    .headers(new LinkedHashMap<>())
                    .warnings(List.of("cURL 命令为空"))
                    .build();
        }

        List<String> warnings = new ArrayList<>();
        String normalized = curlCommand.trim();

        // 去除开头的 "curl" 命令前缀
        if (normalized.toLowerCase().startsWith("curl ")) {
            normalized = normalized.substring(5).trim();
        } else if (normalized.startsWith("'") && normalized.toLowerCase().startsWith("'curl ")) {
            normalized = normalized.substring(6).trim();
            if (normalized.endsWith("'")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }

        // 分词处理
        List<String> tokens = tokenize(normalized);

        // 解析各选项
        String url = null;
        String method = null;
        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder bodyBuilder = new StringBuilder();
        boolean hasData = false;
        boolean hasForm = false;
        boolean followRedirects = false;

        int i = 0;
        while (i < tokens.size()) {
            String token = tokens.get(i);

            if (isOption(token)) {
                String option = token;

                if (option.equals("-X") || option.equals("--request")) {
                    method = consumeValue(tokens, ++i, warnings);
                    if (method != null) method = method.toUpperCase();
                } else if (option.equals("-H") || option.equals("--header")) {
                    String headerValue = consumeValue(tokens, ++i, warnings);
                    parseHeader(headerValue, headers, warnings);
                } else if (option.equals("-d") || option.equals("--data") || option.equals("--data-raw") || option.equals("--data-binary")) {
                    String data = consumeValue(tokens, ++i, warnings);
                    appendBody(bodyBuilder, data);
                    hasData = true;
                } else if (option.equals("--data-urlencode")) {
                    String data = consumeValue(tokens, ++i, warnings);
                    // URL encode the data
                    if (data != null) {
                        String encoded = urlEncodeData(data);
                        appendBody(bodyBuilder, encoded);
                        hasData = true;
                    }
                } else if (option.equals("-F") || option.equals("--form")) {
                    String formData = consumeValue(tokens, ++i, warnings);
                    appendBody(bodyBuilder, formData);
                    hasForm = true;
                } else if (option.equals("-u") || option.equals("--user")) {
                    String userPass = consumeValue(tokens, ++i, warnings);
                    if (userPass != null) {
                        String encoded = Base64.getEncoder().encodeToString(userPass.getBytes());
                        headers.putIfAbsent("Authorization", "Basic " + encoded);
                    }
                } else if (option.equals("-L") || option.equals("--location")) {
                    followRedirects = true;
                } else if (OPTIONS_WITH_VALUE.contains(option) ||
                        (option.startsWith("--") && option.contains("="))) {
                    // 跳过其他带参数值的选项
                    if (option.contains("=")) {
                        // --option=value 形式
                    } else {
                        i++; // 跳过参数值
                    }
                }
                // 其他无参数选项（-s, -k, -v 等）直接跳过
            } else {
                // 非选项 token，可能是 URL
                if (url == null && isUrl(token)) {
                    url = stripQuotes(token);
                }
            }
            i++;
        }

        // 方法推断
        if (method == null) {
            if (hasData || hasForm) {
                method = "POST";
            } else {
                method = "GET";
            }
        }

        // Content-Type 推断
        String contentType = headers.get("Content-Type");
        if (contentType == null && hasData && !hasForm) {
            contentType = "application/x-www-form-urlencoded";
            headers.put("Content-Type", contentType);
        } else if (contentType == null && hasForm) {
            contentType = "multipart/form-data";
            headers.put("Content-Type", contentType);
        }

        String body = bodyBuilder.length() > 0 ? bodyBuilder.toString() : null;

        // 构建结果
        CurlParseResultVO result = CurlParseResultVO.builder()
                .url(url)
                .method(method)
                .headers(headers)
                .body(body)
                .contentType(contentType)
                .followRedirects(followRedirects)
                .timeoutMs(30000L)
                .warnings(warnings)
                .build();

        log.debug("cURL 解析完成: url={}, method={}, headers={}, bodyLength={}",
                url, method, headers.size(), body != null ? body.length() : 0);

        return result;
    }

    /**
     * 将 cURL 命令字符串分词
     */
    private List<String> tokenize(String command) {
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(command);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    /**
     * 判断 token 是否为 URL
     */
    private boolean isUrl(String token) {
        String clean = stripQuotes(token);
        return URL_PATTERN.matcher(clean).find();
    }

    /**
     * 判断 token 是否为已知选项
     */
    private boolean isOption(String token) {
        String clean = stripQuotes(token);
        if (clean.startsWith("-")) {
            return true;
        }
        return false;
    }

    /**
     * 获取选项的参数值
     */
    private String consumeValue(List<String> tokens, int index, List<String> warnings) {
        if (index >= tokens.size()) {
            return null;
        }
        String value = tokens.get(index);
        // 如果值是另一个选项，则返回 null
        String clean = stripQuotes(value);
        if (clean.startsWith("-") && KNOWN_OPTIONS.contains(clean)) {
            warnings.add("选项 " + tokens.get(index - 1) + " 缺少参数值");
            return null;
        }
        return stripQuotes(value);
    }

    /**
     * 解析 Header 字符串
     */
    private void parseHeader(String headerValue, Map<String, String> headers, List<String> warnings) {
        if (headerValue == null) return;

        int colonIndex = headerValue.indexOf(':');
        if (colonIndex > 0) {
            String name = headerValue.substring(0, colonIndex).trim();
            String value = headerValue.substring(colonIndex + 1).trim();
            if (!name.isEmpty()) {
                // 若 header 已存在，追加逗号分隔
                if (headers.containsKey(name)) {
                    headers.put(name, headers.get(name) + ", " + value);
                } else {
                    headers.put(name, value);
                }
            } else {
                warnings.add("无效的 Header 格式: " + headerValue);
            }
        } else {
            warnings.add("无效的 Header 格式: " + headerValue);
        }
    }

    /**
     * 追加请求体
     */
    private void appendBody(StringBuilder builder, String data) {
        if (data == null) return;
        if (builder.length() > 0) {
            builder.append("&");
        }
        builder.append(data);
    }

    /**
     * 处理 --data-urlencode 的数据
     */
    private String urlEncodeData(String data) {
        if (data == null) return null;
        // 检查是否包含 @file 语法
        if (data.startsWith("@")) {
            return data; // 文件引用，保留原样
        }
        // 检查是否包含 = 号（键值对）
        int eqIndex = data.indexOf('=');
        if (eqIndex > 0) {
            String key = data.substring(0, eqIndex);
            String value = data.substring(eqIndex + 1);
            try {
                return key + "=" + java.net.URLEncoder.encode(value, "UTF-8");
            } catch (Exception e) {
                return data;
            }
        }
        return data;
    }

    /**
     * 去除引号
     */
    private String stripQuotes(String s) {
        if (s == null) return null;
        if (s.length() >= 2) {
            char first = s.charAt(0);
            char last = s.charAt(s.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '"' && last == '"')) {
                return s.substring(1, s.length() - 1);
            }
        }
        return s;
    }
}