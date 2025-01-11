package com.hfwas.devops.controller;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.read.listener.PageReadListener;
import com.google.gson.Gson;
import com.hfwas.devops.tools.api.depency.JavaApi;
import com.hfwas.devops.tools.api.depency.NpmApi;
import com.hfwas.devops.tools.api.nexus.NexusUserApi;
import com.hfwas.devops.tools.api.vulnerability.AvdApi;
import com.hfwas.devops.tools.api.vulnerability.CweApi;
import com.hfwas.devops.tools.api.vulnerability.CweCsvApi;
import com.hfwas.devops.tools.api.vulnerability.GithubAdvisoriesApi;
import com.hfwas.devops.tools.entity.cwe.CweCsv;
import com.hfwas.devops.tools.entity.cwe.CweWeakness;
import com.hfwas.devops.tools.entity.github.GithubAdvisories;
import com.hfwas.devops.tools.entity.nexus.login.NexusSession;
import com.hfwas.devops.tools.enums.CweTypeEnums;
import feign.Response;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author houfei
 * @package com.hfwas.devops.controller
 * @date 2025/1/6
 */
@RestController
@RequestMapping("/demo")
@Slf4j
public class DemoController {

    @Autowired
    private NexusUserApi nexusUserApi;
    @Autowired
    AvdApi avdApi;
    @Autowired
    GithubAdvisoriesApi githubAdvisoriesApi;
    @Autowired
    JavaApi javaApi;
    @Autowired
    NpmApi npmApi;
    @Autowired
    CweApi cweApi;
    @Autowired
    CweCsvApi cweCsvApi;
    @Autowired
    ResourceLoader resourceLoader;

