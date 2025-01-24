package com.hfwas.devops.dto.vul;

import com.hfwas.devops.page.DevopsPage;
import lombok.Data;

/**
 * @author houfei
 * @package com.hfwas.devops.dto.vul
 * @date 2025/1/23
 */
@Data
public class VulDto extends DevopsPage {
    private Long id;
    private String cveId;
}
