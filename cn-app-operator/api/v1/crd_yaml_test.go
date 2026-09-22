package v1

import (
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"testing"

	apiextensionsv1 "k8s.io/apiextensions-apiserver/pkg/apis/apiextensions/v1"
	"sigs.k8s.io/yaml"
)

// 本仓库的 CRD YAML 是手工维护的（controller-gen 未安装，zz_generated.deepcopy.go 也是手写），
// 所以最容易出的错是「改了 Go 类型却忘了同步 config/crd/*.yaml」。
// 这个测试把两边钉在一起。

const crdDir = "../../config/crd"

// crdExpectation 描述一个 CRD 应有的基本形态。
type crdExpectation struct {
	file   string
	kind   string
	plural string
	scope  apiextensionsv1.ResourceScope
	// requiredSpecFields 是 spec 下必须存在的属性名
	requiredSpecFields []string
	// requiredStatusFields 是 status 下必须存在的属性名
	requiredStatusFields []string
}

func loadCRD(t *testing.T, file string) *apiextensionsv1.CustomResourceDefinition {
	t.Helper()

	path := filepath.Join(crdDir, file)
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("读取 CRD 失败 %s: %v", path, err)
	}

	var crd apiextensionsv1.CustomResourceDefinition
	if err := yaml.Unmarshal(raw, &crd); err != nil {
		t.Fatalf("解析 CRD 失败 %s: %v", path, err)
	}
	return &crd
}

// schemaProperties 取出 v1 版本下某个路径的属性集合。
func schemaProperties(t *testing.T, crd *apiextensionsv1.CustomResourceDefinition, path ...string) map[string]apiextensionsv1.JSONSchemaProps {
	t.Helper()

	if len(crd.Spec.Versions) == 0 {
		t.Fatalf("%s 没有定义任何版本", crd.Name)
	}
	root := crd.Spec.Versions[0].Schema.OpenAPIV3Schema
	if root == nil {
		t.Fatalf("%s 缺少 openAPIV3Schema", crd.Name)
	}

	cur := root.Properties
	for _, segment := range path {
		node, ok := cur[segment]
		if !ok {
			t.Fatalf("%s 的 schema 缺少 %v 这一层（在 %q 处断掉）", crd.Name, path, segment)
		}
		cur = node.Properties
	}
	return cur
}

