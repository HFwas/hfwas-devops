package service

import (
	"context"
	"fmt"

	apierrors "k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime/schema"
)

// isNotFound 判断是否为 404。
func isNotFound(err error) bool {
	return apierrors.IsNotFound(err)
}

// 本文件是 delivery.hfwas.io CRD 的「读」侧。
//
// kube.go 原先只有 Create/Delete 没读，导致后端拿不到集群里的真实状态：
// 产品详情页的 phase 只读本地库字段、组件列表从 manifest 拼且 phase 硬编码 unknown。
// podsDetail / workloadDiffs / releaseID 这些 v0.3 新增字段都在 CR status 里，
// 必须先把读侧补上，前端才看得到。

var (
	ccGVR = schema.GroupVersionResource{Group: "delivery.hfwas.io", Version: "v1", Resource: "cloudcomponents"}
	ptGVR = schema.GroupVersionResource{Group: "delivery.hfwas.io", Version: "v1", Resource: "producttasks"}
)

// =============================================================================
// VO 定义（直接作为 API 响应体，与 kube.go 里的 NodeInfo/PVCInfo 同风格）
// =============================================================================

// AppInfo 是 App CR 的读取视图。
type AppInfo struct {
	Name              string            `json:"name"`
	DisplayName       string            `json:"displayName,omitempty"`
	Version           string            `json:"version,omitempty"`
	Phase             string            `json:"phase,omitempty"`
	ReleaseID         string            `json:"releaseID,omitempty"`
	PlanRevision      string            `json:"planRevision,omitempty"`
	ObservedReleaseID string            `json:"observedReleaseID,omitempty"`
	Summary           *AggregateSummary `json:"aggregatedSummary,omitempty"`
	Services          []ServiceStatusVO `json:"serviceStatuses,omitempty"`
}

// AggregateSummary 是 App.status.aggregatedSummary 的视图。
type AggregateSummary struct {
	TotalServices   int `json:"totalServices"`
	ReadyServices   int `json:"readyServices"`
	TotalComponents int `json:"totalComponents"`
	ReadyComponents int `json:"readyComponents"`
}

// ServiceStatusVO 是 App.status.serviceStatuses[] 的视图。
type ServiceStatusVO struct {
	Name              string `json:"name"`
	Phase             string `json:"phase,omitempty"`
	ReleaseID         string `json:"releaseID,omitempty"`
	ObservedReleaseID string `json:"observedReleaseID,omitempty"`
	ErrorMessage      string `json:"errorMessage,omitempty"`
}

// ComponentInfo 是 CloudComponent CR 的读取视图，含 Pod 明细与期望/实际差异。
type ComponentInfo struct {
	Name              string           `json:"name"`
	DisplayName       string           `json:"displayName,omitempty"`
	ComponentType     string           `json:"componentType,omitempty"`
	ChartName         string           `json:"chartName,omitempty"`
	ChartVersion      string           `json:"chartVersion,omitempty"`
	HelmRelease       string           `json:"helmRelease,omitempty"`
	Phase             string           `json:"phase,omitempty"`
	ReleaseID         string           `json:"releaseID,omitempty"`
	ObservedReleaseID string           `json:"observedReleaseID,omitempty"`
	Summary           *WorkloadSummary `json:"summary,omitempty"`
	Pods              []PodDetailVO    `json:"pods,omitempty"`
	Diffs             []WorkloadDiffVO `json:"diffs,omitempty"`
	Conditions        []ConditionVO    `json:"conditions,omitempty"`
}

// WorkloadSummary 是 CloudComponent.status.workloadStatus 的摘要部分。
type WorkloadSummary struct {
	TotalWorkloads    int `json:"totalWorkloads"`
	ReadyWorkloads    int `json:"readyWorkloads"`
	DegradedWorkloads int `json:"degradedWorkloads"`
}

// PodDetailVO 是 CloudComponent.status.podsDetail[] 的视图。
type PodDetailVO struct {
	Name            string `json:"name"`
	Namespace       string `json:"namespace,omitempty"`
	IP              string `json:"ip,omitempty"`
	HostIP          string `json:"hostIP,omitempty"`
	WorkloadKind    string `json:"workloadKind,omitempty"`
	WorkloadName    string `json:"workloadName,omitempty"`
	Ready           bool   `json:"ready"`
	Phase           string `json:"phase,omitempty"`
	State           string `json:"state,omitempty"`
	Restarts        int    `json:"restarts"`
	UpdatedRevision bool   `json:"updatedRevision"`
	Message         string `json:"message,omitempty"`
}

// WorkloadDiffVO 是 CloudComponent.status.workloadDiffs[] 的视图。
type WorkloadDiffVO struct {
	Kind      string `json:"kind,omitempty"`
	Name      string `json:"name,omitempty"`
	Namespace string `json:"namespace,omitempty"`
	Path      string `json:"path,omitempty"`
	Expected  string `json:"expected,omitempty"`
	Current   string `json:"current,omitempty"`
	Message   string `json:"message,omitempty"`
}

