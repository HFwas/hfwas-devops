package controllers

import (
	"context"
	"fmt"
	"time"

	batchv1 "k8s.io/api/batch/v1"
	corev1 "k8s.io/api/core/v1"
	"k8s.io/apimachinery/pkg/api/errors"
	metav1 "k8s.io/apimachinery/pkg/apis/meta/v1"
	"k8s.io/apimachinery/pkg/apis/meta/v1/unstructured"
	"k8s.io/apimachinery/pkg/runtime"
	"k8s.io/apimachinery/pkg/runtime/schema"
	ctrl "sigs.k8s.io/controller-runtime"
	"sigs.k8s.io/controller-runtime/pkg/client"
	"sigs.k8s.io/controller-runtime/pkg/log"

	deliveryv1 "github.com/hfwas/cn-app-operator/api/v1"
	"github.com/hfwas/cn-app-operator/pkg/tasks"
)

// ProductTaskReconciler 调和集群级 ProductTask CR。
//
// 职责：把「某一步骤要排在其他步骤之后」的声明，解析成 status.phase。
// CloudComponent 控制器会读这个 phase 来决定是否放行组件。
type ProductTaskReconciler struct {
	client.Client
	Scheme *runtime.Scheme
}

// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=producttasks,verbs=get;list;watch;create;update;patch;delete
// +kubebuilder:rbac:groups=delivery.hfwas.io,resources=producttasks/status,verbs=get;update;patch
// +kubebuilder:rbac:groups=batch,resources=jobs,verbs=get;list;watch

// Reconcile 处理 ProductTask 的调和循环。
func (r *ProductTaskReconciler) Reconcile(ctx context.Context, req ctrl.Request) (ctrl.Result, error) {
	logger := log.FromContext(ctx).WithValues("producttask", req.Name)
	logger.V(1).Info("Starting reconcile")

	task := &deliveryv1.ProductTask{}
	if err := r.Get(ctx, req.NamespacedName, task); err != nil {
		if errors.IsNotFound(err) {
			return ctrl.Result{}, nil
		}
		return ctrl.Result{}, err
	}

	if !task.DeletionTimestamp.IsZero() {
		return ctrl.Result{}, nil
	}

	siblings, err := r.siblings(ctx, task)
	if err != nil {
		return ctrl.Result{}, err
	}

	// 顺序自检：有环就记到 condition 上，不阻塞其它任务
	if _, err := tasks.Order(siblings); err != nil {
		_ = r.setCondition(ctx, task, deliveryv1.ConditionDegraded, metav1.ConditionTrue,
			"CircularTaskOrder", err.Error())
		logger.Error(err, "Circular task order detected")
	}

	byName := tasks.ByName(siblings)

	// 已到终态则不再推进
	if task.Status.Phase == deliveryv1.TaskPhaseSucceeded || task.Status.Phase == deliveryv1.TaskPhaseFailed {
		return ctrl.Result{}, nil
	}

	// 前置未完成 → Pending
	if ready, reason := tasks.ReadyToRun(task, byName); !ready {
		return r.setPhase(ctx, task, deliveryv1.TaskPhasePending, reason)
	}

	// 没有作用对象：纯顺序标记，直接完成，让后继任务可以跑
	if task.Spec.Resource == nil || task.Spec.Resource.Kind == "" {
		return r.setPhase(ctx, task, deliveryv1.TaskPhaseSucceeded, "无作用对象，作为顺序标记直接完成")
	}

	phase, reason, err := r.evalResource(ctx, task)
	if err != nil {
		logger.Error(err, "Failed to evaluate task resource")
		return ctrl.Result{}, err
	}

	// 未完成的任务定期复查
	result := ctrl.Result{}
	if phase != deliveryv1.TaskPhaseSucceeded && phase != deliveryv1.TaskPhaseFailed {
		result.RequeueAfter = 15 * time.Second
	}
	_, err = r.setPhase(ctx, task, phase, reason)
	return result, err
}

// siblings 返回同一 releaseID 下的全部任务（含自身）。
func (r *ProductTaskReconciler) siblings(ctx context.Context, task *deliveryv1.ProductTask) ([]deliveryv1.ProductTask, error) {
	var list deliveryv1.ProductTaskList
	if err := r.List(ctx, &list); err != nil {
		return nil, err
	}

	out := make([]deliveryv1.ProductTask, 0, len(list.Items))
	for i := range list.Items {
		// 未指定 releaseID 的任务视为自成一组，避免误并入别的发布
		if list.Items[i].Spec.ReleaseID == task.Spec.ReleaseID {
			out = append(out, list.Items[i])
		}
	}
	return out, nil
}

// evalResource 读取任务作用对象的完成状态。
//
// Job 有明确的完成信号，按 status.conditions 判定；
// 其余 Kind 只能断言「对象已存在」——完成与否依赖各控制器自己的语义，
// 这里按存在即满足处理，避免任务永久卡在 Running。
func (r *ProductTaskReconciler) evalResource(ctx context.Context, task *deliveryv1.ProductTask) (deliveryv1.TaskPhase, string, error) {
	res := task.Spec.Resource

	if res.Kind == "Job" {
		return r.evalJob(ctx, res)
	}
	return r.evalByExistence(ctx, res)
}

