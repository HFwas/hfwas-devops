package merge

import (
	"reflect"
	"testing"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// =============================================================================
// setPath 测试
// =============================================================================

func TestSetPath(t *testing.T) {
	tests := []struct {
		name     string
		path     string
		value    interface{}
		initial  map[string]interface{}
		expected map[string]interface{}
	}{
		{
			name:  "simple key",
			path:  "replicaCount",
			value: 3,
			initial: map[string]interface{}{},
			expected: map[string]interface{}{
				"replicaCount": 3,
			},
		},
		{
			name:  "nested path",
			path:  "resources.limits.memory",
			value: "4Gi",
			initial: map[string]interface{}{},
			expected: map[string]interface{}{
				"resources": map[string]interface{}{
					"limits": map[string]interface{}{
						"memory": "4Gi",
					},
				},
			},
		},
		{
			name:  "override existing value",
			path:  "image.tag",
			value: "2.0.0",
			initial: map[string]interface{}{
				"image": map[string]interface{}{
					"tag": "1.0.0",
				},
			},
			expected: map[string]interface{}{
				"image": map[string]interface{}{
					"tag": "2.0.0",
				},
			},
		},
		{
			name:  "deep nested with existing branch",
			path:  "master.persistence.size",
			value: "20Gi",
			initial: map[string]interface{}{
				"master": map[string]interface{}{
					"persistence": map[string]interface{}{
						"storageClass": "alicloud-nas",
					},
				},
			},
			expected: map[string]interface{}{
				"master": map[string]interface{}{
					"persistence": map[string]interface{}{
						"storageClass": "alicloud-nas",
						"size":         "20Gi",
					},
				},
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			obj := deepCopyMap(tt.initial)
			setPath(obj, tt.path, tt.value)
			if !mapsEqual(obj, tt.expected) {
				t.Errorf("setPath(%v, %q, %v) = %v, want %v",
					tt.initial, tt.path, tt.value, obj, tt.expected)
			}
		})
	}
}

// =============================================================================
// deepMerge 测试
// =============================================================================

func TestDeepMerge(t *testing.T) {
	tests := []struct {
		name     string
		base     map[string]interface{}
		overlay  map[string]interface{}
		expected map[string]interface{}
	}{
		{
			name: "simple override",
			base: map[string]interface{}{
				"replicaCount": 1,
				"image": map[string]interface{}{
					"tag": "1.0.0",
				},
			},
			overlay: map[string]interface{}{
				"replicaCount": 3,
			},
			expected: map[string]interface{}{
				"replicaCount": 3,
				"image": map[string]interface{}{
					"tag": "1.0.0",
				},
			},
		},
		{
			name: "nested merge",
			base: map[string]interface{}{
				"master": map[string]interface{}{
					"persistence": map[string]interface{}{
						"size": "8Gi",
					},
					"resources": map[string]interface{}{
						"limits": map[string]interface{}{
							"memory": "512Mi",
						},
					},
				},
			},
			overlay: map[string]interface{}{
				"master": map[string]interface{}{
					"persistence": map[string]interface{}{
						"size": "20Gi",
					},
				},
			},
			expected: map[string]interface{}{
				"master": map[string]interface{}{
					"persistence": map[string]interface{}{
						"size": "20Gi",
					},
					"resources": map[string]interface{}{
						"limits": map[string]interface{}{
							"memory": "512Mi",
						},
					},
				},
			},
		},
		{
			name: "add new key",
			base: map[string]interface{}{
				"architecture": "standalone",
			},
			overlay: map[string]interface{}{
				"auth": map[string]interface{}{
					"enabled": false,
				},
			},
			expected: map[string]interface{}{
				"architecture": "standalone",
				"auth": map[string]interface{}{
					"enabled": false,
				},
			},
		},
		{
			name: "override with nil",
			base: map[string]interface{}{
				"enabled": true,
				"config": map[string]interface{}{
					"key": "value",
				},
			},
			overlay: map[string]interface{}{
				"enabled": false,
				"config":  nil,
			},
			expected: map[string]interface{}{
				"enabled": false,
				"config":  nil,
			},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := deepMerge(tt.base, tt.overlay)
			if !mapsEqual(result, tt.expected) {
				t.Errorf("deepMerge(%v, %v) = %v, want %v",
					tt.base, tt.overlay, result, tt.expected)
			}
		})
	}
}

