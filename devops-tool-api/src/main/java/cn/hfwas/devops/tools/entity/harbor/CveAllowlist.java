package cn.hfwas.devops.tools.entity.harbor;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author houfei
 * @package cn.hfwas.devops.tools.entity.harbor
 * @date 2025/1/2
 */
@Data
public class CveAllowlist {
    private List<gson> items;
    private String project_id;
    private Integer id;
    private Integer expires_at;
    private LocalDateTime update_time;
    private LocalDateTime creation_time;
}
