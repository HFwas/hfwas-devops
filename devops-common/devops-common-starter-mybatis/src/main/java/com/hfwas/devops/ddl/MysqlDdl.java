package com.hfwas.devops.ddl;

import com.baomidou.mybatisplus.extension.ddl.IDdl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author houfei
 * @package com.hfwas.devops.ddl
 * @date 2025/1/23
 */
@Component
public class MysqlDdl implements IDdl {

    @Autowired
    private DataSource dataSource;

    @Override
    public void runScript(Consumer<DataSource> consumer) {
        consumer.accept(this.dataSource);
    }

    @Override
    public List<String> getSqlFiles() {
        // 从`3.5.3.2`版本开始，支持执行存储过程。在文件名后追加`#$$`，其中`$$`是自定义的完整SQL分隔符。"db/procedure.sql#$$",
        // 存储过程脚本以`END`结尾，并追加分隔符`END;$$`表示脚本结束。
        return Arrays.asList("sql/hfwas-devops.sql");
    }

}
