package com.hfwas.devops.controller.database;

import com.baomidou.mybatisplus.generator.config.po.TableField;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.service.database.DataBaseService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * @author houfei
 * @package com.hfwas.devops.controller.database
 * @date 2025/6/17
 */
@RestController
@RequestMapping("/databases")
public class DataBaseController {

    private final DataBaseService dataBaseService;

    public DataBaseController(DataBaseService dataBaseService) {
        this.dataBaseService = dataBaseService;
    }

    @PostMapping("/schema")
    public BaseResult<List<String>> schema() {
        List<String> schema = dataBaseService.schema();
        return BaseResult.ok(schema);
    }

    @PostMapping("/tables")
    public BaseResult<Set<String>> tables() {
        Set<String> tables = dataBaseService.tables();
        return BaseResult.ok(tables);
    }

    @PostMapping("/columns")
    public BaseResult<List<TableField>> columns(@RequestParam("name") String name) {
        List<TableField> columns = dataBaseService.columns(name);
        return BaseResult.ok(columns);
    }

    @PostMapping("/doc")
    public BaseResult doc(@RequestParam("name") String name) {
        dataBaseService.doc(name);
        return BaseResult.ok();
    }

}
