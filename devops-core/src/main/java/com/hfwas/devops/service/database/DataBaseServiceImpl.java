package com.hfwas.devops.service.database;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.generator.config.DataSourceConfig;
import com.baomidou.mybatisplus.generator.config.StrategyConfig;
import com.baomidou.mybatisplus.generator.config.builder.ConfigBuilder;
import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.baomidou.mybatisplus.generator.config.po.TableInfo;
import com.baomidou.mybatisplus.generator.config.rules.DateType;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author houfei
 * @package com.hfwas.devops.service.database
 * @date 2025/6/17
 */
@Service
public class DataBaseServiceImpl implements DataBaseService {

    @Override
    public List<String> schema() {
        List<String> schemas = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:mysql://localhost:3306/hfwas-devops-base", "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SHOW DATABASES")) {

            while (rs.next()) {
                String schema = rs.getString(1);
                schemas.add(schema);
            }
            return schemas;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> tables() {
        List<TableInfo> tables = getTableInfos();
        return tables.stream().map(TableInfo::getName).collect(Collectors.toSet());
    }

    @Override
    public List<TableField> columns(String name) {
        List<TableInfo> tableInfos = getTableInfos();
        Optional<List<TableField>> first = tableInfos.stream()
                .filter(tableInfo -> tableInfo.getName().equals(name))
                .map(TableInfo::getFields).toList().stream().findFirst();

        return first.orElse(null);
    }

    @Override
    public void doc(String name) {

    }

    private static List<TableInfo> getTableInfos() {
        // 使用 MyBatis Plus Generator 解析表结构
        DataSourceConfig.Builder dataSourceConfigBuilder = new DataSourceConfig.Builder("jdbc:mysql://localhost:3306/hfwas-devops-base",
                "root",
                "");
        StrategyConfig.Builder strategyConfig = new StrategyConfig.Builder().enableSkipView(); // 忽略视图，业务上一般用不到
        if (StrUtil.isNotEmpty("ddl_history")) {
            strategyConfig.addExclude("ddl_history");
        } else {
            // 移除工作流和定时任务前缀的表名
            strategyConfig.addExclude("ACT_[\\S\\s]+|QRTZ_[\\S\\s]+|FLW_[\\S\\s]+");
            // 移除 ORACLE 相关的系统表
            strategyConfig.addExclude("IMPDP_[\\S\\s]+|ALL_[\\S\\s]+|HS_[\\S\\\\s]+");
            strategyConfig.addExclude("[\\S\\s]+\\$[\\S\\s]+|[\\S\\s]+\\$"); // 表里不能有 $，一般有都是系统的表
        }

        com.baomidou.mybatisplus.generator.config.GlobalConfig globalConfig = new com.baomidou.mybatisplus.generator.config.GlobalConfig.Builder().dateType(DateType.TIME_PACK).build(); // 只使用 LocalDateTime 类型，不使用 LocalDate
        ConfigBuilder builder = new ConfigBuilder(null, dataSourceConfigBuilder.build(), strategyConfig.build(),
                null, globalConfig, null);
        // 按照名字排序
        List<TableInfo> tables = builder.getTableInfoList();
        tables.sort(Comparator.comparing(TableInfo::getName));
        return tables;
    }
}
