package com.hfwas.devops.service.vul.java;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
import com.hfwas.devops.service.vul.DepenScanFactory;
import com.hfwas.devops.tools.api.depency.JavaApi;
import feign.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author hfwas
 * @package com.hfwas.devops.service.vul.java
 * @date 2025/1/13
 */
@Service("DevopsMavenDepenScan")
@Slf4j
public class DevopsMavenDepenScan extends AbstractDepenScan implements InitializingBean {

    @Resource
    private JavaApi javaApi;

    @Override
    public String language() {
        return "Java";
    }

    @Override
    public String type() {
        return "maven";
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        DepenScanFactory.register("Maven", this);
    }

    @Override
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        List<DevopsVulCodeDependency> devopsVulDependencys = Lists.newArrayList();
        if (originalFilename.equals("aggregate-depgraph.json")) {
            byte[] bytes = multipartFile.getBytes();
            Gson gson = new Gson();
            JsonObject jsonObject = gson.fromJson(new String(bytes, Charset.defaultCharset()), JsonObject.class);
            JsonArray artifacts = jsonObject.getAsJsonArray("artifacts");
            for (JsonElement artifact : artifacts) {
                JsonObject asJsonObject = artifact.getAsJsonObject();
                String groupId = asJsonObject.get("groupId").getAsString();
                String artifactId = asJsonObject.get("artifactId").getAsString();
                String version = asJsonObject.get("version").getAsString();
                int numericId = asJsonObject.get("numericId").getAsInt();
                DevopsVulCodeDependency devopsVulDependency = DevopsVulCodeDependency.builder()
                        .company(groupId)
                        .dependencyName(artifactId)
                        .version(version)
                        .type(1).build();
                devopsVulDependencys.add(devopsVulDependency);
            }

            JsonArray dependencies = jsonObject.getAsJsonArray("dependencies");
            for (JsonElement dependency : dependencies) {
                JsonObject asJsonObject = dependency.getAsJsonObject();
                String from = asJsonObject.get("from").getAsString();
                String to = asJsonObject.get("to").getAsString();
                int numericFrom = asJsonObject.get("numericFrom").getAsInt();
                int numericTo = asJsonObject.get("numericTo").getAsInt();
            }
        }
        return devopsVulDependencys;
    }

    @Override
    public List<String> depenVersion(String depen, String version) {
        List<String> depenVersions = Lists.newArrayList();
        String[] split = depen.split(":");
        String replace1 = split[0].replace(".", "/");
        String format = String.format("%s/%s/maven-metadata.xml", replace1, split[1]);
        Response response = javaApi.maven2(format);
        try {
            byte[] bytes = response.body().asInputStream().readAllBytes();
            Document document = Jsoup.parse(new String(bytes));
            Elements elements = document.select("versions>version");
            for (Element element : elements) {
                Elements selected = element.select("version");
                String value = selected.html();
                depenVersions.add(value);
            }
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }

        if (!Strings.isNullOrEmpty(version)) {
            String preVersion = depenVersions.get(depenVersions.indexOf(version) - 1);
            return Lists.newArrayList(preVersion);
        }
        return depenVersions;
    }
}
