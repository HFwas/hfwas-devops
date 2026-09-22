package status

import (
	"context"
	"fmt"
	"sort"
	"strings"

	appsv1 "k8s.io/api/apps/v1"
	corev1 "k8s.io/api/core/v1"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
	"github.com/hfwas/cn-app-operator/pkg/labelmarker"
)

// Snapshot 是一次状态采集的结果。
//
//   - WorkloadStatus 回答「哪个 workload 没 ready」
//   - PodsDetail    回答「哪个 Pod 没起来、为什么、重启几次、是否切到新修订」
//   - WorkloadDiffs 回答「期望和实际差在哪」
//
// 三者在同一次 List 里采集，避免重复请求 apiserver。
type Snapshot struct {
	WorkloadStatus *deliveryv1.WorkloadStatus
	PodsDetail     []deliveryv1.PodDetail
	WorkloadDiffs  []deliveryv1.WorkloadDiff
}

// AggregateWorkloads 只取 workload 摘要，保留给不需要明细的调用方。
func AggregateWorkloads(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) (*deliveryv1.WorkloadStatus, error) {
	snap, err := Collect(ctx, c, cc)
	if err != nil {
		return nil, err
	}
	return snap.WorkloadStatus, nil
}

// Collect 采集 CloudComponent 的 workload 摘要、Pod 明细与期望/实际差异。
func Collect(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) (*Snapshot, error) {
	logger := log.FromContext(ctx)
	logger.Info("Collecting component status", "component", cc.Name)

	snap := &Snapshot{WorkloadStatus: &deliveryv1.WorkloadStatus{}}
	selector := labelmarker.ComponentLabelSelector(cc.Name)

	// Deployment 的「当前修订」要看它最新的 ReplicaSet，所以先把 RS 拉出来。
	rsByDeploy, err := replicaSetsByDeployment(ctx, c, cc)
	if err != nil {
		logger.Error(err, "Failed to list ReplicaSets")
	}

	// ---- Deployment ----
	var deploys appsv1.DeploymentList
	if err := c.List(ctx, &deploys, selector); err != nil {
		logger.Error(err, "Failed to list Deployments")
	} else {
		for i := range deploys.Items {
			d := &deploys.Items[i]
			ready := d.Status.Replicas > 0 && d.Status.ReadyReplicas >= d.Status.Replicas
			snap.recordWorkload("Deployment", d.Name, d.Namespace, d.APIVersion, ready,
				fmt.Sprintf("%d/%d", d.Status.ReadyReplicas, d.Status.Replicas))
			if d.Status.ReadyReplicas != d.Status.Replicas {
				snap.addDiff("Deployment", d.Name, d.Namespace, ".status.readyReplicas",
					fmt.Sprint(d.Status.Replicas), fmt.Sprint(d.Status.ReadyReplicas),
					"就绪副本数与期望副本数不一致")
			}
			if rs := rsByDeploy[d.Name]; rs != nil {
				for _, ctr := range d.Spec.Template.Spec.Containers {
					snap.addImageDiff("Deployment", d.Name, d.Namespace, ctr.Name, ctr.Image,
						rs.Spec.Template.Spec.Containers)
				}
			}
		}
	}

	// ---- StatefulSet ----
	var stss appsv1.StatefulSetList
	if err := c.List(ctx, &stss, selector); err != nil {
		logger.Error(err, "Failed to list StatefulSets")
	} else {
		for i := range stss.Items {
			s := &stss.Items[i]
			ready := s.Status.Replicas > 0 && s.Status.ReadyReplicas >= s.Status.Replicas
			snap.recordWorkload("StatefulSet", s.Name, s.Namespace, s.APIVersion, ready,
				fmt.Sprintf("%d/%d", s.Status.ReadyReplicas, s.Status.Replicas))
			if s.Status.ReadyReplicas != s.Status.Replicas {
				snap.addDiff("StatefulSet", s.Name, s.Namespace, ".status.readyReplicas",
					fmt.Sprint(s.Status.Replicas), fmt.Sprint(s.Status.ReadyReplicas),
					"就绪副本数与期望副本数不一致")
			}
		}
	}

	// ---- DaemonSet ----
	var dss appsv1.DaemonSetList
	if err := c.List(ctx, &dss, selector); err != nil {
		logger.Error(err, "Failed to list DaemonSets")
	} else {
		for i := range dss.Items {
			ds := &dss.Items[i]
			ready := ds.Status.NumberReady >= ds.Status.DesiredNumberScheduled
			snap.recordWorkload("DaemonSet", ds.Name, ds.Namespace, ds.APIVersion, ready,
				fmt.Sprintf("%d/%d", ds.Status.NumberReady, ds.Status.DesiredNumberScheduled))
			if ds.Status.NumberReady != ds.Status.DesiredNumberScheduled {
				snap.addDiff("DaemonSet", ds.Name, ds.Namespace, ".status.numberReady",
					fmt.Sprint(ds.Status.DesiredNumberScheduled), fmt.Sprint(ds.Status.NumberReady),
					"就绪 Pod 数与期望数不一致")
			}
		}
	}

	// ---- Pod 明细 ----
	pods, err := collectPods(ctx, c, cc, rsByDeploy)
	if err != nil {
		logger.Error(err, "Failed to list Pods")
	}
	snap.PodsDetail = pods

	return snap, nil
}