    @GetMapping("/advisories")
    public long advisories() throws IOException, InterruptedException {
        long timeMillis = System.currentTimeMillis();
        log.info("start time:{}", timeMillis);
        Stream<Path> walk = Files.walk(Paths.get("/Users/houfei/github/ghsa"));
        List<Path> collect = walk.parallel().filter(Files::isRegularFile).collect(Collectors.toList());
        Gson gson = new Gson();
        ArrayList<GithubAdvisories> githubAdvisories1 = new ArrayList<>();
        collect.stream().parallel().forEach(path -> {
            byte[] bytes = null;
            try {
                bytes = Files.readAllBytes(path);
                GithubAdvisories githubAdvisories = gson.fromJson(new String(bytes), GithubAdvisories.class);
                githubAdvisories1.add(githubAdvisories);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        Map<String, GithubAdvisories> elementJsonObjectMap = githubAdvisories1.stream().parallel().collect(Collectors.toMap(jsonObject -> jsonObject.getId(), Function.identity()));
        log.info("end time:{}", (System.currentTimeMillis() - timeMillis));
        return (System.currentTimeMillis() - timeMillis);
    }

    @GetMapping("/ali")
    public void ali() throws IOException {
        long epochMilli = Instant.now().toEpochMilli();
        long epochSecond = Instant.now().getEpochSecond();

        String decode1 = URLDecoder.decode("n4%2BxnDgDuDcDy7CYqGNDQW%2BTxj2D0hqx2h7QTD");
        Response response = avdApi.avd();
        Response.Body body = response.body();
        byte[] bytes = body.asInputStream().readAllBytes();
        Document parse = Jsoup.parse(new String(bytes, Charset.forName("utf-8")));
        Elements a = parse.select("tbody>tr");
        for (Element element : a) {
            Elements elements = element.select("tr>td");
            String avdNumber = elements.get(0).select("a").get(0).select("a").html();
            String name = elements.get(1).select("td").html();
            String cweId = elements.get(2).select("button").html();
            String time = elements.get(3).select("td").html();
            String cveNumber = elements.get(4).select("td>button").get(0).attr("title");
            String code = elements.get(4).select("td>button").get(1).attr("title");
            log.info("avdNumber,name,cweId,time,cveNumber,code: {},{},{},{},{},{}", avdNumber, name, cweId, time, cveNumber, code);
        }
    }

    @GetMapping("/login")
    public void login() {
        NexusSession nexusSession = new NexusSession();
        String username = Base64.getEncoder().encodeToString(new String("admin").getBytes(Charset.defaultCharset()));
        String password = Base64.getEncoder().encodeToString(new String("Ab123456").getBytes(Charset.defaultCharset()));
        nexusSession.setUsername(username);
        nexusSession.setPassword(password);
        Response login = nexusUserApi.login(nexusSession);
        if (login.status() == HttpStatus.NO_CONTENT.value()) {
            log.info("login: {}", login.toString());
        }
        log.info("login: {}", login.toString());
    }

    @GetMapping("/java")
    public void java() throws IOException {
        Response response = javaApi.maven2(null);
        byte[] bytes = response.body().asInputStream().readAllBytes();
        Document document = Jsoup.parse(new String(bytes));
        Elements elements = document.select("a");
        for (Element element : elements) {
            Attribute href = element.attribute("href");
            String value = href.getValue();
            if (!value.equals("../")) {
                log.info("href:{}", value);
            }
        }
        log.info(response.toString());
    }

    @GetMapping("/depen")
    public void depen() throws IOException {
        Set<String> set = new HashSet<>();
        Response response = npmApi.dependents("@sindresorhus");
        byte[] bytes = response.body().asInputStream().readAllBytes();
        Document document = Jsoup.parse(new String(bytes));
        Elements tds = document.select("li");
        for (Element td : tds) {
            String attr = td.select("a").attr("href");
            if (attr.startsWith("/package")) {
                attr = attr.substring(9);
                String decode = URLDecoder.decode(attr, "UTF-8");
                set.add(decode);
            }
            log.info(attr);
        }
        System.out.println(set);
    }

    @GetMapping("/download")
    public void download() throws IOException {
        Response response = cweCsvApi.downloadCsvZip(CweTypeEnums.soft.getCsv());
        InputStream inputStream = response.body().asInputStream();
        // 方法一：使用Files.copy()方法（推荐）
        ResourceLoader resourcePatternResolver = new PathMatchingResourcePatternResolver();
        URI uri = resourcePatternResolver.getResource("classpath:cwe").getURI();
        Path path = Paths.get(uri);
        Files.copy(inputStream, Paths.get("/Users/houfei/anpm/op.zip"), StandardCopyOption.REPLACE_EXISTING);
        log.info(response.toString());
    }

    @GetMapping("/test")
    public void test() throws IOException {
        CweWeakness weakness = cweApi.weakness("78");
        log.info(weakness.toString());


        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream("cwe/699.csv");
        EasyExcel.read(resourceAsStream, CweCsv.class, new PageReadListener<CweCsv>(dataList -> {
            for (CweCsv demoData : dataList) {
                log.info("读取到一条数据{}", demoData);
            }
        })).sheet().doRead();


        Response response = avdApi.avdList(1, "");
        byte[] bytes = response.body().asInputStream().readAllBytes();
        Document document = Jsoup.parse(new String(bytes));
        Elements tds = document.select("tbody>tr");
        for(int j=0;j<tds.size();j++) {
            Elements e = tds.get(j).select("td");
            String avdNumber = e.get(0).select("a").attr("href");
            String avdCode = avdNumber.substring(avdNumber.indexOf("=") + 1);
            log.info("avdCode:{}",avdCode);
        }
        System.out.println(response);
    }

    @GetMapping("/list")
    public Set<String> list() throws IOException {
        HashSet<String> set = new HashSet<>();
        Yaml yaml = new Yaml();
        for (int i = 1; i <=3; i++) {
//            InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream("classpath:pnpm/pnpm-lock"+i+".yaml");
            InputStream resourceAsStream = resourceLoader.getResource("classpath:pnpm/pnpm-lock"+i+".yaml").getInputStream();
            Map<String, Map<String, HashMap>> load = yaml.load(resourceAsStream);
            if (load.containsKey("importers")) {
                Map<String, HashMap> importers = load.get("importers");
                Map<String, HashMap> stringObjectMap = importers.get(".");
                if (stringObjectMap.containsKey("dependencies")) {
                    Set<String> dependencies = stringObjectMap.get("dependencies").keySet();
                    set.addAll(dependencies);
                }
                if (stringObjectMap.containsKey("devDependencies")) {
                    Set<String> devDependencies = stringObjectMap.get("devDependencies").keySet();
                    set.addAll(devDependencies);
                }
            }
            if (load.containsKey("dependencies")) {
                Set<String> dependencies = load.get("dependencies").keySet();
                set.addAll(dependencies);
            }
            if (load.containsKey("devDependencies")) {
                Set<String> dependencies = load.get("devDependencies").keySet();
                set.addAll(dependencies);
            }

            Map<String, HashMap> packages = load.get("packages");
            Set<String> strings = packages.keySet();
            set.addAll(strings);

            if (load.containsKey("snapshots")) {
                Map<String, HashMap> snapshots = load.get("snapshots");
                Set<String> strings1 = snapshots.keySet();
                set.addAll(strings1);
            }
        }
        List<String> values = set.stream().map(key -> {
                    String str = key;
                    if (str.startsWith("/")) {
                        str = str.substring(1);
                    }
                    if (str.startsWith("registry.npmmirror.com")) {
                        str = str.substring(23);
                    }
                    if (str.contains("(")) {
                        int i = str.indexOf("(");
                        str = str.substring(0, i);
                    }
                    int indexOf = str.lastIndexOf("@");
                    if (indexOf != -1) {
                        return str.substring(0,indexOf);
                    } else {
                        return str;
                    }
                }).filter(ke -> !"".equals(ke))
                .collect(Collectors.toList());
        Set<String> dependencies = new HashSet<>();
        for (String value : values) {
            Response response = npmApi.dependents(value);
            byte[] bytes = response.body().asInputStream().readAllBytes();
            Document document = Jsoup.parse(new String(bytes));
            Elements tds = document.select("li");
            for (Element td : tds) {
                String attr = td.select("a").attr("href");
                if (attr.startsWith("/package")) {
                    attr = attr.substring(9);
                    String decode = URLDecoder.decode(attr, "UTF-8");
                    dependencies.add(decode);
                    log.info("============" + decode);
                }
            }
            dependencies.add(value);
        }
        return dependencies;
    }


}
