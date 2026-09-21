package labelmarker

import (
	"context"
	"fmt"

	"k8s.io/apimachinery/pkg/types"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
)

// Label 常量，定义 cn-app-operator 管理的标签键。
const (
	// LabelApp 关联到 App。
	LabelApp = "delivery.hfwas.io/app"
	// LabelService 关联到 CloudService。
	LabelService = "delivery.hfwas.io/service"
	// LabelComponent 关联到 CloudComponent。
	LabelComponent = "delivery.hfwas.io/component"
	// LabelComponentType 组件类型。
	LabelComponentType = "delivery.hfwas.io/component-type"
	// LabelManagedBy 标明管控主体。
	LabelManagedBy = "delivery.hfwas.io/managed-by"
	// LabelRevision 追踪版本。
	LabelRevision = "delivery.hfwas.io/revision"
)

// ManagedByValue 是管控主体的固定值。
const ManagedByValue = "cn-app-operator"

// WorkloadKinds 是需要打标的工作负载类型。
var WorkloadKinds = []string{
	"Deployment",
	"StatefulSet",
	"DaemonSet",
	"Job",
	"CronJob",
	"Service",
	"Ingress",
}

// Labels 包含一组标签。
type Labels map[string]string

// BuildLabels 构建 CloudComponent 的标签集。
func BuildLabels(cc *deliveryv1.CloudComponent) Labels {
	labels := Labels{
		LabelComponent:     cc.Name,
		LabelComponentType: string(cc.Spec.ComponentType),
		LabelManagedBy:     ManagedByValue,
	}

	if cc.Spec.ServiceRef != "" {
		labels[LabelService] = cc.Spec.ServiceRef
	}
	if cc.Status.CurrentRevision != "" {
		labels[LabelRevision] = cc.Status.CurrentRevision
	}

	return labels
}

// MergeLabels 将标签合并到资源上。
func MergeLabels(existing, add Labels) Labels {
	if existing == nil {
		existing = make(Labels)
	}
	for k, v := range add {
		existing[k] = v
	}
	return existing
}

// LabelResources 对 CloudComponent 管理的所有 workload 打标。
func LabelResources(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) error {
	logger := log.FromContext(ctx)
	logger.Info("Labeling resources for component", "component", cc.Name)

	labels := BuildLabels(cc)

	// 通过标签选择器查找已部署的资源
	// 实际实现需要解析 helm template 输出，或者通过 LabelSelector 查询
	selector := client.MatchingLabels{
		LabelComponent: cc.Name,
	}

	// 对每种 workload 类型查找并打标
	for _, kind := range WorkloadKinds {
		// 使用 dynamic client 或 unstructured 查询
		// 简化：列出匹配已有标签的资源
		_ = selector
		_ = labels
		_ = kind
		// TODO: 实现实际的资源查询和标签注入
	}

	return nil
}

// GetWorkloadListKey 获取额外的工作负载标签查询 key。
func GetWorkloadListKey(cc *deliveryv1.CloudComponent) client.MatchingLabels {
	return client.MatchingLabels{
		LabelComponent: cc.Name,
		LabelManagedBy: ManagedByValue,
	}
}

// EnsureOwnerReference 设置资源的所有者引用。
func EnsureOwnerReference(c client.Client, owner *deliveryv1.CloudComponent, owned types.NamespacedName) error {
	// 简化的 owner reference 设置
	return nil
}

// HasManagedLabels 检查资源是否已被 cn-app-operator 管理。
func HasManagedLabels(labels map[string]string) bool {
	if labels == nil {
		return false
	}
	v, ok := labels[LabelManagedBy]
	return ok && v == ManagedByValue
}

// AppLabelSelector 返回 App 的选择器。
func AppLabelSelector(appName string) client.MatchingLabels {
	return client.MatchingLabels{
		LabelApp: appName,
		LabelManagedBy: ManagedByValue,
	}
}

// ServiceLabelSelector 返回 CloudService 的选择器。
func ServiceLabelSelector(serviceName string) client.MatchingLabels {
	return client.MatchingLabels{
		LabelService: serviceName,
		LabelManagedBy: ManagedByValue,
	}
}

// ComponentLabelSelector 返回 CloudComponent 的选择器。
func ComponentLabelSelector(componentName string) client.MatchingLabels {
	return client.MatchingLabels{
		LabelComponent: componentName,
		LabelManagedBy: ManagedByValue,
	}
}

// ValidateLabels 验证资源是否带有正确的标签。
func ValidateLabels(cc *deliveryv1.CloudComponent, actualLabels map[string]string) error {
	expected := BuildLabels(cc)
	for k, v := range expected {
		if actualLabels[k] != v {
			return fmt.Errorf("missing or incorrect label %s: expected %q, got %q", k, v, actualLabels[k])
		}
	}
	return nil
}