// replicaSetsByDeployment 返回 deployment 名称 → 其最新 ReplicaSet。
// 「最新」取 deployment.kubernetes.io/revision 注解最大者。
func replicaSetsByDeployment(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) (map[string]*appsv1.ReplicaSet, error) {
	var rsList appsv1.ReplicaSetList
	if err := c.List(ctx, &rsList, labelmarker.ComponentLabelSelector(cc.Name)); err != nil {
		return nil, err
	}

	newest := make(map[string]*appsv1.ReplicaSet)
	newestRev := make(map[string]int64)
	for i := range rsList.Items {
		rs := &rsList.Items[i]
		deploy := ""
		for _, owner := range rs.OwnerReferences {
			if owner.Kind == "Deployment" {
				deploy = owner.Name
				break
			}
		}
		if deploy == "" {
			continue
		}
		rev := rs.Annotations["deployment.kubernetes.io/revision"]
		var revNum int64
		fmt.Sscanf(rev, "%d", &revNum)
		if cur, ok := newestRev[deploy]; !ok || revNum > cur {
			newest[deploy] = rs
			newestRev[deploy] = revNum
		}
	}
	return newest, nil
}

// collectPods 采集 Pod 明细。
func collectPods(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent,
	rsByDeploy map[string]*appsv1.ReplicaSet) ([]deliveryv1.PodDetail, error) {

	var pods corev1.PodList
	if err := c.List(ctx, &pods, labelmarker.ComponentLabelSelector(cc.Name)); err != nil {
		return nil, err
	}

	// StatefulSet 的当前修订直接读 status.currentRevision
	stsRevision := make(map[string]string)
	var stss appsv1.StatefulSetList
	if err := c.List(ctx, &stss, labelmarker.ComponentLabelSelector(cc.Name)); err == nil {
		for i := range stss.Items {
			stsRevision[stss.Items[i].Name] = stss.Items[i].Status.CurrentRevision
		}
	}

	out := make([]deliveryv1.PodDetail, 0, len(pods.Items))
	for i := range pods.Items {
		p := &pods.Items[i]
		kind, name := owningWorkload(p, rsByDeploy)

		detail := deliveryv1.PodDetail{
			Name:         p.Name,
			Namespace:    p.Namespace,
			IP:           p.Status.PodIP,
			HostIP:       p.Status.HostIP,
			WorkloadKind: kind,
			WorkloadName: name,
			Ready:        podReady(p),
			Phase:        string(p.Status.Phase),
			State:        containerStateSummary(p),
			Restarts:     totalRestarts(p),
		}
		detail.UpdatedRevision = podOnCurrentRevision(p, kind, name, stsRevision, rsByDeploy)
		if !detail.Ready {
			detail.Message = podNotReadyReason(p)
		}
		out = append(out, detail)
	}

	// 输出稳定：按 workload 名 + Pod 名排序，避免 status 抖动
	sort.Slice(out, func(i, j int) bool {
		if out[i].WorkloadName != out[j].WorkloadName {
			return out[i].WorkloadName < out[j].WorkloadName
		}
		return out[i].Name < out[j].Name
	})
	return out, nil
}

// owningWorkload 沿 ownerReferences 找到 Pod 所属的 workload。
// Pod 直接属于 StatefulSet / DaemonSet / Job；属于 Deployment 时中间隔一层 ReplicaSet。
func owningWorkload(p *corev1.Pod, rsByDeploy map[string]*appsv1.ReplicaSet) (string, string) {
	for _, owner := range p.OwnerReferences {
		switch owner.Kind {
		case "ReplicaSet":
			for _, rs := range rsByDeploy {
				if rs.Name == owner.Name {
					for _, rsOwner := range rs.OwnerReferences {
						if rsOwner.Kind == "Deployment" {
							return "Deployment", rsOwner.Name
						}
					}
				}
			}
			return "ReplicaSet", owner.Name
		case "StatefulSet", "DaemonSet", "Job":
			return owner.Kind, owner.Name
		}
	}
	return "", ""
}

// podOnCurrentRevision 判断 Pod 是否已切到 workload 的当前修订。
//
//   - StatefulSet：比对 Pod 的 controller-revision-hash 与 status.currentRevision
//   - Deployment：比对 Pod 的 pod-template-hash 与最新 ReplicaSet 的同一标签
//   - 其他：无法判定，返回 false
func podOnCurrentRevision(p *corev1.Pod, kind, name string, stsRevision map[string]string,
	rsByDeploy map[string]*appsv1.ReplicaSet) bool {

	switch kind {
	case "StatefulSet":
		want := stsRevision[name]
		if want == "" {
			return false
		}
		return p.Labels["controller-revision-hash"] == want
	case "Deployment":
		rs := rsByDeploy[name]
		if rs == nil {
			return false
		}
		want := rs.Labels["pod-template-hash"]
		if want == "" {
			return false
		}
		return p.Labels["pod-template-hash"] == want
	}
	return false
}

