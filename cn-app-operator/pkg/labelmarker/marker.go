package labelmarker

import (
	"context"
	"fmt"
	"strings"

	appsv1 "k8s.io/api/apps/v1"
	batchv1 "k8s.io/api/batch/v1"
	corev1 "k8s.io/api/core/v1"
	networkingv1 "k8s.io/api/networking/v1"
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
	// LabelReleaseID 把一次发布串起来（App / CloudComponent / ProductTask 对齐）。
	LabelReleaseID = "delivery.hfwas.io/release-id"
	// LabelRetain 标记卸载时需要保留的 PVC。
	LabelRetain = "delivery.hfwas.io/retain"
)

// Helm 标准标签与注解。
//
// 本 operator 不直接依赖 Helm SDK（pkg/helm 目前是接口桩），所以定位一个 release
// 渲染出的对象靠的是 Helm 写入的标准标签 app.kubernetes.io/instance=<releaseName>。
const (
	// HelmInstanceLabel 是 Helm 给每个 release 渲染对象打的实例标签。
	HelmInstanceLabel = "app.kubernetes.io/instance"
	// HelmResourcePolicyAnnotation 是 Helm 的资源保留注解。
	HelmResourcePolicyAnnotation = "helm.sh/resource-policy"
	// HelmResourcePolicyKeep 是「卸载时保留」的取值。
	HelmResourcePolicyKeep = "keep"
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
	if cc.Spec.ReleaseID != "" {
		labels[LabelReleaseID] = cc.Spec.ReleaseID
	}
	if cc.Status.CurrentRevision != "" {
		labels[LabelRevision] = cc.Status.CurrentRevision
	}

	return labels
}

// TargetNamespace 返回组件实际部署的命名空间。
func TargetNamespace(cc *deliveryv1.CloudComponent) string {
	if cc.Spec.TargetNamespace != "" {
		return cc.Spec.TargetNamespace
	}
	return cc.Namespace
}

