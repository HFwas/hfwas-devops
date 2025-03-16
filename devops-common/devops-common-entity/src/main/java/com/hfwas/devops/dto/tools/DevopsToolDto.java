package com.hfwas.devops.dto.tools;

import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.dto.tools
 * @date 2025/3/16
 */
@Data
public class DevopsToolDto {
    private String  name;
    private String  protocol;
    private Integer type;
    private String  ip;
    private Integer port;
    private String  username;
    private String  password;
    private Integer tenantId;
}