// evalJob 按 Job 的完成条件判定。
func (r *ProductTaskReconciler) evalJob(ctx context.Context, res *deliveryv1.TaskResource) (deliveryv1.TaskPhase, string, error) {
	var job batchv1.Job
	key := client.ObjectKey{Namespace: res.Namespace, Name: res.Name}
	if err := r.Get(ctx, key, &job); err != nil {
		if errors.IsNotFound(err) {
			return deliveryv1.TaskPhasePending, fmt.Sprintf("Job %s/%s 尚未创建", res.Namespace, res.Name), nil
		}
		return deliveryv1.TaskPhaseFailed, "", err
	}

	for _, cond := range job.Status.Conditions {
		if cond.Type == batchv1.JobComplete && cond.Status == corev1.ConditionTrue {
			return deliveryv1.TaskPhaseSucceeded, fmt.Sprintf("Job %s 已完成", job.Name), nil
		}
		if cond.Type == batchv1.JobFailed && cond.Status == corev1.ConditionTrue {
			return deliveryv1.TaskPhaseFailed, fmt.Sprintf("Job %s 失败: %s", job.Name, cond.Message), nil
		}
	}

	desired := int32(1)
	if job.Spec.Completions != nil {
		desired = *job.Spec.Completions
	}
	return deliveryv1.TaskPhaseRunning,
		fmt.Sprintf("Job %s 运行中（%d/%d 成功）", job.Name, job.Status.Succeeded, desired), nil
}

// evalByExistence 非 Job 类型只断言对象是否存在。
func (r *ProductTaskReconciler) evalByExistence(ctx context.Context, res *deliveryv1.TaskResource) (deliveryv1.TaskPhase, string, error) {
	obj := &unstructured.Unstructured{}
	obj.SetGroupVersionKind(schema.GroupVersionKind{
		Group:   res.Group,
		Version: res.Version,
		Kind:    res.Kind,
	})

	key := client.ObjectKey{Namespace: res.Namespace, Name: res.Name}
	if err := r.Get(ctx, key, obj); err != nil {
		if errors.IsNotFound(err) {
			return deliveryv1.TaskPhasePending, fmt.Sprintf("%s %s/%s 尚未创建", res.Kind, res.Namespace, res.Name), nil
		}
		// 未注册的 Kind 无法读取，按存在即满足处理并在消息里说明
		if runtime.IsNotRegisteredError(err) {
			return deliveryv1.TaskPhaseSucceeded,
				fmt.Sprintf("%s 未注册到 scheme，跳过完成度判定", res.Kind), nil
		}
		return deliveryv1.TaskPhaseFailed, "", err
	}

	return deliveryv1.TaskPhaseSucceeded,
		fmt.Sprintf("%s %s/%s 已存在（非 Job 类型只做存在性判定）", res.Kind, res.Namespace, res.Name), nil
}

// setPhase 更新阶段并追加一条 message。
func (r *ProductTaskReconciler) setPhase(ctx context.Context, task *deliveryv1.ProductTask,
	phase deliveryv1.TaskPhase, message string) (ctrl.Result, error) {

	// 阶段和消息都没变就不写，避免 status 抖动触发无限调和
	if task.Status.Phase == phase && lastMessage(task) == message {
		return ctrl.Result{}, nil
	}

	now := metav1.Now()
	task.Status.Phase = phase
	task.Status.ObservedGeneration = task.Generation
	task.Status.Messages = append(task.Status.Messages, deliveryv1.TaskMessage{
		Message:            message,
		LastTransitionTime: now,
	})
	// messages 只保留最近 20 条
	if len(task.Status.Messages) > 20 {
		task.Status.Messages = task.Status.Messages[len(task.Status.Messages)-20:]
	}

	if err := r.Status().Update(ctx, task); err != nil {
		return ctrl.Result{}, err
	}

	condStatus := metav1.ConditionFalse
	switch phase {
	case deliveryv1.TaskPhaseSucceeded:
		condStatus = metav1.ConditionTrue
	case deliveryv1.TaskPhaseFailed:
		condStatus = metav1.ConditionTrue
	}
	_ = r.setCondition(ctx, task, deliveryv1.ConditionProgressing, condStatus,
		string(phase), message)

	return ctrl.Result{}, nil
}

// setCondition 写入/更新一条 condition。
func (r *ProductTaskReconciler) setCondition(ctx context.Context, task *deliveryv1.ProductTask,
	condType deliveryv1.ConditionType, status metav1.ConditionStatus,
	reason, message string) error {

	now := metav1.Now()
	for i := range task.Status.Conditions {
		if task.Status.Conditions[i].Type == condType {
			task.Status.Conditions[i].Status = status
			task.Status.Conditions[i].Reason = reason
			task.Status.Conditions[i].Message = message
			task.Status.Conditions[i].LastTransitionTime = now
			task.Status.Conditions[i].ObservedGeneration = task.Generation
			return r.Status().Update(ctx, task)
		}
	}

	task.Status.Conditions = append(task.Status.Conditions, deliveryv1.Condition{
		Type:               condType,
		Status:             status,
		Reason:             reason,
		Message:            message,
		LastTransitionTime: now,
		ObservedGeneration: task.Generation,
	})
	return r.Status().Update(ctx, task)
}

// lastMessage 返回最近一条 message。
func lastMessage(task *deliveryv1.ProductTask) string {
	if len(task.Status.Messages) == 0 {
		return ""
	}
	return task.Status.Messages[len(task.Status.Messages)-1].Message
}

// SetupWithManager 注册 Controller 到 Manager。
func (r *ProductTaskReconciler) SetupWithManager(mgr ctrl.Manager) error {
	return ctrl.NewControllerManagedBy(mgr).
		For(&deliveryv1.ProductTask{}).
		Complete(r)
}
