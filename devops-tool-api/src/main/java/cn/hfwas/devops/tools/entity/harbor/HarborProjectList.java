package cn.hfwas.devops.tools.entity.harbor;

import com.google.gson.JsonObject;
import lombok.Data;

/**
 * @author houfei
 * @package cn.hfwas.devops.tools.entity.harbor
 * @date 2025/1/3
 */
@Data
public class HarborProjectList {
    private Integer project_id;
    private Integer owner_id;
    private Integer name;
    private Integer registry_id;
    private Integer creation_time;
    private Integer update_time;
    private Integer deleted;
    private Integer owner_name;
    private Integer togglable;
    private Integer current_user_role_id;
    private Integer current_user_role_ids;
    private Integer repo_count;
    private JsonObject metadata;
}
