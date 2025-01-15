package com.hfwas.devops.service.vul.java;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hfwas.devops.entity.DevopsVulDependency;
import com.hfwas.devops.service.vul.AbstractDepenScan;
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
public class DevopsMavenDepenScan extends AbstractDepenScan {

    @Override
    public String language() {
        return "Java";
    }

    @Override
    public String type() {
        return "maven";
    }

    @Override
    public List<DevopsVulDependency> dependencys(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        List<DevopsVulDependency> devopsVulDependencys = Lists.newArrayList();
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
                DevopsVulDependency devopsVulDependency = DevopsVulDependency.builder()
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

}