// ConditionVO 是 condition 的视图。
type ConditionVO struct {
	Type    string `json:"type"`
	Status  string `json:"status"`
	Reason  string `json:"reason,omitempty"`
	Message string `json:"message,omitempty"`
}

// TaskInfo 是 ProductTask CR 的读取视图。
type TaskInfo struct {
	Name      string `json:"name"`
	ReleaseID string `json:"releaseID,omitempty"`
	OpsType   string `json:"opsType,omitempty"`
	Phase     string `json:"phase,omitempty"`
	Component string `json:"component,omitempty"`
	// Resource 是作用对象的 "Kind/name" 简述
	Resource string   `json:"resource,omitempty"`
	Messages []string `json:"messages,omitempty"`
	// After 是前置任务名
	After []string `json:"after,omitempty"`
}

// =============================================================================
// 读取方法
// =============================================================================

// GetAppCR 读取 App CR。找不到时返回 (nil, nil)，由调用方决定兜底。
func (k *KubeClient) GetAppCR(namespace, name string) (*AppInfo, error) {
	obj, err := k.dynamicClient.Resource(appGVR).Namespace(namespace).Get(context.Background(), name, metav1.GetOptions{})
	if err != nil {
		if isNotFound(err) {
			return nil, nil
		}
		return nil, err
	}

	info := &AppInfo{
		Name:              obj.GetName(),
		DisplayName:       nestedString(obj, "spec", "displayName"),
		Version:           nestedString(obj, "spec", "version"),
		ReleaseID:         nestedString(obj, "spec", "releaseID"),
		PlanRevision:      nestedString(obj, "spec", "planRevision"),
		Phase:             nestedString(obj, "status", "phase"),
		ObservedReleaseID: nestedString(obj, "status", "observedReleaseID"),
	}

	if s, found, _ := unstructured.NestedMap(obj.Object, "status", "aggregatedSummary"); found {
		info.Summary = &AggregateSummary{
			TotalServices:   asInt(s["totalServices"]),
			ReadyServices:   asInt(s["readyServices"]),
			TotalComponents: asInt(s["totalComponents"]),
			ReadyComponents: asInt(s["readyComponents"]),
		}
	}

	services, _, _ := unstructured.NestedSlice(obj.Object, "status", "serviceStatuses")
	for _, raw := range services {
		m, ok := raw.(map[string]interface{})
		if !ok {
			continue
		}
		info.Services = append(info.Services, ServiceStatusVO{
			Name:              asString(m["name"]),
			Phase:             asString(m["phase"]),
			ReleaseID:         asString(m["releaseID"]),
			ObservedReleaseID: asString(m["observedReleaseID"]),
			ErrorMessage:      asString(m["errorMessage"]),
		})
	}
	return info, nil
}

// ListCloudComponents 列出命名空间下的 CloudComponent CR，并带出 Pod 明细与差异。
func (k *KubeClient) ListCloudComponents(namespace string) ([]ComponentInfo, error) {
	list, err := k.dynamicClient.Resource(ccGVR).Namespace(namespace).List(context.Background(), metav1.ListOptions{})
	if err != nil {
		return nil, err
	}

	out := make([]ComponentInfo, 0, len(list.Items))
	for i := range list.Items {
		out = append(out, toComponentInfo(&list.Items[i]))
	}
	return out, nil
}

// ListProductTasks 列出某个 releaseID 下的 ProductTask（集群级，不带 namespace）。
func (k *KubeClient) ListProductTasks(releaseID string) ([]TaskInfo, error) {
	opts := metav1.ListOptions{}
	if releaseID != "" {
		// 用字段选择器在服务端过滤，避免把集群里所有任务拉回来
		opts = metav1.ListOptions{FieldSelector: "spec.releaseID=" + releaseID}
	}

	list, err := k.dynamicClient.Resource(ptGVR).List(context.Background(), opts)
	if err != nil {
		if isNotFound(err) {
			// CRD 未安装（老集群）不视为错误
			return nil, nil
		}
		return nil, err
	}

	out := make([]TaskInfo, 0, len(list.Items))
	for i := range list.Items {
		out = append(out, toTaskInfo(&list.Items[i]))
	}
	return out, nil
}

// =============================================================================
// 转换与取值辅助
// =============================================================================

