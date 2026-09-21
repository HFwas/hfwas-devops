package status

import (
	"context"
	"fmt"

	appsv1 "k8s.io/api/apps/v1"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
	"github.com/hfwas/cn-app-operator/pkg/labelmarker"
)

// AggregateWorkloads 聚合 CloudComponent 的所有 Workload 状态。
func AggregateWorkloads(ctx context.Context, c client.Client, cc *deliveryv1.CloudComponent) (*deliveryv1.WorkloadStatus, error) {
	logger := log.FromContext(ctx)
	logger.Info("Aggregating workload status", "component", cc.Name)

	ws := &deliveryv1.WorkloadStatus{}
	selector := labelmarker.ComponentLabelSelector(cc.Name)

	// 聚合 Deployment
	var deploys appsv1.DeploymentList
	if err := c.List(ctx, &deploys, selector); err != nil {
		logger.Error(err, "Failed to list Deployments")
	} else {
		for i := range deploys.Items {
			d := &deploys.Items[i]
			ready := d.Status.ReadyReplicas >= d.Status.Replicas
			ws.TotalWorkloads++
			if ready {
				ws.ReadyWorkloads++
			} else {
				ws.DegradedWorkloads++
			}
			ws.Workloads = append(ws.Workloads, deliveryv1.WorkloadInfo{
				Kind:       "Deployment",
				Name:       d.Name,
				Namespace:  d.Namespace,
				APIVersion: d.APIVersion,
				Ready:      ready,
				Status:     formatDeploymentStatus(&d.Status),
			})
		}
	}

	// 聚合 StatefulSet
	var stss appsv1.StatefulSetList
	if err := c.List(ctx, &stss, selector); err != nil {
		logger.Error(err, "Failed to list StatefulSets")
	} else {
		for i := range stss.Items {
			s := &stss.Items[i]
			ready := s.Status.ReadyReplicas >= s.Status.Replicas
			ws.TotalWorkloads++
			if ready {
				ws.ReadyWorkloads++
			} else {
				ws.DegradedWorkloads++
			}
			ws.Workloads = append(ws.Workloads, deliveryv1.WorkloadInfo{
				Kind:       "StatefulSet",
				Name:       s.Name,
				Namespace:  s.Namespace,
				APIVersion: s.APIVersion,
				Ready:      ready,
				Status:     fmt.Sprintf("%d/%d", s.Status.ReadyReplicas, s.Status.Replicas),
			})
		}
	}

	// 聚合 DaemonSet
	var dss appsv1.DaemonSetList
	if err := c.List(ctx, &dss, selector); err != nil {
		logger.Error(err, "Failed to list DaemonSets")
	} else {
		for i := range dss.Items {
			ds := &dss.Items[i]
			ready := ds.Status.NumberReady >= ds.Status.DesiredNumberScheduled
			ws.TotalWorkloads++
			if ready {
				ws.ReadyWorkloads++
			} else {
				ws.DegradedWorkloads++
			}
			ws.Workloads = append(ws.Workloads, deliveryv1.WorkloadInfo{
				Kind:       "DaemonSet",
				Name:       ds.Name,
				Namespace:  ds.Namespace,
				APIVersion: ds.APIVersion,
				Ready:      ready,
				Status:     fmt.Sprintf("%d/%d", ds.Status.NumberReady, ds.Status.DesiredNumberScheduled),
			})
		}
	}

	return ws, nil
}

// formatDeploymentStatus 格式化 Deployment 状态字符串。
func formatDeploymentStatus(status *appsv1.DeploymentStatus) string {
	return fmt.Sprintf("%d/%d", status.ReadyReplicas, status.Replicas)
}