// =============================================================================
// MergeAll 完整 6 步合并测试
// =============================================================================

func TestMergeAll_FullPipeline(t *testing.T) {
	// 模拟一个 Redis 组件的完整合并流程
	chartDefaults := map[string]interface{}{
		"architecture": "standalone",
		"auth": map[string]interface{}{
			"enabled": true,
			"password": "default-pass",
		},
		"master": map[string]interface{}{
			"persistence": map[string]interface{}{
				"size": "8Gi",
				"storageClass": "",
			},
		},
		"replicaCount": 1,
		"image": map[string]interface{}{
			"registry": "docker.io",
			"repository": "bitnami/redis",
			"tag": "7.0.0",
		},
	}

	componentDefaults := map[string]interface{}{
		"auth": map[string]interface{}{
			"enabled": false,
		},
	}

	productParams := []deliveryv1.ParameterSchema{
		{
			Path:             "master.persistence.size",
			DefaultValue:     "20Gi",
			TargetComponents: []string{"redis"},
		},
	}

	serviceParams := map[string]interface{}{
		"replicaCount": 3,
	}

	valuesOverlay := map[string]interface{}{
		"master": map[string]interface{}{
			"resources": map[string]interface{}{
				"limits": map[string]interface{}{
					"memory": "1Gi",
				},
			},
		},
	}

	globalParams := &deliveryv1.GlobalParameters{
		ImageRegistry: "registry.example.com",
	}

	result := MergeAll(
		chartDefaults,
		componentDefaults,
		productParams,
		serviceParams,
		valuesOverlay,
		globalParams,
		"redis",
	)

	if result == nil {
		t.Fatal("MergeAll returned nil")
	}

	// 验证每个步骤的覆盖效果
	tests := []struct {
		path  string
		want  interface{}
		desc  string
	}{
		{"auth.enabled", false, "Step 1: component default should override auth.enabled"},
		{"master.persistence.size", "20Gi", "Step 2: product param should override size"},
		{"replicaCount", 3, "Step 3: service param should override replicaCount"},
		{"master.resources.limits.memory", "1Gi", "Step 4: valuesOverlay should add resource limits"},
		{"image.registry", "registry.example.com", "Step 5: global param should override image registry"},
	}

	for _, tt := range tests {
		t.Run(tt.desc, func(t *testing.T) {
			got := getNested(result.Values, tt.path)
			if got != tt.want {
				t.Errorf("%s: path %q = %v, want %v", tt.desc, tt.path, got, tt.want)
			}
		})
	}

	// 验证原始 chart 中未被覆盖的值保留
	if getNested(result.Values, "image.repository") != "bitnami/redis" {
		t.Error("uncharted default values should be preserved")
	}

	// 验证 hash 不为空
	if result.ValuesHash == "" {
		t.Error("ValuesHash should not be empty")
	}
}

func TestMergeAll_TargetComponents(t *testing.T) {
	// 测试 targetComponents 过滤
	productParams := []deliveryv1.ParameterSchema{
		{
			Path:             "persistence.size",
			DefaultValue:     "50Gi",
			TargetComponents: []string{"postgresql"},
		},
		{
			Path:             "persistence.size",
			DefaultValue:     "8Gi",
			TargetComponents: []string{"redis"},
		},
	}

	result := MergeAll(
		map[string]interface{}{},
		nil,
		productParams,
		nil,
		nil,
		nil,
		"postgresql",
	)

	if getNested(result.Values, "persistence.size") != "50Gi" {
		t.Errorf("expected postgresql to get 50Gi, got %v", getNested(result.Values, "persistence.size"))
	}
}

func TestMergeAll_EmptyInputs(t *testing.T) {
	// 测试所有输入为空的情况
	result := MergeAll(nil, nil, nil, nil, nil, nil, "test")

	if result == nil {
		t.Fatal("MergeAll with nil inputs should return empty result")
	}
	if result.Values == nil {
		t.Error("MergeAll with nil inputs should return non-nil Values map")
	}
	if len(result.Values) != 0 {
		t.Errorf("expected empty map, got %v", result.Values)
	}
}

// =============================================================================
// Helper 函数测试
// =============================================================================

