package merge

import (
	"crypto/sha256"
	"encoding/json"
	"fmt"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// ValuesMerger 负责多级 Helm values 合并。
type ValuesMerger struct{}

// MergeResult 包含合并后的 values 及其 hash。
type MergeResult struct {
	Values     map[string]interface{}
	ValuesHash string
}

// MergeAll 执行完整的多级 values 合并。
// 合并顺序：
//   Step 0: Chart 默认 Values
//   Step 1: 组件默认值 (ComponentDef.DefaultValues) - JSON Merge Patch
//   Step 2: 产品级参数 (CloudService parameters, 按 targetComponents 匹配) - Simple Override
//   Step 3: 服务级参数 (App services[].parameters) - Simple Override
//   Step 4: 组件级覆盖 (CloudComponent.ValuesOverlay) - JSON Merge Patch
//   Step 5: 全局参数 (App GlobalParameters) - Simple Override
func MergeAll(
	chartDefaultValues map[string]interface{},
	componentDefaults map[string]interface{},
	productParams []deliveryv1.ParameterSchema,
	serviceParams map[string]interface{},
	valuesOverlay map[string]interface{},
	globalParams *deliveryv1.GlobalParameters,
	componentName string,
) *MergeResult {
	// Step 0: Chart 默认值
	values := deepCopyMap(chartDefaultValues)

	// Step 1: 组件默认值 — JSON Merge Patch
	if componentDefaults != nil {
		values = deepMerge(values, componentDefaults)
	}

	// Step 2: 产品级参数 — Simple Override
	for _, param := range productParams {
		if len(param.TargetComponents) > 0 && !contains(param.TargetComponents, componentName) {
			continue
		}
		if param.Path != "" && param.DefaultValue != nil {
			setPath(values, param.Path, param.DefaultValue)
		}
	}

	// Step 3: 服务级参数 — Simple Override
	for key, val := range serviceParams {
		setPath(values, key, val)
	}

	// Step 4: 组件级覆盖 — JSON Merge Patch
	if valuesOverlay != nil {
		values = deepMerge(values, valuesOverlay)
	}

	// Step 5: 全局参数 — Simple Override
	if globalParams != nil {
		if globalParams.ImageRegistry != "" {
			setPath(values, "image.registry", globalParams.ImageRegistry)
		}
		if globalParams.DefaultStorageClass != "" {
			setPath(values, "global.storageClass", globalParams.DefaultStorageClass)
		}
		if globalParams.DomainSuffix != "" {
			setPath(values, "global.domainSuffix", globalParams.DomainSuffix)
		}
	}

	hash := computeHash(values)
	return &MergeResult{Values: values, ValuesHash: hash}
}

// setPath 按点号路径设置值。
// 例如 setPath(obj, "resources.limits.memory", "4Gi")
// 会在 obj["resources"]["limits"]["memory"] = "4Gi"
func setPath(obj map[string]interface{}, path string, value interface{}) {
	keys := splitPath(path)
	current := obj
	for i, key := range keys {
		if i == len(keys)-1 {
			current[key] = value
			return
		}
		if _, ok := current[key]; !ok {
			current[key] = make(map[string]interface{})
		}
		if nextMap, ok := current[key].(map[string]interface{}); ok {
			current = nextMap
		} else {
			newMap := make(map[string]interface{})
			current[key] = newMap
			current = newMap
		}
	}
}

// splitPath 将点号路径分割成 key 列表。
func splitPath(path string) []string {
	keys := make([]string, 0)
	start := 0
	for i := 0; i < len(path); i++ {
		if path[i] == '.' {
			if i > start {
				keys = append(keys, path[start:i])
			}
			start = i + 1
		}
	}
	if start < len(path) {
		keys = append(keys, path[start:])
	}
	return keys
}

// deepMerge 深度合并两个 map（JSON Merge Patch 风格）。
func deepMerge(base, overlay map[string]interface{}) map[string]interface{} {
	result := make(map[string]interface{})
	for k, v := range base {
		result[k] = v
	}
	for k, v := range overlay {
		if baseVal, ok := base[k]; ok {
			if baseMap, ok1 := baseVal.(map[string]interface{}); ok1 {
				if overlayMap, ok2 := v.(map[string]interface{}); ok2 {
					result[k] = deepMerge(baseMap, overlayMap)
					continue
				}
			}
		}
		result[k] = v
	}
	return result
}

// deepCopyMap 深拷贝 map。
func deepCopyMap(src map[string]interface{}) map[string]interface{} {
	if src == nil {
		return make(map[string]interface{})
	}
	dst := make(map[string]interface{}, len(src))
	for k, v := range src {
		if m, ok := v.(map[string]interface{}); ok {
			dst[k] = deepCopyMap(m)
		} else {
			dst[k] = v
		}
	}
	return dst
}

// computeHash 计算 values 的 SHA256 摘要。
func computeHash(values map[string]interface{}) string {
	data, err := json.Marshal(values)
	if err != nil {
		return ""
	}
	h := sha256.Sum256(data)
	return fmt.Sprintf("%x", h)
}

// contains 检查字符串切片是否包含目标。
func contains(slice []string, target string) bool {
	for _, s := range slice {
		if s == target {
			return true
		}
	}
	return false
}