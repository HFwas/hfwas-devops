package com.hfwas.devops.service.database;

import com.baomidou.mybatisplus.generator.config.po.TableField;

import java.util.List;
import java.util.Set;

/**
 * @author houfei
 * @package com.hfwas.devops.service.database
 * @date 2025/6/17
 */
public interface DataBaseService {

    List<String> schema();
    Set<String> tables();
    List<TableField> colums(String name);
    void doc(String name);

}