func TestCRDYAML_MatchesGoTypes(t *testing.T) {
	cases := []crdExpectation{
		{
			file:   "app.yaml",
			kind:   "App",
			plural: "apps",
			scope:  apiextensionsv1.NamespaceScoped,
			// v0.3：发布实例层
			requiredSpecFields:   []string{"displayName", "version", "releaseID", "planRevision", "services"},
			requiredStatusFields: []string{"phase", "aggregatedSummary", "serviceStatuses", "observedReleaseID"},
		},
		{
			file:   "cloudservice.yaml",
			kind:   "CloudService",
			plural: "cloudservices",
			scope:  apiextensionsv1.NamespaceScoped,
			// v0.3：releaseID 下发到服务层
			requiredSpecFields: []string{"displayName", "components", "releaseID"},
			// v0.3：observedReleaseID
			requiredStatusFields: []string{"phase", "componentStatuses", "observedReleaseID"},
		},
		{
			file:   "cloudcomponent.yaml",
			kind:   "CloudComponent",
			plural: "cloudcomponents",
			scope:  apiextensionsv1.NamespaceScoped,
			// v0.3：引用已有对象 + 持久化配置
			requiredSpecFields: []string{"chartName", "chartVersion", "releaseID", "refResources", "persistentVolumeConfigs"},
			// v0.3：Pod 明细 + 期望/实际差异
			requiredStatusFields: []string{"phase", "workloadStatus", "observedReleaseID", "podsDetail", "workloadDiffs"},
		},
		{
			file:   "producttask.yaml",
			kind:   "ProductTask",
			plural: "producttasks",
			scope:  apiextensionsv1.ClusterScoped,
			// v0.3 新增的第 4 个 CRD
			requiredSpecFields:   []string{"releaseID", "opsType", "resource", "refComponent", "taskOrder"},
			requiredStatusFields: []string{"phase", "messages", "conditions", "observedGeneration"},
		},
	}

	if len(cases) == 0 {
		t.Fatal("no CRD expectations defined")
	}

	for _, tc := range cases {
		t.Run(tc.kind, func(t *testing.T) {
			crd := loadCRD(t, tc.file)

			if crd.Spec.Group != "delivery.hfwas.io" {
				t.Errorf("group = %q, want delivery.hfwas.io", crd.Spec.Group)
			}
			if crd.Spec.Names.Kind != tc.kind {
				t.Errorf("kind = %q, want %q", crd.Spec.Names.Kind, tc.kind)
			}
			if crd.Spec.Names.Plural != tc.plural {
				t.Errorf("plural = %q, want %q", crd.Spec.Names.Plural, tc.plural)
			}
			if crd.Spec.Scope != tc.scope {
				t.Errorf("scope = %q, want %q", crd.Spec.Scope, tc.scope)
			}

			// 版本与子资源
			if len(crd.Spec.Versions) != 1 {
				t.Fatalf("expected exactly 1 version, got %d", len(crd.Spec.Versions))
			}
			v := crd.Spec.Versions[0]
			if v.Name != "v1" || !v.Served || !v.Storage {
				t.Errorf("version %q served=%v storage=%v, want v1 served+storage", v.Name, v.Served, v.Storage)
			}
			if v.Subresources == nil || v.Subresources.Status == nil {
				t.Error("缺少 status 子资源")
			}

			specProps := schemaProperties(t, crd, "spec")
			for _, f := range tc.requiredSpecFields {
				if _, ok := specProps[f]; !ok {
					t.Errorf("spec 缺少字段 %q", f)
				}
			}

			statusProps := schemaProperties(t, crd, "status")
			for _, f := range tc.requiredStatusFields {
				if _, ok := statusProps[f]; !ok {
					t.Errorf("status 缺少字段 %q", f)
				}
			}
		})
	}
}

// TestCRDYAML_ProductTaskPhaseEnum 保证 CRD 里的 phase 枚举与 Go 常量一致。
// 加了 enum 却漏改 YAML 会让控制器写入的值被 apiserver 拒绝。
func TestCRDYAML_ProductTaskPhaseEnum(t *testing.T) {
	crd := loadCRD(t, "producttask.yaml")
	phase := schemaProperties(t, crd, "status")["phase"]

	declared := make(map[string]bool, len(phase.Enum))
	for _, e := range phase.Enum {
		var s string
		if err := yaml.Unmarshal(e.Raw, &s); err != nil {
			t.Fatalf("解析 phase enum 项失败: %v", err)
		}
		declared[s] = true
	}

	for _, want := range []TaskPhase{
		TaskPhasePending, TaskPhaseRunning, TaskPhaseSucceeded, TaskPhaseFailed,
	} {
		if !declared[string(want)] {
			t.Errorf("CRD 的 status.phase enum 缺少 Go 常量 %q", want)
		}
	}
	if len(declared) != 4 {
		t.Errorf("status.phase enum 有 %d 项，Go 侧定义了 4 项；请同步", len(declared))
	}
}

// TestCRDYAML_CloudComponentReleaseIDOnAllLayers 保证发布号在三层都可达。
// releaseID 是串起 App → CloudService → CloudComponent → ProductTask 的线，
// 任何一层漏了字段都会让「这一次发布」断链。
func TestCRDYAML_ReleaseIDOnAllLayers(t *testing.T) {
	layers := []struct{ file, kind string }{
		{"app.yaml", "App"},
		{"cloudservice.yaml", "CloudService"},
		{"cloudcomponent.yaml", "CloudComponent"},
		{"producttask.yaml", "ProductTask"},
	}

	for _, l := range layers {
		crd := loadCRD(t, l.file)
		if _, ok := schemaProperties(t, crd, "spec")["releaseID"]; !ok {
			t.Errorf("%s 的 spec 缺少 releaseID，发布号链路断开", l.kind)
		}
	}
}