func TestSplitPath(t *testing.T) {
	tests := []struct {
		path     string
		expected []string
	}{
		{"replicaCount", []string{"replicaCount"}},
		{"resources.limits.memory", []string{"resources", "limits", "memory"}},
		{"master.persistence.size", []string{"master", "persistence", "size"}},
		{"a.b.c.d", []string{"a", "b", "c", "d"}},
		{"", []string{}},
		{"single", []string{"single"}},
	}

	for _, tt := range tests {
		t.Run(tt.path, func(t *testing.T) {
			result := splitPath(tt.path)
			if len(result) != len(tt.expected) {
				t.Errorf("splitPath(%q) = %v, want %v", tt.path, result, tt.expected)
				return
			}
			for i := range result {
				if result[i] != tt.expected[i] {
					t.Errorf("splitPath(%q)[%d] = %q, want %q", tt.path, i, result[i], tt.expected[i])
				}
			}
		})
	}
}

func TestDeepCopyMap(t *testing.T) {
	original := map[string]interface{}{
		"key1": "value1",
		"nested": map[string]interface{}{
			"key2": "value2",
			"deep": map[string]interface{}{
				"key3": 123,
			},
		},
		"list": []string{"a", "b"},
	}

	copied := deepCopyMap(original)

	// 验证值相等
	if !mapsEqual(copied, original) {
		t.Error("copied map should equal original")
	}

	// 验证是深拷贝（修改 copied 不应影响 original）
	setPath(copied, "nested.deep.key3", 456)
	if getNested(original, "nested.deep.key3") == 456 {
		t.Error("deepCopyMap should produce a deep copy, not a shallow reference")
	}
}

func TestComputeHash(t *testing.T) {
	v1 := map[string]interface{}{
		"key": "value",
		"num": 42,
	}
	v2 := map[string]interface{}{
		"key": "value",
		"num": 42,
	}
	v3 := map[string]interface{}{
		"key": "different",
		"num": 42,
	}

	h1 := computeHash(v1)
	h2 := computeHash(v2)
	h3 := computeHash(v3)

	if h1 == "" {
		t.Error("hash should not be empty")
	}
	if h1 != h2 {
		t.Error("same values should produce same hash")
	}
	if h1 == h3 {
		t.Error("different values should produce different hash")
	}
}

func TestContains(t *testing.T) {
	slice := []string{"redis", "postgresql", "backend"}

	if !contains(slice, "redis") {
		t.Error("contains should find 'redis'")
	}
	if contains(slice, "frontend") {
		t.Error("contains should not find 'frontend'")
	}
	if contains([]string{}, "test") {
		t.Error("contains should return false for empty slice")
	}
}

// =============================================================================
// Benchmarks
// =============================================================================

func BenchmarkMergeAll(b *testing.B) {
	chartDefaults := map[string]interface{}{
		"master": map[string]interface{}{
			"persistence": map[string]interface{}{
				"size": "8Gi",
			},
		},
		"replicaCount": 1,
	}
	productParams := []deliveryv1.ParameterSchema{
		{Path: "replicaCount", DefaultValue: 3},
	}

	b.ResetTimer()
	for i := 0; i < b.N; i++ {
		MergeAll(chartDefaults, nil, productParams, nil, nil, nil, "test")
	}
}

// =============================================================================
// Test helpers
// =============================================================================

// mapsEqual 递归比较两个 map 是否相等。
func mapsEqual(a, b map[string]interface{}) bool {
	if len(a) != len(b) {
		return false
	}
	for k, av := range a {
		bv, ok := b[k]
		if !ok {
			return false
		}
		switch avTyped := av.(type) {
		case map[string]interface{}:
			bvTyped, ok := bv.(map[string]interface{})
			if !ok || !mapsEqual(avTyped, bvTyped) {
				return false
			}
		default:
			if !valuesEqual(av, bv) {
				return false
			}
		}
	}
	return true
}

// valuesEqual 递归比较两个值是否相等。
func valuesEqual(a, b interface{}) bool {
	return reflect.DeepEqual(a, b)
}

// getNested 按点号路径取值。
func getNested(obj map[string]interface{}, path string) interface{} {
	keys := splitPath(path)
	current := obj
	for i, key := range keys {
		if i == len(keys)-1 {
			return current[key]
		}
		if next, ok := current[key].(map[string]interface{}); ok {
			current = next
		} else {
			return nil
		}
	}
	return nil
}