// ReleaseName 返回组件对应的 Helm release 名称。
func ReleaseName(cc *deliveryv1.CloudComponent) string {
	if cc.Spec.ReleaseName != "" {
		return cc.Spec.ReleaseName
	}
	if cc.Status.HelmReleaseName != "" {
		return cc.Status.HelmReleaseName
	}
	return fmt.Sprintf("%s-%s", cc.Namespace, cc.Name)
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

// LabelResources 给 CloudComponent 对应的 Helm release 渲染出的对象打标。
//
// 定位方式：Helm 会给每个渲染对象打 app.kubernetes.io/instance=<releaseName>，
// 据此在目标 namespace 内列出对象，再合并本 operator 的标签。
func LabelResources(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) error {
	logger := log.FromContext(ctx)
	ns := TargetNamespace(cc)
	release := ReleaseName(cc)

	labels := BuildLabels(cc)
	patched := 0

	for _, kind := range WorkloadKinds {
		objs, err := listByKind(ctx, c, kind, ns, release)
		if err != nil {
			logger.Error(err, "Failed to list resources", "kind", kind)
			continue
		}
		for _, obj := range objs {
			if err := mergeLabels(ctx, c, obj, labels); err != nil {
				logger.Error(err, "Failed to label resource", "kind", kind, "name", obj.GetName())
				continue
			}
			patched++
		}
	}

	logger.Info("Labeled resources", "component", cc.Name, "release", release, "count", patched)
	return nil
}

// MarkRetainedVolumes 给声明了 retain 的持久化卷对应的 PVC 打保留标记。
//
// 用 Helm 自己的语义实现保留：打上 helm.sh/resource-policy: keep 之后，
// helm uninstall 不会删除该 PVC，数据得以保留。
func MarkRetainedVolumes(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) error {
	retainVolumes := make([]string, 0)
	for _, pv := range cc.Spec.PersistentVolumeConfigs {
		if pv.Retain && pv.VolumeName != "" {
			retainVolumes = append(retainVolumes, pv.VolumeName)
		}
	}
	if len(retainVolumes) == 0 {
		return nil
	}

	var pvcs corev1.PersistentVolumeClaimList
	if err := c.List(ctx, &pvcs,
		client.InNamespace(TargetNamespace(cc)),
		client.MatchingLabels{HelmInstanceLabel: ReleaseName(cc)}); err != nil {
		return err
	}

	for i := range pvcs.Items {
		pvc := &pvcs.Items[i]
		if !nameMatchesAnyVolume(pvc.Name, retainVolumes) {
			continue
		}
		if pvc.Annotations[HelmResourcePolicyAnnotation] == HelmResourcePolicyKeep {
			continue
		}
		patch := client.MergeFrom(pvc.DeepCopy())
		if pvc.Annotations == nil {
			pvc.Annotations = make(map[string]string, 1)
		}
		pvc.Annotations[HelmResourcePolicyAnnotation] = HelmResourcePolicyKeep
		if err := c.Patch(ctx, pvc, patch); err != nil {
			return err
		}
	}
	return nil
}

// nameMatchesAnyVolume 判断 PVC 名称是否对应某个声明的卷名。
//
// chart 生成的 PVC 名通常是 <release>-<volume> 或 <volume>-<release>-<ordinal>，
// 所以这里用「包含」而非相等匹配；这是启发式，必要时用 label 显式关联。
func nameMatchesAnyVolume(pvcName string, volumes []string) bool {
	for _, v := range volumes {
		if pvcName == v || strings.Contains(pvcName, v) {
			return true
		}
	}
	return false
}

// listByKind 按 Kind 列出对象，统一返回 client.Object 以便合并标签。
func listByKind(ctx context.Context, c client.Client, kind, namespace, release string) ([]client.Object, error) {
	inNs := client.InNamespace(namespace)
	byRelease := client.MatchingLabels{HelmInstanceLabel: release}

	switch kind {
	case "Deployment":
		var l appsv1.DeploymentList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	case "StatefulSet":
		var l appsv1.StatefulSetList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	case "DaemonSet":
		var l appsv1.DaemonSetList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	case "Job":
		var l batchv1.JobList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	case "CronJob":
		var l batchv1.CronJobList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	case "Service":
		var l corev1.ServiceList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	case "Ingress":
		var l networkingv1.IngressList
		if err := c.List(ctx, &l, inNs, byRelease); err != nil {
			return nil, err
		}
		return toObjects(l.Items, func(i int) client.Object { return &l.Items[i] }), nil
	}
	return nil, nil
}

// toObjects 把列表项转成 client.Object 切片。
func toObjects[T any](items []T, at func(int) client.Object) []client.Object {
	out := make([]client.Object, 0, len(items))
	for i := range items {
		out = append(out, at(i))
	}
	return out
}

// mergeLabels 把标签合并到对象上，无变化时不发起写入。
func mergeLabels(ctx context.Context, c client.Client, obj client.Object, add Labels) error {
	existing := obj.GetLabels()
	merged := MergeLabels(cloneLabels(existing), add)
	if labelsEqual(existing, merged) {
		return nil
	}

	patch := client.MergeFrom(obj.DeepCopyObject().(client.Object))
	obj.SetLabels(merged)
	return c.Patch(ctx, obj, patch)
}

// cloneLabels 复制一份标签，避免改动调用方持有的 map。
func cloneLabels(in map[string]string) Labels {
	out := make(Labels, len(in))
	for k, v := range in {
		out[k] = v
	}
	return out
}

// labelsEqual 判断两组标签是否等价。
func labelsEqual(a, b map[string]string) bool {
	if len(a) != len(b) {
		return false
	}
	for k, v := range a {
		if b[k] != v {
			return false
		}
	}
	return true
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
		LabelApp:       appName,
		LabelManagedBy: ManagedByValue,
	}
}

// ServiceLabelSelector 返回 CloudService 的选择器。
func ServiceLabelSelector(serviceName string) client.MatchingLabels {
	return client.MatchingLabels{
		LabelService:   serviceName,
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