func toComponentInfo(obj *unstructured.Unstructured) ComponentInfo {
	info := ComponentInfo{
		Name:              obj.GetName(),
		DisplayName:       nestedString(obj, "spec", "displayName"),
		ComponentType:     nestedString(obj, "spec", "componentType"),
		ChartName:         nestedString(obj, "spec", "chartName"),
		ChartVersion:      nestedString(obj, "spec", "chartVersion"),
		Phase:             nestedString(obj, "status", "phase"),
		ReleaseID:         nestedString(obj, "spec", "releaseID"),
		ObservedReleaseID: nestedString(obj, "status", "observedReleaseID"),
	}
	if release := nestedString(obj, "status", "helmReleaseName"); release != "" {
		info.HelmRelease = release
	}

	if m, found, _ := unstructured.NestedMap(obj.Object, "status", "workloadStatus"); found {
		info.Summary = &WorkloadSummary{
			TotalWorkloads:    asInt(m["totalWorkloads"]),
			ReadyWorkloads:    asInt(m["readyWorkloads"]),
			DegradedWorkloads: asInt(m["degradedWorkloads"]),
		}
	}

	pods, _, _ := unstructured.NestedSlice(obj.Object, "status", "podsDetail")
	for _, raw := range pods {
		m, ok := raw.(map[string]interface{})
		if !ok {
			continue
		}
		info.Pods = append(info.Pods, PodDetailVO{
			Name:            asString(m["name"]),
			Namespace:       asString(m["namespace"]),
			IP:              asString(m["ip"]),
			HostIP:          asString(m["hostIP"]),
			WorkloadKind:    asString(m["workloadKind"]),
			WorkloadName:    asString(m["workloadName"]),
			Ready:           asBool(m["ready"]),
			Phase:           asString(m["phase"]),
			State:           asString(m["state"]),
			Restarts:        asInt(m["restarts"]),
			UpdatedRevision: asBool(m["updatedRevision"]),
			Message:         asString(m["message"]),
		})
	}

	diffs, _, _ := unstructured.NestedSlice(obj.Object, "status", "workloadDiffs")
	for _, raw := range diffs {
		m, ok := raw.(map[string]interface{})
		if !ok {
			continue
		}
		info.Diffs = append(info.Diffs, WorkloadDiffVO{
			Kind:      asString(m["kind"]),
			Name:      asString(m["name"]),
			Namespace: asString(m["namespace"]),
			Path:      asString(m["path"]),
			Expected:  asString(m["expected"]),
			Current:   asString(m["current"]),
			Message:   asString(m["message"]),
		})
	}

	conds, _, _ := unstructured.NestedSlice(obj.Object, "status", "conditions")
	for _, raw := range conds {
		m, ok := raw.(map[string]interface{})
		if !ok {
			continue
		}
		info.Conditions = append(info.Conditions, ConditionVO{
			Type:    asString(m["type"]),
			Status:  asString(m["status"]),
			Reason:  asString(m["reason"]),
			Message: asString(m["message"]),
		})
	}

	return info
}

func toTaskInfo(obj *unstructured.Unstructured) TaskInfo {
	info := TaskInfo{
		Name:      obj.GetName(),
		ReleaseID: nestedString(obj, "spec", "releaseID"),
		OpsType:   nestedString(obj, "spec", "opsType"),
		Phase:     nestedString(obj, "status", "phase"),
	}

	if ref, found, _ := unstructured.NestedMap(obj.Object, "spec", "refComponent"); found {
		if c := asString(ref["component"]); c != "" {
			info.Component = c
		}
	}
	if res, found, _ := unstructured.NestedMap(obj.Object, "spec", "resource"); found {
		kind, name := asString(res["kind"]), asString(res["name"])
		if kind != "" || name != "" {
			info.Resource = fmt.Sprintf("%s/%s", kind, name)
		}
	}
	if ord, found, _ := unstructured.NestedMap(obj.Object, "spec", "taskOrder"); found {
		if after, ok := ord["after"].([]interface{}); ok {
			for _, a := range after {
				if s := asString(a); s != "" {
					info.After = append(info.After, s)
				}
			}
		}
	}

	msgs, _, _ := unstructured.NestedSlice(obj.Object, "status", "messages")
	for _, raw := range msgs {
		m, ok := raw.(map[string]interface{})
		if !ok {
			continue
		}
		if s := asString(m["message"]); s != "" {
			info.Messages = append(info.Messages, s)
		}
	}
	// 只保留最近几条，避免响应体过长
	if len(info.Messages) > 5 {
		info.Messages = info.Messages[len(info.Messages)-5:]
	}
	return info
}

// nestedString 取嵌套字符串字段。
func nestedString(obj *unstructured.Unstructured, fields ...string) string {
	v, found, err := unstructured.NestedString(obj.Object, fields...)
	if err != nil || !found {
		return ""
	}
	return v
}

func asString(v interface{}) string {
	if s, ok := v.(string); ok {
		return s
	}
	return ""
}

func asBool(v interface{}) bool {
	if b, ok := v.(bool); ok {
		return b
	}
	return false
}

func asInt(v interface{}) int {
	switch n := v.(type) {
	case int64:
		return int(n)
	case float64:
		return int(n)
	case int:
		return n
	}
	return 0
}