// podReady 判断 Pod 是否所有容器就绪。
func podReady(p *corev1.Pod) bool {
	if p.Status.Phase != corev1.PodRunning && p.Status.Phase != corev1.PodSucceeded {
		return false
	}
	for i := range p.Status.ContainerStatuses {
		if !p.Status.ContainerStatuses[i].Ready {
			return false
		}
	}
	return true
}

// containerStateSummary 汇总容器状态，取第一个异常状态；全正常时返回 Running。
func containerStateSummary(p *corev1.Pod) string {
	for i := range p.Status.ContainerStatuses {
		cs := &p.Status.ContainerStatuses[i]
		if cs.State.Waiting != nil && cs.State.Waiting.Reason != "" {
			return cs.State.Waiting.Reason
		}
		if cs.State.Terminated != nil && cs.State.Terminated.Reason != "" {
			return cs.State.Terminated.Reason
		}
	}
	for i := range p.Status.ContainerStatuses {
		if p.Status.ContainerStatuses[i].State.Running != nil {
			return "Running"
		}
	}
	return string(p.Status.Phase)
}

// podNotReadyReason 给出未就绪的简要原因。
func podNotReadyReason(p *corev1.Pod) string {
	for _, cond := range p.Status.Conditions {
		if cond.Status == corev1.ConditionFalse &&
			(cond.Type == corev1.PodReady || cond.Type == corev1.PodScheduled || cond.Type == corev1.ContainersReady) {
			if cond.Message != "" {
				return fmt.Sprintf("%s: %s", cond.Type, cond.Message)
			}
			return fmt.Sprintf("%s: %s", cond.Type, cond.Reason)
		}
	}
	return ""
}

// totalRestarts 统计容器重启总次数。
func totalRestarts(p *corev1.Pod) int {
	total := 0
	for i := range p.Status.ContainerStatuses {
		total += int(p.Status.ContainerStatuses[i].RestartCount)
	}
	return total
}

// recordWorkload 追加一条 workload 摘要。
func (s *Snapshot) recordWorkload(kind, name, namespace, apiVersion string, ready bool, status string) {
	s.WorkloadStatus.TotalWorkloads++
	if ready {
		s.WorkloadStatus.ReadyWorkloads++
	} else {
		s.WorkloadStatus.DegradedWorkloads++
	}
	s.WorkloadStatus.Workloads = append(s.WorkloadStatus.Workloads, deliveryv1.WorkloadInfo{
		Kind:       kind,
		Name:       name,
		Namespace:  namespace,
		APIVersion: apiVersion,
		Ready:      ready,
		Status:     status,
	})
}

// addDiff 追加一条差异记录。
func (s *Snapshot) addDiff(kind, name, namespace, path, expected, current, message string) {
	s.WorkloadDiffs = append(s.WorkloadDiffs, deliveryv1.WorkloadDiff{
		Kind:      kind,
		Name:      name,
		Namespace: namespace,
		Path:      path,
		Expected:  expected,
		Current:   current,
		Message:   message,
	})
}

// addImageDiff 比对 workload 期望镜像与实际运行镜像。
// 这是「升级后 Pod 没切到新版本」的直接判据。
func (s *Snapshot) addImageDiff(kind, name, namespace, container, expectedImage string, actual []corev1.Container) {
	for i := range actual {
		if actual[i].Name != container {
			continue
		}
		if actual[i].Image != expectedImage {
			s.addDiff(kind, name, namespace,
				fmt.Sprintf(".containers[%s].image", container),
				expectedImage, actual[i].Image,
				"运行中的镜像与期望镜像不一致")
		}
		return
	}
}

// SummarizeWorkloadStatus 生成一行可读摘要，供日志与 kubectl 展示使用。
func SummarizeWorkloadStatus(ws *deliveryv1.WorkloadStatus) string {
	if ws == nil {
		return "no workload status"
	}
	notReady := make([]string, 0)
	for i := range ws.Workloads {
		if !ws.Workloads[i].Ready {
			notReady = append(notReady, fmt.Sprintf("%s/%s", ws.Workloads[i].Kind, ws.Workloads[i].Name))
		}
	}
	if len(notReady) == 0 {
		return fmt.Sprintf("%d/%d ready", ws.ReadyWorkloads, ws.TotalWorkloads)
	}
	return fmt.Sprintf("%d/%d ready, not ready: %s",
		ws.ReadyWorkloads, ws.TotalWorkloads, strings.Join(notReady, ", "))
}
