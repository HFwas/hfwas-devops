package com.hfwas.devops.service.vul.java;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.hfwas.devops.convert.DevopsVulCodeDependencyVersionConvert;
import com.hfwas.devops.entity.DevopsVulCodeDependency;
import com.hfwas.devops.entity.DevopsVulCodeDependencyVersion;
import com.hfwas.devops.language.java.*;
import com.hfwas.devops.mapper.DevopsVulDependencyMapper;
import com.hfwas.devops.mapper.DevopsVulDependencyVersionMapper;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    @Resource
    DevopsVulDependencyMapper dependencyMapper;
    @Autowired
    private DevopsVulDependencyVersionMapper dependencyVersionMapper;
    @Resource
    private Gson gson;

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
    @Transactional
    public List<DevopsVulCodeDependency> dependencys(MultipartFile multipartFile) throws IOException {
        String originalFilename = multipartFile.getOriginalFilename();
        List<DevopsVulCodeDependency> devopsVulDependencys = Lists.newArrayList();
        // mvn -B com.github.ferstl:depgraph-maven-plugin:4.0.3:aggregate -DgraphFormat=json -DoutputDirectory=target -DoutputFileName=aggregate-depgraph.json
        if (originalFilename.equals("aggregate-depgraph.json")) {
            byte[] bytes = multipartFile.getBytes();
            Depgraph depgraph = gson.fromJson(new String(bytes, Charset.defaultCharset()), Depgraph.class);
            List<DepgraphArtifact> artifacts = depgraph.getArtifacts();

            MavenDependencyGraph mavenDependencyGraph = new MavenDependencyGraph(depgraph);
            JavaManifest manifest = mavenDependencyGraph.createManifest(null);
            String json = gson.toJson(manifest);

            Map<Long, DevopsVulCodeDependency> nodeMap = new HashMap<>();
            Map<Long, Long> numericIdToDbIdMap = new HashMap<>(); // 存储 numericId 到数据库主键的映射

            for (DepgraphArtifact artifact : artifacts) {
                String groupId = artifact.getGroupId();
                String artifactId = artifact.getArtifactId();
                String version = artifact.getVersion();
                Long numericId = artifact.getNumericId();
                DevopsVulCodeDependency dependency = DevopsVulCodeDependency.builder()
                        .company(groupId)
                        .dependencyName(artifactId)
                        .version(version)
                        .type(1).parentId(null).build();

                // 检查数据库中是否已存在相同的依赖项
                LambdaQueryWrapper<DevopsVulCodeDependency> queryWrapper = Wrappers.<DevopsVulCodeDependency>lambdaQuery().eq(DevopsVulCodeDependency::getCompany, groupId)
                        .eq(DevopsVulCodeDependency::getDependencyName, artifactId);
                DevopsVulCodeDependency existingDependency = dependencyMapper.selectOne(queryWrapper);

                Long dbId;
                if (existingDependency != null) {
                    // 如果已存在，直接使用数据库中的记录
                    dbId = existingDependency.getId();
                    dependency.setId(existingDependency.getId());
                } else {
                    // 如果不存在，创建新的依赖项并插入数据库
                    dependencyMapper.insert(dependency);
                    dbId = dependency.getId(); // 获取生成的 id
                }

                DevopsVulCodeDependencyVersion dependencyVersion = DevopsVulCodeDependencyVersionConvert.INSTANCE.to(dependency);
                // 检查数据库中是否已存在相同的依赖项
                LambdaQueryWrapper<DevopsVulCodeDependencyVersion> versionQueryWrapper = Wrappers.<DevopsVulCodeDependencyVersion>lambdaQuery()
                        .eq(DevopsVulCodeDependencyVersion::getDepenId, dependencyVersion.getDepenId())
                        .eq(DevopsVulCodeDependencyVersion::getVersion, dependencyVersion.getVersion());
                DevopsVulCodeDependencyVersion existingDependencyVersion = dependencyVersionMapper.selectOne(versionQueryWrapper);
                if (existingDependencyVersion != null) {
                    dependencyVersion.setId(dependencyVersion.getId());
                    dependencyVersionMapper.updateById(dependencyVersion);
                } else {
                    dependencyVersionMapper.save(dependencyVersion);
                }

                // 存储映射关系
                devopsVulDependencys.add(dependency);
            }

            List<DepgraphDependency> dependencies = depgraph.getDependencies();
            for (DepgraphDependency dependency : dependencies) {
                Long numericFrom = dependency.getNumericFrom();
                Long numericTo = dependency.getNumericTo();

                // 获取数据库主键
                Long fromDbId = numericIdToDbIdMap.get(numericFrom);
                Long toDbId = numericIdToDbIdMap.get(numericTo);

                // 设置to节点的parentId为from节点的数据库主键
                DevopsVulCodeDependency toNode = nodeMap.get(numericTo);
                if (toNode != null && fromDbId != null) {
                    toNode.setParentId(fromDbId); // 使用数据库主键
                    dependencyMapper.updateById(toNode); // 更新数据库
                }
            }
            // 打印依赖图谱
            for (DevopsVulCodeDependency node : nodeMap.values()) {
                if (node.getParentId() == null) {
                    // 根节点（没有parentId）
                    System.out.println("Root: " + node);
                    printChildren(node, nodeMap, 1);
                }
            }
            return devopsVulDependencys;
        }
        return devopsVulDependencys;
    }

    private static void printChildren(DevopsVulCodeDependency parent, Map<Long, DevopsVulCodeDependency> nodeMap, int depth) {
        for (DevopsVulCodeDependency node : nodeMap.values()) {
            if (node.getParentId() != null && node.getParentId().equals(parent.getId())) {
                // 根据深度缩进
                for (int i = 0; i < depth; i++) {
                    System.out.print("  ");
                }
                System.out.println("└─ " + node);

                // 递归打印子节点
                printChildren(node, nodeMap, depth + 1);
            }
        }
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