// kindToCRDFile 是样本校验用的 kind → CRD 文件映射。
var kindToCRDFile = map[string]string{
	"App":            "app.yaml",
	"CloudService":   "cloudservice.yaml",
	"CloudComponent": "cloudcomponent.yaml",
	"ProductTask":    "producttask.yaml",
}

// TestSamples_ConformToCRDSchema 校验 config/samples 下的样本没有写出 schema 之外的字段。
//
// 手写 YAML 最容易出的错是字段名拼错（如 taskOrder 写成 taskorder），
// 而 sigs.k8s.io/yaml 反序列化会静默忽略未知字段，所以这里显式比对 schema。
func TestSamples_ConformToCRDSchema(t *testing.T) {
	files, err := filepath.Glob(filepath.Join(crdDir, "..", "samples", "*.yaml"))
	if err != nil {
		t.Fatalf("列举样本失败: %v", err)
	}
	if len(files) == 0 {
		t.Skip("config/samples 下没有样本")
	}

	schemas := make(map[string]*apiextensionsv1.JSONSchemaProps)
	checked := 0

	for _, file := range files {
		raw, err := os.ReadFile(file)
		if err != nil {
			t.Fatalf("读取样本失败 %s: %v", file, err)
		}

		for _, doc := range splitYAMLDocs(raw) {
			var obj map[string]interface{}
			if err := yaml.Unmarshal(doc, &obj); err != nil {
				t.Fatalf("解析样本失败 %s: %v", file, err)
			}
			kind, _ := obj["kind"].(string)
			crdFile, ok := kindToCRDFile[kind]
			if !ok {
				continue // 样本里也可能有非 CRD 对象（如 Namespace），跳过
			}

			root, ok := schemas[crdFile]
			if !ok {
				crd := loadCRD(t, crdFile)
				root = crd.Spec.Versions[0].Schema.OpenAPIV3Schema
				schemas[crdFile] = root
			}

			checked++
			base := filepath.Base(file)
			for _, section := range []string{"spec", "status"} {
				sub, ok := obj[section].(map[string]interface{})
				if !ok {
					continue
				}
				walkAgainstSchema(t, base, section, sub, root.Properties[section])
			}
		}
	}

	if checked == 0 {
		t.Fatal("没有校验到任何 CRD 样本")
	}
}

// splitYAMLDocs 按 --- 切分多文档 YAML。
func splitYAMLDocs(raw []byte) [][]byte {
	parts := bytes.Split(raw, []byte("\n---"))
	out := make([][]byte, 0, len(parts))
	for _, p := range parts {
		if len(bytes.TrimSpace(p)) > 0 {
			out = append(out, p)
		}
	}
	return out
}

// walkAgainstSchema 递归比对对象字段与 CRD schema，报告 schema 之外的字段名。
func walkAgainstSchema(t *testing.T, file, path string, obj map[string]interface{}, schema apiextensionsv1.JSONSchemaProps) {
	t.Helper()

	// preserve-unknown-fields 的子树不校验，例如 valuesOverlay
	if schema.XPreserveUnknownFields != nil && *schema.XPreserveUnknownFields {
		return
	}
	// 没有 properties 说明是自由对象（如 additionalProperties），不校验
	if schema.Properties == nil {
		return
	}

	for key, val := range obj {
		child, ok := schema.Properties[key]
		if !ok {
			t.Errorf("%s: %s.%s 不在 CRD schema 中（字段名拼写错误或 CRD 未同步）",
				file, path, key)
			continue
		}

		switch v := val.(type) {
		case map[string]interface{}:
			walkAgainstSchema(t, file, path+"."+key, v, child)
		case []interface{}:
			// Items 是 JSONSchemaPropsOrArray，取单 schema 形式即可
			if child.Items == nil || child.Items.Schema == nil {
				continue
			}
			itemSchema := *child.Items.Schema
			for i, item := range v {
				obj, ok := item.(map[string]interface{})
				if !ok {
					continue
				}
				walkAgainstSchema(t, file, fmt.Sprintf("%s.%s[%d]", path, key, i), obj, itemSchema)
			}
		}
	}
}
